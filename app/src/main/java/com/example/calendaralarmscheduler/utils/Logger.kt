package com.example.calendaralarmscheduler.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = "CalendarAlarmScheduler"
    private const val LOG_FILE_PREFIX = "app_log_"
    private const val MAX_LOG_FILES = 5
    
    enum class Level(val priority: Int, val tag: String) {
        VERBOSE(Log.VERBOSE, "V"),
        DEBUG(Log.DEBUG, "D"),
        INFO(Log.INFO, "I"),
        WARN(Log.WARN, "W"),
        ERROR(Log.ERROR, "E"),
        CRASH(Log.ASSERT, "CRASH")
    }
    
    private var context: Context? = null
    private var isDebugBuild = false
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    fun initialize(context: Context, isDebug: Boolean) {
        this.context = context
        this.isDebugBuild = isDebug
        
        if (isDebug) {
            cleanOldLogFiles()
        }
        
        i("Logger", "Logger initialized - Debug: $isDebug, API: ${Build.VERSION.SDK_INT}")
    }
    
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.VERBOSE, tag, message, throwable)
    }
    
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.DEBUG, tag, message, throwable)
    }
    
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.INFO, tag, message, throwable)
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }
    
    fun crash(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.CRASH, tag, message, throwable)
    }
    
    fun logPerformance(tag: String, operation: String, timeMs: Long) {
        i("Performance_$tag", "$operation took ${timeMs}ms")
    }
    
    fun logUserAction(action: String, details: String = "") {
        i("UserAction", "$action${if (details.isNotEmpty()) " - $details" else ""}")
    }
    
    fun logLifecycle(component: String, state: String, details: String = "") {
        d("Lifecycle_$component", "$state${if (details.isNotEmpty()) " - $details" else ""}")
    }
    
    fun logNavigation(from: String, to: String, action: String = "") {
        i("Navigation", "$from -> $to${if (action.isNotEmpty()) " ($action)" else ""}")
    }
    
    fun logDatabase(operation: String, table: String, details: String = "", timeMs: Long? = null) {
        val message = "DB $operation on $table${if (details.isNotEmpty()) " - $details" else ""}"
        val finalMessage = if (timeMs != null) "$message (${timeMs}ms)" else message
        d("Database", finalMessage)
    }
    
    fun logPermission(permission: String, granted: Boolean, rationale: String = "") {
        val status = if (granted) "GRANTED" else "DENIED"
        i("Permission", "$permission: $status${if (rationale.isNotEmpty()) " - $rationale" else ""}")
    }
    
    fun dumpContext(tag: String, context: Any?) {
        val contextInfo = when (context) {
            null -> "null"
            is android.app.Activity -> "Activity: ${context::class.simpleName}, isFinishing: ${context.isFinishing}"
            is androidx.fragment.app.Fragment -> "Fragment: ${context::class.simpleName}, isAdded: ${context.isAdded}, isVisible: ${context.isVisible}"
            else -> "${context::class.simpleName}: $context"
        }
        d(tag, "Context dump: $contextInfo")
    }
    
    fun dumpSystemInfo(tag: String) {
        val info = buildString {
            appendLine("=== System Info ===")
            appendLine("API Level: ${Build.VERSION.SDK_INT}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Available Memory: ${getAvailableMemory()}MB")
            appendLine("Free Storage: ${getFreeStorage()}MB")
            appendLine("=== End System Info ===")
        }
        i(tag, info)
    }
    
    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        val fullTag = "${TAG}_$tag"
        val timestamp = dateFormat.format(Date())
        val formattedMessage = "[$timestamp][${level.tag}] $message"
        
        // Always log to Android logcat
        when (level) {
            Level.VERBOSE -> Log.v(fullTag, formattedMessage, throwable)
            Level.DEBUG -> Log.d(fullTag, formattedMessage, throwable)
            Level.INFO -> Log.i(fullTag, formattedMessage, throwable)
            Level.WARN -> Log.w(fullTag, formattedMessage, throwable)
            Level.ERROR -> Log.e(fullTag, formattedMessage, throwable)
            Level.CRASH -> Log.wtf(fullTag, formattedMessage, throwable)
        }
        
        // Write to file only in debug builds
        if (isDebugBuild) {
            writeToFile(level, fullTag, formattedMessage, throwable)
        }
    }
    
    private fun writeToFile(level: Level, tag: String, message: String, throwable: Throwable?) {
        context?.let { ctx ->
            try {
                val logFile = getCurrentLogFile(ctx)
                FileWriter(logFile, true).use { writer ->
                    writer.appendLine("${level.tag}/$tag: $message")
                    throwable?.let {
                        writer.appendLine("Exception: ${it.message}")
                        it.stackTrace.forEach { element ->
                            writer.appendLine("  at $element")
                        }
                    }
                    writer.flush()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write log to file", e)
            }
        }
    }
    
    private fun getCurrentLogFile(context: Context): File {
        val logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return File(logDir, "$LOG_FILE_PREFIX$dateString.log")
    }
    
    private fun cleanOldLogFiles() {
        context?.let { ctx ->
            val logDir = File(ctx.filesDir, "logs")
            if (!logDir.exists()) return
            
            val logFiles = logDir.listFiles { file ->
                file.name.startsWith(LOG_FILE_PREFIX)
            }?.sortedByDescending { it.lastModified() }
            
            if (logFiles != null && logFiles.size > MAX_LOG_FILES) {
                logFiles.drop(MAX_LOG_FILES).forEach { file ->
                    file.delete()
                    Log.d(TAG, "Deleted old log file: ${file.name}")
                }
            }
        }
    }
    
    private fun getAvailableMemory(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
        } catch (e: Exception) {
            -1
        }
    }
    
    private fun getFreeStorage(): Long {
        return try {
            context?.filesDir?.freeSpace?.div(1024 * 1024) ?: -1
        } catch (e: Exception) {
            -1
        }
    }
    
    fun getLogFiles(): List<File> {
        val logDir = File(context?.filesDir, "logs")
        return if (logDir.exists()) {
            logDir.listFiles { file ->
                file.name.startsWith(LOG_FILE_PREFIX)
            }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun exportLogs(): String? {
        return try {
            val allLogs = StringBuilder()
            getLogFiles().sortedBy { it.lastModified() }.forEach { file ->
                allLogs.appendLine("=== ${file.name} ===")
                allLogs.appendLine(file.readText())
                allLogs.appendLine()
            }
            allLogs.toString()
        } catch (e: Exception) {
            e("Logger", "Failed to export logs", e)
            null
        }
    }
}