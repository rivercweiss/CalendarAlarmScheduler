package com.example.calendaralarmscheduler.workers

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.calendaralarmscheduler.utils.PermissionUtils
import com.example.calendaralarmscheduler.utils.Logger
import java.time.Duration
import java.util.concurrent.TimeUnit

class WorkerManager(private val context: Context) {
    
    companion object {
        private const val CALENDAR_REFRESH_WORK_NAME = "calendar_refresh_work"
        private const val IMMEDIATE_REFRESH_WORK_NAME = "immediate_refresh_work"
        
        // Available refresh intervals in minutes
        const val INTERVAL_5_MINUTES = 5
        const val INTERVAL_15_MINUTES = 15
        const val INTERVAL_30_MINUTES = 30
        const val INTERVAL_60_MINUTES = 60
        const val DEFAULT_INTERVAL_MINUTES = INTERVAL_30_MINUTES
        
        val AVAILABLE_INTERVALS = listOf(
            INTERVAL_5_MINUTES,
            INTERVAL_15_MINUTES,
            INTERVAL_30_MINUTES,
            INTERVAL_60_MINUTES
        )
    }
    
    private val workManager = WorkManager.getInstance(context)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    /**
     * Schedule periodic calendar refresh with specified interval
     */
    fun schedulePeriodicRefresh(intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES) {
        try {
            Logger.i("WorkerManager", "Scheduling periodic calendar refresh with interval: ${intervalMinutes} minutes")
            
            if (!AVAILABLE_INTERVALS.contains(intervalMinutes)) {
                Logger.w("WorkerManager", "Invalid interval: $intervalMinutes. Using default: $DEFAULT_INTERVAL_MINUTES")
            }
            
            val validInterval = if (AVAILABLE_INTERVALS.contains(intervalMinutes)) intervalMinutes else DEFAULT_INTERVAL_MINUTES
            
            // Create constraints for background work
            val constraints = createWorkConstraints()
            
            val workRequest = PeriodicWorkRequestBuilder<CalendarRefreshWorker>(
                validInterval.toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()
            
            // Use REPLACE policy to update the interval if it changed
            workManager.enqueueUniquePeriodicWork(
                CALENDAR_REFRESH_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
            
            Logger.i("WorkerManager", "Periodic calendar refresh scheduled successfully with ${validInterval}-minute interval")
            
            // Check battery optimization status and warn if needed
            checkBatteryOptimizationStatus()
            
            // Check Doze mode compatibility
            checkDozeCompatibility()
            
        } catch (e: Exception) {
            Logger.e("WorkerManager", "Failed to schedule periodic calendar refresh", e)
        }
    }
    
    /**
     * Cancel periodic calendar refresh
     */
    fun cancelPeriodicRefresh() {
        try {
            Logger.i("WorkerManager", "Cancelling periodic calendar refresh")
            workManager.cancelUniqueWork(CALENDAR_REFRESH_WORK_NAME)
            Logger.i("WorkerManager", "Periodic calendar refresh cancelled successfully")
        } catch (e: Exception) {
            Logger.e("WorkerManager", "Failed to cancel periodic calendar refresh", e)
        }
    }
    
    /**
     * Reschedule with new interval (cancels old work and creates new)
     */
    fun reschedulePeriodicRefresh(newIntervalMinutes: Int) {
        Logger.i("WorkerManager", "Rescheduling periodic refresh from current to ${newIntervalMinutes} minutes")
        schedulePeriodicRefresh(newIntervalMinutes)
    }
    
    /**
     * Enqueue immediate one-time calendar refresh (e.g., for timezone changes)
     */
    fun enqueueImmediateRefresh() {
        try {
            Logger.i("WorkerManager", "Enqueuing immediate calendar refresh")
            
            val constraints = createWorkConstraints()
            
            val workRequest = OneTimeWorkRequestBuilder<CalendarRefreshWorker>()
                .setConstraints(constraints)
                .build()
            
            // Use REPLACE policy to ensure only one immediate refresh runs at a time
            workManager.enqueueUniqueWork(
                IMMEDIATE_REFRESH_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            
            Logger.i("WorkerManager", "Immediate calendar refresh enqueued successfully")
            
        } catch (e: Exception) {
            Logger.e("WorkerManager", "Failed to enqueue immediate calendar refresh", e)
        }
    }
    
    /**
     * Get current work status information
     */
    fun getWorkStatus(): WorkStatus {
        return try {
            val workInfos = workManager.getWorkInfosForUniqueWork(CALENDAR_REFRESH_WORK_NAME).get()
            if (workInfos.isNotEmpty()) {
                val workInfo = workInfos.first()
                WorkStatus(
                    isScheduled = workInfo.state != androidx.work.WorkInfo.State.CANCELLED,
                    state = workInfo.state.name,
                    runAttemptCount = workInfo.runAttemptCount,
                    nextScheduleTimeMillis = null // WorkInfo doesn't provide this directly
                )
            } else {
                WorkStatus(isScheduled = false, state = "NOT_SCHEDULED")
            }
        } catch (e: Exception) {
            Logger.e("WorkerManager", "Error getting work status", e)
            WorkStatus(isScheduled = false, state = "ERROR", errorMessage = e.message)
        }
    }
    
    /**
     * Create work constraints optimized for calendar refresh
     */
    private fun createWorkConstraints(): Constraints {
        return Constraints.Builder()
            // No network required - we're reading local calendar provider
            .setRequiredNetworkType(androidx.work.NetworkType.NOT_REQUIRED)
            // Allow work during device idle (doze mode compatibility)
            .setRequiresDeviceIdle(false)
            // Don't require charging - calendar refresh is lightweight
            .setRequiresCharging(false)
            // Work can run when battery is low - critical for alarm reliability
            .setRequiresBatteryNotLow(false)
            // Work can run in all storage states
            .setRequiresStorageNotLow(false)
            .build()
    }
    
    /**
     * Check battery optimization status and log information
     */
    private fun checkBatteryOptimizationStatus() {
        try {
            val isWhitelisted = PermissionUtils.isBatteryOptimizationWhitelisted(context)
            
            if (isWhitelisted) {
                Logger.i("WorkerManager", "✅ App is whitelisted from battery optimization - background work should run reliably")
            } else {
                Logger.w("WorkerManager", "⚠️ App is NOT whitelisted from battery optimization - background work may be delayed")
            }
            
        } catch (e: Exception) {
            Logger.e("WorkerManager", "Failed to check battery optimization status", e)
        }
    }
    
    /**
     * Check if the device is in Doze mode (API 23+)
     */
    fun isDeviceInDozeMode(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            false
        }
    }
    
    /**
     * Check if battery optimization is ignored (app is whitelisted)
     */
    fun isBatteryOptimizationIgnored(): Boolean {
        val isWhitelisted = PermissionUtils.isBatteryOptimizationWhitelisted(context)
        
        Logger.d("WorkerManager", "Battery optimization check: ${if (isWhitelisted) "Whitelisted" else "Not whitelisted"}")
        
        return isWhitelisted
    }
    
    /**
     * Validate interval value
     */
    fun validateInterval(intervalMinutes: Int): Boolean {
        return AVAILABLE_INTERVALS.contains(intervalMinutes)
    }
    
    /**
     * Get human-readable interval description
     */
    fun getIntervalDescription(intervalMinutes: Int): String {
        return when (intervalMinutes) {
            INTERVAL_5_MINUTES -> "5 minutes (High frequency - may impact battery)"
            INTERVAL_15_MINUTES -> "15 minutes (Balanced frequency)"
            INTERVAL_30_MINUTES -> "30 minutes (Default - recommended)"
            INTERVAL_60_MINUTES -> "60 minutes (Low frequency - better battery)"
            else -> "Custom: $intervalMinutes minutes"
        }
    }
    
    /**
     * Check Doze mode compatibility and log warnings
     */
    private fun checkDozeCompatibility() {
        try {
            val isDozeMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isDeviceIdleMode
            } else {
                false
            }
            
            val isWhitelisted = PermissionUtils.isBatteryOptimizationWhitelisted(context)
            
            if (isDozeMode && !isWhitelisted) {
                Logger.w("WorkerManager", "⚠️ Device is in Doze mode and app is not whitelisted - background work may be restricted")
            } else if (isWhitelisted) {
                Logger.i("WorkerManager", "✅ App is whitelisted - should work reliably even in Doze mode")
            } else {
                Logger.d("WorkerManager", "Device not in Doze mode - background work should proceed normally")
            }
            
        } catch (e: Exception) {
            Logger.e("WorkerManager", "Failed to check Doze mode", e)
        }
    }
    
    data class WorkStatus(
        val isScheduled: Boolean,
        val state: String,
        val runAttemptCount: Int = 0,
        val nextScheduleTimeMillis: Long? = null,
        val errorMessage: String? = null
    )
}