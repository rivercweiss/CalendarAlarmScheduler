package com.example.calendaralarmscheduler.utils

import android.content.Context
import android.os.Build
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(private val context: Context? = null) : Thread.UncaughtExceptionHandler {
    
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    
    companion object {
        private var instance: CrashHandler? = null
        
        fun initialize(context: Context) {
            if (instance == null) {
                instance = CrashHandler(context.applicationContext)
                Thread.setDefaultUncaughtExceptionHandler(instance)
                Logger.i("CrashHandler", "Crash handler initialized")
            }
        }
    }
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Logger.crash("CrashHandler", "UNCAUGHT EXCEPTION in thread ${thread.name}", throwable)
            logCrashDetails(throwable, thread)
        } catch (e: Exception) {
            // If our logging fails, at least try to log that
            android.util.Log.wtf("CrashHandler", "Failed to log crash details", e)
        } finally {
            // Call the default handler to let the system handle the crash
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    private fun logCrashDetails(throwable: Throwable, thread: Thread) {
        val crashReport = buildString {
            appendLine("=== CRASH REPORT ===")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}")
            appendLine("Thread: ${thread.name} (ID: ${thread.id})")
            appendLine()
            
            appendLine("=== DEVICE INFO ===")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Brand: ${Build.BRAND}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine("SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Release: ${Build.VERSION.RELEASE}")
            appendLine()
            
            appendLine("=== MEMORY INFO ===")
            val runtime = Runtime.getRuntime()
            appendLine("Max Memory: ${runtime.maxMemory() / (1024 * 1024)}MB")
            appendLine("Total Memory: ${runtime.totalMemory() / (1024 * 1024)}MB")
            appendLine("Free Memory: ${runtime.freeMemory() / (1024 * 1024)}MB")
            appendLine("Available Memory: ${(runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)}MB")
            appendLine()
            
            appendLine("=== EXCEPTION DETAILS ===")
            appendLine("Exception Type: ${throwable::class.java.name}")
            appendLine("Message: ${throwable.message}")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(getStackTrace(throwable))
            
            // Include cause chain if available
            var cause = throwable.cause
            var level = 1
            while (cause != null && level <= 5) { // Limit to 5 levels to prevent infinite loops
                appendLine()
                appendLine("=== CAUSED BY (Level $level) ===")
                appendLine("Exception Type: ${cause::class.java.name}")
                appendLine("Message: ${cause.message}")
                appendLine("Stack Trace:")
                appendLine(getStackTrace(cause))
                cause = cause.cause
                level++
            }
            
            appendLine()
            appendLine("=== END CRASH REPORT ===")
        }
        
        Logger.crash("CRASH_REPORT", crashReport)
    }
    
    private fun getStackTrace(throwable: Throwable): String {
        return try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            sw.toString()
        } catch (e: Exception) {
            "Failed to get stack trace: ${e.message}"
        }
    }
    
    fun logNonFatalException(tag: String, message: String, throwable: Throwable) {
        Logger.e(tag, "Non-fatal exception: $message", throwable)
        
        val report = buildString {
            appendLine("=== NON-FATAL EXCEPTION ===")
            appendLine("Tag: $tag")
            appendLine("Message: $message")
            appendLine("Exception: ${throwable::class.java.name}")
            appendLine("Exception Message: ${throwable.message}")
            appendLine("Stack Trace:")
            appendLine(getStackTrace(throwable))
        }
        
        Logger.e("NON_FATAL", report)
    }
    
    fun logCurrentAppState(tag: String) {
        try {
            val stateReport = buildString {
                appendLine("=== APP STATE DUMP ===")
                appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}")
                appendLine("Active Thread Count: ${Thread.activeCount()}")
                
                val runtime = Runtime.getRuntime()
                appendLine("Memory Usage: ${(runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)}MB / ${runtime.totalMemory() / (1024 * 1024)}MB")
                
                // Log all running threads
                appendLine("Active Threads:")
                Thread.getAllStackTraces().keys.forEach { thread ->
                    appendLine("  ${thread.name} - ${thread.state} (Priority: ${thread.priority})")
                }
                appendLine("=== END APP STATE DUMP ===")
            }
            
            Logger.i(tag, stateReport)
        } catch (e: Exception) {
            Logger.e(tag, "Failed to dump app state", e)
        }
    }
}