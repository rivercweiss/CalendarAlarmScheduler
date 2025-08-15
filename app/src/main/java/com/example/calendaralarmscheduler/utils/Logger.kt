package com.example.calendaralarmscheduler.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = "CalendarAlarmScheduler"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    fun initialize() {
        i("Logger", "Logger initialized")
    }
    
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.VERBOSE, tag, message, throwable)
    }
    
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.DEBUG, tag, message, throwable)
    }
    
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.INFO, tag, message, throwable)
    }
    
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.WARN, tag, message, throwable)
    }
    
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.ERROR, tag, message, throwable)
    }
    
    fun crash(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.ASSERT, tag, message, throwable)
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
    
    private fun log(level: Int, tag: String, message: String, throwable: Throwable?) {
        val fullTag = "${TAG}_$tag"
        val timestamp = dateFormat.format(Date())
        val formattedMessage = "[$timestamp] $message"
        
        when (level) {
            Log.VERBOSE -> Log.v(fullTag, formattedMessage, throwable)
            Log.DEBUG -> Log.d(fullTag, formattedMessage, throwable)
            Log.INFO -> Log.i(fullTag, formattedMessage, throwable)
            Log.WARN -> Log.w(fullTag, formattedMessage, throwable)
            Log.ERROR -> Log.e(fullTag, formattedMessage, throwable)
            Log.ASSERT -> Log.wtf(fullTag, formattedMessage, throwable)
        }
    }
}