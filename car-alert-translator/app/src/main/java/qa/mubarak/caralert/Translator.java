package qa.mubarak.caralert;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * يترجم النص من الصينية.
 * الترتيب: الذاكرة المؤقتة ← ترجمة جوجل أونلاين ← ترجمة ML Kit على الجهاز (بدون إنترنت).
 * يجب استدعاؤه من خيط خلفي (ليس الخيط الرئيسي).
 */
final class Translator {
    static final Pattern CJK = Pattern.compile("[\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]");

    static final class Result {
        final String text; final String engine;
        Result(String text, String engine) { this.text = text; this.engine = engine; }
    }

    private static final Map<String, com.google.mlkit.nl.translate.Translator> offline = new HashMap<>();

    static boolean hasChinese(CharSequence s) {
        return s != null && CJK.matcher(s).find();
    }

    static Result translate(Context ctx, String text, String lang) {
        if (text == null || text.trim().isEmpty() || !hasChinese(text)) return new Result(text, "-");

        text = applyDictionary(ctx, text);
        if (!hasChinese(text)) return new Result(text, "dictionary");

        SharedPreferences cache = ctx.getSharedPreferences("cache_" + lang, Context.MODE_PRIVATE);
        String cached = cache.getString(text, null);
        if (cached != null) return new Result(cached, "cache");

        String out = null; String engine = null;
        if (Prefs.online(ctx)) {
            try { out = online(text, lang); engine = "online"; } catch (Exception ignored) { }
        }
        if (out == null) {
            try { out = offline(text, lang); engine = "offline"; } catch (Exception ignored) { }
        }
        if (out == null) return null;

        if (cache.getAll().size() > 1000) cache.edit().clear().apply();
        cache.edit().putString(text, out).apply();
        return new Result(out, engine);
    }

    /** يستبدل الكلمات من القاموس المخصص (الأطول أولاً). */
    static String applyDictionary(Context ctx, String text) {
        java.util.List<String[]> pairs = new java.util.ArrayList<>();
        for (String line : Prefs.dictionary(ctx).split("\\r?\\n")) {
            int i = line.indexOf('=');
            if (i <= 0) continue;
            String k = line.substring(0, i).trim(), v = line.substring(i + 1).trim();
            if (!k.isEmpty()) pairs.add(new String[]{k, v});
        }
        pairs.sort((a, b) -> b[0].length() - a[0].length());
        for (String[] p : pairs) {
            if (text.contains(p[0])) text = text.replace(p[0], " " + p[1] + " ");
        }
        return text.replaceAll("\\s{2,}", " ").trim();
    }

    private static String online(String text, String lang) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl="
                + lang + "&dt=t&q=" + URLEncoder.encode(text, "UTF-8");
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(6000);
        con.setReadTimeout(6000);
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        try {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                String line; while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONArray parts = new JSONArray(sb.toString()).getJSONArray(0);
            StringBuilder res = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONArray p = parts.optJSONArray(i);
                if (p != null && !p.isNull(0)) res.append(p.getString(0));
            }
            String s = res.toString().trim();
            if (s.isEmpty()) throw new Exception("empty");
            return s;
        } finally {
            con.disconnect();
        }
    }

    private static synchronized com.google.mlkit.nl.translate.Translator offlineClient(String lang) {
        com.google.mlkit.nl.translate.Translator t = offline.get(lang);
        if (t == null) {
            String target = "en".equals(lang) ? TranslateLanguage.ENGLISH : TranslateLanguage.ARABIC;
            t = Translation.getClient(new TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.CHINESE)
                    .setTargetLanguage(target)
                    .build());
            offline.put(lang, t);
        }
        return t;
    }

    /** ينزّل نموذج الترجمة على الجهاز (مرة واحدة تقريباً 30-60 ميجا). */
    static void downloadOfflineModel(String lang) throws Exception {
        Tasks.await(offlineClient(lang).downloadModelIfNeeded(new DownloadConditions.Builder().build()),
                10, TimeUnit.MINUTES);
    }

    private static String offline(String text, String lang) throws Exception {
        com.google.mlkit.nl.translate.Translator t = offlineClient(lang);
        Tasks.await(t.downloadModelIfNeeded(), 2, TimeUnit.MINUTES);
        return Tasks.await(t.translate(text), 30, TimeUnit.SECONDS);
    }

    private Translator() { }
}
