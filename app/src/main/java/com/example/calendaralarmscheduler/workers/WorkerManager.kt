package com.example.calendaralarmscheduler.workers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.example.calendaralarmscheduler.BuildConfig
import com.example.calendaralarmscheduler.receivers.BackgroundRefreshReceiver
import com.example.calendaralarmscheduler.utils.PermissionUtils
import com.example.calendaralarmscheduler.utils.Logger

/**
 * Manages background calendar refresh using AlarmManager for guaranteed exact timing.
 * Replaces WorkManager to bypass Doze mode and battery optimization restrictions.
 */
class BackgroundRefreshManager(private val context: Context) {
    
    companion object {
        private const val PERIODIC_REFRESH_REQUEST_CODE = 1001
        private const val IMMEDIATE_REFRESH_REQUEST_CODE = 1002
        
        // Available refresh intervals in minutes (now supporting 1 minute with AlarmManager for debug)
        const val INTERVAL_1_MINUTE = 1
        const val INTERVAL_5_MINUTES = 5
        const val INTERVAL_15_MINUTES = 15
        const val INTERVAL_30_MINUTES = 30
        const val INTERVAL_60_MINUTES = 60
        
        // Build-specific default: 1 minute for debug builds, 30 minutes for release
        val DEFAULT_INTERVAL_MINUTES = if (BuildConfig.DEBUG) INTERVAL_1_MINUTE else INTERVAL_30_MINUTES
        
        // Build-specific available intervals: Include 1 minute only in debug builds
        val AVAILABLE_INTERVALS = if (BuildConfig.DEBUG) {
            listOf(
                INTERVAL_1_MINUTE,
                INTERVAL_5_MINUTES,
                INTERVAL_15_MINUTES,
                INTERVAL_30_MINUTES,
                INTERVAL_60_MINUTES
            )
        } else {
            listOf(
                INTERVAL_5_MINUTES,
                INTERVAL_15_MINUTES,
                INTERVAL_30_MINUTES,
                INTERVAL_60_MINUTES
            )
        }
    }
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    /**
     * Schedule periodic calendar refresh using AlarmManager.setExactAndAllowWhileIdle()
     * for guaranteed timing that bypasses Doze mode and battery optimization.
     */
    fun schedulePeriodicRefresh(intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES) {
        try {
            Logger.i("BackgroundRefreshManager", "Scheduling periodic calendar refresh with interval: ${intervalMinutes} minutes")
            
            if (!AVAILABLE_INTERVALS.contains(intervalMinutes)) {
                Logger.w("BackgroundRefreshManager", "Invalid interval: $intervalMinutes. Using default: $DEFAULT_INTERVAL_MINUTES")
            }
            
            val validInterval = if (AVAILABLE_INTERVALS.contains(intervalMinutes)) intervalMinutes else DEFAULT_INTERVAL_MINUTES
            
            // Cancel any existing periodic refresh
            cancelPeriodicRefresh()
            
            // Create intent for periodic refresh
            val intent = Intent(context, BackgroundRefreshReceiver::class.java).apply {
                action = BackgroundRefreshReceiver.ACTION_PERIODIC_REFRESH
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                PERIODIC_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Schedule first alarm
            val intervalMillis = validInterval * 60 * 1000L
            val nextTriggerTime = SystemClock.elapsedRealtime() + intervalMillis
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                nextTriggerTime,
                pendingIntent
            )
            
            Logger.i("BackgroundRefreshManager", "Periodic calendar refresh scheduled successfully with ${validInterval}-minute interval")
            
            // Check battery optimization status and warn if needed
            checkBatteryOptimizationStatus()
            
            // Check battery optimization compatibility
            checkDozeCompatibility()
            
        } catch (e: Exception) {
            Logger.e("BackgroundRefreshManager", "Failed to schedule periodic calendar refresh", e)
        }
    }
    
    /**
     * Cancel periodic calendar refresh
     */
    fun cancelPeriodicRefresh() {
        try {
            Logger.i("BackgroundRefreshManager", "Cancelling periodic calendar refresh")
            
            val intent = Intent(context, BackgroundRefreshReceiver::class.java).apply {
                action = BackgroundRefreshReceiver.ACTION_PERIODIC_REFRESH
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                PERIODIC_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Logger.i("BackgroundRefreshManager", "Periodic calendar refresh cancelled successfully")
            } else {
                Logger.d("BackgroundRefreshManager", "No periodic refresh alarm was scheduled")
            }
        } catch (e: Exception) {
            Logger.e("BackgroundRefreshManager", "Failed to cancel periodic calendar refresh", e)
        }
    }
    
    /**
     * Reschedule with new interval (cancels old alarm and creates new)
     */
    fun reschedulePeriodicRefresh(newIntervalMinutes: Int) {
        Logger.i("BackgroundRefreshManager", "Rescheduling periodic refresh from current to ${newIntervalMinutes} minutes")
        schedulePeriodicRefresh(newIntervalMinutes)
    }
    
