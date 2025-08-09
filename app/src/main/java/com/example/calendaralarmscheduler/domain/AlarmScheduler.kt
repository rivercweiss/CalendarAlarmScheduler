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
            
            val intent = createAlarmIntent(alarm)
            val pendingIntent = createPendingIntent(alarm, intent)
            
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    return@withContext ScheduleResult(
                        success = false,
                        message = "Exact alarm permission not granted"
                    )
                }
                else -> {
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
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for ${alarm.eventTitle}", e)
            return@withContext ScheduleResult(
                success = false,
                message = "Failed to schedule alarm: ${e.message}"
            )
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
    
    fun getNextAlarmTime(alarms: List<ScheduledAlarm>): Long? {
        return alarms
            .filter { it.isActive() }
            .minByOrNull { it.alarmTimeUtc }
            ?.alarmTimeUtc
    }
}