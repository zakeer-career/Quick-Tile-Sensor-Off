package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val SHIZUKU_REQ_CODE = 1001

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isShizukuRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun isShizukuAuthorized(): Boolean {
        return if (isShizukuRunning()) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (e: Throwable) {
                false
            }
        } else {
            false
        }
    }

    @Volatile
    private var cachedRootAvailable: Boolean? = null

    fun isRootAvailable(): Boolean {
        cachedRootAvailable?.let { return it }
        val hasSuBinary = try {
            val paths = arrayOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/data/local/su"
            )
            paths.any { java.io.File(it).exists() }
        } catch (e: Throwable) {
            false
        }

        if (!hasSuBinary) {
            cachedRootAvailable = false
            return false
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val line = process.inputStream.bufferedReader().use { it.readLine() }
            process.errorStream.bufferedReader().use { while (it.readLine() != null) {} }
            process.waitFor()
            val available = line != null && line.contains("uid=0")
            cachedRootAvailable = available
            available
        } catch (e: Throwable) {
            cachedRootAvailable = false
            false
        }
    }

    fun hasSecureSettingsPermission(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestShizukuPermission() {
        if (isShizukuRunning()) {
            try {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(SHIZUKU_REQ_CODE)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to request Shizuku permission", e)
            }
        } else {
            Log.d(TAG, "Shizuku is not running yet")
        }
    }

    fun getAdbGrantCommand(context: Context): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * Main method to toggle SensorsOff state using direct SensorPrivacyManager system calls:
     * 1. Shizuku (if running & authorized) via single batched command script
     * 2. Direct Root Shell (`su`) in a single batch process
     * 3. Direct WRITE_SECURE_SETTINGS permission
     * 4. SharedPreferences fallback
     */
    fun setSensorsOffState(context: Context, turnOff: Boolean): Boolean {
        val targetValue = if (turnOff) 1 else 0
        Log.d(TAG, "Setting SensorsOff state to $targetValue")

        var executedSuccessfully = false

        // Batch all sensor privacy shell commands and service calls into one single execution script
        val commands = mutableListOf(
            "settings put global sensors_off $targetValue",
            "settings put secure sensor_privacy $targetValue",
            "cmd sensor_privacy ${if (turnOff) "enable" else "disable"}"
        )

        if (turnOff) {
            commands.add("cmd sensor_privacy set-sensor-state 0 mic true 2>/dev/null")
            commands.add("cmd sensor_privacy set-sensor-state 0 camera true 2>/dev/null")
            commands.add("cmd sensor_privacy set-sensor-state 0 1 true 2>/dev/null")
            commands.add("cmd sensor_privacy set-sensor-state 0 2 true 2>/dev/null")
            commands.add("service call sensor_privacy 1 i32 1")
            commands.add("service call sensor_privacy 2 i32 1")
            commands.add("service call sensor_privacy 6 i32 1")
            commands.add("service call sensor_privacy 7 i32 1")
            commands.add("service call sensor_privacy 8 i32 1")
            commands.add("service call sensor_privacy 9 i32 1")
            commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 1 i32 1")
            commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 2 i32 1")
        } else {
            commands.add("cmd sensor_privacy set-sensor-state 0 mic false 2>/dev/null")
            commands.add("cmd sensor_privacy set-sensor-state 0 camera false 2>/dev/null")
            commands.add("cmd sensor_privacy set-sensor-state 0 1 false 2>/dev/null")
            commands.add("cmd sensor_privacy set-sensor-state 0 2 false 2>/dev/null")
            commands.add("service call sensor_privacy 1 i32 0")
            commands.add("service call sensor_privacy 2 i32 0")
            commands.add("service call sensor_privacy 6 i32 0")
            commands.add("service call sensor_privacy 7 i32 0")
            commands.add("service call sensor_privacy 8 i32 0")
            commands.add("service call sensor_privacy 9 i32 0")
            commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 1 i32 0")
            commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 2 i32 0")
        }

        val compoundCommand = commands.joinToString(" ; ")

        // Method 1: Shizuku (Single invocation for maximum speed)
        if (isShizukuRunning() && isShizukuAuthorized()) {
            try {
                runShizukuCommand(compoundCommand)
                executedSuccessfully = true
                Log.d(TAG, "Executed SensorPrivacy commands via Shizuku successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Shizuku execution failed", e)
            }
        }

        // Method 2: Direct Root SU (Single invocation)
        if (!executedSuccessfully && isRootAvailable()) {
            try {
                runRootCommand(compoundCommand)
                executedSuccessfully = true
                Log.d(TAG, "Executed SensorPrivacy commands via Root SU successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Root SU execution failed", e)
            }
        }

        // Method 3: WRITE_SECURE_SETTINGS permission directly
        if (!executedSuccessfully || hasSecureSettingsPermission(context)) {
            try {
                val success = Settings.Global.putInt(context.contentResolver, "sensors_off", targetValue)
                try {
                    Settings.Secure.putInt(context.contentResolver, "sensor_privacy", targetValue)
                } catch (e: Throwable) {}
                if (success) {
                    executedSuccessfully = true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Direct Settings.Global modification failed", e)
            }
        }

        // Always sync local SharedPreferences for consistent app state
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("sensors_off_enabled", turnOff)
            .putBoolean("sensor_blocked_camera", turnOff)
            .putBoolean("sensor_blocked_mic", turnOff)
            .putBoolean("sensor_blocked_motion", turnOff)
            .putBoolean("sensor_blocked_gyro", turnOff)
            .putBoolean("sensor_blocked_proximity", turnOff)
            .putBoolean("sensor_blocked_light", turnOff)
            .apply()

        // Explicitly request SystemUI to update the Quick Settings tile immediately
        notifyTileServiceToUpdate(context)

        return executedSuccessfully
    }

    /**
     * Toggles an individual hardware sensor (Camera, Mic, Motion, etc.)
     */
    fun setIndividualSensorState(context: Context, sensorId: String, turnOff: Boolean): Boolean {
        Log.d(TAG, "Setting individual sensor '$sensorId' blocked state to $turnOff")
        val commands = mutableListOf<String>()

        when (sensorId.lowercase()) {
            "camera" -> {
                commands.add("cmd sensor_privacy set-sensor-state 0 camera $turnOff 2>/dev/null")
                commands.add("cmd sensor_privacy set-sensor-state 0 2 $turnOff 2>/dev/null")
                commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 2 i32 ${if (turnOff) 1 else 0}")
            }
            "mic", "microphone" -> {
                commands.add("cmd sensor_privacy set-sensor-state 0 mic $turnOff 2>/dev/null")
                commands.add("cmd sensor_privacy set-sensor-state 0 1 $turnOff 2>/dev/null")
                commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 1 i32 ${if (turnOff) 1 else 0}")
            }
            else -> {
                commands.add("cmd sensor_privacy set-sensor-state 0 $turnOff 2>/dev/null")
            }
        }

        val compoundCommand = commands.joinToString(" ; ")
        var executedSuccessfully = false

        if (isShizukuRunning() && isShizukuAuthorized()) {
            try {
                runShizukuCommand(compoundCommand)
                executedSuccessfully = true
            } catch (e: Throwable) {
                Log.e(TAG, "Shizuku individual sensor toggle failed", e)
            }
        }

        if (!executedSuccessfully && isRootAvailable()) {
            try {
                runRootCommand(compoundCommand)
                executedSuccessfully = true
            } catch (e: Throwable) {
                Log.e(TAG, "Root individual sensor toggle failed", e)
            }
        }

        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sensor_blocked_$sensorId", turnOff).apply()

        // Explicitly request SystemUI to update the Quick Settings tile immediately
        notifyTileServiceToUpdate(context)

        return executedSuccessfully
    }

    fun getIndividualSensorState(context: Context, sensorId: String): Boolean {
        // If all sensors are off globally, this sensor is off
        if (getSensorsOffState(context)) {
            return true
        }

        // Check native SensorPrivacyManager for camera / mic
        try {
            val spm = context.getSystemService("sensor_privacy")
            if (spm != null) {
                val cls = spm.javaClass
                val mSensor = cls.methods.firstOrNull {
                    it.name == "isSensorPrivacyEnabled" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
                }
                if (mSensor != null) {
                    if (sensorId.equals("camera", ignoreCase = true)) {
                        val cam = mSensor.invoke(spm, 2) as? Boolean
                        if (cam == true) return true
                    } else if (sensorId.equals("mic", ignoreCase = true) || sensorId.equals("microphone", ignoreCase = true)) {
                        val mic = mSensor.invoke(spm, 1) as? Boolean
                        if (mic == true) return true
                    }
                }
            }
        } catch (e: Throwable) {}

        // Check Secure settings for camera/mic
        try {
            val cr = context.contentResolver
            if (sensorId.equals("camera", ignoreCase = true)) {
                if (Settings.Secure.getInt(cr, "sensor_privacy_camera", -1) == 1) return true
            } else if (sensorId.equals("mic", ignoreCase = true) || sensorId.equals("microphone", ignoreCase = true)) {
                if (Settings.Secure.getInt(cr, "sensor_privacy_microphone", -1) == 1) return true
            }
        } catch (e: Throwable) {}

        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("sensor_blocked_$sensorId", false)
    }

    fun getTileIconStyle(context: Context): String {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        return prefs.getString("tile_icon_style", "stock") ?: "stock"
    }

    fun getTileLabelText(context: Context): String {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        val value = prefs.getString("tile_label_text", "Sensors Off")
        return if (!value.isNullOrBlank()) value else "Sensors Off"
    }

    fun getTileActiveSubtitleText(context: Context): String {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        return prefs.getString("tile_active_subtitle", "") ?: ""
    }

    fun getTileDisabledSubtitleText(context: Context): String {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        return prefs.getString("tile_disabled_subtitle", "") ?: ""
    }

    fun getTileBlockMode(context: Context): String {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        return prefs.getString("tile_block_mode", "global") ?: "global"
    }

    fun getCustomIconPath(context: Context): String? {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        val path = prefs.getString("custom_icon_path", null)
        if (path != null && java.io.File(path).exists()) {
            return path
        }
        return null
    }

    fun saveTileSettings(
        context: Context,
        iconStyle: String,
        labelText: String,
        activeSubtitleText: String = "",
        disabledSubtitleText: String = "",
        blockMode: String = "global",
        customIconPath: String? = null
    ) {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString("tile_icon_style", iconStyle)
            .putString("tile_label_text", labelText.ifBlank { "Sensors Off" })
            .putString("tile_active_subtitle", activeSubtitleText)
            .putString("tile_disabled_subtitle", disabledSubtitleText)
            .putString("tile_block_mode", blockMode)

        if (customIconPath != null) {
            editor.putString("custom_icon_path", customIconPath)
        }
        editor.apply()
        notifyTileServiceToUpdate(context)
    }

    suspend fun saveCustomTileIconFromUri(context: Context, uri: android.net.Uri): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return@withContext null

            val targetSize = 48
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetSize, targetSize, true)

            val monochromeBitmap = android.graphics.Bitmap.createBitmap(targetSize, targetSize, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(monochromeBitmap)
            val paint = android.graphics.Paint()
            val colorMatrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
            canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)

            val destFile = java.io.File(context.filesDir, "custom_tile_icon.png")
            val outputStream = java.io.FileOutputStream(destFile)
            monochromeBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            val savedPath = destFile.absolutePath
            val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("custom_icon_path", savedPath).apply()
            Log.d(TAG, "Custom tile icon saved to $savedPath")
            savedPath
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to save custom tile icon from Uri", e)
            null
        }
    }

    /**
     * Injects the Quick Settings tile directly into the active QS shade via Shizuku/Root
     * and prompts SystemUI via official StatusBarManager API (Android 13+).
     */
    fun addTileToQuickSettings(context: Context, addNativeAospTile: Boolean = false): Pair<Boolean, String> {
        val packageName = context.packageName
        val appTileComponent = "custom($packageName/$packageName.SensorsOffTileService)"
        val aospTileComponent = "custom(com.android.settings/com.android.settings.development.qs.SensorPrivacyTileService)"
        val aospPlain = "sensor_privacy"

        val targetTile = if (addNativeAospTile) aospTileComponent else appTileComponent

        // Method 1: Android 13+ (API 33+) native request prompt (Zero risk, official API)
        if (!addNativeAospTile && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                val sbm = context.getSystemService(android.app.StatusBarManager::class.java)
                if (sbm != null) {
                    val component = android.content.ComponentName(context, SensorsOffTileService::class.java)
                    val icon = android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_sensors_off)
                    sbm.requestAddTileService(
                        component,
                        context.getString(R.string.tile_label),
                        icon,
                        context.mainExecutor
                    ) { _ -> }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "StatusBarManager.requestAddTileService fallback", t)
            }
        }

        // Method 2: Via Shizuku or Root direct injection into sysui_qs_tiles
        val isShizuku = isShizukuRunning() && isShizukuAuthorized()
        val isRoot = isRootAvailable()

        if (!isShizuku && !isRoot) {
            return Pair(false, "Shizuku authorization or Root required to inject Quick Settings tile directly.")
        }

        val runCommand = { cmd: String ->
            if (isShizuku) runShizukuCommand(cmd) else runRootCommand(cmd)
        }

        return try {
            val currentTilesOutput = runCommand("settings get secure sysui_qs_tiles").trim()
            if (currentTilesOutput.isBlank() || currentTilesOutput == "null") {
                return Pair(false, "Could not read sysui_qs_tiles.")
            }

            if (currentTilesOutput.contains(targetTile) || (addNativeAospTile && currentTilesOutput.contains(aospPlain))) {
                // Ensure SystemUI re-reads it
                runCommand("killall com.android.systemui")
                return Pair(true, "Tile is already in your Quick Settings list! Refreshed SystemUI.")
            }

            val newTiles = if (addNativeAospTile) {
                "$currentTilesOutput,$aospTileComponent,$aospPlain"
            } else {
                "$currentTilesOutput,$appTileComponent"
            }

            runCommand("settings put secure sysui_qs_tiles \"$newTiles\"")
            // Refresh SystemUI
            runCommand("killall com.android.systemui")

            TileLogManager.logTileEvent(
                context,
                "Tile Injected",
                "Successfully injected $targetTile into sysui_qs_tiles",
                LogLevel.SUCCESS
            )
            Pair(true, "Successfully added to Quick Settings!")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to inject Quick Settings tile", e)
            Pair(false, "Failed to inject tile: ${e.message}")
        }
    }

    // App Theme Preferences Storage
    fun getAppThemeMode(context: Context): String {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        return prefs.getString("app_theme_mode", "system") ?: "system"
    }

    fun saveAppThemeMode(context: Context, mode: String) {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme_mode", mode).apply()
    }

    // App Launcher Re-branding Aliases
    fun getAppLauncherAlias(context: Context): String {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        return prefs.getString("app_launcher_alias", "MainActivityDefault") ?: "MainActivityDefault"
    }

    fun setAppLauncherAlias(context: Context, aliasName: String) {
        val pm = context.packageManager
        val packageName = context.packageName

        val aliases = listOf(
            "$packageName.MainActivityDefault",
            "$packageName.MainActivityMinimal",
            "$packageName.MainActivityDiscrete"
        )

        for (alias in aliases) {
            try {
                val componentName = android.content.ComponentName(context, alias)
                val newState = if (alias == "$packageName.$aliasName") {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(
                    componentName,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to set activity alias component state for $alias", e)
            }
        }

        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_launcher_alias", aliasName).apply()
    }

    fun getSensorsOffState(context: Context): Boolean {
        // Layer 1: Check native Android SensorPrivacyManager directly via reflection
        try {
            val spm = context.getSystemService("sensor_privacy")
            if (spm != null) {
                val cls = spm.javaClass
                
                // 1. isSensorPrivacyEnabled()
                val mGlobal = cls.methods.firstOrNull { it.name == "isSensorPrivacyEnabled" && it.parameterTypes.isEmpty() }
                if (mGlobal != null) {
                    val res = mGlobal.invoke(spm) as? Boolean
                    if (res == true) return true
                }
                
                // 2. isAllSensorPrivacyEnabled()
                val mAll = cls.methods.firstOrNull { it.name == "isAllSensorPrivacyEnabled" && it.parameterTypes.isEmpty() }
                if (mAll != null) {
                    val res = mAll.invoke(spm) as? Boolean
                    if (res == true) return true
                }

                // 3. isSensorPrivacyEnabled(int sensor) - 1: Mic, 2: Camera
                val mSensor = cls.methods.firstOrNull {
                    it.name == "isSensorPrivacyEnabled" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType
                }
                if (mSensor != null) {
                    val mic = mSensor.invoke(spm, 1) as? Boolean
                    val cam = mSensor.invoke(spm, 2) as? Boolean
                    if (mic == true && cam == true) return true
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "SensorPrivacyManager reflection check: ${e.message}")
        }

        // Layer 2: Check System / Global / Secure settings across all OEM key variations
        try {
            val cr = context.contentResolver
            val keys = listOf(
                "sensors_off",
                "sensor_privacy",
                "all_sensors_off",
                "sensor_privacy_camera",
                "sensor_privacy_microphone"
            )
            for (key in keys) {
                try {
                    val gVal = Settings.Global.getInt(cr, key, -1)
                    if (gVal == 1) return true
                } catch (t: Throwable) {}
                try {
                    val sVal = Settings.Secure.getInt(cr, key, -1)
                    if (sVal == 1) return true
                } catch (t: Throwable) {}
                try {
                    val sysVal = Settings.System.getInt(cr, key, -1)
                    if (sysVal == 1) return true
                } catch (t: Throwable) {}
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Settings table check error: ${e.message}")
        }

        // Layer 3: Check live status via Shizuku IPC if available
        if (isShizukuRunning() && isShizukuAuthorized()) {
            try {
                // Command 1: cmd sensor_privacy is-sensor-privacy-enabled 0
                val cmdUserOut = runShizukuCommand("cmd sensor_privacy is-sensor-privacy-enabled 0").trim()
                if (cmdUserOut.contains("true", ignoreCase = true)) return true
                if (cmdUserOut.contains("false", ignoreCase = true)) return false

                // Command 2: cmd sensor_privacy is-sensor-privacy-enabled
                val cmdOut = runShizukuCommand("cmd sensor_privacy is-sensor-privacy-enabled").trim()
                if (cmdOut.contains("true", ignoreCase = true)) return true
                if (cmdOut.contains("false", ignoreCase = true)) return false

                // Command 3: dumpsys sensor_privacy
                val dumpOut = runShizukuCommand("dumpsys sensor_privacy").trim()
                if (dumpOut.isNotBlank()) {
                    if (dumpOut.contains("Global sensor privacy: true", ignoreCase = true) ||
                        dumpOut.contains("isAllSensorPrivacyEnabled: true", ignoreCase = true) ||
                        dumpOut.contains("Sensor privacy is enabled: true", ignoreCase = true) ||
                        dumpOut.contains("isEnabled: true", ignoreCase = true) ||
                        dumpOut.contains("mIsSensorPrivacyEnabled: true", ignoreCase = true) ||
                        dumpOut.contains("State: ENABLED", ignoreCase = true)
                    ) {
                        return true
                    }
                }

                // Command 4: service call sensor_privacy 1 or 8
                val serviceOut = runShizukuCommand("service call sensor_privacy 1 ; service call sensor_privacy 8 i32 0").trim()
                if (serviceOut.contains("00000001")) {
                    return true
                }

                // Command 5: settings get global sensors_off
                val globOut = runShizukuCommand("settings get global sensors_off").trim()
                if (globOut == "1") return true
                if (globOut == "0") return false
            } catch (e: Throwable) {
                Log.w(TAG, "Could not query sensor_privacy via Shizuku", e)
            }
        }

        // Layer 4: Check live status via Root SU if available
        if (isRootAvailable()) {
            try {
                val rootCmd = runRootCommand("cmd sensor_privacy is-sensor-privacy-enabled 0 || cmd sensor_privacy is-sensor-privacy-enabled || dumpsys sensor_privacy | grep -i 'true'").trim()
                if (rootCmd.contains("true", ignoreCase = true) || rootCmd.contains("ENABLED", ignoreCase = true)) {
                    return true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Could not query sensor_privacy via Root SU", e)
            }
        }

        // Layer 5: Fallback to SharedPreferences
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("sensors_off_enabled", false)
    }

    private fun runShizukuCommand(command: String): String {
        return try {
            val targetMethod = Shizuku::class.java.declaredMethods.firstOrNull { 
                it.name == "newProcess" && it.parameterTypes.size == 3 
            } ?: Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            targetMethod.isAccessible = true
            val process = targetMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as java.lang.Process
            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }
            process.errorStream.bufferedReader().use { reader ->
                while (reader.readLine() != null) {}
            }
            process.waitFor()
            output.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing Shizuku command: $command", e)
            ""
        }
    }

    private fun runRootCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
            }

            val output = StringBuilder()
            process.inputStream.bufferedReader().use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
            }
            process.errorStream.bufferedReader().use { reader ->
                while (reader.readLine() != null) {}
            }
            process.waitFor()
            output.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing Root command: $command", e)
            ""
        }
    }

    /**
     * Sends an explicit signal to Android SystemUI to refresh the Quick Settings tile
     * whenever settings or sensor states change inside the app.
     */
    fun notifyTileServiceToUpdate(context: Context) {
        try {
            android.service.quicksettings.TileService.requestListeningState(
                context,
                android.content.ComponentName(context, SensorsOffTileService::class.java)
            )
            TileLogManager.logTileEvent(
                context,
                "SystemUI Sync Dispatched",
                "Invoked TileService.requestListeningState() -> SystemUI forced tile invalidate",
                LogLevel.DEBUG
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Could not requestListeningState for tile: ${e.message}")
            TileLogManager.logTileEvent(
                context,
                "SystemUI Sync Warning",
                "requestListeningState failed: ${e.message}",
                LogLevel.WARN
            )
        }
    }
}
