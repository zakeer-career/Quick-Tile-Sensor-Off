package com.example

import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogCategory(val displayName: String, val badgeText: String) {
    ALL("All Events", "ALL"),
    TILE("Quick Tile", "TILE"),
    SENSOR("Sensor HAL", "HAL"),
    PRIVILEGE("Privilege/IPC", "IPC"),
    SYSTEM("System AOSP", "SYS")
}

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR,
    DEBUG
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val formattedTime: String,
    val category: LogCategory,
    val level: LogLevel,
    val title: String,
    val detail: String = "",
    val executionMs: Long? = null
) {
    fun toFormattedString(): String {
        val execStr = if (executionMs != null) " [${executionMs}ms]" else ""
        val detailStr = if (detail.isNotBlank()) "\n  ↳ $detail" else ""
        return "[$formattedTime] [${category.badgeText}] [${level.name}] $title$execStr$detailStr"
    }
}

data class TileDiagnostics(
    val lastState: String = "UNKNOWN",
    val lastAction: String = "None recorded",
    val lastActionTime: String = "--",
    val lastLatencyMs: Long? = null,
    val blockMode: String = "global",
    val label: String = "Sensors Off",
    val iconStyle: String = "stock",
    val isListening: Boolean = false,
    val serviceActive: Boolean = true
)

object TileLogManager {
    private const val TAG = "TileLogManager"
    private const val PREFS_NAME = "sensorsoff_telemetry_logs"
    private const val KEY_PERSISTED_LOGS = "persisted_logs_v2"
    private const val KEY_LAST_TILE_DIAGNOSTICS = "last_tile_diagnostics"
    private const val MAX_LOGS = 80

    private val idCounter = AtomicLong(System.currentTimeMillis())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private val _diagnosticsFlow = MutableStateFlow(TileDiagnostics())
    val diagnosticsFlow: StateFlow<TileDiagnostics> = _diagnosticsFlow.asStateFlow()

