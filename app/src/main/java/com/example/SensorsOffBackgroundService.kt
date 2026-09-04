package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lightweight Foreground Keep-Alive Service for SensorsOff.
 * Prevents Android OS and aggressive OEM task killers (Xiaomi MIUI/HyperOS, Samsung)
 * from killing the process when swiped from Recents.
 *
 * Guarantees:
 * 1. Shizuku AIDL IPC connection is kept permanently active in RAM.
 * 2. Instant 0ms response time when tapping the Quick Settings tile.
 * 3. Status notification in shade with 1-tap toggle action.
 */
class SensorsOffBackgroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SensorsOffBackgroundService created")
        TileLogManager.initialize(applicationContext)
        ShizukuManager.initialize(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action=$action")

        when (action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                serviceScope.launch {
                    val isCurrentlyOff = ShizukuManager.getSensorsOffState(applicationContext)
                    val targetState = !isCurrentlyOff
                    ShizukuManager.setSensorsOffState(applicationContext, targetState, skipNotify = true)

                    // Refresh QS tile
                    try {
                        TileService.requestListeningState(
                            applicationContext,
                            ComponentName(applicationContext, SensorsOffTileService::class.java)
                        )
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to notify tile: ${e.message}")
                    }

                    updateForegroundNotification()
                }
            }
            ACTION_UPDATE, ACTION_START -> {
                updateForegroundNotification()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SensorsOffBackgroundService destroyed")
        serviceScope.cancel()
    }

    private fun updateForegroundNotification() {
        val notification = buildStatusNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildStatusNotification(): Notification {
        val isOff = ShizukuManager.getSensorsOffState(applicationContext)
        val mode = ShizukuManager.getTileBlockMode(applicationContext)
        val modeTitle = if (mode == "cam_mic") "Camera & Mic" else "All Hardware Sensors"

        val title = if (isOff) "Sensors Blocked" else "Sensors Allowed"
        val subtitle = if (isOff) "$modeTitle are disabled" else "$modeTitle are active"

        // Open app intent
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Toggle action intent
        val toggleIntent = Intent(this, SensorsOffBackgroundService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionLabel = if (isOff) "Allow Sensors" else "Block Sensors"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sensors_off)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText("Keep-Alive Active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_sensors_off, actionLabel, togglePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Keep-Alive Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps SensorsOff active in memory to guarantee immediate Quick Settings tile response."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "SensorsOffBgService"
        const val CHANNEL_ID = "sensors_off_keep_alive_channel"
        const val NOTIFICATION_ID = 4040

        const val ACTION_START = "com.example.action.START_KEEP_ALIVE"
        const val ACTION_STOP = "com.example.action.STOP_KEEP_ALIVE"
        const val ACTION_UPDATE = "com.example.action.UPDATE_STATUS"
        const val ACTION_TOGGLE = "com.example.action.TOGGLE_SENSORS"

        private const val PREF_KEY_KEEP_ALIVE = "pref_keep_alive_service_enabled"

        fun isKeepAliveEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_KEY_KEEP_ALIVE, false)
        }

        fun setKeepAliveEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_KEY_KEEP_ALIVE, enabled).apply()
            if (enabled) {
                start(context)
            } else {
                stop(context)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, SensorsOffBackgroundService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start SensorsOffBackgroundService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SensorsOffBackgroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to stop SensorsOffBackgroundService: ${e.message}")
            }
        }

        fun update(context: Context) {
            if (!isKeepAliveEnabled(context)) return
            val intent = Intent(context, SensorsOffBackgroundService::class.java).apply {
                action = ACTION_UPDATE
            }
            try {
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to update notification: ${e.message}")
            }
        }
    }
}
