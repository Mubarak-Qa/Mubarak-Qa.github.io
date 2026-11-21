package com.mubarak.bydspeed.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mubarak.bydspeed.R
import com.mubarak.bydspeed.ui.MainActivity
import org.json.JSONObject

/** Background internet-radio playback, so music keeps playing while Waze is open. */
class RadioService : Service() {

    private var player: ExoPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    State.playing = isPlaying; State.buffering = false; State.error = null; notifyListeners()
                }
                override fun onPlaybackStateChanged(s: Int) {
                    State.buffering = s == Player.STATE_BUFFERING; notifyListeners()
                }
                override fun onPlayerError(error: PlaybackException) {
                    State.playing = false; State.buffering = false
                    State.error = error.errorCodeName; notifyListeners()
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> intent.getStringExtra(EXTRA_STATION)?.let {
                val s = Station.fromJson(JSONObject(it))
                State.station = s
                foreground(s)
                val item = MediaItem.Builder().setUri(s.url)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(s.name).build())
                    .apply { if (s.hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                    .build()
                player?.run { setMediaItem(item); prepare(); play() }
                State.buffering = true; State.error = null; notifyListeners()
            }
            ACTION_TOGGLE -> player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release(); player = null
        State.playing = false; State.buffering = false; State.station = null
        notifyListeners()
        super.onDestroy()
    }

    private fun foreground(s: Station) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.tab_radio), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, RadioService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = b.setSmallIcon(R.drawable.ic_radio).setContentTitle(s.name)
            .setContentText(getString(R.string.tab_radio)).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.radio_stop), stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF_ID, n)
    }

    /** Shared playback state observed by the radio tab. */
    object State {
        @Volatile var station: Station? = null
        @Volatile var playing = false
        @Volatile var buffering = false
        @Volatile var error: String? = null
        val listeners = mutableSetOf<() -> Unit>()
    }

    companion object {
        private const val CHANNEL = "radio"
        private const val NOTIF_ID = 2
        private const val ACTION_PLAY = "play"
        private const val ACTION_TOGGLE = "toggle"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_STATION = "station"
        private val main = Handler(Looper.getMainLooper())

        private fun notifyListeners() = main.post { State.listeners.toList().forEach { it() } }

        fun play(context: Context, s: Station) = ContextCompat.startForegroundService(
            context, Intent(context, RadioService::class.java).setAction(ACTION_PLAY)
                .putExtra(EXTRA_STATION, s.toJson().toString())
        )

        fun toggle(context: Context) {
            if (State.station != null)
                context.startService(Intent(context, RadioService::class.java).setAction(ACTION_TOGGLE))
        }

        fun stop(context: Context) = context.stopService(Intent(context, RadioService::class.java))
    }
}
