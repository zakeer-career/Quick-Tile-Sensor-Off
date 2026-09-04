package com.example

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        @Volatile
        private var pendingTargetState: Boolean? = null
        @Volatile
        private var pendingTargetExpiryTimeMs: Long = 0L
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var listeningJob: kotlinx.coroutines.Job? = null
    private var clickJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        TileLogManager.initialize(applicationContext)
        ShizukuManager.initialize(applicationContext)
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

        // Cancel any pending listening job
        listeningJob?.cancel()

        // If user tapped recently within lock window, preserve optimistic state with 0ms sync
        val now = System.currentTimeMillis()
        if (pendingTargetState != null && now < pendingTargetExpiryTimeMs) {
            refreshTileImmediately()
            return
        }

        // 1. Immediate synchronous update from fast local state so SystemUI never displays STATE_UNAVAILABLE
        refreshTileImmediately()

        // 2. Asynchronous verification of live hardware state
        listeningJob = serviceScope.launch {
            try {
                if (ShizukuManager.isShizukuInstalled(applicationContext) && !ShizukuManager.isShizukuRunning()) {
                    ShizukuManager.awaitShizukuBinder(250L)
                }

                val blockMode = ShizukuManager.getTileBlockMode(applicationContext)
                val label = ShizukuManager.getTileLabelText(applicationContext)
                val iconStyle = ShizukuManager.getTileIconStyle(applicationContext)

                val isSensorsOff = if (blockMode == "cam_mic") {
                    ShizukuManager.getIndividualSensorState(applicationContext, "camera") ||
                            ShizukuManager.getIndividualSensorState(applicationContext, "mic")
                } else {
                    ShizukuManager.getSensorsOffState(applicationContext)
                }

                // If user tapped during our hardware query, abort immediately so we don't overwrite optimistic UI
                if (pendingTargetState != null && System.currentTimeMillis() < pendingTargetExpiryTimeMs) {
                    return@launch
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
                    if (pendingTargetState == null || System.currentTimeMillis() >= pendingTargetExpiryTimeMs) {
                        updateTileState(isSensorsOff)
                    }
                }
            } catch (e: Throwable) {
                if (e !is kotlinx.coroutines.CancellationException) {
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
            val now = System.currentTimeMillis()
            val isSensorsOff = if (pendingTargetState != null && now < pendingTargetExpiryTimeMs) {
                pendingTargetState!!
            } else {
                val prefs = applicationContext.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
                prefs.getBoolean("sensors_off_enabled", false)
            }
            updateTileState(isSensorsOff)
        } catch (e: Throwable) {
            Log.e(TAG, "Error in immediate tile refresh", e)
        }
    }

    override fun onClick() {
        super.onClick()
        val clickTime = System.currentTimeMillis()
        Log.d(TAG, "Tile clicked! Executing instant 0ms optimistic UI toggle...")

        // 1. Immediately cancel any background query from onStartListening so it cannot overwrite the UI
        listeningJob?.cancel()

        // 2. Instant synchronous determination of target state (0ms)
        val currentTileState = qsTile?.state ?: Tile.STATE_INACTIVE
        val isCurrentlyActive = (currentTileState == Tile.STATE_ACTIVE)
        val target = !isCurrentlyActive
        val targetStateName = if (target) "STATE_ACTIVE (On)" else "STATE_INACTIVE (Off)"

        // 3. Lock optimistic target state so rapid SystemUI onStartListening cycles don't revert UI
        pendingTargetState = target
        pendingTargetExpiryTimeMs = System.currentTimeMillis() + 2000L

        // 4. Instant synchronous tile update (0ms latency, identical to native developer tile)
        updateTileState(target)

        TileLogManager.logTileEvent(
            applicationContext,
            "QS Tile Tap Event",
            "Trigger: SystemUI Tap | Current: ${if (isCurrentlyActive) "ON" else "OFF"} -> Target: $targetStateName",
            LogLevel.INFO
        )

        // 5. Asynchronous hardware toggle on Dispatchers.IO (< 1ms via direct AIDL binder)
        clickJob?.cancel()
        clickJob = serviceScope.launch(Dispatchers.IO) {
            val executionStartTime = System.currentTimeMillis()
            val blockMode = ShizukuManager.getTileBlockMode(applicationContext)

            val success = if (blockMode == "cam_mic") {
                val camSuccess = ShizukuManager.setIndividualSensorState(applicationContext, "camera", target)
                val micSuccess = ShizukuManager.setIndividualSensorState(applicationContext, "mic", target)
                camSuccess && micSuccess
            } else {
                ShizukuManager.setSensorsOffState(applicationContext, target, skipNotify = true)
            }

            val elapsedMs = System.currentTimeMillis() - executionStartTime
            Log.d(TAG, "Hardware sensor toggle completed in ${elapsedMs}ms (success=$success)")

            val backendUsed = when {
                ShizukuManager.isShizukuAuthorized() -> "Direct Shizuku AIDL Proxy (<1ms)"
                ShizukuManager.isRootAvailable() -> "Root SuperUser (su)"
                ShizukuManager.hasSecureSettingsPermission(applicationContext) -> "Settings.Global (WRITE_SECURE)"
                else -> "System Fallback"
            }

            TileLogManager.logTileEvent(
                applicationContext,
                "Tile Toggle Completed",
                "Target: $target | Success: $success | Backend: $backendUsed | IPC Latency: ${elapsedMs}ms | Total: ${System.currentTimeMillis() - clickTime}ms",
                if (success) LogLevel.SUCCESS else LogLevel.WARN,
                executionMs = elapsedMs
            )

            TileLogManager.updateTileDiagnostics(
                applicationContext,
                lastState = if (target) "STATE_ACTIVE" else "STATE_INACTIVE",
                lastAction = "Toggle to ${if (target) "ON" else "OFF"}",
                lastLatencyMs = elapsedMs,
                blockMode = blockMode
            )

            withContext(Dispatchers.Main) {
                pendingTargetState = null
                if (!success) {
                    // Revert to true live hardware state if hardware toggle failed
                    val confirmed = ShizukuManager.getSensorsOffState(applicationContext)
                    updateTileState(confirmed)
                }
                SensorsOffBackgroundService.update(applicationContext)
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
        listeningJob?.cancel()
        clickJob?.cancel()
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
                Icon.createWithResource(this, if (isSensorsOff) R.drawable.tile_icon_sensorsoff_active else R.drawable.tile_icon_sensorsoff_inactive)
            }
        } else {
            val resId = when (iconStyle) {
                "shield" -> R.drawable.ic_shield_sensors
                "camera_off" -> R.drawable.ic_camera_off
                "mic_off" -> R.drawable.ic_mic_off
                "motion_off" -> R.drawable.ic_motion_sensors_off
                else -> if (isSensorsOff) R.drawable.tile_icon_sensorsoff_active else R.drawable.tile_icon_sensorsoff_inactive
            }
            Icon.createWithResource(this, resId)
        }

        val displayLabel = if (customLabel.isNotBlank()) customLabel else getString(R.string.tile_label)

        if (isSensorsOff) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = displayLabel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val sub = if (activeSubtitle.isNotBlank() && !activeSubtitle.equals("Blocked", ignoreCase = true)) {
                    activeSubtitle
                } else {
                    "On"
                }
                tile.subtitle = sub
            }
            tile.icon = tileIcon
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = displayLabel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val sub = if (disabledSubtitle.isNotBlank() && !disabledSubtitle.equals("Available", ignoreCase = true)) {
                    disabledSubtitle
                } else {
                    "Off"
                }
                tile.subtitle = sub
            }
            tile.icon = tileIcon
        }

        tile.updateTile()
    }
}


