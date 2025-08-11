package com.example.calendaralarmscheduler

import android.app.Application
import android.content.BroadcastReceiver
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.utils.BackgroundUsageDetector
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
            val isDebug = true // Always enable debug logging for now
            Logger.initialize(this, isDebug)
            Logger.i("Application", "Starting CalendarAlarmApplication initialization - Debug: $isDebug")
            
            // Initialize crash handling
            CrashHandler.initialize(this)
            
            // Log system information
            Logger.dumpSystemInfo("Application")
            
            // Set up SettingsRepository callback for refresh interval changes
            settingsRepository.setOnRefreshIntervalChanged { newIntervalMinutes ->
                onRefreshIntervalChanged(newIntervalMinutes)
            }
            
            // Schedule background calendar refresh worker with user-configured interval
            try {
                val refreshInterval = settingsRepository.getRefreshIntervalMinutes()
                workerManager.schedulePeriodicRefresh(refreshInterval)
                Logger.i("Application", "Background calendar refresh worker scheduled successfully with ${refreshInterval}-minute interval")
                
                // Log current settings
                settingsRepository.dumpSettings()
            } catch (e: Exception) {
                Logger.e("Application", "Failed to schedule background worker", e)
                // Don't throw - app can still function without background refresh
            }
            
            // Set up timezone change handling
            setupTimezoneChangeHandling()
            
            // Initialize background usage cache asynchronously to prevent main thread blocking
            applicationScope.launch {
                try {
                    BackgroundUsageDetector.initializeBackgroundUsageCache(this@CalendarAlarmApplication)
                    Logger.i("Application", "Background usage detection cache initialized successfully")
                } catch (e: Exception) {
                    Logger.w("Application", "Failed to initialize background usage cache (will use fallback)", e)
                }
            }
            
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