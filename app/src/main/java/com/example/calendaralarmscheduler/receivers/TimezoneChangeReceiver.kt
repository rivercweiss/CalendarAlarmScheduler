package com.example.calendaralarmscheduler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.ZoneId

class TimezoneChangeReceiver : BroadcastReceiver() {
    
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED -> {
                Logger.i("TimezoneChangeReceiver", "Timezone change detected")
                handleTimezoneChange(context)
            }
            Intent.ACTION_TIME_CHANGED -> {
                Logger.i("TimezoneChangeReceiver", "System time change detected")
                handleTimeChange(context)
            }
        }
    }
    
    private fun handleTimezoneChange(context: Context) {
        val result = goAsync()
        
        scope.launch {
            try {
                val currentZone = ZoneId.systemDefault()
                Logger.i("TimezoneChangeReceiver", "Handling timezone change to: ${currentZone.id}")
                
                val app = context.applicationContext as CalendarAlarmApplication
                
                // Reset last sync time to force full calendar rescan
                app.settingsRepository.handleTimezoneChange()
                
                // Reset day tracking due to timezone change (day boundaries change)
                app.dayTrackingRepository.handleTimezoneChange()
                
                // Reschedule midnight reset alarm for new timezone
                app.dayResetService.handleTimezoneChange()
                
                // Reschedule all active alarms with new timezone
                rescheduleAllAlarms(context)
                
                // Trigger immediate calendar refresh to catch any new events
                app.backgroundRefreshManager.enqueueImmediateRefresh()
                
                Logger.i("TimezoneChangeReceiver", "Timezone change handling completed for zone: ${currentZone.id}")
                
            } catch (e: Exception) {
                Logger.e("TimezoneChangeReceiver", "Error handling timezone change", e)
            } finally {
                result.finish()
            }
        }
    }
    
    private fun handleTimeChange(context: Context) {
        val result = goAsync()
        
        scope.launch {
            try {
                Logger.i("TimezoneChangeReceiver", "Handling system time change")
                
                val app = context.applicationContext as CalendarAlarmApplication
                
                // Time changes can affect alarm scheduling, especially around DST transitions
                // Reschedule all alarms to ensure they fire at the correct adjusted times
                rescheduleAllAlarms(context)
                
                // Trigger calendar refresh to handle any date-boundary crossings
                app.backgroundRefreshManager.enqueueImmediateRefresh()
                
                Logger.i("TimezoneChangeReceiver", "System time change handling completed")
                
            } catch (e: Exception) {
                Logger.e("TimezoneChangeReceiver", "Error handling time change", e)
            } finally {
                result.finish()
            }
        }
    }
    
    private suspend fun rescheduleAllAlarms(context: Context) {
        try {
            val app = context.applicationContext as CalendarAlarmApplication
            val alarmRepository = app.alarmRepository
            val alarmScheduler = app.alarmScheduler
            
            // Get all active alarms from database
            val activeAlarms = alarmRepository.getActiveAlarms()
            
            activeAlarms.collect { alarms ->
                // Filter to only future, non-dismissed alarms
                val futureAlarms = alarms.filter { !it.userDismissed && it.alarmTimeUtc > System.currentTimeMillis() }
                
                Logger.i("TimezoneChangeReceiver", "Found ${futureAlarms.size} alarms to reschedule after timezone/time change")
                
                var successCount = 0
                var failureCount = 0
                
                // Cancel and reschedule each alarm
                futureAlarms.forEach { alarm ->
                    // Cancel existing alarm
                    alarmScheduler.cancelAlarm(alarm)
                    
                    // Reschedule alarm
                    val success = alarmScheduler.scheduleAlarm(alarm)
                    if (success) {
                        successCount++
                    } else {
                        failureCount++
                    }
                }
                
                Logger.i("TimezoneChangeReceiver", 
                    "Rescheduled $successCount alarms successfully, $failureCount failed after timezone change")
                
                return@collect // Exit the collect block after processing
            }
            
        } catch (e: Exception) {
            Logger.e("TimezoneChangeReceiver", "Error rescheduling alarms after timezone change", e)
            throw e
        }
    }
}