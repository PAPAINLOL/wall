package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel(val label: String, val emoji: String) {
    DEBUG("DEBUG", "🔍"),
    INFO("INFO", "ℹ️"),
    SUCCESS("SUCCESS", "✅"),
    WARN("WARN", "⚠️"),
    ERROR("ERROR", "❌")
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    fun formatLine(): String {
        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
        val base = "[$timeStr] ${level.emoji} [${level.label}] [$tag]: $message"
        return if (!details.isNullOrBlank()) "$base\n   -> $details" else base
    }
}

object AppLogger {
    private const val MAX_LOG_ENTRIES = 500
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val lock = Any()

    fun d(tag: String, message: String, details: String? = null) {
        log(LogLevel.DEBUG, tag, message, details)
        Log.d(tag, if (details != null) "$message | $details" else message)
    }

    fun i(tag: String, message: String, details: String? = null) {
        log(LogLevel.INFO, tag, message, details)
        Log.i(tag, if (details != null) "$message | $details" else message)
    }

    fun s(tag: String, message: String, details: String? = null) {
        log(LogLevel.SUCCESS, tag, message, details)
        Log.i(tag, if (details != null) "SUCCESS: $message | $details" else "SUCCESS: $message")
    }

    fun w(tag: String, message: String, details: String? = null) {
        log(LogLevel.WARN, tag, message, details)
        Log.w(tag, if (details != null) "$message | $details" else message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val details = throwable?.let {
            "${it.javaClass.simpleName}: ${it.message}\n${it.stackTrace.take(5).joinToString("\n")}"
        }
        log(LogLevel.ERROR, tag, message, details)
        Log.e(tag, message, throwable)
    }

    private fun log(level: LogLevel, tag: String, message: String, details: String?) {
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            details = details
        )
        synchronized(lock) {
            val current = _logs.value.toMutableList()
            current.add(0, entry) // Most recent first
            if (current.size > MAX_LOG_ENTRIES) {
                current.removeAt(current.size - 1)
            }
            _logs.value = current
        }
    }

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
        }
        i("AppLogger", "Registro de logs limpiado por el usuario")
    }

    fun getAllLogsAsText(): String {
        return synchronized(lock) {
            _logs.value.reversed().joinToString("\n") { it.formatLine() }
        }
    }

    fun copyLogsToClipboard(context: Context): Boolean {
        return try {
            val text = getAllLogsAsText()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Reddit Wallpaper Logs", text)
            clipboard.setPrimaryClip(clip)
            i("AppLogger", "Logs copiados al portapapeles (${text.lines().size} líneas)")
            true
        } catch (e: Exception) {
            e("AppLogger", "Error al copiar logs al portapapeles", e)
            false
        }
    }
}
