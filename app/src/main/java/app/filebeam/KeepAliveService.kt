package app.filebeam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/** Keeps the process, CPU and WiFi awake while files are moving. */
class KeepAliveService : Service() {
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra("text") ?: "Transferring files"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder: Notification.Builder
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("transfer", "File transfer", NotificationManager.IMPORTANCE_LOW)
            )
            builder = Notification.Builder(this, "transfer")
        } else {
            builder = Notification.Builder(this)
        }
        val n = builder
            .setContentTitle("FileBeam")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, n)
        }

        if (wake == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "filebeam:transfer").apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L)
            }
        }
        if (wifi == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "filebeam:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        try { if (wake?.isHeld == true) wake?.release() } catch (e: Exception) {}
        try { if (wifi?.isHeld == true) wifi?.release() } catch (e: Exception) {}
        wake = null
        wifi = null
        super.onDestroy()
    }

    companion object {
        private var users = 0

        fun start(ctx: Context, text: String) {
            val app = ctx.applicationContext
            synchronized(this) { users++ }
            val i = Intent(app, KeepAliveService::class.java).putExtra("text", text)
            try {
                if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
            } catch (e: Exception) {
            }
        }

        fun stop(ctx: Context) {
            val app = ctx.applicationContext
            Handler(Looper.getMainLooper()).postDelayed({
                val remaining = synchronized(this) {
                    if (users > 0) users--
                    users
                }
                if (remaining == 0) {
                    try { app.stopService(Intent(app, KeepAliveService::class.java)) } catch (e: Exception) {}
                }
            }, 1500)
        }
    }
}
