package com.example

import android.app.Application
import android.util.Log

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
        } catch (e: Throwable) {
            Log.e("SensorsOffApp", "Failed during application initialization", e)
        }
    }
}
