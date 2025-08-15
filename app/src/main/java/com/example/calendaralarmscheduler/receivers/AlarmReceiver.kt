package com.example.calendaralarmscheduler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.utils.AlarmNotificationManager
import com.example.calendaralarmscheduler.utils.Logger

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        const val EXTRA_ALARM_ID = AlarmScheduler.EXTRA_ALARM_ID
        const val EXTRA_EVENT_TITLE = AlarmScheduler.EXTRA_EVENT_TITLE
        const val EXTRA_EVENT_START_TIME = AlarmScheduler.EXTRA_EVENT_START_TIME
        const val EXTRA_RULE_ID = AlarmScheduler.EXTRA_RULE_ID
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.i("AlarmReceiver", "=== ALARM RECEIVED ===")
        
        try {
            val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
            val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Unknown Event"
            val eventStartTime = intent.getLongExtra(EXTRA_EVENT_START_TIME, 0L)
            val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: "Unknown Rule"
            val isTestAlarm = intent.getBooleanExtra("IS_TEST_ALARM", false)
            
            Logger.i("AlarmReceiver", "Alarm ID: '$alarmId', Event: '$eventTitle', Test: $isTestAlarm")
            
            if (alarmId == null) {
                Logger.e("AlarmReceiver", "❌ Alarm ID is null, cannot process alarm")
                return
            }
            
            // Show unmissable notification
            val alarmNotificationManager = AlarmNotificationManager(context)
            alarmNotificationManager.showAlarmNotification(
                alarmId = alarmId,
                eventTitle = eventTitle,
                eventStartTime = eventStartTime,
                ruleId = ruleId,
                isTestAlarm = isTestAlarm
            )
            
            Logger.i("AlarmReceiver", "✅ Unmissable alarm notification triggered for: $eventTitle")
            
        } catch (e: Exception) {
            Logger.e("AlarmReceiver", "❌ Error processing alarm", e)
        }
    }
}