package com.mubarak.bydspeed.car

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.mubarak.bydspeed.BuildConfig
import rikka.shizuku.Shizuku

/** Client side of [CarUserService]: connects through Shizuku and routes car reads/writes to it. */
object ShizukuCar {
    private const val REQ = 4097

    enum class State { CONNECTED, NO_SHIZUKU, NO_PERMISSION, BINDING, OLD, ERROR }

    @Volatile var bridge: ICarBridge? = null
        private set
    val connected get() = bridge?.asBinder()?.pingBinder() == true

    /** Fired on the main thread whenever connection state changes. */
    var onChange: (() -> Unit)? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bridge = service?.takeIf { it.pingBinder() }?.let { ICarBridge.Stub.asInterface(it) }
            onChange?.invoke()
        }
        override fun onServiceDisconnected(name: ComponentName?) { bridge = null; onChange?.invoke() }
    }

    private val permListener = Shizuku.OnRequestPermissionResultListener { code, grant ->
        if (code == REQ && grant == PackageManager.PERMISSION_GRANTED) bind() else onChange?.invoke()
    }

    @Volatile private var started = false

    /** Call once at app startup: auto-binds whenever Shizuku is running and already authorized. */
    fun autoStart() {
        if (started) return
        started = true
        try {
            Shizuku.addRequestPermissionResultListener(permListener)
            Shizuku.addBinderDeadListener { bridge = null; onChange?.invoke() }
            Shizuku.addBinderReceivedListenerSticky {
                try {
                    if (!connected && !Shizuku.isPreV11() &&
                        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind()
                } catch (_: Throwable) {}
                onChange?.invoke()
            }
        } catch (_: Throwable) {}
    }

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, CarUserService::class.java.name))
            .daemon(false).processNameSuffix("car").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE)
    }

    fun status(): State = try {
        when {
            !Shizuku.pingBinder() -> State.NO_SHIZUKU
            Shizuku.isPreV11() -> State.OLD
            connected -> State.CONNECTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.BINDING
            else -> State.NO_PERMISSION
        }
    } catch (t: Throwable) { State.ERROR }

    /** Starts (or resumes) the connection; may trigger a Shizuku permission prompt. */
    fun connect(): State = try {
        when {
            !Shizuku.pingBinder() -> State.NO_SHIZUKU
            Shizuku.isPreV11() -> State.OLD
            connected -> State.CONNECTED
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> { bind(); State.BINDING }
            else -> { Shizuku.requestPermission(REQ); State.NO_PERMISSION }
        }
    } catch (t: Throwable) { State.ERROR }

    private fun bind() = try { Shizuku.bindUserService(args, conn) } catch (t: Throwable) { onChange?.invoke() }

    fun get(cls: String?, methods: List<String>, args: List<Int>): Double? {
        val b = bridge ?: return null
        return try {
            b.invokeGet(cls ?: "", methods.toTypedArray(), args.toIntArray())
                .let { if (it.startsWith("OK:")) it.substring(3).toDoubleOrNull() else null }
        } catch (t: Throwable) { null }
    }

    fun set(cls: String?, method: String, args: List<Double>): String? {
        val b = bridge ?: return "shizuku-off"
        return try {
            b.invokeSet(cls ?: "", method, args.toDoubleArray()).let { if (it == "OK") null else it }
        } catch (t: Throwable) { t.message ?: "remote-error" }
    }

    fun scan(): String? = try { bridge?.scanReport() } catch (t: Throwable) { null }
}
