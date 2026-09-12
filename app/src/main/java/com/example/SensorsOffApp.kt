package com.example

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom Application class for SensorsOff.
 * Ensures critical subsystems (Shizuku AIDL binder listeners and TileLogManager)
 * are initialized immediately upon process creation, whether launched from the UI,
 * Quick Settings TileService, or system broadcasts.
 */
class SensorsOffApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("SensorsOffApp", "SensorsOff Application process initialized")
        try {
            TileLogManager.initialize(this)
            ShizukuManager.initialize(this)
            
            // Pre-warm root state asynchronously to prevent cold-start UI stalls or false-negatives
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                ShizukuManager.refreshRootState()
            }

            // Honor user preference: only run foreground keep-alive if explicitly enabled by user
            if (SensorsOffBackgroundService.isKeepAliveEnabled(this)) {
                SensorsOffBackgroundService.start(this)
            } else {
                SensorsOffBackgroundService.stop(this)
            }
        } catch (e: Throwable) {
            Log.e("SensorsOffApp", "Failed during application initialization", e)
        }
    }
}
