package com.example

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver that listens to system events like boot, update, or device unlock
 * and requests SystemUI to request listening / refresh the Quick Settings tile,
 * ensuring the tile is pre-warmed and never marked as unavailable.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: "UNKNOWN_ACTION"
        Log.d("BootCompletedReceiver", "Received intent action: $action")
        try {
            TileLogManager.initialize(context)
            ShizukuManager.initialize(context)

            TileLogManager.log(
                context,
                LogCategory.SYSTEM,
                LogLevel.INFO,
                "Device boot trigger: $action"
            )

            // 1. Pre-warm Quick Settings tile
            TileService.requestListeningState(
                context,
                ComponentName(context, SensorsOffTileService::class.java)
            )

            // 2. Start keep-alive daemon if enabled by user
            if (SensorsOffBackgroundService.isKeepAliveEnabled(context)) {
                SensorsOffBackgroundService.start(context)
            }

            // 3. If device is rooted and Shizuku is down, try auto-starting Shizuku daemon
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    if (ShizukuManager.isRootAvailable() && !ShizukuManager.isShizukuRunning()) {
                        ShizukuManager.tryAutoStartShizukuViaRoot(context)
                    }
                } catch (e: Throwable) {
                    Log.d("BootCompletedReceiver", "Root auto-start note: ${e.message}")
                }
            }
        } catch (e: Throwable) {
            Log.e("BootCompletedReceiver", "Failed to process boot event", e)
        }
    }
}
