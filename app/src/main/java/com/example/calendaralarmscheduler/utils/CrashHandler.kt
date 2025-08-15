package com.example.calendaralarmscheduler.utils

import android.util.Log

class CrashHandler : Thread.UncaughtExceptionHandler {
    
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    
    companion object {
        private var instance: CrashHandler? = null
        
        fun initialize() {
            if (instance == null) {
                instance = CrashHandler()
                Thread.setDefaultUncaughtExceptionHandler(instance)
                Logger.i("CrashHandler", "Crash handler initialized")
            }
        }
    }
    
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            Logger.crash("CrashHandler", "UNCAUGHT EXCEPTION in thread ${thread.name}: ${throwable.message}", throwable)
        } catch (e: Exception) {
            Log.wtf("CrashHandler", "Failed to log crash", e)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    
    fun logNonFatalException(tag: String, message: String, throwable: Throwable) {
        Logger.e(tag, "Non-fatal exception: $message", throwable)
    }
}