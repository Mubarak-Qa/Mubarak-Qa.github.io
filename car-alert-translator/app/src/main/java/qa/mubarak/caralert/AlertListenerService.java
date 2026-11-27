package qa.mubarak.caralert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * يستمع لتنبيهات التطبيقات المختارة، يترجم النص الصيني، ثم يعرض تنبيهاً جديداً مترجماً.
 */
public class AlertListenerService extends NotificationListenerService {
    static final String CHANNEL_ALERTS = "translated_alerts";
    static final String CHANNEL_STATUS = "translated_status";

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    /** آخر نص تمت ترجمته لكل تنبيه، لتجنّب التكرار عند تحديث التنبيه بنفس المحتوى. */
    private final Map<String, String> lastText = Collections.synchronizedMap(new HashMap<>());
    /** التنبيهات الأصلية التي أخفيناها نحن (حتى لا نحذف الترجمة عند إخفائها). */
    private final Set<String> hiddenByUs = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onCreate() {
        super.onCreate();
        createChannels(this);
    }

    static void createChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS, L.t(c, "ch_alerts"),
                NotificationManager.IMPORTANCE_HIGH);
        alerts.enableVibration(true);
        nm.createNotificationChannel(alerts);
        NotificationChannel status = new NotificationChannel(CHANNEL_STATUS, L.t(c, "ch_status"),
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(status);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        if (!Prefs.isTarget(this, sbn.getPackageName())) return;

        Notification n = sbn.getNotification();
        if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle ex = n.extras;
        String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
        String text = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (text.isEmpty()) text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
        if (text.isEmpty() && n.tickerText != null) text = n.tickerText.toString();

        if (!Translator.hasChinese(title) && !Translator.hasChinese(text)) return;

        final String key = sbn.getKey();
        final String signature = title + "\n" + text;
        if (signature.equals(lastText.get(key))) return;
        lastText.put(key, signature);

        final String fTitle = title, fText = text;
        final boolean ongoing = (n.flags & Notification.FLAG_ONGOING_EVENT) != 0;
        final PendingIntent open = n.contentIntent;
        final String pkg = sbn.getPackageName();

        worker.execute(() -> {
            String lang = Prefs.lang(this);
            Translator.Result tTitle = Translator.translate(this, fTitle, lang);
            Translator.Result tText = Translator.translate(this, fText, lang);
            String outTitle = tTitle != null ? tTitle.text : fTitle;
            String outText = tText != null ? tText.text : fText;
            String engine = tText != null ? tText.engine : (tTitle != null ? tTitle.engine : "failed");
            if (tTitle == null && tText == null) {
                outTitle = L.t(this, "fail_title");
                outText = fTitle + "\n" + fText;
            }
            String app = appLabel(pkg);
            post(key, outTitle, outText, fTitle + "\n" + fText, ongoing, open, pkg, app);
            if (!ongoing) Prefs.addLog(this, app, (fTitle + " " + fText).trim(),
                    (outTitle + " — " + outText).trim(), engine);

            if (Prefs.hideOriginal(this) && !ongoing && (tTitle != null || tText != null)) {
                hiddenByUs.add(key);
                try { cancelNotification(key); } catch (Exception ignored) { }
            }
        });
    }

    /** يعزل كل سطر باتجاه ثابت حتى لا تختلط الكلمات العربية والإنجليزية والأرقام. */
    private static String dir(String s, boolean rtl) {
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n", -1)) {
            if (out.length() > 0) out.append('\n');
            String l = line.trim();
            if (!l.isEmpty()) out.append(rtl ? '\u2067' : '\u2066').append(l).append('\u2069');
        }
        return out.toString();
    }

    private static String tidy(String s) {
        // إزالة الفواصل الزائدة في البداية/النهاية الناتجة عن القاموس (مثل "—")
        return s.replaceAll("^[\\s—:：-]+|[\\s—:：-]+$", "").replaceAll("\\s*[:：]\\s*—\\s*", " — ").trim();
    }

    private void post(String key, String title, String text, String original, boolean ongoing,
                      PendingIntent open, String pkg, String app) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean rtl = !"en".equals(Prefs.lang(this));
        String t = tidy(title);
        String body = tidy(text);
        if (t.isEmpty()) t = L.t(this, "default_title");
        String fTitle = dir(t, rtl);
        String fBody = dir(body, rtl);

        String big = fBody;
        if (Prefs.showOriginal(this) && !original.trim().isEmpty()) {
            big = fBody + "\n\n" + dir(L.t(this, "original"), rtl) + "\n" + dir(original.trim(), false);
        }

        String channel = ongoing ? CHANNEL_STATUS : CHANNEL_ALERTS;
        String group = "tr_" + pkg;
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channel)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(fTitle)
                .setContentText(fBody)
                .setSubText(app)
                .setStyle(new Notification.BigTextStyle().setBigContentTitle(fTitle).bigText(big))
                .setGroup(group)
                .setAutoCancel(!ongoing)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(ongoing)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());
        android.graphics.Bitmap icon = appIcon(pkg);
        if (icon != null) b.setLargeIcon(icon);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(ongoing ? Notification.PRIORITY_LOW : Notification.PRIORITY_HIGH);
        if (!ongoing) b.setDefaults(Notification.DEFAULT_ALL);

        PendingIntent pi = open;
        if (pi == null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) pi = PendingIntent.getActivity(this, key.hashCode(), launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        if (pi != null) b.setContentIntent(pi);
        try {
            nm.notify(key.hashCode(), b.build());
            if (!ongoing) {
                // تنبيه ملخّص يجمع ترجمات نفس التطبيق في مجموعة واحدة مرتبة
                Notification.Builder sum = Build.VERSION.SDK_INT >= 26
                        ? new Notification.Builder(this, channel) : new Notification.Builder(this);
                sum.setSmallIcon(R.drawable.ic_notify)
                        .setSubText(app)
                        .setContentTitle(app)
                        .setGroup(group)
                        .setGroupSummary(true)
                        .setAutoCancel(true)
                        .setOnlyAlertOnce(true);
                if (Build.VERSION.SDK_INT >= 26) sum.setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
                if (pi != null) sum.setContentIntent(pi);
                nm.notify(("sum_" + pkg).hashCode(), sum.build());
            }
        } catch (SecurityException ignored) { }
    }

    private android.graphics.Bitmap appIcon(String pkg) {
        try {
            android.graphics.drawable.Drawable d = getPackageManager().getApplicationIcon(pkg);
            int size = (int) (64 * getResources().getDisplayMetrics().density);
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(bmp);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            return bmp;
        } catch (Exception e) { return null; }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        String key = sbn.getKey();
        lastText.remove(key);
        if (hiddenByUs.remove(key)) return; // نحن أخفيناه: نُبقي الترجمة ظاهرة
        // إذا اختفى التنبيه الأصلي المستمر (مثل حالة الشحن) نحذف ترجمته أيضاً
        if ((sbn.getNotification().flags & Notification.FLAG_ONGOING_EVENT) != 0) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.cancel(key.hashCode());
        }
    }

    private String appLabel(String pkg) {
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    @Override
    public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
