package qa.mubarak.caralert;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView accessStatus, targetView, testResult, modelStatus;
    private LinearLayout logBox;
    private Button accessBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AlertListenerService.createChannels(this);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(root);

        // 1) الصلاحية
        root.addView(header("١. صلاحية قراءة التنبيهات"));
        accessStatus = body("");
        root.addView(accessStatus);
        accessBtn = button("فتح إعدادات الصلاحية", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(accessBtn);
        root.addView(button("استثناء التطبيق من توفير البطارية (مستحسن)", v -> askBatteryExemption()));

        // 2) التطبيق المستهدف
        root.addView(header("٢. تطبيق السيارة"));
        targetView = body("");
        root.addView(targetView);
        root.addView(button("اختيار تطبيق السيارة", v -> pickApp()));

        // 3) اللغة
        root.addView(header("٣. لغة الترجمة"));
        RadioGroup rg = new RadioGroup(this);
        RadioButton ar = new RadioButton(this); ar.setText("العربية"); ar.setId(View.generateViewId());
        RadioButton en = new RadioButton(this); en.setText("English"); en.setId(View.generateViewId());
        rg.addView(ar); rg.addView(en);
        rg.check("en".equals(Prefs.lang(this)) ? en.getId() : ar.getId());
        rg.setOnCheckedChangeListener((g, id) -> Prefs.setLang(this, id == en.getId() ? "en" : "ar"));
        root.addView(rg);

        // 4) خيارات
        root.addView(header("٤. خيارات"));
        Switch online = new Switch(this);
        online.setText("استخدام ترجمة جوجل أونلاين (أدق) — وإلا الترجمة على الجهاز");
        online.setChecked(Prefs.online(this));
        online.setOnCheckedChangeListener((b, c) -> Prefs.setOnline(this, c));
        root.addView(online);
        Switch hide = new Switch(this);
        hide.setText("إخفاء التنبيه الصيني الأصلي بعد ترجمته");
        hide.setChecked(Prefs.hideOriginal(this));
        hide.setOnCheckedChangeListener((b, c) -> Prefs.setHideOriginal(this, c));
        root.addView(hide);

        modelStatus = body("نموذج الترجمة بدون إنترنت: يُنزَّل مرة واحدة ليعمل التطبيق حتى بدون اتصال.");
        root.addView(modelStatus);
        root.addView(button("تنزيل نموذج الترجمة بدون إنترنت", v -> downloadModel()));

        // 5) تجربة
        root.addView(header("٥. تجربة الترجمة"));
        EditText input = new EditText(this);
        input.setText("车门未锁，请检查");
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(input);
        testResult = body("");
        root.addView(button("ترجم", v -> {
            String s = input.getText().toString();
            testResult.setText("جاري الترجمة…");
            new Thread(() -> {
                Translator.Result r = Translator.translate(this, s, Prefs.lang(this));
                ui.post(() -> testResult.setText(r == null ? "تعذّرت الترجمة. تأكد من الإنترنت أو نزّل النموذج."
                        : r.text + "   [" + r.engine + "]"));
            }).start();
        }));
        root.addView(testResult);

        // 6) السجل
        root.addView(header("٦. آخر التنبيهات المترجمة"));
        root.addView(button("مسح السجل", v -> { Prefs.clearLog(this); refresh(); }));
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(logBox);
        return scroll;
    }

    private TextView header(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Color.parseColor("#1565C0"));
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setPadding(0, dp(4), 0, dp(4));
        t.setTextIsSelectable(true);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private boolean hasAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        ComponentName me = new ComponentName(this, AlertListenerService.class);
        for (String s : flat.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    private void refresh() {
        boolean ok = hasAccess();
        accessStatus.setText(ok ? "✅ الصلاحية مفعّلة — التطبيق يعمل في الخلفية."
                : "❌ غير مفعّلة. اضغط الزر وفعّل \"" + getString(R.string.app_name) + "\".");
        accessStatus.setTextColor(ok ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));
        accessBtn.setText(ok ? "إعدادات الصلاحية" : "تفعيل الصلاحية الآن");
        targetView.setText("الحالي: " + Prefs.targetLabel(this));

        logBox.removeAllViews();
        JSONArray log = Prefs.log(this);
        if (log.length() == 0) logBox.addView(body("لا يوجد تنبيهات مترجمة بعد."));
        for (int i = 0; i < log.length(); i++) {
            JSONObject o = log.optJSONObject(i);
            if (o == null) continue;
            TextView t = body(o.optString("t") + "\n" + o.optString("r") + "\n↳ " + o.optString("o"));
            t.setBackgroundColor(Color.parseColor("#F1F5FB"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(4), 0, dp(4));
            t.setLayoutParams(lp);
            t.setPadding(dp(10), dp(8), dp(10), dp(8));
            logBox.addView(t);
        }
    }

    private void pickApp() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        final List<String> labels = new ArrayList<>();
        final List<String> pkgs = new ArrayList<>();
        labels.add("كل التطبيقات (أي تنبيه بالصينية)");
        pkgs.add(Prefs.ALL_APPS);
        List<String[]> rows = new ArrayList<>();
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || pkgs.contains(pkg)) continue;
            String label = ri.loadLabel(pm).toString();
            rows.add(new String[]{label, pkg});
        }
        // التطبيقات ذات الأسماء الصينية أولاً
        rows.sort((a, b) -> {
            boolean ca = Translator.hasChinese(a[0]), cb = Translator.hasChinese(b[0]);
            if (ca != cb) return ca ? -1 : 1;
            return a[0].compareToIgnoreCase(b[0]);
        });
        for (String[] r : rows) {
            if (pkgs.contains(r[1])) continue;
            labels.add(r[0] + "\n" + r[1]);
            pkgs.add(r[1]);
        }
        new AlertDialog.Builder(this)
                .setTitle("اختر تطبيق السيارة")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String label = labels.get(which).split("\n")[0];
                    Prefs.setTarget(this, pkgs.get(which), label);
                    refresh();
                })
                .show();
    }

    private void downloadModel() {
        String lang = Prefs.lang(this);
        modelStatus.setText("جاري تنزيل النموذج… قد يستغرق دقيقة أو أكثر.");
        new Thread(() -> {
            String msg;
            try {
                Translator.downloadOfflineModel(lang);
                msg = "✅ تم تنزيل نموذج الترجمة بدون إنترنت.";
            } catch (Exception e) {
                msg = "❌ فشل التنزيل: " + e.getMessage();
            }
            final String m = msg;
            ui.post(() -> modelStatus.setText(m));
        }).start();
    }

    private void askBatteryExemption() {
        if (Build.VERSION.SDK_INT < 23) return;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, "التطبيق مستثنى بالفعل ✅", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }
}
