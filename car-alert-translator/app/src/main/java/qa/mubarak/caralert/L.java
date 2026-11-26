package qa.mubarak.caralert;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/** نصوص الواجهة بالعربية والإنجليزية. */
final class L {
    private static final Map<String, String[]> M = new HashMap<>();
    private static void p(String k, String ar, String en) { M.put(k, new String[]{ar, en}); }

    static {
        p("title", "مترجم التنبيهات الصينية", "Chinese Alert Translator");
        p("subtitle", "يترجم تنبيهات التطبيقات الصينية تلقائياً", "Automatically translates Chinese app notifications");
        p("sec_access", "صلاحية قراءة التنبيهات", "Notification access");
        p("access_on", "✅ الصلاحية مفعّلة، والتطبيق يعمل في الخلفية.", "✅ Access granted. The app is running in the background.");
        p("access_off", "❌ غير مفعّلة. اضغط الزر وفعّل \"%s\".", "❌ Not granted. Tap the button and enable \"%s\".");
        p("access_btn_on", "إعدادات الصلاحية", "Access settings");
        p("access_btn_off", "تفعيل الصلاحية الآن", "Grant access now");
        p("battery_btn", "استثناء من توفير البطارية (مستحسن)", "Exclude from battery optimization (recommended)");
        p("battery_ok", "التطبيق مستثنى بالفعل ✅", "Already excluded ✅");
        p("sec_apps", "التطبيقات المراد ترجمتها", "Apps to translate");
        p("all_apps", "ترجمة أي تنبيه صيني من كل التطبيقات", "Translate any Chinese notification from all apps");
        p("selected", "التطبيقات المختارة:", "Selected apps:");
        p("none_selected", "لم تختر أي تطبيق بعد.", "No apps selected yet.");
        p("all_mode_note", "يتم ترجمة أي تنبيه يحتوي على نص صيني من أي تطبيق.", "Any notification containing Chinese text, from any app, will be translated.");
        p("pick_btn", "اختيار التطبيقات", "Choose apps");
        p("pick_title", "اختر تطبيقاً أو أكثر", "Choose one or more apps");
        p("search", "بحث…", "Search…");
        p("ok", "حفظ", "Save");
        p("cancel", "إلغاء", "Cancel");
        p("sec_lang", "لغة الترجمة", "Translate to");
        p("sec_options", "خيارات", "Options");
        p("online", "ترجمة جوجل أونلاين (أدق). عند إيقافها تتم الترجمة على الجهاز", "Google online translation (more accurate). When off, translates on the device");
        p("hide", "إخفاء التنبيه الصيني الأصلي بعد ترجمته", "Hide the original Chinese notification after translating");
        p("model_note", "نموذج الترجمة بدون إنترنت يُنزَّل مرة واحدة (حوالي 30 ميجا).", "The offline translation model is downloaded once (about 30 MB).");
        p("model_btn", "تنزيل نموذج الترجمة بدون إنترنت", "Download offline translation model");
        p("model_busy", "جاري التنزيل… قد يستغرق دقيقة أو أكثر.", "Downloading… this can take a minute or more.");
        p("model_ok", "✅ تم تنزيل النموذج.", "✅ Model downloaded.");
        p("model_fail", "❌ فشل التنزيل: ", "❌ Download failed: ");
        p("sec_dict", "قاموس مخصص", "Custom dictionary");
        p("dict_note", "كل سطر بالشكل: كلمة صينية=الترجمة. يُطبَّق قبل الترجمة الآلية، وهو مفيد لأسماء الماركات والموديلات.",
                "One entry per line: Chinese=translation. Applied before machine translation. Useful for brand and model names.");
        p("dict_saved", "تم حفظ القاموس ✅", "Dictionary saved ✅");
        p("sec_test", "تجربة الترجمة", "Try a translation");
        p("translate", "ترجم", "Translate");
        p("translating", "جاري الترجمة…", "Translating…");
        p("translate_fail", "تعذّرت الترجمة. تأكد من الإنترنت أو نزّل نموذج الترجمة.", "Translation failed. Check your internet or download the offline model.");
        p("sec_log", "آخر التنبيهات المترجمة", "Recent translated notifications");
        p("clear_log", "مسح السجل", "Clear log");
        p("log_empty", "لا توجد تنبيهات مترجمة بعد.", "No translated notifications yet.");
        p("footer", "جميع الحقوق محفوظة © %d\nتطوير مبارك السليطي", "All rights reserved © %d\nDeveloped by Mubarak Al-Sulaiti");
        p("ch_alerts", "التنبيهات المترجمة", "Translated alerts");
        p("ch_status", "التنبيهات المستمرة (مترجمة)", "Ongoing notifications (translated)");
        p("fail_title", "⚠ تنبيه (تعذّرت الترجمة)", "⚠ Notification (translation failed)");
        p("original", "الأصل: ", "Original: ");
        p("default_title", "تنبيه", "Notification");
        p("theme_desc", "تبديل الوضع النهاري/الليلي", "Toggle light/dark mode");
        p("lang_desc", "تغيير اللغة", "Change language");
    }

    static boolean en(Context c) { return "en".equals(Prefs.uiLang(c)); }

    static String t(Context c, String key) {
        String[] v = M.get(key);
        if (v == null) return key;
        return en(c) ? v[1] : v[0];
    }

    private L() { }
}
