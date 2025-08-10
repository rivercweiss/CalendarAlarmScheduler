package com.example.calendaralarmscheduler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.ui.alarm.AlarmActivity
import com.example.calendaralarmscheduler.utils.Logger

class AlarmReceiver : BroadcastReceiver() {
    
    companion object {
        const val EXTRA_ALARM_ID = AlarmScheduler.EXTRA_ALARM_ID
        const val EXTRA_EVENT_TITLE = AlarmScheduler.EXTRA_EVENT_TITLE
        const val EXTRA_EVENT_START_TIME = AlarmScheduler.EXTRA_EVENT_START_TIME
        const val EXTRA_RULE_ID = AlarmScheduler.EXTRA_RULE_ID
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.d("AlarmReceiver_onReceive", "Alarm broadcast received")
        
        try {
            val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
            val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Unknown Event"
            val eventStartTime = intent.getLongExtra(EXTRA_EVENT_START_TIME, 0L)
            val ruleId = intent.getStringExtra(EXTRA_RULE_ID) ?: "Unknown Rule"
            
            if (alarmId == null) {
                Logger.e("AlarmReceiver_onReceive", "Alarm ID is null, cannot process alarm")
                return
            }
            
            Logger.i("AlarmReceiver_onReceive", "Processing alarm: ID=$alarmId, Event='$eventTitle', RuleID='$ruleId'")
            
            // Create intent to launch AlarmActivity
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_EVENT_TITLE, eventTitle)
                putExtra(EXTRA_EVENT_START_TIME, eventStartTime)
                putExtra(EXTRA_RULE_ID, ruleId)
            }
            
            // Launch alarm activity
            ContextCompat.startActivity(context, alarmIntent, null)
            
            Logger.i("AlarmReceiver_onReceive", "Successfully launched AlarmActivity for alarm $alarmId")
            
        } catch (e: Exception) {
            Logger.e("AlarmReceiver_onReceive", "Error processing alarm broadcast", e)
        }
    }
}