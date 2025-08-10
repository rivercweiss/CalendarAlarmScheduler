package com.example.calendaralarmscheduler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.ui.alarm.AlarmActivity
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
        Logger.i("AlarmReceiver_onReceive", "=== ALARM BROADCAST RECEIVED ===")
        Logger.i("AlarmReceiver_onReceive", "Broadcast time: ${java.util.Date()}")
        Logger.i("AlarmReceiver_onReceive", "Intent action: ${intent.action}")
        Logger.i("AlarmReceiver_onReceive", "Intent component: ${intent.component}")
        Logger.i("AlarmReceiver_onReceive", "Intent package: ${intent.`package`}")
        Logger.i("AlarmReceiver_onReceive", "Intent extras: ${intent.extras}")
        
        try {
            // Log all extras for debugging
            intent.extras?.let { extras ->
                for (key in extras.keySet()) {
                    // Use type-safe approach for debugging - try common types
                    val value = when {
                        extras.containsKey(key) -> {
                            try {
                                extras.getString(key) ?: extras.getLong(key, -1).takeIf { it != -1L } 
                                    ?: extras.getBoolean(key, false).takeIf { it }
                                    ?: "unknown type"
                            } catch (e: Exception) {
                                "error reading value"
                            }
                        }
                        else -> null
                    }
                    Logger.d("AlarmReceiver_onReceive", "Extra: $key = $value")
                }
            }
            
            val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
            val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Unknown Event"
            val eventStartTime = intent.getLongExtra(EXTRA_EVENT_START_TIME, 0L)
            val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: "Unknown Rule"
            val isTestAlarm = intent.getBooleanExtra("IS_TEST_ALARM", false)
            
            Logger.i("AlarmReceiver_onReceive", "Alarm ID: '$alarmId'")
            Logger.i("AlarmReceiver_onReceive", "Event Title: '$eventTitle'")
            Logger.i("AlarmReceiver_onReceive", "Event Start Time: ${if (eventStartTime > 0) java.util.Date(eventStartTime) else "Not set"} (UTC: $eventStartTime)")
            Logger.i("AlarmReceiver_onReceive", "Rule ID: '$ruleId'")
            Logger.i("AlarmReceiver_onReceive", "Is Test Alarm: $isTestAlarm")
            
            if (alarmId == null) {
                Logger.e("AlarmReceiver_onReceive", "❌ Alarm ID is null, cannot process alarm")
                return
            }
            
            if (isTestAlarm) {
                Logger.i("AlarmReceiver_onReceive", "🧪 Processing TEST ALARM")
            }
            
            Logger.i("AlarmReceiver_onReceive", "Creating full-screen alarm notification to bypass BAL restrictions...")
            
            // Use AlarmNotificationManager to show full-screen intent notification
            // This bypasses Android's Background Activity Launch restrictions
            val alarmNotificationManager = AlarmNotificationManager(context)
            
            Logger.i("AlarmReceiver_onReceive", "Showing full-screen alarm notification...")
            alarmNotificationManager.showAlarmNotification(
                alarmId = alarmId,
                eventTitle = eventTitle,
                eventStartTime = eventStartTime,
                ruleId = ruleId,
                isTestAlarm = isTestAlarm
            )
            
            Logger.i("AlarmReceiver_onReceive", "✅ Full-screen alarm notification triggered for alarm $alarmId")
            Logger.i("AlarmReceiver_onReceive", "📱 Notification will launch AlarmActivity and bypass BAL restrictions")
            
        } catch (e: Exception) {
            Logger.e("AlarmReceiver_onReceive", "❌ Error processing alarm broadcast", e)
            Logger.e("AlarmReceiver_onReceive", "Stack trace: ${e.stackTraceToString()}")
        } finally {
            Logger.i("AlarmReceiver_onReceive", "=== ALARM RECEIVER PROCESSING COMPLETE ===")
        }
    }
}