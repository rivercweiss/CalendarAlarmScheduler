package com.example.calendaralarmscheduler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Logger.i("BootReceiver_onReceive", "Device boot or app update detected, re-registering alarms")
                
                val result = goAsync()
                
                scope.launch {
                    try {
                        rescheduleAllAlarms(context)
                        
                        // Also schedule the periodic worker in case it's not running
                        schedulePeriodicWorker(context)
                        
                        Logger.i("BootReceiver_onReceive", "Alarm re-registration completed")
                        
                    } catch (e: Exception) {
                        Logger.e("BootReceiver_onReceive", "Error re-registering alarms after boot", e)
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }
    
    private suspend fun rescheduleAllAlarms(context: Context) {
        try {
            val app = context.applicationContext as CalendarAlarmApplication
            val alarmRepository = app.alarmRepository
            val alarmScheduler = app.alarmScheduler
            
            // Get all active alarms from database  
            val activeAlarms = alarmRepository.getActiveAlarmsSync()
            
            // Filter to only future, non-dismissed alarms
            val futureAlarms = activeAlarms.filter { alarm ->
                alarm.alarmTimeUtc > System.currentTimeMillis() && !alarm.userDismissed
            }
                
            Logger.i("BootReceiver_rescheduleAlarms", "Found ${futureAlarms.size} alarms to reschedule")
            
            if (futureAlarms.isNotEmpty()) {
                var successCount = 0
                var failureCount = 0
                
                for (alarm in futureAlarms) {
                    if (alarmScheduler.scheduleAlarm(alarm)) {
                        successCount++
                    } else {
                        failureCount++
                        Logger.w("BootReceiver_rescheduleAlarms", "Failed to reschedule alarm: ${alarm.eventTitle}")
                    }
                }
                
                Logger.i("BootReceiver_rescheduleAlarms", 
                    "Rescheduled $successCount alarms successfully, $failureCount failed")
            }
            
        } catch (e: Exception) {
            Logger.e("BootReceiver_rescheduleAlarms", "Error during alarm rescheduling", e)
            throw e
        }
    }
    
    private fun schedulePeriodicWorker(context: Context) {
        try {
            // Schedule calendar refresh worker to start in 1 minute
            // This ensures the device has fully booted before starting background work
            val refreshWorkRequest = OneTimeWorkRequestBuilder<com.example.calendaralarmscheduler.workers.CalendarRefreshWorker>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context).enqueue(refreshWorkRequest)
            
            Logger.d("BootReceiver_scheduleWorker", "Scheduled calendar refresh worker to start in 1 minute")
            
        } catch (e: Exception) {
            Logger.e("BootReceiver_scheduleWorker", "Error scheduling periodic worker", e)
        }
    }
}