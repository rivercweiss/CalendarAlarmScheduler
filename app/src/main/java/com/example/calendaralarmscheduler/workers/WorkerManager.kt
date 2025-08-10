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
import com.example.calendaralarmscheduler.utils.BackgroundUsageDetector
import com.example.calendaralarmscheduler.utils.DozeCompatibilityUtils
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
     * Check comprehensive background usage status and log detailed information
     */
    private fun checkBatteryOptimizationStatus() {
        try {
            val backgroundStatus = BackgroundUsageDetector.getDetailedBackgroundUsageStatus(context)
            
            when {
                backgroundStatus.isBackgroundUsageAllowed -> {
                    Logger.i("WorkerManager", 
                        "✅ Background usage allowed via ${backgroundStatus.detectionMethod} - " +
                        "background work should run reliably"
                    )
                }
                else -> {
                    val methodDescription = when (backgroundStatus.detectionMethod) {
                        BackgroundUsageDetector.DetectionMethod.LEGACY_BATTERY_OPTIMIZATION -> 
                            "traditional battery optimization"
                        BackgroundUsageDetector.DetectionMethod.MODERN_BACKGROUND_USAGE -> 
                            "modern background app refresh settings"
                        BackgroundUsageDetector.DetectionMethod.APP_STANDBY_BUCKET -> 
                            "app standby bucket (${backgroundStatus.details["bucketName"]})"
                        BackgroundUsageDetector.DetectionMethod.BACKGROUND_RESTRICTION -> 
                            "background app restrictions"
                        BackgroundUsageDetector.DetectionMethod.APP_OPS_BACKGROUND_CHECK -> 
                            "AppOps background permissions"
                        BackgroundUsageDetector.DetectionMethod.OEM_SPECIFIC -> 
                            "${backgroundStatus.details["oem"]} OEM power management"
                        else -> "unknown method"
                    }
                    
                    Logger.w("WorkerManager", 
                        "⚠️ Background usage restricted via $methodDescription. " +
                        "Background calendar refresh may be delayed or skipped. " +
                        "Consider guiding users to allow background usage for reliable alarm scheduling."
                    )
                    
                    // Log additional details for debugging
                    if (backgroundStatus.details.isNotEmpty()) {
                        Logger.d("WorkerManager", "Background restriction details: ${backgroundStatus.details}")
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("WorkerManager", "Error checking background usage status", e)
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
     * Get comprehensive background usage status (replaces legacy battery optimization check)
     */
    fun isBatteryOptimizationIgnored(): Boolean {
        // Use the new comprehensive background usage detector
        val backgroundStatus = BackgroundUsageDetector.getDetailedBackgroundUsageStatus(context)
        
        Logger.d("WorkerManager", "Background usage check:")
        Logger.d("WorkerManager", "  Allowed: ${backgroundStatus.isBackgroundUsageAllowed}")
        Logger.d("WorkerManager", "  Method: ${backgroundStatus.detectionMethod}")
        Logger.d("WorkerManager", "  API Level: ${backgroundStatus.apiLevel}")
        
        return backgroundStatus.isBackgroundUsageAllowed
    }
    
    /**
     * Get detailed background usage status for debugging
     */
    fun getBackgroundUsageStatus(): BackgroundUsageDetector.BackgroundUsageStatus {
        return BackgroundUsageDetector.getDetailedBackgroundUsageStatus(context)
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
            val testResult = DozeCompatibilityUtils.testDozeCompatibility(context)
            
            when (testResult.severity) {
                DozeCompatibilityUtils.DozeTestResult.Severity.NONE -> {
                    Logger.i("WorkerManager", "Doze mode compatibility: ${testResult.message}")
                }
                DozeCompatibilityUtils.DozeTestResult.Severity.MEDIUM -> {
                    Logger.w("WorkerManager", "Doze mode warning: ${testResult.message}")
                    testResult.recommendations.forEach { recommendation ->
                        Logger.w("WorkerManager", "Recommendation: $recommendation")
                    }
                }
                DozeCompatibilityUtils.DozeTestResult.Severity.HIGH -> {
                    Logger.e("WorkerManager", "Doze mode critical issue: ${testResult.message}")
                    testResult.issues.forEach { issue ->
                        Logger.e("WorkerManager", "Issue: $issue")
                    }
                    testResult.recommendations.forEach { recommendation ->
                        Logger.e("WorkerManager", "Critical recommendation: $recommendation")
                    }
                }
            }
            
            // Log detailed status in debug mode
            DozeCompatibilityUtils.logDozeStatus(context)
            
        } catch (e: Exception) {
            Logger.e("WorkerManager", "Error checking Doze mode compatibility", e)
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