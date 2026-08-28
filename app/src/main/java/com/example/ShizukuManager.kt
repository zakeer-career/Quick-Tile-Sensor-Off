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
     * 1. Shizuku (if running & authorized) via `cmd sensor_privacy` and `service call sensor_privacy`
     * 2. Direct Root Shell (`su`)
     * 3. Direct WRITE_SECURE_SETTINGS permission
     * 4. SharedPreferences fallback
     */
    fun setSensorsOffState(context: Context, turnOff: Boolean): Boolean {
        val targetValue = if (turnOff) 1 else 0
        Log.d(TAG, "Setting SensorsOff state to $targetValue")

        var executedSuccessfully = false

        // Direct SensorPrivacy shell commands and service calls
        val commands = mutableListOf(
            "settings put global sensors_off $targetValue",
            "settings put secure sensor_privacy $targetValue",
            "cmd sensor_privacy ${if (turnOff) "enable" else "disable"}"
        )

        if (turnOff) {
            // Android 12+ (API 31+) hidden API commands for Microphone & Camera toggles
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
            // Android 13/14+ mic (1) and camera (2) toggle service calls
            commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 1 i32 1")
            commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 2 i32 1")
        } else {
            // Android 12+ (API 31+) hidden API commands for Microphone & Camera re-enabling
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
            // Android 13/14+ mic (1) and camera (2) allow service calls
            commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 1 i32 0")
            commands.add("service call sensor_privacy 10 i32 0 i32 0 i32 2 i32 0")
        }

        // Method 1: Shizuku
        if (isShizukuRunning() && isShizukuAuthorized()) {
            try {
                for (cmd in commands) {
                    runShizukuCommand(cmd)
                }
                executedSuccessfully = true
                Log.d(TAG, "Executed SensorPrivacy commands via Shizuku successfully")
            } catch (e: Throwable) {
                Log.e(TAG, "Shizuku execution failed", e)
            }
        }

        // Method 2: Direct Root SU
        if (!executedSuccessfully && isRootAvailable()) {
            try {
                for (cmd in commands) {
                    runRootCommand(cmd)
                }
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

        var executedSuccessfully = false

        if (isShizukuRunning() && isShizukuAuthorized()) {
            try {
                for (cmd in commands) {
                    runShizukuCommand(cmd)
                }
                executedSuccessfully = true
            } catch (e: Throwable) {
                Log.e(TAG, "Shizuku individual sensor toggle failed", e)
            }
        }

        if (!executedSuccessfully && isRootAvailable()) {
            try {
                for (cmd in commands) {
                    runRootCommand(cmd)
                }
                executedSuccessfully = true
            } catch (e: Throwable) {
                Log.e(TAG, "Root individual sensor toggle failed", e)
            }
        }

        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sensor_blocked_$sensorId", turnOff).apply()

        return executedSuccessfully
    }

    fun getIndividualSensorState(context: Context, sensorId: String): Boolean {
        val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
        val defaultVal = prefs.getBoolean("sensors_off_enabled", false)
        return prefs.getBoolean("sensor_blocked_$sensorId", defaultVal)
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
        // Method 1: Check live status directly via Shizuku if available
        if (isShizukuRunning() && isShizukuAuthorized()) {
            try {
                val cmdOut = runShizukuCommand("cmd sensor_privacy is-sensor-privacy-enabled").trim()
                if (cmdOut.contains("true", ignoreCase = true)) {
                    return true
                } else if (cmdOut.contains("false", ignoreCase = true)) {
                    return false
                }

                val globOut = runShizukuCommand("settings get global sensors_off").trim()
                if (globOut == "1") return true
                if (globOut == "0") return false
            } catch (e: Throwable) {
                Log.w(TAG, "Could not query sensor_privacy via Shizuku", e)
            }
        }

        // Method 2: Check global or secure settings
        return try {
            val globalVal = Settings.Global.getInt(context.contentResolver, "sensors_off", -1)
            if (globalVal != -1) {
                globalVal == 1
            } else {
                val secureVal = Settings.Secure.getInt(context.contentResolver, "sensor_privacy", -1)
                if (secureVal != -1) {
                    secureVal == 1
                } else {
                    val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
                    prefs.getBoolean("sensors_off_enabled", false)
                }
            }
        } catch (e: Throwable) {
            val prefs = context.getSharedPreferences("sensors_off_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("sensors_off_enabled", false)
        }
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
}
