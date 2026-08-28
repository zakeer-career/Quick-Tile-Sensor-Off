package com.example

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

data class SensorItem(
    val id: String,
    val name: String,
    val type: String,
    val isBlocked: Boolean,
    val iconResName: String
)

data class TileSettingsState(
    val iconStyle: String = "stock", // "stock", "shield", "camera_off", "mic_off", "motion_off", "aosp", "custom"
    val customLabel: String = "Sensors Off",
    val activeSubtitle: String = "Sensors Disabled",
    val disabledSubtitle: String = "Sensors Enabled",
    val blockMode: String = "global", // "global" or "cam_mic"
    val customIconPath: String? = null
)

data class SensorUiState(
    val isSensorsOff: Boolean = false,
    val isShizukuInstalled: Boolean = false,
    val isShizukuRunning: Boolean = false,
    val isShizukuAuthorized: Boolean = false,
    val isRootAvailable: Boolean = false,
    val hasSecureSettingsPermission: Boolean = false,
    val adbGrantCommand: String = "",
    val deviceManufacturer: String = Build.MANUFACTURER,
    val deviceModel: String = Build.MODEL,
    val androidVersion: String = Build.VERSION.RELEASE,
    val appThemeMode: String = "system",
    val appLauncherAlias: String = "MainActivityDefault",
    val logs: List<String> = emptyList(),
    val tileSettings: TileSettingsState = TileSettingsState(),
    val sensorList: List<SensorItem> = listOf(
        SensorItem("camera", "Camera", "Hardware Sensor", false, "ic_camera"),
        SensorItem("mic", "Microphone", "Audio Input", false, "ic_mic"),
        SensorItem("motion", "Accelerometer", "Motion Sensor", false, "ic_motion"),
        SensorItem("gyro", "Gyroscope", "Orientation Sensor", false, "ic_gyro"),
        SensorItem("proximity", "Proximity Sensor", "Distance Sensor", false, "ic_proximity"),
        SensorItem("light", "Ambient Light Sensor", "Light Sensor", false, "ic_light")
    )
)

