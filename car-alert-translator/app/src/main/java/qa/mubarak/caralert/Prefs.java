package qa.mubarak.caralert;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** إعدادات التطبيق وسجل التنبيهات المترجمة. */
final class Prefs {
    private static final String FILE = "settings";
    private static final String K_PKGS = "target_pkgs";
    private static final String K_ALL = "all_apps";
    private static final String K_LANG = "lang";
    private static final String K_UI = "ui_lang";
    private static final String K_DARK = "dark";
    private static final String K_HIDE = "hide_original";
    private static final String K_ONLINE = "online";
    private static final String K_DICT = "dictionary";
    private static final String K_LOG = "log";
    private static final int MAX_LOG = 50;

    static final String DEFAULT_DICT = "比亚迪=BYD\n方程豹=Fangchengbao\n";

    private static SharedPreferences sp(Context c) {
        SharedPreferences s = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        // ترحيل من الإصدار 1.0 (تطبيق واحد فقط)
        if (s.contains("target_pkg")) {
            String old = s.getString("target_pkg", "*");
            SharedPreferences.Editor e = s.edit().remove("target_pkg").remove("target_label");
            if (!"*".equals(old)) {
                Set<String> set = new HashSet<>(); set.add(old);
                e.putStringSet(K_PKGS, set).putBoolean(K_ALL, false);
            }
            e.apply();
        }
        return s;
    }

    static Set<String> packages(Context c) { return new HashSet<>(sp(c).getStringSet(K_PKGS, new HashSet<>())); }
    static void setPackages(Context c, Set<String> pkgs) { sp(c).edit().putStringSet(K_PKGS, new HashSet<>(pkgs)).apply(); }

    static boolean allApps(Context c) { return sp(c).getBoolean(K_ALL, true); }
    static void setAllApps(Context c, boolean v) { sp(c).edit().putBoolean(K_ALL, v).apply(); }

    static boolean isTarget(Context c, String pkg) { return allApps(c) || packages(c).contains(pkg); }

    /** لغة الترجمة (المخرجات). */
    static String lang(Context c) { return sp(c).getString(K_LANG, "ar"); }
    static void setLang(Context c, String l) { sp(c).edit().putString(K_LANG, l).apply(); }

    /** لغة الواجهة. */
    static String uiLang(Context c) {
        String v = sp(c).getString(K_UI, null);
        if (v != null) return v;
        return "ar".equals(Locale.getDefault().getLanguage()) ? "ar" : "en";
    }
    static void setUiLang(Context c, String l) { sp(c).edit().putString(K_UI, l).apply(); }

    static boolean dark(Context c) {
        SharedPreferences s = sp(c);
        if (s.contains(K_DARK)) return s.getBoolean(K_DARK, false);
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
    static void setDark(Context c, boolean v) { sp(c).edit().putBoolean(K_DARK, v).apply(); }

    static boolean hideOriginal(Context c) { return sp(c).getBoolean(K_HIDE, false); }
    static void setHideOriginal(Context c, boolean v) { sp(c).edit().putBoolean(K_HIDE, v).apply(); }

    static boolean online(Context c) { return sp(c).getBoolean(K_ONLINE, true); }
    static void setOnline(Context c, boolean v) { sp(c).edit().putBoolean(K_ONLINE, v).apply(); }

    static String dictionary(Context c) { return sp(c).getString(K_DICT, DEFAULT_DICT); }
    static void setDictionary(Context c, String v) { sp(c).edit().putString(K_DICT, v).apply(); }

    static synchronized void addLog(Context c, String app, String original, String translated, String engine) {
        try {
            JSONArray old = new JSONArray(sp(c).getString(K_LOG, "[]"));
            JSONArray arr = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("t", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
            o.put("a", app);
            o.put("o", original);
            o.put("r", translated);
            o.put("e", engine);
            arr.put(o);
            for (int i = 0; i < old.length() && arr.length() < MAX_LOG; i++) arr.put(old.get(i));
            sp(c).edit().putString(K_LOG, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    static JSONArray log(Context c) {
        try { return new JSONArray(sp(c).getString(K_LOG, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    static void clearLog(Context c) { sp(c).edit().remove(K_LOG).apply(); }

    private Prefs() { }
}
