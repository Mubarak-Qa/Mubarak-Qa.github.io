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
 * يستمع لتنبيهات تطبيق السيارة، يترجمها، ثم يعرض تنبيهاً جديداً مترجماً.
 */
public class AlertListenerService extends NotificationListenerService {
    static final String CHANNEL_ALERTS = "car_alerts";
    static final String CHANNEL_STATUS = "car_status";

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
        NotificationChannel alerts = new NotificationChannel(CHANNEL_ALERTS, "تنبيهات السيارة المترجمة",
                NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("التنبيهات المترجمة من تطبيق السيارة");
        alerts.enableVibration(true);
        nm.createNotificationChannel(alerts);
        NotificationChannel status = new NotificationChannel(CHANNEL_STATUS, "حالة السيارة المستمرة (مترجمة)",
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(status);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
        String target = Prefs.targetPackage(this);
        if (!Prefs.ALL_APPS.equals(target) && !target.equals(sbn.getPackageName())) return;

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
            String engine = tText != null ? tText.engine : (tTitle != null ? tTitle.engine : "فشل");
            if (tTitle == null && tText == null) {
                outTitle = "⚠ تنبيه (تعذّرت الترجمة)";
                outText = fTitle + "\n" + fText;
            }
            post(key, outTitle, outText, fTitle + "\n" + fText, ongoing, open, pkg);
            if (!ongoing) Prefs.addLog(this, (fTitle + " " + fText).trim(),
                    (outTitle + " — " + outText).trim(), engine);

            if (Prefs.hideOriginal(this) && !ongoing && (tTitle != null || tText != null)) {
                hiddenByUs.add(key);
                try { cancelNotification(key); } catch (Exception ignored) { }
            }
        });
    }

    private void post(String key, String title, String text, String original, boolean ongoing,
                      PendingIntent open, String pkg) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ongoing ? CHANNEL_STATUS : CHANNEL_ALERTS)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title.isEmpty() ? "تنبيه السيارة" : title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text + "\n\n— الأصل: " + original.trim()))
                .setAutoCancel(!ongoing)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(ongoing)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT < 26) b.setPriority(ongoing ? Notification.PRIORITY_LOW : Notification.PRIORITY_HIGH);
        if (!ongoing) b.setDefaults(Notification.DEFAULT_ALL);

        PendingIntent pi = open;
        if (pi == null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch != null) pi = PendingIntent.getActivity(this, key.hashCode(), launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        if (pi != null) b.setContentIntent(pi);
        try { nm.notify(key.hashCode(), b.build()); } catch (SecurityException ignored) { }
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

    private static String str(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    @Override
    public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
