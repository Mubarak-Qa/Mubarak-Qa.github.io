package com.mubarak.bydspeed.car

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Entry point for the shell-side daemon. It is NOT started like a normal Android component;
 * the embedded ADB client ([AdbCar]) runs it with:
 *
 *   CLASSPATH=<app.apk> app_process /system/bin --nice-name=bydcarhub.daemon \
 *       com.mubarak.bydspeed.car.CarHelper
 *
 * app_process launches it as the shell user (UID 2000), which BYD whitelists for the BYDAUTO
 * permissions. It then reads one JSON command per stdin line and prints one JSON reply per line,
 * reusing [CarBridge] for all reflection. Nothing but JSON is written to stdout.
 */
object CarHelper {
    @JvmStatic
    fun main(args: Array<String>) {
        val context = systemContext()
        val out = System.out
        out.println("""{"ready":true}""")
        out.flush()
        val reader = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            val line = try { reader.readLine() } catch (t: Throwable) { null } ?: break
            if (line.isBlank()) continue
            val reply = try { handle(context, JSONObject(line)) } catch (t: Throwable) {
                JSONObject().put("ok", false).put("err", t.javaClass.simpleName + ": " + t.message)
            }
            out.println(reply.toString())
            out.flush()
        }
    }

    private fun handle(context: Context, cmd: JSONObject): JSONObject {
        val id = cmd.optInt("id", 0)
        val reply = JSONObject().put("id", id)
        when (cmd.optString("op")) {
            "ping" -> reply.put("ok", true)
            "get" -> {
                val cls = cmd.optString("cls").ifEmpty { null }
                val methods = cmd.getJSONArray("methods").let { a -> (0 until a.length()).map { a.getString(it) } }
                val args = cmd.optJSONArray("args").toIntList()
                var err = "no-method"
                for (m in methods) {
                    val p = CarBridge.find(context, cls, m, args.size) ?: run { err = "missing $m"; null } ?: continue
                    try {
                        val v = CarBridge.invoke(p.first, p.second, args.map { it as Number })
                        return reply.put("ok", true).put("value", (v as? Number)?.toDouble() ?: Double.NaN)
                    } catch (t: Throwable) { err = CarBridge.describe(t) }
                }
                reply.put("ok", false).put("err", err)
            }
            "set" -> {
                val cls = cmd.optString("cls").ifEmpty { null }
                val method = cmd.getString("method")
                val args = cmd.optJSONArray("args").toDoubleList()
                val p = CarBridge.find(context, cls, method, args.size)
                    ?: return reply.put("ok", false).put("err", "missing $method/${args.size}")
                val r = CarBridge.invoke(p.first, p.second, args.map { it as Number })
                if (r is Int && r < 0) reply.put("ok", false).put("err", "code $r") else reply.put("ok", true)
            }
            "scan" -> reply.put("ok", true).put("report", CarBridge.scanReport(context))
            else -> reply.put("ok", false).put("err", "unknown-op")
        }
        return reply
    }

    private fun JSONArray?.toIntList(): List<Int> =
        this?.let { (0 until it.length()).map { i -> it.optInt(i) } } ?: emptyList()

    private fun JSONArray?.toDoubleList(): List<Double> =
        this?.let { (0 until it.length()).map { i -> it.optDouble(i) } } ?: emptyList()

    private fun systemContext(): Context {
        val at = Class.forName("android.app.ActivityThread")
        val thread = at.getMethod("systemMain").invoke(null)
        return at.getMethod("getSystemContext").invoke(thread) as Context
    }
}