class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuManager.SHIZUKU_REQ_CODE) {
            val isGranted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            addLog("Shizuku Permission Result: ${if (isGranted) "GRANTED" else "DENIED"}")
            refreshState()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        addLog("Shizuku binder connected. Checking permissions...")
        checkAndRequestShizukuPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        addLog("Shizuku binder disconnected.")
        refreshState()
    }

    private val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val context = getApplication<Application>().applicationContext
            val isOff = ShizukuManager.getSensorsOffState(context)
            addLog("Detected system sensor privacy change -> SensorsOff = $isOff")
            refreshState()
        }
    }

    init {
        val context = application.applicationContext
        refreshState()
        addLog("SensorsOff initialized on ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")

        // Register Shizuku listeners
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) {
            addLog("Shizuku listener note: ${e.message}")
        }

        // Register ContentObserver to track real-time global settings changes
        try {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor("sensors_off"),
                false,
                contentObserver
            )
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("sensor_privacy"),
                false,
                contentObserver
            )
        } catch (e: Throwable) {
            // Observer fail safe
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Throwable) {}

        try {
            getApplication<Application>().contentResolver.unregisterContentObserver(contentObserver)
        } catch (e: Throwable) {}
    }

    fun checkAndRequestShizukuPermission() {
        val isRunning = ShizukuManager.isShizukuRunning()
        val isAuthorized = ShizukuManager.isShizukuAuthorized()
        if (isRunning && !isAuthorized) {
            addLog("Shizuku detected: Requesting authorization...")
            ShizukuManager.requestShizukuPermission()
        }
    }

    fun refreshState() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val isInstalled = ShizukuManager.isShizukuInstalled(context)
            val isRunning = ShizukuManager.isShizukuRunning()
            val isAuthorized = ShizukuManager.isShizukuAuthorized()
            val isRoot = ShizukuManager.isRootAvailable()
            val hasPermission = ShizukuManager.hasSecureSettingsPermission(context)
            val adbCmd = ShizukuManager.getAdbGrantCommand(context)
            val isOff = ShizukuManager.getSensorsOffState(context)

            val tileIconStyle = ShizukuManager.getTileIconStyle(context)
            val tileLabelText = ShizukuManager.getTileLabelText(context)
            val activeSubtitle = ShizukuManager.getTileActiveSubtitleText(context)
            val disabledSubtitle = ShizukuManager.getTileDisabledSubtitleText(context)
            val tileBlockMode = ShizukuManager.getTileBlockMode(context)
            val customIconPath = ShizukuManager.getCustomIconPath(context)

            val themeMode = ShizukuManager.getAppThemeMode(context)
            val launcherAlias = ShizukuManager.getAppLauncherAlias(context)

            val updatedSensors = _uiState.value.sensorList.map { sensor ->
                val sensorBlocked = ShizukuManager.getIndividualSensorState(context, sensor.id)
                sensor.copy(isBlocked = sensorBlocked)
            }

            _uiState.update { state ->
                state.copy(
                    isShizukuInstalled = isInstalled,
                    isShizukuRunning = isRunning,
                    isShizukuAuthorized = isAuthorized,
                    isRootAvailable = isRoot,
                    hasSecureSettingsPermission = hasPermission,
                    adbGrantCommand = adbCmd,
                    isSensorsOff = isOff,
                    appThemeMode = themeMode,
                    appLauncherAlias = launcherAlias,
                    tileSettings = TileSettingsState(
                        iconStyle = tileIconStyle,
                        customLabel = tileLabelText,
                        activeSubtitle = activeSubtitle,
                        disabledSubtitle = disabledSubtitle,
                        blockMode = tileBlockMode,
                        customIconPath = customIconPath
                    ),
                    sensorList = updatedSensors
                )
            }
        }
    }

    fun toggleSensorsOff() {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val current = _uiState.value.isSensorsOff
            val target = !current

            addLog("Action: Toggling Master SensorsOff to ${if (target) "ENABLED (Sensors Off)" else "DISABLED (Sensors On)"}...")
            val success = ShizukuManager.setSensorsOffState(context, target)

            if (success) {
                val updatedSensors = _uiState.value.sensorList.map { it.copy(isBlocked = target) }
                _uiState.update { it.copy(isSensorsOff = target, sensorList = updatedSensors) }
                addLog("Status: Successfully set Master SensorsOff = $target and synced all sensors")
            } else {
                addLog("Error: Failed to set SensorsOff state. Ensure Shizuku, Root, or Secure Settings permission is granted.")
            }

            delay(200)
            refreshState()
        }
    }

    fun toggleIndividualSensor(sensorId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val currentSensor = _uiState.value.sensorList.find { it.id == sensorId } ?: return@launch
            val targetState = !currentSensor.isBlocked

            addLog("Action: Toggling '${currentSensor.name}' to ${if (targetState) "BLOCKED" else "ACTIVE"}...")
            val success = ShizukuManager.setIndividualSensorState(context, sensorId, targetState)

            if (success) {
                val updatedSensors = _uiState.value.sensorList.map {
                    if (it.id == sensorId) it.copy(isBlocked = targetState) else it
                }
                val anyBlocked = updatedSensors.any { it.isBlocked }
                _uiState.update { it.copy(sensorList = updatedSensors, isSensorsOff = anyBlocked) }
                addLog("Status: '${currentSensor.name}' set to ${if (targetState) "BLOCKED" else "ACTIVE"}")
            } else {
                addLog("Error: Failed to toggle '${currentSensor.name}'. Check permissions.")
            }

            delay(150)
            refreshState()
        }
    }

    fun updateTileSettings(
        iconStyle: String,
        customLabel: String,
        activeSubtitle: String = "Sensors Disabled",
        disabledSubtitle: String = "Sensors Enabled",
        blockMode: String = "global"
    ) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            ShizukuManager.saveTileSettings(context, iconStyle, customLabel, activeSubtitle, disabledSubtitle, blockMode)
            addLog("Tile updated: icon=$iconStyle, label='$customLabel', activeSub='$activeSubtitle', disabledSub='$disabledSubtitle', mode=$blockMode")
            refreshState()
        }
    }

    fun importCustomTileIconUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            addLog("Importing custom tile icon image...")
            val savedPath = ShizukuManager.saveCustomTileIconFromUri(context, uri)
            if (savedPath != null) {
                val currentSettings = _uiState.value.tileSettings
                ShizukuManager.saveTileSettings(
                    context,
                    iconStyle = "custom",
                    labelText = currentSettings.customLabel,
                    activeSubtitleText = currentSettings.activeSubtitle,
                    disabledSubtitleText = currentSettings.disabledSubtitle,
                    blockMode = currentSettings.blockMode,
                    customIconPath = savedPath
                )
                addLog("Custom tile icon imported and set successfully.")
            } else {
                addLog("Error: Failed to process custom tile icon image.")
            }
            refreshState()
        }
    }

    fun updateAppThemeMode(mode: String) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            ShizukuManager.saveAppThemeMode(context, mode)
            _uiState.update { it.copy(appThemeMode = mode) }
            addLog("App visual theme changed to: ${mode.uppercase()}")
        }
    }

    fun updateAppLauncherAlias(aliasName: String) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            ShizukuManager.setAppLauncherAlias(context, aliasName)
            _uiState.update { it.copy(appLauncherAlias = aliasName) }
            val friendlyName = when (aliasName) {
                "MainActivityMinimal" -> "Privacy Engine"
                "MainActivityDiscrete" -> "System Utility"
                else -> "Ultra Private / Sensors Off"
            }
            addLog("App icon rebranding set to: $friendlyName")
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
        addLog("Console logs cleared.")
    }

    fun requestShizukuPermission() {
        if (ShizukuManager.isShizukuRunning()) {
            if (!ShizukuManager.isShizukuAuthorized()) {
                addLog("Requesting Shizuku authorization...")
                ShizukuManager.requestShizukuPermission()
            } else {
                addLog("Shizuku is already authorized.")
            }
        } else {
            addLog("Shizuku service is not running. Please start Shizuku app first.")
        }
        viewModelScope.launch {
            delay(500)
            refreshState()
        }
    }

    fun addLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _uiState.update { state ->
            val updatedLogs = (listOf("[$timestamp] $msg") + state.logs).take(30)
            state.copy(logs = updatedLogs)
        }
    }
}
