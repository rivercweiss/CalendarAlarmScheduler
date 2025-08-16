package com.example.calendaralarmscheduler

import android.app.Application
import android.content.BroadcastReceiver
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.utils.CrashHandler
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.TimezoneUtils
import com.example.calendaralarmscheduler.workers.WorkerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CalendarAlarmApplication : Application() {
    
    // Application-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Injected dependencies needed for application logic
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var workerManager: WorkerManager
    
    // Additional injected dependencies for legacy components (BootReceiver, CalendarRefreshWorker)
    @Inject
    lateinit var alarmRepository: com.example.calendaralarmscheduler.data.AlarmRepository
    
    @Inject
    lateinit var alarmScheduler: com.example.calendaralarmscheduler.domain.AlarmScheduler
    
    @Inject
    lateinit var calendarRepository: com.example.calendaralarmscheduler.data.CalendarRepository
    
    @Inject
    lateinit var ruleRepository: com.example.calendaralarmscheduler.data.RuleRepository
    
    @Inject
    lateinit var dayTrackingRepository: com.example.calendaralarmscheduler.data.DayTrackingRepository
    
    @Inject
    lateinit var dayResetService: com.example.calendaralarmscheduler.services.DayResetService
    
    // Timezone change listener
    private var timezoneChangeReceiver: BroadcastReceiver? = null
    
    /**
     * Handle settings changes that require worker rescheduling
     */
    fun onRefreshIntervalChanged(newIntervalMinutes: Int) {
        try {
            Logger.i("Application", "Refresh interval changed to $newIntervalMinutes minutes - rescheduling worker")
            workerManager.reschedulePeriodicRefresh(newIntervalMinutes)
        } catch (e: Exception) {
            Logger.e("Application", "Failed to reschedule worker after settings change", e)
        }
    }
    
    /**
     * Set up timezone change handling to reset last sync time and reschedule alarms
     */
    private fun setupTimezoneChangeHandling() {
        try {
            timezoneChangeReceiver = TimezoneUtils.registerTimezoneChangeListener(this) {
                handleTimezoneChange()
            }
            Logger.i("Application", "Timezone change listener registered successfully")
        } catch (e: Exception) {
            Logger.e("Application", "Failed to register timezone change listener", e)
        }
    }
    
    /**
     * Handle timezone changes by resetting last sync time and triggering alarm rescheduling
     */
    private fun handleTimezoneChange() {
        try {
            Logger.i("Application", "Handling timezone change")
            
            // Reset last sync time to force full calendar rescan
            settingsRepository.handleTimezoneChange()
            
            // Trigger immediate calendar refresh to reschedule all alarms
            workerManager.enqueueImmediateRefresh()
            
            Logger.i("Application", "Timezone change handled successfully - forced calendar refresh")
        } catch (e: Exception) {
            Logger.e("Application", "Failed to handle timezone change", e)
        }
    }
    
    override fun onCreate() {
        val startTime = System.currentTimeMillis()
        
        try {
            super.onCreate()
            
            // Initialize logging first
            Logger.initialize()
            Logger.i("Application", "Starting CalendarAlarmApplication initialization")
            
            // Initialize crash handling
            CrashHandler.initialize()
            
            // Set up SettingsRepository callback for refresh interval changes
            settingsRepository.setOnRefreshIntervalChanged { newIntervalMinutes ->
                onRefreshIntervalChanged(newIntervalMinutes)
            }
            
            // Schedule background calendar refresh worker with user-configured interval
            try {
                val refreshInterval = settingsRepository.getRefreshIntervalMinutes()
                workerManager.schedulePeriodicRefresh(refreshInterval)
                Logger.i("Application", "Background calendar refresh worker scheduled successfully with ${refreshInterval}-minute interval")
                
            } catch (e: Exception) {
                Logger.e("Application", "Failed to schedule background worker", e)
                // Don't throw - app can still function without background refresh
            }
            
            // Set up timezone change handling
            setupTimezoneChangeHandling()
            
            // Schedule daily reset for "first event of day only" rules
            try {
                dayResetService.scheduleNextMidnightReset()
                Logger.i("Application", "Daily reset alarm scheduled successfully")
            } catch (e: Exception) {
                Logger.e("Application", "Failed to schedule daily reset alarm", e)
            }
            
            // Clean up expired alarms on app start to prevent database bloat
            applicationScope.launch {
                try {
                    val cleanupStartTime = System.currentTimeMillis()
                    alarmRepository.cleanupOldAlarms()
                    val cleanupTime = System.currentTimeMillis() - cleanupStartTime
                    Logger.i("Application", "Expired alarms cleanup completed in ${cleanupTime}ms")
                } catch (e: Exception) {
                    Logger.w("Application", "Failed to cleanup expired alarms on startup", e)
                }
            }
            
            val initTime = System.currentTimeMillis() - startTime
            Logger.logPerformance("Application", "Application.onCreate()", initTime)
            Logger.i("Application", "CalendarAlarmApplication initialization completed successfully in ${initTime}ms")
            
        } catch (e: Exception) {
            // If something goes wrong during initialization, log it and re-throw
            try {
                Logger.crash("Application", "FATAL: Application initialization failed", e)
            } catch (loggingException: Exception) {
                // Last resort - use system logging
                android.util.Log.wtf("CalendarAlarmApplication", "Application init failed and logging failed", e)
                android.util.Log.wtf("CalendarAlarmApplication", "Logging exception", loggingException)
            }
            throw e
        }
    }
    
    /**
     * Log comprehensive system memory usage for monitoring
     */
    private fun logSystemMemoryUsage(context: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val usagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
        
        // Also get system memory info
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        activityManager.getMemoryInfo(memoryInfo)
        
        Logger.i("Application_Memory", 
            "$context - App Memory: ${usedMemory / 1024 / 1024}MB/${totalMemory / 1024 / 1024}MB " +
            "(${usagePercent.toInt()}% of ${maxMemory / 1024 / 1024}MB max)")
        Logger.i("Application_Memory", 
            "$context - System Memory: ${(memoryInfo.totalMem - memoryInfo.availMem) / 1024 / 1024}MB/" +
            "${memoryInfo.totalMem / 1024 / 1024}MB (${memoryInfo.availMem / 1024 / 1024}MB available)")
    }
    
    override fun onTerminate() {
        Logger.i("Application", "Application terminating")
        
        // Clean up timezone change receiver
        timezoneChangeReceiver?.let { receiver ->
            try {
                TimezoneUtils.unregisterTimezoneChangeListener(this, receiver)
                Logger.i("Application", "Timezone change listener unregistered")
            } catch (e: Exception) {
                Logger.w("Application", "Error unregistering timezone change listener", e)
            }
            timezoneChangeReceiver = null
        }
        
        super.onTerminate()
    }
    
    override fun onLowMemory() {
        Logger.w("Application", "Low memory warning received from Android system")
        logSystemMemoryUsage("onLowMemory callback")
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
        logSystemMemoryUsage("onTrimMemory $levelName")
        
        // Trigger cleanup in severe memory situations
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            Logger.i("Application", "Requesting aggressive cleanup due to memory pressure")
            applicationScope.launch {
                try {
                    alarmRepository.cleanupOldAlarms()
                    System.gc()
                    kotlinx.coroutines.delay(1000)
                    logSystemMemoryUsage("Post-cleanup")
                } catch (e: Exception) {
                    Logger.w("Application", "Error during memory pressure cleanup", e)
                }
            }
        }
        
        super.onTrimMemory(level)
    }
}