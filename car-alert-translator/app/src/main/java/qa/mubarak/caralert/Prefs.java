package qa.mubarak.caralert;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** إعدادات التطبيق وسجل التنبيهات المترجمة. */
final class Prefs {
    static final String ALL_APPS = "*";

    private static final String FILE = "settings";
    private static final String K_PKG = "target_pkg";
    private static final String K_PKG_LABEL = "target_label";
    private static final String K_LANG = "lang";
    private static final String K_HIDE = "hide_original";
    private static final String K_ONLINE = "online";
    private static final String K_LOG = "log";
    private static final int MAX_LOG = 40;

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String targetPackage(Context c) { return sp(c).getString(K_PKG, ALL_APPS); }
    static String targetLabel(Context c) { return sp(c).getString(K_PKG_LABEL, "كل التطبيقات (أي تنبيه بالصينية)"); }
    static void setTarget(Context c, String pkg, String label) {
        sp(c).edit().putString(K_PKG, pkg).putString(K_PKG_LABEL, label).apply();
    }

    static String lang(Context c) { return sp(c).getString(K_LANG, "ar"); }
    static void setLang(Context c, String l) { sp(c).edit().putString(K_LANG, l).apply(); }

    static boolean hideOriginal(Context c) { return sp(c).getBoolean(K_HIDE, false); }
    static void setHideOriginal(Context c, boolean v) { sp(c).edit().putBoolean(K_HIDE, v).apply(); }

    static boolean online(Context c) { return sp(c).getBoolean(K_ONLINE, true); }
    static void setOnline(Context c, boolean v) { sp(c).edit().putBoolean(K_ONLINE, v).apply(); }

    static synchronized void addLog(Context c, String original, String translated, String engine) {
        try {
            JSONArray old = new JSONArray(sp(c).getString(K_LOG, "[]"));
            JSONArray arr = new JSONArray();
            JSONObject o = new JSONObject();
            o.put("t", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()));
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
