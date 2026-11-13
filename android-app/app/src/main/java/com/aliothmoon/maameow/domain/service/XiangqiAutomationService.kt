package com.aliothmoon.maameow.domain.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aliothmoon.maameow.MainActivity
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.domain.xiangqi.XiangqiTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Keeps the independent Xiangqi loop alive, with a stop action available outside the app. */
class XiangqiAutomationService : Service() {
    companion object {
        private const val CHANNEL = "xiangqi_auto"
        private const val NOTIFICATION = 4201
        private const val STOP = "xiangqi.stop"
        private val ids = AtomicLong()
        internal data class Run(val id: Long, val hintsOnly: Boolean = false, val stop: (String) -> Unit)
        internal val current = MutableStateFlow<Run?>(null)

        internal fun start(context: Context, stop: (String) -> Unit, hintsOnly: Boolean = false): Run {
            check(!SpecialUseFgsGate.isDenied(context)) { "Foreground service permission denied" }
            val run = Run(ids.incrementAndGet(), hintsOnly, stop)
            current.value = run
            try {
                context.startForegroundService(Intent(context, XiangqiAutomationService::class.java))
            } catch (error: RuntimeException) {
                finish(run)
                throw error
            }
            return run
        }

        internal fun finish(run: Run) { current.compareAndSet(run, null) }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var bound: Run? = null
    private var observing = false
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        bound = current.value
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.xiangqi_auto_start), NotificationManager.IMPORTANCE_LOW))
        val immutable = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), immutable)
        val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(STOP)
            .putExtra("run", bound?.id ?: 0), immutable)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_maa_logo).setContentTitle(getString(
                if (bound?.hintsOnly == true) R.string.xiangqi_hint_start else R.string.xiangqi_auto_start))
            .setContentText(getString(if (bound?.hintsOnly == true) R.string.xiangqi_hint_notification else R.string.xiangqi_auto_notification)).setContentIntent(open)
            .setOngoing(true).addAction(0, getString(R.string.xiangqi_auto_stop), stop).build()
        try {
            // Promote even if the loop already finished before this callback arrived.
            SpecialUseFgsGate.startForeground(this, NOTIFICATION, notification)
        } catch (error: RuntimeException) {
            XiangqiTrace("auto-service").failure("foreground", error)
            bound?.stop?.invoke("foreground-service-start-failed")
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == STOP && intent.getLongExtra("run", 0) == bound?.id) bound?.stop?.invoke("notification-stop")
        if (!observing) {
            observing = true
            scope.launch {
                current.collect { run ->
                    if (run == null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else bound = run
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        bound?.takeIf { it == current.value }?.stop?.invoke("automation-service-destroyed")
        scope.cancel()
        super.onDestroy()
    }
}
