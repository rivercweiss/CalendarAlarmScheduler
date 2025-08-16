package com.example.calendaralarmscheduler.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.calendaralarmscheduler.data.DayTrackingRepository
import com.example.calendaralarmscheduler.receivers.DayResetReceiver
import com.example.calendaralarmscheduler.utils.Logger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar

/**
 * Service that manages scheduling daily reset alarms to clear day tracking at midnight.
 * Ensures that "first event of day only" rules reset properly each day.
 * 
 * Key Features:
 * - Schedules exact alarms at local midnight using AlarmManager.setExactAndAllowWhileIdle()
 * - Automatically handles timezone changes by rescheduling reset alarms
 * - Uses local timezone calculations to determine correct day boundaries
 * - Reliable cross-reboot operation by rescheduling on device startup
 * - Integrates with DayTrackingRepository for state management
 */
class DayResetService(
    private val context: Context,
    private val dayTrackingRepository: DayTrackingRepository
) {
    
    companion object {
        private const val LOG_TAG = "DayResetService"
        private const val DAY_RESET_REQUEST_CODE = 9999
    }
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    /**
     * Schedule the next midnight reset alarm
     */
    fun scheduleNextMidnightReset() {
        val nextMidnight = getNextMidnightInLocalTime()
        val nextMidnightUtc = nextMidnight.toInstant().toEpochMilli()
        
        val intent = Intent(context, DayResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAY_RESET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            // Use setExactAndAllowWhileIdle for reliable midnight reset
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextMidnightUtc,
                pendingIntent
            )
            
            Logger.i(LOG_TAG, "Scheduled midnight reset for: $nextMidnight (${nextMidnightUtc}ms)")
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Failed to schedule midnight reset alarm", e)
        }
    }
    
    /**
     * Cancel any existing midnight reset alarm
     */
    fun cancelMidnightReset() {
        val intent = Intent(context, DayResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAY_RESET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        Logger.d(LOG_TAG, "Cancelled midnight reset alarm")
    }
    
    /**
     * Perform the actual day reset and schedule the next one
     */
    fun performDayReset() {
        Logger.i(LOG_TAG, "Performing daily reset of day tracking")
        
        try {
            // Reset day tracking
            dayTrackingRepository.forceReset()
            
            // Schedule next midnight reset
            scheduleNextMidnightReset()
            
            Logger.i(LOG_TAG, "Daily reset completed successfully")
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Failed to perform daily reset", e)
            
            // Still try to schedule next reset even if this one failed
            try {
                scheduleNextMidnightReset()
            } catch (scheduleError: Exception) {
                Logger.e(LOG_TAG, "Failed to schedule next reset after reset failure", scheduleError)
            }
        }
    }
    
    /**
     * Handle timezone change by cancelling and rescheduling reset
     */
    fun handleTimezoneChange() {
        Logger.i(LOG_TAG, "Handling timezone change")
        
        cancelMidnightReset()
        dayTrackingRepository.handleTimezoneChange()
        scheduleNextMidnightReset()
        
        Logger.i(LOG_TAG, "Timezone change handling completed")
    }
    
    /**
     * Get the next midnight in local time
     */
    private fun getNextMidnightInLocalTime(): ZonedDateTime {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val tomorrow = now.toLocalDate().plusDays(1)
        val nextMidnight = tomorrow.atStartOfDay(ZoneId.systemDefault())
        
        Logger.v(LOG_TAG, "Next midnight calculated: $nextMidnight")
        return nextMidnight
    }
    
    /**
     * Check if reset alarm is scheduled (for debugging)
     */
    fun isResetAlarmScheduled(): Boolean {
        val intent = Intent(context, DayResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAY_RESET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        
        val isScheduled = pendingIntent != null
        Logger.v(LOG_TAG, "Reset alarm scheduled: $isScheduled")
        return isScheduled
    }
    
    /**
     * Get debug information about next reset time
     */
    fun getDebugInfo(): Map<String, String> {
        val nextMidnight = getNextMidnightInLocalTime()
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val hoursUntilReset = java.time.Duration.between(now, nextMidnight).toHours()
        
        return mapOf(
            "nextResetTime" to nextMidnight.toString(),
            "hoursUntilReset" to hoursUntilReset.toString(),
            "isResetAlarmScheduled" to isResetAlarmScheduled().toString(),
            "currentTimeZone" to ZoneId.systemDefault().toString()
        )
    }
}