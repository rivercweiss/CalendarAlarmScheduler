package com.example.calendaralarmscheduler.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.calendaralarmscheduler.utils.Logger

/**
 * Invisible full-screen alarm activity that provides technical benefits
 * (DND bypass, wake device, audio priority) without showing any UI.
 * User interacts only with the persistent notification.
 */
class AlarmActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_EVENT_TITLE = "EVENT_TITLE"
        const val EXTRA_IS_TEST_ALARM = "IS_TEST_ALARM"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Extract alarm data for logging only
            val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: "unknown"
            val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Calendar Event"
            val isTestAlarm = intent.getBooleanExtra(EXTRA_IS_TEST_ALARM, false)
            
            Logger.i("AlarmActivity", "🔔 Invisible full-screen alarm triggered: $eventTitle (test: $isTestAlarm, id: $alarmId)")
            
            // Immediately finish - no UI displayed
            // Technical benefits (DND bypass, wake device, audio priority) still apply
            finish()
            
        } catch (e: Exception) {
            Logger.e("AlarmActivity", "❌ Error in invisible alarm activity", e)
            finish()
        }
    }
}