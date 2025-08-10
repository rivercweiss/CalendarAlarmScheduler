package com.example.calendaralarmscheduler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.utils.AlarmNotificationManager
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmDismissReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_DISMISS_ALARM = "com.example.calendaralarmscheduler.DISMISS_ALARM"
        const val EXTRA_ALARM_ID = "ALARM_ID"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.i("AlarmDismissReceiver_onReceive", "=== ALARM DISMISS BROADCAST RECEIVED ===")
        Logger.i("AlarmDismissReceiver_onReceive", "Broadcast time: ${java.util.Date()}")
        Logger.i("AlarmDismissReceiver_onReceive", "Intent action: ${intent.action}")
        Logger.i("AlarmDismissReceiver_onReceive", "Intent extras: ${intent.extras}")
        
        try {
            // Validate the intent action
            if (intent.action != ACTION_DISMISS_ALARM) {
                Logger.w("AlarmDismissReceiver_onReceive", "Unexpected intent action: ${intent.action}, expected: $ACTION_DISMISS_ALARM")
                return
            }
            
            // Extract alarm ID
            val alarmId = intent.getStringExtra(EXTRA_ALARM_ID)
            if (alarmId == null) {
                Logger.e("AlarmDismissReceiver_onReceive", "❌ Alarm ID is null in dismiss intent")
                return
            }
            
            Logger.i("AlarmDismissReceiver_onReceive", "Processing dismiss for alarm ID: '$alarmId'")
            
            // Get application instance
            val application = context.applicationContext as CalendarAlarmApplication
            
            // Use goAsync() to handle async operations in BroadcastReceiver
            val pendingResult = goAsync()
            
            // Perform dismiss operations on IO dispatcher
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Logger.i("AlarmDismissReceiver_onReceive", "Starting async dismiss operations for alarm: $alarmId")
                    
                    // Mark alarm as dismissed in database
                    Logger.d("AlarmDismissReceiver_onReceive", "Marking alarm as dismissed in database")
                    application.alarmRepository.markAlarmDismissed(alarmId)
                    Logger.i("AlarmDismissReceiver_onReceive", "✅ Alarm marked as dismissed in database")
                    
                    // Dismiss the notification
                    Logger.d("AlarmDismissReceiver_onReceive", "Dismissing alarm notification")
                    val alarmNotificationManager = AlarmNotificationManager(context)
                    alarmNotificationManager.dismissAlarmNotification(alarmId)
                    Logger.i("AlarmDismissReceiver_onReceive", "✅ Alarm notification dismissed")
                    
                    Logger.i("AlarmDismissReceiver_onReceive", "🎯 Alarm dismiss completed successfully for alarm: $alarmId")
                    
                } catch (e: Exception) {
                    Logger.e("AlarmDismissReceiver_onReceive", "❌ Error during alarm dismiss operation", e)
                    Logger.e("AlarmDismissReceiver_onReceive", "Stack trace: ${e.stackTraceToString()}")
                } finally {
                    // Signal that the async work is complete
                    pendingResult.finish()
                    Logger.d("AlarmDismissReceiver_onReceive", "Async dismiss operation finished")
                }
            }
            
        } catch (e: Exception) {
            Logger.e("AlarmDismissReceiver_onReceive", "❌ Error processing alarm dismiss broadcast", e)
            Logger.e("AlarmDismissReceiver_onReceive", "Stack trace: ${e.stackTraceToString()}")
        } finally {
            Logger.i("AlarmDismissReceiver_onReceive", "=== ALARM DISMISS RECEIVER PROCESSING COMPLETE ===")
        }
    }
}