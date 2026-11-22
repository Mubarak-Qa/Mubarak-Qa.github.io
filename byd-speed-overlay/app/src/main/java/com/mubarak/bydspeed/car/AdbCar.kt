package com.mubarak.bydspeed.car

import android.content.Context
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * No-Shizuku car control: the app becomes its own ADB client, connects to the head unit's
 * wireless-debugging port on localhost, and runs [CarHelper] as the shell user. Reads/writes
 * are JSON requests over that one shell stream.
 *
 * One-time setup per car: enable Wireless debugging and pair once (6-digit code). After that
 * the app auto-connects on launch. Entirely experimental and unverified on hardware.
 */
object AdbCar {
    enum class State { DISCONNECTED, CONNECTING, PAIRING_REQUIRED, CONNECTED, ERROR }

    @Volatile var state: State = State.DISCONNECTED
        private set
    @Volatile var lastError: String? = null
        private set
    var onChange: (() -> Unit)? = null
    /** Appended diagnostic log, newest last. */
    val log = StringBuilder()

    val connected get() = state == State.CONNECTED && stream?.isClosed == false

    private var stream: AdbStream? = null
    private var writer: OutputStream? = null
    private var reader: BufferedReader? = null
    private val seq = AtomicInteger(1)
    private val lock = Any()

    private fun log(msg: String) {
        synchronized(log) { log.append(msg).append('\n'); if (log.length > 8000) log.delete(0, log.length - 6000) }
    }
    private fun setState(s: State, err: String? = null) { state = s; lastError = err; onChange?.invoke() }

    /** Discover the wireless-debugging connect port via mDNS, then connect and start the daemon. */
    fun connect(context: Context, port: Int = 0) {
        if (state == State.CONNECTING || connected) return
        setState(State.CONNECTING)
        Thread {
            try {
                val mgr = AdbCarManager.getInstance(context)
                log("autoConnect…")
                val ok = try {
                    if (port > 0) { log("connect 127.0.0.1:$port"); mgr.connect("127.0.0.1", port) }
                    else { log("autoConnect (mDNS)…"); mgr.autoConnect(context, 12_000) }
                } catch (e: AdbPairingRequiredException) {
                    log("pairing required")
                    setState(State.PAIRING_REQUIRED); return@Thread
                }
                if (!ok) { log("connect failed"); setState(State.ERROR, "connect-failed"); return@Thread }
                startDaemon(context, mgr)
            } catch (t: Throwable) {
                log("ERR ${t.javaClass.simpleName}: ${t.message}")
                setState(State.ERROR, "${t.javaClass.simpleName}: ${t.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    /** Pair using the 6-digit code; the pairing port is discovered via mDNS. */
    fun pair(context: Context, code: String, port: Int, onDone: (Boolean, String?) -> Unit) {
        Thread {
            try {
                val p = if (port > 0) port else (discoverPort(context, AdbMdns.SERVICE_TYPE_TLS_PAIRING)
                    ?: run { onDone(false, "pairing port not found; type it from the pairing dialog"); return@Thread })
                log("pairing on :$p")
                val ok = AdbCarManager.getInstance(context).pair(p, code)
                log("pair result: $ok")
                onDone(ok, if (ok) null else "pair-failed")
                if (ok) connect(context)
            } catch (t: Throwable) {
                log("pair ERR ${t.message}")
                onDone(false, "${t.javaClass.simpleName}: ${t.message}")
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun discoverPort(context: Context, service: String, timeoutMs: Long = 8000): Int? {
        val latch = CountDownLatch(1)
        val found = AtomicInteger(-1)
        val mdns = AdbMdns(context, service) { _: InetAddress?, port: Int ->
            if (port > 0 && found.compareAndSet(-1, port)) latch.countDown()
        }
        mdns.start()
        return try { latch.await(timeoutMs, TimeUnit.MILLISECONDS); found.get().takeIf { it > 0 } }
        finally { mdns.stop() }
    }

    private fun startDaemon(context: Context, mgr: AdbCarManager) {
        val apk = context.applicationInfo.sourceDir
        val cmd = "CLASSPATH=$apk app_process /system/bin --nice-name=bydcarhub.daemon " +
            "com.mubarak.bydspeed.car.CarHelper"
        log("starting daemon")
        val s = mgr.openStream("shell:$cmd")
        stream = s
        writer = s.openOutputStream()
        reader = BufferedReader(InputStreamReader(s.openInputStream()))
        // Wait for the ready line.
        val ready = readLineWithTimeout(3000)
        if (ready?.contains("\"ready\"") == true) {
            log("daemon ready"); setState(State.CONNECTED)
        } else {
            log("no ready line: $ready"); setState(State.ERROR, "daemon-no-ready")
            runCatching { s.close() }
        }
    }

    private fun readLineWithTimeout(timeoutMs: Long): String? {
        val r = reader ?: return null
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (r.ready()) return r.readLine()
            Thread.sleep(20)
        }
        return if (r.ready()) r.readLine() else null
    }

    private fun request(obj: JSONObject): JSONObject? = synchronized(lock) {
        val w = writer ?: return null
        val r = reader ?: return null
        return try {
            val id = seq.getAndIncrement()
            obj.put("id", id)
            w.write((obj.toString() + "\n").toByteArray())
            w.flush()
            // Read lines until the matching id (daemon replies in order, but be safe).
            val deadline = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < deadline) {
                val line = if (r.ready()) r.readLine() else { Thread.sleep(15); continue }
                line ?: break
                if (!line.startsWith("{")) continue
                val j = JSONObject(line)
                if (j.optInt("id", id) == id) return j
            }
            null
        } catch (t: Throwable) {
            log("req ERR ${t.message}"); disconnect(); null
        }
    }

    fun get(cls: String?, methods: List<String>, args: List<Int>): Double? {
        val j = request(JSONObject().put("op", "get").put("cls", cls ?: "")
            .put("methods", JSONArray(methods)).put("args", JSONArray(args))) ?: return null
        if (!j.optBoolean("ok")) return null
        val v = j.optDouble("value", Double.NaN)
        return if (v.isNaN()) null else v
    }

    fun set(cls: String?, method: String, args: List<Double>): String? {
        val j = request(JSONObject().put("op", "set").put("cls", cls ?: "")
            .put("method", method).put("args", JSONArray(args))) ?: return "adb-offline"
        return if (j.optBoolean("ok")) null else j.optString("err", "error")
    }

    fun scan(): String? {
        val j = request(JSONObject().put("op", "scan")) ?: return null
        return if (j.optBoolean("ok")) j.optString("report") else null
    }

    fun disconnect() {
        runCatching { stream?.close() }
        stream = null; writer = null; reader = null
        if (state == State.CONNECTED) setState(State.DISCONNECTED)
    }
}
