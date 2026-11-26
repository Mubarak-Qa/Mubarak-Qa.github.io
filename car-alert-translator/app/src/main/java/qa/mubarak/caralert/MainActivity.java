package qa.mubarak.caralert;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView accessStatus, appsView, testResult, modelStatus;
    private LinearLayout logBox;
    private Button accessBtn, pickBtn;
    private boolean dark, en;

    // الألوان
    private int cBg, cCard, cText, cSub, cAccent, cStroke, cSoft;

    private String t(String k) { return L.t(this, k); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        dark = Prefs.dark(this);
        en = L.en(this);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(savedInstanceState);
        initColors();
        getWindow().setStatusBarColor(Color.parseColor(dark ? "#0B2A66" : "#1747AE"));
        getWindow().setNavigationBarColor(cBg);
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

    private void initColors() {
        if (dark) {
            cBg = 0xFF0E1217; cCard = 0xFF18202A; cText = 0xFFE6EBF1; cSub = 0xFF93A1B0;
            cAccent = 0xFF5B9BFF; cStroke = 0xFF26303C; cSoft = 0xFF121A23;
        } else {
            cBg = 0xFFF3F6FB; cCard = 0xFFFFFFFF; cText = 0xFF17202A; cSub = 0xFF5B6776;
            cAccent = 0xFF1E5BD8; cStroke = 0xFFE3E8F0; cSoft = 0xFFF1F5FB;
        }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    // ---------------------------------------------------------------- الواجهة

    private View buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setLayoutDirection(en ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        page.setBackgroundColor(cBg);
        page.addView(topBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(16));
        scroll.addView(root);

        // 1) الصلاحية
        LinearLayout c1 = card(root, "1", t("sec_access"));
        accessStatus = body("");
        c1.addView(accessStatus);
        accessBtn = primaryButton("", v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        c1.addView(accessBtn);
        c1.addView(secondaryButton(t("battery_btn"), v -> askBatteryExemption()));

        // 2) التطبيقات
        LinearLayout c2 = card(root, "2", t("sec_apps"));
        Switch all = sw(t("all_apps"), Prefs.allApps(this), (b, c) -> { Prefs.setAllApps(this, c); refresh(); });
        c2.addView(all);
        appsView = body("");
        c2.addView(appsView);
        pickBtn = primaryButton(t("pick_btn"), v -> pickApps());
        c2.addView(pickBtn);

        // 3) لغة الترجمة
        LinearLayout c3 = card(root, "3", t("sec_lang"));
        RadioGroup rg = new RadioGroup(this);
        RadioButton ar = radio("العربية"), enR = radio("English");
        rg.addView(ar); rg.addView(enR);
        rg.check("en".equals(Prefs.lang(this)) ? enR.getId() : ar.getId());
        rg.setOnCheckedChangeListener((g, id) -> Prefs.setLang(this, id == enR.getId() ? "en" : "ar"));
        c3.addView(rg);

        // 4) خيارات
        LinearLayout c4 = card(root, "4", t("sec_options"));
        c4.addView(sw(t("online"), Prefs.online(this), (b, c) -> Prefs.setOnline(this, c)));
        c4.addView(sw(t("hide"), Prefs.hideOriginal(this), (b, c) -> Prefs.setHideOriginal(this, c)));
        modelStatus = small(t("model_note"));
        c4.addView(modelStatus);
        c4.addView(secondaryButton(t("model_btn"), v -> downloadModel()));

        // 5) القاموس
        LinearLayout c5 = card(root, "5", t("sec_dict"));
        c5.addView(small(t("dict_note")));
        EditText dict = input(Prefs.dictionary(this));
        dict.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        dict.setMinLines(3);
        dict.setGravity(Gravity.TOP | Gravity.START);
        c5.addView(dict);
        c5.addView(secondaryButton(t("ok"), v -> {
            Prefs.setDictionary(this, dict.getText().toString());
            Toast.makeText(this, t("dict_saved"), Toast.LENGTH_SHORT).show();
        }));

        // 6) تجربة
        LinearLayout c6 = card(root, "6", t("sec_test"));
        EditText test = input("方程豹提醒您：开始充电");
        c6.addView(test);
        testResult = body("");
        c6.addView(primaryButton(t("translate"), v -> {
            String s = test.getText().toString();
            testResult.setText(t("translating"));
            new Thread(() -> {
                Translator.Result r = Translator.translate(this, s, Prefs.lang(this));
                ui.post(() -> testResult.setText(r == null ? t("translate_fail") : r.text + "   [" + r.engine + "]"));
            }).start();
        }));
        c6.addView(testResult);

        // 7) السجل
        LinearLayout c7 = card(root, "7", t("sec_log"));
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        c7.addView(logBox);
        c7.addView(secondaryButton(t("clear_log"), v -> { Prefs.clearLog(this); refresh(); }));

        // التذييل
        TextView footer = new TextView(this);
        footer.setText(String.format(java.util.Locale.US, t("footer"), Calendar.getInstance().get(Calendar.YEAR)));
        footer.setGravity(Gravity.CENTER);
        footer.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        footer.setTextColor(cSub);
        footer.setTextSize(13);
        footer.setLineSpacing(dp(2), 1f);
        footer.setPadding(0, dp(18), 0, dp(8));
        root.addView(footer);
        return page;
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(14), dp(10), dp(14));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                dark ? new int[]{0xFF0B2A66, 0xFF075E68} : new int[]{0xFF1E5BD8, 0xFF00A8B5});
        bar.setBackground(g);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        bar.addView(logo, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, dp(6), 0);
        TextView title = new TextView(this);
        title.setText(t("title"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        TextView sub = new TextView(this);
        sub.setText(t("subtitle"));
        sub.setTextColor(0xD9FFFFFF);
        sub.setTextSize(12);
        sub.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        titles.addView(title);
        titles.addView(sub);
        bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        // زر اللغة
        TextView langBtn = new TextView(this);
        langBtn.setText(en ? "ع" : "EN");
        langBtn.setTextColor(Color.WHITE);
        langBtn.setTextSize(15);
        langBtn.setTypeface(Typeface.DEFAULT_BOLD);
        langBtn.setGravity(Gravity.CENTER);
        langBtn.setContentDescription(t("lang_desc"));
        langBtn.setBackground(circle());
        langBtn.setOnClickListener(v -> { Prefs.setUiLang(this, en ? "ar" : "en"); recreate(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.setMarginEnd(dp(8));
        bar.addView(langBtn, lp);

        // زر الوضع: هلال في الوضع الليلي، شمس في الوضع النهاري
        ImageView themeBtn = new ImageView(this);
        themeBtn.setImageResource(dark ? R.drawable.ic_moon : R.drawable.ic_sun);
        themeBtn.setPadding(dp(9), dp(9), dp(9), dp(9));
        themeBtn.setContentDescription(t("theme_desc"));
        themeBtn.setBackground(circle());
        themeBtn.setOnClickListener(v -> { Prefs.setDark(this, !dark); recreate(); });
        bar.addView(themeBtn, new LinearLayout.LayoutParams(dp(42), dp(42)));
        return bar;
    }

    private Drawable circle() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0x33FFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(0x55FFFFFF), d, null);
    }

    private LinearLayout card(LinearLayout parent, String num, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cCard);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), cStroke);
        c.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(8), 0, dp(4));
        parent.addView(c, lp);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = new TextView(this);
        badge.setText(num);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable bd = new GradientDrawable();
        bd.setShape(GradientDrawable.OVAL);
        bd.setColor(cAccent);
        badge.setBackground(bd);
        head.addView(badge, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextSize(17);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextColor(cText);
        h.setPadding(dp(10), 0, dp(10), 0);
        h.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        head.addView(h, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(head);
        View gap = new View(this);
        c.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
        return c;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(cText);
        t.setPadding(0, dp(4), 0, dp(4));
        t.setTextIsSelectable(true);
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return t;
    }

    private TextView small(String s) {
        TextView t = body(s);
        t.setTextSize(13);
        t.setTextColor(cSub);
        return t;
    }

    private EditText input(String s) {
        EditText e = new EditText(this);
        e.setText(s);
        e.setTextColor(cText);
        e.setTextSize(15);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cSoft);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), cStroke);
        e.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(6), 0, dp(4));
        e.setLayoutParams(lp);
        return e;
    }

    private Switch sw(String text, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(this);
        s.setText(text);
        s.setTextColor(cText);
        s.setTextSize(15);
        s.setChecked(checked);
        s.setPadding(0, dp(8), 0, dp(8));
        s.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        s.setOnCheckedChangeListener(l);
        return s;
    }

    private RadioButton radio(String text) {
        RadioButton r = new RadioButton(this);
        r.setText(text);
        r.setTextColor(cText);
        r.setTextSize(15);
        r.setId(View.generateViewId());
        return r;
    }

    private Button primaryButton(String s, View.OnClickListener l) {
        return styledButton(s, l, true);
    }

    private Button secondaryButton(String s, View.OnClickListener l) {
        return styledButton(s, l, false);
    }

    private Button styledButton(String s, View.OnClickListener l, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setStateListAnimator(null);
        b.setTextColor(primary ? Color.WHITE : cAccent);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (primary) bg.setColor(cAccent); else { bg.setColor(Color.TRANSPARENT); bg.setStroke(dp(1), cAccent); }
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), bg, null));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.setMargins(0, dp(6), 0, dp(4));
        b.setLayoutParams(lp);
        return b;
    }

    // ---------------------------------------------------------------- المنطق

    private boolean hasAccess() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        ComponentName me = new ComponentName(this, AlertListenerService.class);
        for (String s : flat.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) { return pkg; }
    }

    private void refresh() {
        boolean ok = hasAccess();
        accessStatus.setText(ok ? t("access_on") : String.format(t("access_off"), t("title")));
        accessStatus.setTextColor(ok ? (dark ? 0xFF6FD08C : 0xFF2E7D32) : (dark ? 0xFFFF8A80 : 0xFFC62828));
        accessBtn.setText(ok ? t("access_btn_on") : t("access_btn_off"));

        boolean all = Prefs.allApps(this);
        Set<String> pkgs = Prefs.packages(this);
        pickBtn.setEnabled(!all);
        pickBtn.setAlpha(all ? 0.45f : 1f);
        if (all) {
            appsView.setText(t("all_mode_note"));
        } else if (pkgs.isEmpty()) {
            appsView.setText(t("none_selected"));
        } else {
            StringBuilder sb = new StringBuilder(t("selected"));
            for (String p : pkgs) sb.append("\n• ").append(label(p));
            appsView.setText(sb.toString());
        }

        logBox.removeAllViews();
        JSONArray log = Prefs.log(this);
        if (log.length() == 0) logBox.addView(small(t("log_empty")));
        for (int i = 0; i < log.length(); i++) {
            JSONObject o = log.optJSONObject(i);
            if (o == null) continue;
            String app = o.optString("a");
            TextView tv = body(o.optString("t") + (app.isEmpty() ? "" : "  ·  " + app)
                    + "\n" + o.optString("r") + "\n↳ " + o.optString("o"));
            tv.setTextSize(14);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cSoft);
            bg.setCornerRadius(dp(10));
            tv.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(4), 0, dp(4));
            tv.setLayoutParams(lp);
            tv.setPadding(dp(12), dp(8), dp(12), dp(8));
            logBox.addView(tv);
        }
    }

    private static final class AppRow {
        final String label, pkg; final ResolveInfo ri;
        AppRow(String label, String pkg, ResolveInfo ri) { this.label = label; this.pkg = pkg; this.ri = ri; }
    }

    private void pickApps() {
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppRow> rows = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            rows.add(new AppRow(ri.loadLabel(pm).toString(), pkg, ri));
        }
        final Set<String> selected = Prefs.packages(this);
        // المختارة أولاً، ثم التطبيقات ذات الأسماء الصينية، ثم الباقي أبجدياً
        rows.sort((a, b) -> {
            boolean sa = selected.contains(a.pkg), sb = selected.contains(b.pkg);
            if (sa != sb) return sa ? -1 : 1;
            boolean ca = Translator.hasChinese(a.label), cb = Translator.hasChinese(b.label);
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });

        final List<AppRow> shown = new ArrayList<>(rows);
        final Map<String, Drawable> icons = new HashMap<>();
        BaseAdapter adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) (v != null ? v : getLayoutInflater()
                        .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false));
                AppRow r = shown.get(i);
                row.setText(r.label);
                row.setTextColor(cText);
                row.setChecked(selected.contains(r.pkg));
                Drawable ic = icons.get(r.pkg);
                if (ic == null) {
                    ic = r.ri.loadIcon(pm);
                    ic.setBounds(0, 0, dp(32), dp(32));
                    icons.put(r.pkg, ic);
                }
                row.setCompoundDrawablesRelative(ic, null, null, null);
                row.setCompoundDrawablePadding(dp(12));
                return row;
            }
        };

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(en ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        EditText search = input("");
        search.setHint(t("search"));
        search.setSingleLine(true);
        box.addView(search);
        ListView list = new ListView(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, i, id) -> {
            String pkg = shown.get(i).pkg;
            if (!selected.remove(pkg)) selected.add(pkg);
            adapter.notifyDataSetChanged();
        });
        box.addView(list, new LinearLayout.LayoutParams(-1, dp(420)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase();
                shown.clear();
                for (AppRow r : rows)
                    if (q.isEmpty() || r.label.toLowerCase().contains(q) || r.pkg.contains(q)) shown.add(r);
                adapter.notifyDataSetChanged();
            }
        });

        new AlertDialog.Builder(this)
                .setTitle(t("pick_title"))
                .setView(box)
                .setPositiveButton(t("ok"), (d, w) -> { Prefs.setPackages(this, selected); refresh(); })
                .setNegativeButton(t("cancel"), null)
                .show();
    }

    private void downloadModel() {
        String lang = Prefs.lang(this);
        modelStatus.setText(t("model_busy"));
        new Thread(() -> {
            String msg;
            try {
                Translator.downloadOfflineModel(lang);
                msg = t("model_ok");
            } catch (Exception e) {
                msg = t("model_fail") + e.getMessage();
            }
            final String m = msg;
            ui.post(() -> modelStatus.setText(m));
        }).start();
    }

    private void askBatteryExemption() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Toast.makeText(this, t("battery_ok"), Toast.LENGTH_SHORT).show();
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
