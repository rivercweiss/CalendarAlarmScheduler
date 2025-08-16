package com.example.calendaralarmscheduler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver that handles midnight reset alarms to clear day tracking for "first event of day only" rules.
 */
class DayResetReceiver : BroadcastReceiver() {
    
    companion object {
        private const val LOG_TAG = "DayResetReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Logger.i(LOG_TAG, "Received day reset broadcast")
        
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as CalendarAlarmApplication
                val dayResetService = application.dayResetService
                
                // Perform the day reset
                dayResetService.performDayReset()
                
                Logger.i(LOG_TAG, "Day reset completed successfully")
            } catch (e: Exception) {
                Logger.e(LOG_TAG, "Failed to perform day reset", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}