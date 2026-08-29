package com.example

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings Tile Service for SensorsOff / Ultra private.
 * Toggles device hardware sensor privacy asynchronously off the main UI thread.
 */
class SensorsOffTileService : TileService() {

    companion object {
        private const val TAG = "SensorsOffTileService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile added to Quick Settings panel")
        refreshTileImmediately()
    }

    override fun onStartListening() {
        super.onStartListening()
        // 1. Immediate synchronous update from fast local state so SystemUI never displays STATE_UNAVAILABLE
        refreshTileImmediately()

        // 2. Asynchronous verification of live hardware state
        serviceScope.launch {
            try {
                val blockMode = ShizukuManager.getTileBlockMode(applicationContext)
                val isSensorsOff = if (blockMode == "cam_mic") {
                    ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                            ShizukuManager.getIndividualSensorState(applicationContext, "mic")
                } else {
                    ShizukuManager.getSensorsOffState(applicationContext)
                }

                withContext(Dispatchers.Main) {
                    updateTileState(isSensorsOff)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error checking live sensor state in onStartListening", e)
            }
        }
    }

    private fun refreshTileImmediately() {
        try {
            val prefs = applicationContext.getSharedPreferences("sensors_off_prefs", MODE_PRIVATE)
            val blockMode = prefs.getString("tile_block_mode", "global") ?: "global"
            val isSensorsOff = if (blockMode == "cam_mic") {
                prefs.getBoolean("sensor_blocked_camera", false) || prefs.getBoolean("sensor_blocked_mic", false)
            } else {
                prefs.getBoolean("sensors_off_enabled", false)
            }
            updateTileState(isSensorsOff)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in immediate tile refresh", e)
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked! Initiating asynchronous sensor toggle...")

        val blockMode = ShizukuManager.getTileBlockMode(applicationContext)
        val prefs = applicationContext.getSharedPreferences("sensors_off_prefs", MODE_PRIVATE)
        val current = if (blockMode == "cam_mic") {
            prefs.getBoolean("sensor_blocked_camera", false) || prefs.getBoolean("sensor_blocked_mic", false)
        } else {
            prefs.getBoolean("sensors_off_enabled", false)
        }
        val target = !current

        // Instant optimistic tile update so user feels immediate response
        updateTileState(target)

        serviceScope.launch {
            if (blockMode == "cam_mic") {
                Log.d(TAG, "Toggling Selective Camera + Mic to $target")
                ShizukuManager.setIndividualSensorState(applicationContext, "camera", target)
                ShizukuManager.setIndividualSensorState(applicationContext, "mic", target)
            } else {
                Log.d(TAG, "Toggling Global SensorsOff to $target")
                ShizukuManager.setSensorsOffState(applicationContext, target)
            }

            // Sync with hardware state
            val confirmedState = if (blockMode == "cam_mic") {
                ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                        ShizukuManager.getIndividualSensorState(applicationContext, "mic")
            } else {
                ShizukuManager.getSensorsOffState(applicationContext)
            }

            withContext(Dispatchers.Main) {
                updateTileState(confirmedState)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun updateTileState(isSensorsOff: Boolean) {
        val tile = qsTile ?: return

        val iconStyle = ShizukuManager.getTileIconStyle(applicationContext)
        val customLabel = ShizukuManager.getTileLabelText(applicationContext)
        val activeSubtitle = ShizukuManager.getTileActiveSubtitleText(applicationContext)
        val disabledSubtitle = ShizukuManager.getTileDisabledSubtitleText(applicationContext)
        val customIconPath = ShizukuManager.getCustomIconPath(applicationContext)

        val tileIcon: Icon = if (iconStyle == "custom" && customIconPath != null) {
            val bitmap = android.graphics.BitmapFactory.decodeFile(customIconPath)
            if (bitmap != null) {
                Icon.createWithBitmap(bitmap)
            } else {
                Icon.createWithResource(this, R.drawable.ic_sensors_off)
            }
        } else {
            val resId = when (iconStyle) {
                "shield" -> R.drawable.ic_shield_sensors
                "camera_off" -> R.drawable.ic_camera_off
                "mic_off" -> R.drawable.ic_mic_off
                "motion_off" -> R.drawable.ic_motion_sensors_off
                "aosp" -> R.drawable.ic_sensor_off
                else -> R.drawable.ic_sensors_off
            }
            Icon.createWithResource(this, resId)
        }

        val displayLabel = if (customLabel.isNotBlank()) customLabel else getString(R.string.tile_label)

        if (isSensorsOff) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = displayLabel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (activeSubtitle.isNotBlank()) activeSubtitle else null
            }
            tile.icon = tileIcon
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = displayLabel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (disabledSubtitle.isNotBlank()) disabledSubtitle else null
            }
            tile.icon = tileIcon
        }

        tile.updateTile()
    }
}

