package com.example

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.util.Log

/**
 * Receiver that listens to system events like boot, update, or device unlock
 * and requests SystemUI to request listening / refresh the Quick Settings tile,
 * ensuring the tile is pre-warmed and never marked as unavailable.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.d("BootCompletedReceiver", "Received intent action: ${intent?.action}")
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, SensorsOffTileService::class.java)
            )
        } catch (e: Throwable) {
            Log.e("BootCompletedReceiver", "Failed to request listening state for tile", e)
        }
    }
}
