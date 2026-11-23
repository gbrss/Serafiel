package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.i18n.tr

/**
 * Mantiene viva la música del grupo con la app minimizada o la pantalla apagada
 * (servicio en primer plano con notificación fija y botón para detener).
 */
class MusicPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            onStopRequested?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val notification = build(title)
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    private fun build(title: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, tr(R.string.music_menu), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MusicPlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_serafiel)
            .setContentTitle(title.ifBlank { tr(R.string.music_menu) })
            .setContentText(tr(R.string.music_menu))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(0, tr(R.string.music_cd_stop_local), stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "group_music"
        private const val NOTIF_ID = 4711
        private const val ACTION_STOP = "com.example.music.STOP"
        private const val EXTRA_TITLE = "title"

        /** Lo asigna el reproductor: qué hacer al pulsar "Detener" en la notificación. */
        @Volatile var onStopRequested: (() -> Unit)? = null
        @Volatile private var running = false
        @Volatile private var lastTitle = ""

        fun start(context: Context, title: String) {
            if (running && title == lastTitle) return
            running = true; lastTitle = title
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, MusicPlaybackService::class.java).putExtra(EXTRA_TITLE, title)
                )
            }
        }

        fun stop(context: Context) {
            if (!running) return
            running = false; lastTitle = ""
            runCatching { context.stopService(Intent(context, MusicPlaybackService::class.java)) }
        }
    }
}
