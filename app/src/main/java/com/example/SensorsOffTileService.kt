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
 * Toggles device hardware sensor privacy asynchronously off the main UI thread
 * and provides deep telemetry logging to the app console.
 */
class SensorsOffTileService : TileService() {

    companion object {
        private const val TAG = "SensorsOffTileService"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        TileLogManager.initialize(applicationContext)
        TileLogManager.logTileEvent(
            applicationContext,
            "Tile Service Created",
            "SensorsOffTileService initialized by SystemUI process",
            LogLevel.DEBUG
        )
    }

    override fun onTileAdded() {
        super.onTileAdded()
        Log.d(TAG, "Tile added to Quick Settings panel")
        TileLogManager.logTileEvent(
            applicationContext,
            "Tile Added to Quick Settings",
            "SystemUI successfully bound SensorsOffTileService to user QS shade",
            LogLevel.SUCCESS
        )
        refreshTileImmediately()
    }

    override fun onTileRemoved() {
        super.onTileRemoved()
        Log.d(TAG, "Tile removed from Quick Settings panel")
        TileLogManager.logTileEvent(
            applicationContext,
            "Tile Removed from Shade",
            "User dragged tile out of active Quick Settings panel",
            LogLevel.WARN
        )
    }

    override fun onStartListening() {
        super.onStartListening()
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Tile entered onStartListening")

        // 1. Immediate synchronous update from fast local state so SystemUI never displays STATE_UNAVAILABLE
        refreshTileImmediately()

        // 2. Asynchronous verification of live hardware state
        serviceScope.launch {
            try {
                val blockMode = ShizukuManager.getTileBlockMode(applicationContext)
                val label = ShizukuManager.getTileLabelText(applicationContext)
                val iconStyle = ShizukuManager.getTileIconStyle(applicationContext)

                val isSensorsOff = if (blockMode == "cam_mic") {
                    ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                            ShizukuManager.getIndividualSensorState(applicationContext, "mic")
                } else {
                    ShizukuManager.getSensorsOffState(applicationContext)
                }

                val latency = System.currentTimeMillis() - startTime
                val stateName = if (isSensorsOff) "STATE_ACTIVE (2)" else "STATE_INACTIVE (1)"

                TileLogManager.logTileEvent(
                    applicationContext,
                    "QS Shade Opened (onStartListening)",
                    "State: $stateName | Mode: $blockMode | Label: '$label' | Style: $iconStyle | Sync Latency: ${latency}ms",
                    LogLevel.INFO,
                    executionMs = latency
                )

                TileLogManager.updateTileDiagnostics(
                    applicationContext,
                    lastState = stateName,
                    lastAction = "Shade Opened / Listening",
                    lastLatencyMs = latency,
                    blockMode = blockMode,
                    label = label,
                    iconStyle = iconStyle,
                    isListening = true
                )

                withContext(Dispatchers.Main) {
                    updateTileState(isSensorsOff)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error checking live sensor state in onStartListening", e)
                TileLogManager.logTileEvent(
                    applicationContext,
                    "Listening Sync Error",
                    "Exception during hardware state check: ${e.message}",
                    LogLevel.ERROR
                )
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(TAG, "Tile entered onStopListening")
        TileLogManager.logTileEvent(
            applicationContext,
            "QS Shade Closed (onStopListening)",
            "SystemUI released tile listening state to conserve background resources",
            LogLevel.DEBUG
        )
        TileLogManager.updateTileDiagnostics(
            applicationContext,
            isListening = false
        )
    }

    private fun refreshTileImmediately() {
        try {
            val blockMode = ShizukuManager.getTileBlockMode(applicationContext)
            val isSensorsOff = if (blockMode == "cam_mic") {
                ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                        ShizukuManager.getIndividualSensorState(applicationContext, "mic")
            } else {
                ShizukuManager.getSensorsOffState(applicationContext)
            }
            updateTileState(isSensorsOff)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in immediate tile refresh", e)
        }
    }

    override fun onClick() {
        super.onClick()
        val clickTime = System.currentTimeMillis()
        Log.d(TAG, "Tile clicked! Initiating asynchronous sensor toggle...")

        val blockMode = ShizukuManager.getTileBlockMode(applicationContext)
        val current = if (blockMode == "cam_mic") {
            ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                    ShizukuManager.getIndividualSensorState(applicationContext, "mic")
        } else {
            ShizukuManager.getSensorsOffState(applicationContext)
        }
        val target = !current
        val targetStateName = if (target) "STATE_ACTIVE (Sensors Blocked)" else "STATE_INACTIVE (Sensors Allowed)"

        TileLogManager.logTileEvent(
            applicationContext,
            "QS Tile Tap Event",
            "Trigger: SystemUI Tap | Current: ${if (current) "OFF" else "ON"} -> Target: $targetStateName | Mode: $blockMode",
            LogLevel.INFO
        )

        // Instant optimistic tile update so user feels immediate response
        updateTileState(target)

        serviceScope.launch {
            val executionStartTime = System.currentTimeMillis()
            val backendUsed: String

            if (blockMode == "cam_mic") {
                Log.d(TAG, "Toggling Selective Camera + Mic to $target")
                val camSuccess = ShizukuManager.setIndividualSensorState(applicationContext, "camera", target)
                val micSuccess = ShizukuManager.setIndividualSensorState(applicationContext, "mic", target)
                backendUsed = if (camSuccess && micSuccess) "Selective Camera/Mic HAL" else "Selective Matrix (Partial/Fail)"
            } else {
                Log.d(TAG, "Toggling Global SensorsOff to $target")
                val success = ShizukuManager.setSensorsOffState(applicationContext, target)
                backendUsed = when {
                    ShizukuManager.isShizukuAuthorized() -> "Shizuku AIDL Proxy"
                    ShizukuManager.isRootAvailable() -> "Root SuperUser (su)"
                    ShizukuManager.hasSecureSettingsPermission(applicationContext) -> "Settings.Global (WRITE_SECURE)"
                    else -> "System Fallback"
                }
            }

            val elapsedMs = System.currentTimeMillis() - executionStartTime

            // Verify live hardware state
            val confirmedState = if (blockMode == "cam_mic") {
                ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                        ShizukuManager.getIndividualSensorState(applicationContext, "mic")
            } else {
                ShizukuManager.getSensorsOffState(applicationContext)
            }

            val isConfirmed = confirmedState == target
            val level = if (isConfirmed) LogLevel.SUCCESS else LogLevel.WARN

            TileLogManager.logTileEvent(
                applicationContext,
                "Tile Toggle Completed",
                "Target: $target | Confirmed State: $confirmedState | Backend: $backendUsed | IPC Latency: ${elapsedMs}ms | Total: ${System.currentTimeMillis() - clickTime}ms",
                level,
                executionMs = elapsedMs
            )

            TileLogManager.updateTileDiagnostics(
                applicationContext,
                lastState = if (confirmedState) "STATE_ACTIVE" else "STATE_INACTIVE",
                lastAction = "Toggle to ${if (target) "BLOCKED" else "ALLOWED"}",
                lastLatencyMs = elapsedMs,
                blockMode = blockMode
            )

            withContext(Dispatchers.Main) {
                updateTileState(confirmedState)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TileLogManager.logTileEvent(
            applicationContext,
            "Tile Service Destroyed",
            "SensorsOffTileService lifecycle unbound by SystemUI",
            LogLevel.DEBUG
        )
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


