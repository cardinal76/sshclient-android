package it.cardinal.sshclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Servizio in primo piano: finche' c'e' una connessione aperta mostra una
 * notifica fissa, e in cambio Android non chiude il processo quando l'app
 * va sullo sfondo. Non fa altro: la connessione e' di [TerminalSession].
 */
class SessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = (application as SshClientApp).session
        // Chi parte con startForegroundService deve chiamare startForeground
        // comunque, anche se sta per fermarsi: altrimenti Android chiude l'app.
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(session?.host?.title),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (intent?.action == ACTION_DISCONNECT) session?.disconnect()
        // La connessione puo' essere gia' finita (server irraggiungibile)
        // prima che il servizio sia partito.
        stopIfIdle()
        return START_NOT_STICKY
    }

    private fun notification(title: String?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Connessione aperta", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnect = PendingIntent.getService(
            this, 1,
            Intent(this, SessionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_terminal)
            .setContentTitle(title ?: getString(R.string.app_name))
            .setContentText("Connessione SSH aperta")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "Disconnetti", disconnect)
            .build()
    }

    companion object {
        private const val CHANNEL = "sessione"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "it.cardinal.sshclient.DISCONNETTI"

        private var instance: SessionService? = null

        /** Ferma il servizio se non c'e' piu' una connessione da tenere viva. Dal thread principale. */
        fun stopIfIdle() {
            val service = instance ?: return
            if ((service.application as SshClientApp).session?.isActive == true) return
            ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
            service.stopSelf()
        }
    }
}
