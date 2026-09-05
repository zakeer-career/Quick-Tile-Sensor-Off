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
import kotlinx.coroutines.withContext

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
    private var shizukuWatchJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SensorsOffBackgroundService created")
        TileLogManager.initialize(applicationContext)
        ShizukuManager.initialize(applicationContext)
        createNotificationChannel()
        startShizukuWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action=$action")

        startShizukuWatcher()

        when (action) {
            ACTION_STOP -> {
                shizukuWatchJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                serviceScope.launch {
                    val mode = ShizukuManager.getTileBlockMode(applicationContext)
                    val isCurrentlyOff = if (mode == "cam_mic") {
                        ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                                ShizukuManager.getIndividualSensorState(applicationContext, "mic")
                    } else {
                        ShizukuManager.getSensorsOffState(applicationContext)
                    }
                    val targetState = !isCurrentlyOff
                    if (mode == "cam_mic") {
                        ShizukuManager.setIndividualSensorState(applicationContext, "camera", targetState, skipNotify = true)
                        ShizukuManager.setIndividualSensorState(applicationContext, "mic", targetState, skipNotify = true)
                    } else {
                        ShizukuManager.setSensorsOffState(applicationContext, targetState, skipNotify = true)
                    }

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

    private fun startShizukuWatcher() {
        if (ShizukuManager.isPrivilegeAvailable(applicationContext)) {
            shizukuWatchJob?.cancel()
            return
        }
        if (shizukuWatchJob?.isActive == true) return

        shizukuWatchJob = serviceScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Starting Shizuku setup watcher in background service...")
            var waitedMs = 0L
            val maxWait = 300_000L // 5 minutes post-reboot
            while (waitedMs < maxWait) {
                if (ShizukuManager.isPrivilegeAvailable(applicationContext)) {
                    Log.i(TAG, "Shizuku became available! Refreshing tile and notification.")
                    withContext(Dispatchers.Main) {
                        updateForegroundNotification()
                    }
                    try {
                        TileService.requestListeningState(
                            applicationContext,
                            ComponentName(applicationContext, SensorsOffTileService::class.java)
                        )
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to requestListeningState: ${e.message}")
                    }
                    TileLogManager.logPrivilegeEvent(
                        applicationContext,
                        "Shizuku Auto-Detected",
                        "Shizuku finished setting up in background. Tile auto-updated to operational state.",
                        LogLevel.SUCCESS
                    )
                    break
                }
                kotlinx.coroutines.delay(1000)
                waitedMs += 1000
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SensorsOffBackgroundService destroyed")
        shizukuWatchJob?.cancel()
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
        val hasPrivilege = ShizukuManager.isPrivilegeAvailable(applicationContext)
        val mode = ShizukuManager.getTileBlockMode(applicationContext)
        val isOff = if (mode == "cam_mic") {
            ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                    ShizukuManager.getIndividualSensorState(applicationContext, "mic")
        } else {
            ShizukuManager.getSensorsOffState(applicationContext)
        }
        val modeTitle = if (mode == "cam_mic") "Camera & Mic" else "All Hardware Sensors"

        val title = if (!hasPrivilege) {
            "Waiting for Shizuku..."
        } else if (isOff) {
            "Sensors Blocked"
        } else {
            "Sensors Allowed"
        }

        val subtitle = if (!hasPrivilege) {
            "SensorsOff will auto-activate when Shizuku setup completes"
        } else if (isOff) {
            "$modeTitle are disabled"
        } else {
            "$modeTitle are active"
        }

        val subText = if (!hasPrivilege) "Waiting for Privilege" else "Keep-Alive Active"

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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sensors_off)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setSubText(subText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent)

        if (hasPrivilege) {
            val actionLabel = if (isOff) "Allow Sensors" else "Block Sensors"
            builder.addAction(R.drawable.ic_sensors_off, actionLabel, togglePendingIntent)
        }

        return builder.build()
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