    /**
     * Schedule immediate one-time calendar refresh (e.g., for timezone changes)
     * using AlarmManager with minimal delay for guaranteed execution.
     */
    fun enqueueImmediateRefresh() {
        try {
            Logger.i("BackgroundRefreshManager", "Enqueuing immediate calendar refresh")
            
            val intent = Intent(context, BackgroundRefreshReceiver::class.java).apply {
                action = BackgroundRefreshReceiver.ACTION_IMMEDIATE_REFRESH
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                IMMEDIATE_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Schedule immediate execution (1 second delay to ensure proper context)
            val triggerTime = SystemClock.elapsedRealtime() + 1000
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
            
            Logger.i("BackgroundRefreshManager", "Immediate calendar refresh scheduled successfully")
            
        } catch (e: Exception) {
            Logger.e("BackgroundRefreshManager", "Failed to enqueue immediate calendar refresh", e)
        }
    }
    
    /**
     * Get current background refresh status by checking PendingIntent existence.
     * AlarmManager doesn't provide detailed status like WorkManager did.
     */
    fun getWorkStatus(): WorkStatus {
        return try {
            val intent = Intent(context, BackgroundRefreshReceiver::class.java).apply {
                action = BackgroundRefreshReceiver.ACTION_PERIODIC_REFRESH
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                PERIODIC_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            val isScheduled = pendingIntent != null
            WorkStatus(
                isScheduled = isScheduled,
                state = if (isScheduled) "SCHEDULED" else "NOT_SCHEDULED",
                runAttemptCount = 0, // Not tracked with AlarmManager
                nextScheduleTimeMillis = null // Not easily available with AlarmManager
            )
        } catch (e: Exception) {
            Logger.e("BackgroundRefreshManager", "Error getting work status", e)
            WorkStatus(isScheduled = false, state = "ERROR", errorMessage = e.message)
        }
    }
    
    
    /**
     * Check battery optimization status and log information
     */
    private fun checkBatteryOptimizationStatus() {
        try {
            val isWhitelisted = PermissionUtils.isBatteryOptimizationWhitelisted(context)
            
            if (isWhitelisted) {
                Logger.i("BackgroundRefreshManager", "✅ App is whitelisted from battery optimization - background refresh should run reliably")
            } else {
                Logger.w("BackgroundRefreshManager", "⚠️ App is NOT whitelisted from battery optimization - background refresh may be delayed")
            }
            
        } catch (e: Exception) {
            Logger.e("BackgroundRefreshManager", "Failed to check battery optimization status", e)
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
        
        Logger.d("BackgroundRefreshManager", "Battery optimization check: ${if (isWhitelisted) "Whitelisted" else "Not whitelisted"}")
        
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
            INTERVAL_1_MINUTE -> "1 minute (Debug only - very high battery usage)"
            INTERVAL_5_MINUTES -> "5 minutes (High frequency - more battery usage)"
            INTERVAL_15_MINUTES -> "15 minutes (Balanced frequency)"
            INTERVAL_30_MINUTES -> "30 minutes (Default - recommended)"
            INTERVAL_60_MINUTES -> "60 minutes (Low frequency - better battery)"
            else -> "Custom: $intervalMinutes minutes"
        }
    }
    
    /**
     * Check battery optimization compatibility and log warnings
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
                Logger.w("BackgroundRefreshManager", "⚠️ Device is in Doze mode and app is not whitelisted - background refresh may be restricted")
            } else if (isWhitelisted) {
                Logger.i("BackgroundRefreshManager", "✅ App is whitelisted - should work reliably even in Doze mode")
            } else {
                Logger.d("BackgroundRefreshManager", "Device not in Doze mode - background refresh should proceed normally")
            }
            
        } catch (e: Exception) {
            Logger.e("BackgroundRefreshManager", "Failed to check Doze mode", e)
        }
    }
    
    /**
     * Schedule the next periodic refresh (used by receiver after execution)
     */
    fun scheduleNextPeriodicRefresh(intervalMinutes: Int) {
        try {
            val intent = Intent(context, BackgroundRefreshReceiver::class.java).apply {
                action = BackgroundRefreshReceiver.ACTION_PERIODIC_REFRESH
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                PERIODIC_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val intervalMillis = intervalMinutes * 60 * 1000L
            val nextTriggerTime = SystemClock.elapsedRealtime() + intervalMillis
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                nextTriggerTime,
                pendingIntent
            )
            
            Logger.i("BackgroundRefreshManager", "Next periodic refresh scheduled for ${intervalMinutes} minutes from now")
            
        } catch (e: Exception) {
            Logger.e("BackgroundRefreshManager", "Failed to schedule next periodic refresh", e)
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