    private var isInitialized = false
    private val scope = CoroutineScope(Dispatchers.IO)

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadPersistedLogs(context)
    }

    private fun loadPersistedLogs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawJson = prefs.getString(KEY_PERSISTED_LOGS, null)
            val list = mutableListOf<LogEntry>()

            if (!rawJson.isNullOrBlank()) {
                val array = JSONArray(rawJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        LogEntry(
                            id = obj.optLong("id", System.currentTimeMillis()),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            formattedTime = obj.optString("formattedTime", ""),
                            category = runCatching { LogCategory.valueOf(obj.getString("category")) }.getOrDefault(LogCategory.TILE),
                            level = runCatching { LogLevel.valueOf(obj.getString("level")) }.getOrDefault(LogLevel.INFO),
                            title = obj.getString("title"),
                            detail = obj.optString("detail", ""),
                            executionMs = if (obj.has("executionMs")) obj.getLong("executionMs") else null
                        )
                    )
                }
            }

            // Restore diagnostics
            val diagJson = prefs.getString(KEY_LAST_TILE_DIAGNOSTICS, null)
            if (!diagJson.isNullOrBlank()) {
                val diagObj = JSONObject(diagJson)
                _diagnosticsFlow.value = TileDiagnostics(
                    lastState = diagObj.optString("lastState", "STATE_INACTIVE"),
                    lastAction = diagObj.optString("lastAction", "Ready"),
                    lastActionTime = diagObj.optString("lastActionTime", "--"),
                    lastLatencyMs = if (diagObj.has("lastLatencyMs")) diagObj.getLong("lastLatencyMs") else null,
                    blockMode = diagObj.optString("blockMode", "global"),
                    label = diagObj.optString("label", "Sensors Off"),
                    iconStyle = diagObj.optString("iconStyle", "stock"),
                    isListening = false,
                    serviceActive = true
                )
            }

            _logsFlow.value = list
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load telemetry logs", e)
        }
    }

    private fun persistLogs(context: Context) {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val currentLogs = _logsFlow.value.take(MAX_LOGS)
                val array = JSONArray()
                for (entry in currentLogs) {
                    val obj = JSONObject().apply {
                        put("id", entry.id)
                        put("timestamp", entry.timestamp)
                        put("formattedTime", entry.formattedTime)
                        put("category", entry.category.name)
                        put("level", entry.level.name)
                        put("title", entry.title)
                        put("detail", entry.detail)
                        entry.executionMs?.let { put("executionMs", it) }
                    }
                    array.put(obj)
                }

                val diag = _diagnosticsFlow.value
                val diagObj = JSONObject().apply {
                    put("lastState", diag.lastState)
                    put("lastAction", diag.lastAction)
                    put("lastActionTime", diag.lastActionTime)
                    diag.lastLatencyMs?.let { put("lastLatencyMs", it) }
                    put("blockMode", diag.blockMode)
                    put("label", diag.label)
                    put("iconStyle", diag.iconStyle)
                }

                prefs.edit()
                    .putString(KEY_PERSISTED_LOGS, array.toString())
                    .putString(KEY_LAST_TILE_DIAGNOSTICS, diagObj.toString())
                    .apply()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to persist logs: ${e.message}")
            }
        }
    }

    fun log(
        context: Context,
        category: LogCategory,
        level: LogLevel,
        title: String,
        detail: String = "",
        executionMs: Long? = null
    ) {
        val now = System.currentTimeMillis()
        val formatted = timeFormat.format(Date(now))
        val entry = LogEntry(
            id = idCounter.incrementAndGet(),
            timestamp = now,
            formattedTime = formatted,
            category = category,
            level = level,
            title = title,
            detail = detail,
            executionMs = executionMs
        )

        Log.d(TAG, "[${category.badgeText}] $title | $detail")

        _logsFlow.update { current ->
            (listOf(entry) + current).take(MAX_LOGS)
        }
        persistLogs(context)
    }

    // Quick Tile Specific Deep Loggers
    fun logTileEvent(
        context: Context,
        action: String,
        detail: String,
        level: LogLevel = LogLevel.INFO,
        executionMs: Long? = null
    ) {
        log(context, LogCategory.TILE, level, action, detail, executionMs)
    }

    fun updateTileDiagnostics(
        context: Context,
        lastState: String? = null,
        lastAction: String? = null,
        lastLatencyMs: Long? = null,
        blockMode: String? = null,
        label: String? = null,
        iconStyle: String? = null,
        isListening: Boolean? = null
    ) {
        val now = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _diagnosticsFlow.update { prev ->
            prev.copy(
                lastState = lastState ?: prev.lastState,
                lastAction = lastAction ?: prev.lastAction,
                lastActionTime = if (lastAction != null) now else prev.lastActionTime,
                lastLatencyMs = lastLatencyMs ?: prev.lastLatencyMs,
                blockMode = blockMode ?: prev.blockMode,
                label = label ?: prev.label,
                iconStyle = iconStyle ?: prev.iconStyle,
                isListening = isListening ?: prev.isListening
            )
        }
        persistLogs(context)
    }

    fun logSensorEvent(
        context: Context,
        title: String,
        detail: String,
        level: LogLevel = LogLevel.INFO,
        executionMs: Long? = null
    ) {
        log(context, LogCategory.SENSOR, level, title, detail, executionMs)
    }

    fun logPrivilegeEvent(
        context: Context,
        title: String,
        detail: String,
        level: LogLevel = LogLevel.INFO,
        executionMs: Long? = null
    ) {
        log(context, LogCategory.PRIVILEGE, level, title, detail, executionMs)
    }

    fun logSystemEvent(
        context: Context,
        title: String,
        detail: String,
        level: LogLevel = LogLevel.INFO
    ) {
        log(context, LogCategory.SYSTEM, level, title, detail)
    }

    fun clear(context: Context) {
        _logsFlow.value = emptyList()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PERSISTED_LOGS).apply()
        log(context, LogCategory.SYSTEM, LogLevel.INFO, "Telemetry Console Cleared", "All in-memory and persisted logs purged")
    }
}
