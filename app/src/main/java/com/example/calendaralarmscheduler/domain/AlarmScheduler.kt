package com.example.calendaralarmscheduler.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager
) {
    
    companion object {
        private const val TAG = "AlarmScheduler"
        private const val ALARM_RECEIVER_ACTION = "com.example.calendaralarmscheduler.ALARM_TRIGGER"
        
        // Intent extras
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_EVENT_TITLE = "EVENT_TITLE"
        const val EXTRA_EVENT_START_TIME = "EVENT_START_TIME"
        const val EXTRA_RULE_ID = "RULE_ID"
    }
    
    data class ScheduleResult(
        val success: Boolean,
        val message: String,
        val alarm: ScheduledAlarm? = null
    )
    
    suspend fun scheduleAlarm(alarm: ScheduledAlarm): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            if (alarm.isInPast()) {
                return@withContext ScheduleResult(
                    success = false,
                    message = "Cannot schedule alarm for past time: ${alarm.getLocalAlarmTime()}"
                )
            }
            
            // Check permissions before creating PendingIntent to avoid exceptions in tests
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                return@withContext ScheduleResult(
                    success = false,
                    message = "Exact alarm permission not granted"
                )
            }
            
            val intent = createAlarmIntent(alarm)
            val pendingIntent = createPendingIntent(alarm, intent)
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.alarmTimeUtc,
                pendingIntent
            )
            
            Log.d(TAG, "Scheduled alarm for ${alarm.eventTitle} at ${alarm.getLocalAlarmTime()}")
            
            return@withContext ScheduleResult(
                success = true,
                message = "Alarm scheduled successfully",
                alarm = alarm
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for ${alarm.eventTitle}", e)
            
            // Attempt retry with RetryManager for retriable exceptions
            if (com.example.calendaralarmscheduler.utils.RetryManager.isRetriableException(e)) {
                val retryResult = com.example.calendaralarmscheduler.utils.RetryManager.withRetry(
                    operation = "schedule_alarm_${alarm.id}",
                    maxRetries = 2,
                    onRetry = { attempt, error ->
                        Log.i(TAG, "Retrying alarm scheduling for ${alarm.eventTitle} (attempt $attempt): ${error.message}")
                    }
                ) {
                    scheduleAlarmInternal(alarm)
                }
                
                if (retryResult.isSuccess) {
                    return@withContext ScheduleResult(
                        success = true,
                        message = "Alarm scheduled successfully after retry",
                        alarm = alarm
                    )
                } else {
                    return@withContext ScheduleResult(
                        success = false,
                        message = "Failed to schedule alarm after retries: ${retryResult.exceptionOrNull()?.message}"
                    )
                }
            } else {
                return@withContext ScheduleResult(
                    success = false,
                    message = "Failed to schedule alarm: ${e.message}"
                )
            }
        }
    }
    
    suspend fun cancelAlarm(alarm: ScheduledAlarm): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            val intent = createAlarmIntent(alarm)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.pendingIntentRequestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                
                Log.d(TAG, "Cancelled alarm for ${alarm.eventTitle}")
                
                return@withContext ScheduleResult(
                    success = true,
                    message = "Alarm cancelled successfully"
                )
            } else {
                return@withContext ScheduleResult(
                    success = true,
                    message = "Alarm was not scheduled (no pending intent found)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm for ${alarm.eventTitle}", e)
            return@withContext ScheduleResult(
                success = false,
                message = "Failed to cancel alarm: ${e.message}"
            )
        }
    }
    
    suspend fun rescheduleAlarm(oldAlarm: ScheduledAlarm, newAlarm: ScheduledAlarm): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            // Cancel the old alarm first
            val cancelResult = cancelAlarm(oldAlarm)
            if (!cancelResult.success) {
                Log.w(TAG, "Warning: Could not cancel old alarm: ${cancelResult.message}")
            }
            
            // Schedule the new alarm
            val scheduleResult = scheduleAlarm(newAlarm)
            
            if (scheduleResult.success) {
                Log.d(TAG, "Rescheduled alarm for ${newAlarm.eventTitle}")
                return@withContext ScheduleResult(
                    success = true,
                    message = "Alarm rescheduled successfully",
                    alarm = newAlarm
                )
            } else {
                return@withContext scheduleResult
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule alarm", e)
            return@withContext ScheduleResult(
                success = false,
                message = "Failed to reschedule alarm: ${e.message}"
            )
        }
    }
    
    suspend fun scheduleMultipleAlarms(alarms: List<ScheduledAlarm>): List<ScheduleResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScheduleResult>()
        
        for (alarm in alarms) {
            results.add(scheduleAlarm(alarm))
        }
        
        val successCount = results.count { it.success }
        Log.d(TAG, "Scheduled $successCount out of ${alarms.size} alarms")
        
        return@withContext results
    }
    
    suspend fun cancelMultipleAlarms(alarms: List<ScheduledAlarm>): List<ScheduleResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScheduleResult>()
        
        for (alarm in alarms) {
            results.add(cancelAlarm(alarm))
        }
        
        val successCount = results.count { it.success }
        Log.d(TAG, "Cancelled $successCount out of ${alarms.size} alarms")
        
        return@withContext results
    }
    
    fun isAlarmScheduled(alarm: ScheduledAlarm): Boolean {
        return try {
            val intent = createAlarmIntent(alarm)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.pendingIntentRequestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            pendingIntent != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if alarm is scheduled", e)
            false
        }
    }
    
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    private fun createAlarmIntent(alarm: ScheduledAlarm): Intent {
        return Intent(ALARM_RECEIVER_ACTION).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_EVENT_TITLE, alarm.eventTitle)
            putExtra(EXTRA_EVENT_START_TIME, alarm.eventStartTimeUtc)
            putExtra(EXTRA_RULE_ID, alarm.ruleId)
        }
    }
    
    private fun createPendingIntent(alarm: ScheduledAlarm, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            alarm.pendingIntentRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    suspend fun validateAndScheduleAlarms(
        alarms: List<ScheduledAlarm>,
        onValidationError: (ScheduledAlarm, String) -> Unit = { _, _ -> }
    ): List<ScheduleResult> = withContext(Dispatchers.IO) {
        val validAlarms = alarms.filter { alarm ->
            when {
                alarm.isInPast() -> {
                    onValidationError(alarm, "Alarm time is in the past")
                    false
                }
                alarm.userDismissed -> {
                    onValidationError(alarm, "Alarm was dismissed by user")
                    false
                }
                else -> true
            }
        }
        
        return@withContext scheduleMultipleAlarms(validAlarms)
    }
    
    suspend fun cleanupPastAlarms(alarms: List<ScheduledAlarm>): List<ScheduleResult> = withContext(Dispatchers.IO) {
        val pastAlarms = alarms.filter { it.isInPast() }
        return@withContext cancelMultipleAlarms(pastAlarms)
    }
    
    suspend fun scheduleSnoozeAlarm(originalAlarmId: String, snoozeTimeUtc: Long): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            // Create a temporary snooze alarm
            val snoozeAlarm = ScheduledAlarm(
                id = "${originalAlarmId}_snooze_${System.currentTimeMillis()}",
                eventId = "snooze_${originalAlarmId}",
                ruleId = "snooze_rule",
                eventTitle = "Snoozed Alarm",
                eventStartTimeUtc = snoozeTimeUtc,
                alarmTimeUtc = snoozeTimeUtc,
                scheduledAt = System.currentTimeMillis(),
                userDismissed = false,
                pendingIntentRequestCode = generateSnoozeRequestCode(originalAlarmId),
                lastEventModified = System.currentTimeMillis()
            )
            
            return@withContext scheduleAlarm(snoozeAlarm)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule snooze alarm for $originalAlarmId", e)
            return@withContext ScheduleResult(
                success = false,
                message = "Failed to schedule snooze alarm: ${e.message}"
            )
        }
    }
    
    private fun generateSnoozeRequestCode(originalAlarmId: String): Int {
        return "snooze_${originalAlarmId}".hashCode()
    }
    
    fun getNextAlarmTime(alarms: List<ScheduledAlarm>): Long? {
        return alarms
            .filter { it.isActive() }
            .minByOrNull { it.alarmTimeUtc }
            ?.alarmTimeUtc
    }
    
    /**
     * Check if an alarm is actually scheduled in the system (has active PendingIntent)
     * This helps detect user-dismissed alarms by comparing database state vs system state
     */
    fun isAlarmActiveInSystem(alarm: ScheduledAlarm): Boolean {
        return isAlarmScheduled(alarm)
    }
    
    /**
     * Check multiple alarms and return which ones are missing from the system
     * (indicating they were manually dismissed by the user)
     */
    suspend fun detectDismissedAlarms(alarms: List<ScheduledAlarm>): List<ScheduledAlarm> = withContext(Dispatchers.IO) {
        val dismissedAlarms = mutableListOf<ScheduledAlarm>()
        
        for (alarm in alarms) {
            // Only check non-dismissed, future alarms
            if (!alarm.userDismissed && !alarm.isInPast()) {
                if (!isAlarmActiveInSystem(alarm)) {
                    Log.d(TAG, "Detected dismissed alarm: ${alarm.eventTitle} (ID: ${alarm.id})")
                    dismissedAlarms.add(alarm)
                }
            }
        }
        
        Log.d(TAG, "Detected ${dismissedAlarms.size} dismissed alarms out of ${alarms.size} checked")
        return@withContext dismissedAlarms
    }
    
    /**
     * Monitor system alarm state and detect user dismissals
     * Returns a list of alarms that should be marked as dismissed in the database
     */
    suspend fun monitorSystemState(databaseAlarms: List<ScheduledAlarm>): SystemStateResult = withContext(Dispatchers.IO) {
        try {
            val activeAlarms = databaseAlarms.filter { !it.userDismissed && !it.isInPast() }
            val dismissedAlarms = detectDismissedAlarms(activeAlarms)
            
            return@withContext SystemStateResult(
                success = true,
                dismissedAlarms = dismissedAlarms,
                message = "System state monitored successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error monitoring system state", e)
            return@withContext SystemStateResult(
                success = false,
                dismissedAlarms = emptyList(),
                message = "Failed to monitor system state: ${e.message}"
            )
        }
    }
    
    data class SystemStateResult(
        val success: Boolean,
        val dismissedAlarms: List<ScheduledAlarm>,
        val message: String
    )
    
    /**
     * Schedule alarm with individual parameters (for retry logic)
     */
    suspend fun scheduleAlarm(
        eventId: String,
        ruleId: String,
        eventTitle: String,
        alarmTimeUtc: Long,
        requestCode: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (alarmTimeUtc <= System.currentTimeMillis()) {
                Log.w(TAG, "Cannot schedule alarm for past time")
                return@withContext false
            }
            
            // Use the standardized intent creation method
            val alarmId = "$eventId-$ruleId"
            val tempAlarm = ScheduledAlarm(
                id = alarmId,
                eventId = eventId,
                ruleId = ruleId,
                eventTitle = eventTitle,
                eventStartTimeUtc = alarmTimeUtc,
                alarmTimeUtc = alarmTimeUtc,
                scheduledAt = System.currentTimeMillis(),
                userDismissed = false,
                pendingIntentRequestCode = requestCode,
                lastEventModified = System.currentTimeMillis()
            )
            
            val intent = createAlarmIntent(tempAlarm)
            val pendingIntent = createPendingIntent(tempAlarm, intent)
            
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    Log.w(TAG, "Cannot schedule exact alarms without permission")
                    return@withContext false
                }
                else -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTimeUtc,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled alarm for $eventTitle at ${java.util.Date(alarmTimeUtc)}")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm", e)
            return@withContext false
        }
    }
    
    /**
     * Schedule a test alarm for manual testing
     */
    suspend fun scheduleTestAlarm(
        testEventTitle: String,
        testAlarmTime: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== SCHEDULING TEST ALARM ===")
            Log.i(TAG, "Test event title: '$testEventTitle'")
            Log.i(TAG, "Test alarm time: ${java.util.Date(testAlarmTime)} (UTC: $testAlarmTime)")
            Log.i(TAG, "Current time: ${java.util.Date(System.currentTimeMillis())} (UTC: ${System.currentTimeMillis()})")
            Log.i(TAG, "Time until alarm: ${(testAlarmTime - System.currentTimeMillis()) / 1000} seconds")
            
            // Check if time is in the past
            if (testAlarmTime <= System.currentTimeMillis()) {
                Log.e(TAG, "❌ Test alarm time is in the past!")
                return@withContext false
            }
            
            // Check permissions first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "❌ Cannot schedule test alarm without exact alarm permission")
                return@withContext false
            }
            
            val testRequestCode = (testAlarmTime % Int.MAX_VALUE).toInt()
            val testAlarmId = "test-${System.currentTimeMillis()}"
            
            Log.d(TAG, "Test alarm ID: $testAlarmId")
            Log.d(TAG, "Test request code: $testRequestCode")
            
            // Create standardized test alarm object
            val testAlarm = ScheduledAlarm(
                id = testAlarmId,
                eventId = "test_event",
                ruleId = "test_rule",
                eventTitle = testEventTitle,
                eventStartTimeUtc = testAlarmTime + 60_000, // Event "starts" 1 minute after alarm
                alarmTimeUtc = testAlarmTime,
                scheduledAt = System.currentTimeMillis(),
                userDismissed = false,
                pendingIntentRequestCode = testRequestCode,
                lastEventModified = System.currentTimeMillis()
            )
            
            // Add test alarm marker to intent
            val intent = createAlarmIntent(testAlarm).apply {
                putExtra("IS_TEST_ALARM", true)
                Log.d(TAG, "Test alarm intent action: $action")
                Log.d(TAG, "Test alarm intent component: $component")
                Log.d(TAG, "Test alarm intent package: $`package`")
            }
            
            val pendingIntent = createPendingIntent(testAlarm, intent)
            Log.d(TAG, "Test alarm PendingIntent created: $pendingIntent")
            
            // Schedule the alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                testAlarmTime,
                pendingIntent
            )
            
            Log.i(TAG, "✅ Test alarm scheduled successfully!")
            Log.i(TAG, "Alarm will trigger at: ${java.util.Date(testAlarmTime)}")
            
            // Verify the alarm was scheduled
            val isScheduled = isAlarmScheduled(testAlarm)
            Log.d(TAG, "Alarm verification - is scheduled in system: $isScheduled")
            
            if (!isScheduled) {
                Log.w(TAG, "⚠️ Warning: Alarm verification failed - alarm may not be properly scheduled")
            }
            
            Log.i(TAG, "=== TEST ALARM SCHEDULING COMPLETE ===")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling test alarm", e)
            return@withContext false
        }
    }
    
    /**
     * Internal method for scheduling alarms (used by retry logic)
     */
    private suspend fun scheduleAlarmInternal(alarm: ScheduledAlarm) {
        if (alarm.isInPast()) {
            throw IllegalArgumentException("Cannot schedule alarm for past time: ${alarm.getLocalAlarmTime()}")
        }
        
        val intent = createAlarmIntent(alarm)
        val pendingIntent = createPendingIntent(alarm, intent)
        
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                throw SecurityException("Exact alarm permission not granted")
            }
            else -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.alarmTimeUtc,
                    pendingIntent
                )
                
                Log.d(TAG, "Scheduled alarm for ${alarm.eventTitle} at ${alarm.getLocalAlarmTime()}")
            }
        }
    }
}