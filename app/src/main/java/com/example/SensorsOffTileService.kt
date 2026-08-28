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

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
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
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d(TAG, "Tile clicked! Initiating asynchronous sensor toggle...")

        serviceScope.launch {
            val blockMode = ShizukuManager.getTileBlockMode(applicationContext)
            
            if (blockMode == "cam_mic") {
                val camState = ShizukuManager.getIndividualSensorState(applicationContext, "camera")
                val micState = ShizukuManager.getIndividualSensorState(applicationContext, "mic")
                val newState = !(camState || micState)

                Log.d(TAG, "Toggling Selective Camera + Mic to $newState")
                ShizukuManager.setIndividualSensorState(applicationContext, "camera", newState)
                ShizukuManager.setIndividualSensorState(applicationContext, "mic", newState)

                withContext(Dispatchers.Main) {
                    updateTileState(newState)
                }
            } else {
                val currentState = ShizukuManager.getSensorsOffState(applicationContext)
                val newState = !currentState

                Log.d(TAG, "Toggling Global SensorsOff to $newState")
                ShizukuManager.setSensorsOffState(applicationContext, newState)

                val updatedState = ShizukuManager.getSensorsOffState(applicationContext)
                withContext(Dispatchers.Main) {
                    updateTileState(updatedState)
                }
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

