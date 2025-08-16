package com.example.calendaralarmscheduler.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.calendaralarmscheduler.R
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.utils.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen alarm activity that shows on lock screen for unmissable alarms.
 * Reuses existing dismiss flow via AlarmDismissReceiver broadcast.
 */
@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    companion object {
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_EVENT_TITLE = "EVENT_TITLE"
        const val EXTRA_IS_TEST_ALARM = "IS_TEST_ALARM"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.i("AlarmActivity", "=== FULL-SCREEN ALARM ACTIVITY LAUNCHED ===")
        
        try {
            // Extract alarm data from intent
            val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
            val eventTitle = intent.getStringExtra(EXTRA_EVENT_TITLE) ?: "Calendar Event"
            val isTestAlarm = intent.getBooleanExtra(EXTRA_IS_TEST_ALARM, false)
            
            if (alarmId == null) {
                Logger.e("AlarmActivity", "❌ No alarm ID provided, finishing activity")
                finish()
                return
            }
            
            Logger.i("AlarmActivity", "Showing alarm: $eventTitle (test: $isTestAlarm)")
            
            // Create simple layout programmatically for minimal dependencies
            createSimpleAlarmUI(alarmId, eventTitle, isTestAlarm)
            
        } catch (e: Exception) {
            Logger.e("AlarmActivity", "❌ Error creating alarm activity", e)
            finish()
        }
    }
    
    private fun createSimpleAlarmUI(alarmId: String, eventTitle: String, isTestAlarm: Boolean) {
        // Create vertical linear layout programmatically to avoid XML dependencies
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        // Title
        val titleText = TextView(this).apply {
            text = if (isTestAlarm) "📅 Test Alarm" else "📅 Calendar Alarm"
            textSize = 28f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        
        // Event details (with premium gating same as notification)
        val isPremium = settingsRepository.isPremiumPurchased()
        val displayTitle = if (isPremium || isTestAlarm) eventTitle else "Calendar Event"
        val displayMessage = when {
            isTestAlarm -> "Test alarm for: $eventTitle"
            isPremium -> "Calendar event: $eventTitle"
            else -> "Calendar event alarm. Upgrade to see event details."
        }
        
        val eventText = TextView(this).apply {
            text = displayMessage
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 64)
        }
        
        // Large dismiss button
        val dismissButton = Button(this).apply {
            text = "DISMISS ALARM"
            textSize = 20f
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                dismissAlarm(alarmId)
            }
        }
        
        // Add views to layout
        layout.addView(titleText)
        layout.addView(eventText)
        layout.addView(dismissButton)
        
        setContentView(layout)
        
        Logger.i("AlarmActivity", "✅ Alarm UI created successfully")
    }
    
    private fun dismissAlarm(alarmId: String) {
        try {
            Logger.i("AlarmActivity", "User dismissed alarm: $alarmId")
            
            // Send same broadcast as notification dismiss button
            val dismissIntent = Intent("com.example.calendaralarmscheduler.DISMISS_ALARM").apply {
                setPackage(packageName)
                putExtra("ALARM_ID", alarmId)
            }
            
            sendBroadcast(dismissIntent)
            Logger.i("AlarmActivity", "✅ Dismiss broadcast sent")
            
            // Close the activity
            finish()
            
        } catch (e: Exception) {
            Logger.e("AlarmActivity", "❌ Error dismissing alarm", e)
            finish()
        }
    }
    
    override fun onBackPressed() {
        // Treat back button as dismiss for alarms
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
        if (alarmId != null) {
            dismissAlarm(alarmId)
        } else {
            super.onBackPressed()
        }
    }
}