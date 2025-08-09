package com.example.calendaralarmscheduler

import android.app.Application
import com.example.calendaralarmscheduler.utils.CrashHandler
import com.example.calendaralarmscheduler.utils.Logger

class CalendarAlarmApplication : Application() {
    
    override fun onCreate() {
        val startTime = System.currentTimeMillis()
        
        try {
            super.onCreate()
            
            // Initialize logging first
            val isDebug = true // Always enable debug logging for now
            Logger.initialize(this, isDebug)
            Logger.i("Application", "Starting CalendarAlarmApplication initialization - Debug: $isDebug")
            
            // Initialize crash handling
            CrashHandler.initialize(this)
            
            // Log system information
            Logger.dumpSystemInfo("Application")
            
            val initTime = System.currentTimeMillis() - startTime
            Logger.logPerformance("Application", "Application.onCreate()", initTime)
            Logger.i("Application", "CalendarAlarmApplication initialization completed successfully in ${initTime}ms")
            
        } catch (e: Exception) {
            // If something goes wrong during initialization, log it and re-throw
            try {
                Logger.crash("Application", "FATAL: Application initialization failed", e)
                CrashHandler(this).logCurrentAppState("Application")
            } catch (loggingException: Exception) {
                // Last resort - use system logging
                android.util.Log.wtf("CalendarAlarmApplication", "Application init failed and logging failed", e)
                android.util.Log.wtf("CalendarAlarmApplication", "Logging exception", loggingException)
            }
            throw e
        }
    }
    
    override fun onTerminate() {
        Logger.i("Application", "Application terminating")
        super.onTerminate()
    }
    
    override fun onLowMemory() {
        Logger.w("Application", "Low memory warning received")
        super.onLowMemory()
    }
    
    override fun onTrimMemory(level: Int) {
        val levelName = when (level) {
            TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
            TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
            TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
            TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
            TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
            TRIM_MEMORY_MODERATE -> "MODERATE"
            TRIM_MEMORY_COMPLETE -> "COMPLETE"
            else -> "UNKNOWN($level)"
        }
        
        Logger.w("Application", "Trim memory: $levelName (level $level)")
        super.onTrimMemory(level)
    }
}