package com.example.calendaralarmscheduler.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Core alarm scheduling service using Android AlarmManager.
 * Handles scheduling, canceling, and rescheduling of exact alarms.
 */
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
    
    suspend fun scheduleAlarm(alarm: ScheduledAlarm): Boolean = withContext(Dispatchers.IO) {
        try {
            if (alarm.isInPast()) {
                Log.w(TAG, "Cannot schedule alarm for past time: ${alarm.eventTitle}")
                return@withContext false
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission not granted")
                return@withContext false
            }
            
            val intent = createAlarmIntent(alarm)
            val pendingIntent = createPendingIntent(alarm, intent)
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarm.alarmTimeUtc,
                pendingIntent
            )
            
            Log.d(TAG, "Scheduled alarm for ${alarm.eventTitle} at ${alarm.getLocalAlarmTime()}")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for ${alarm.eventTitle}", e)
            return@withContext false
        }
    }
    
    suspend fun cancelAlarm(alarm: ScheduledAlarm): Boolean = withContext(Dispatchers.IO) {
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
            }
            
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm for ${alarm.eventTitle}", e)
            return@withContext false
        }
    }
    
    suspend fun rescheduleAlarm(oldAlarm: ScheduledAlarm, newAlarm: ScheduledAlarm): Boolean = withContext(Dispatchers.IO) {
        cancelAlarm(oldAlarm)
        return@withContext scheduleAlarm(newAlarm)
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
}