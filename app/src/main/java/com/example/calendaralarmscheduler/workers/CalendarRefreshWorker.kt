package com.example.calendaralarmscheduler.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.domain.AlarmSchedulingService
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.flow.first

class CalendarRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    
    companion object {
        private const val TAG = "CalendarRefreshWorker"
    }
    
    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        Logger.i(TAG, "Starting calendar refresh")
        
        return try {
            val app = applicationContext as CalendarAlarmApplication
            val calendarRepository = app.calendarRepository
            val ruleRepository = app.ruleRepository
            val alarmRepository = app.alarmRepository
            val alarmScheduler = app.alarmScheduler
            val settingsRepository = app.settingsRepository
            val alarmSchedulingService = AlarmSchedulingService(alarmRepository, alarmScheduler)
            
            // Get enabled rules
            val enabledRules = ruleRepository.getEnabledRules().first()
            if (enabledRules.isEmpty()) {
                Logger.i(TAG, "No enabled rules found")
                return Result.success()
            }
            
            // Get calendar events
            val lastSyncTime = settingsRepository.getLastSyncTime()
            val events = calendarRepository.getUpcomingEvents(
                lastSyncTime = if (lastSyncTime > 0) lastSyncTime else null
            )
            
            Logger.d(TAG, "Found ${events.size} events, ${enabledRules.size} rules")
            
            if (events.isEmpty()) {
                settingsRepository.updateLastSyncTime()
                return Result.success()
            }
            
            // Find matches and schedule alarms
            val ruleMatcher = RuleMatcher()
            val matches = ruleMatcher.findMatchingRules(events, enabledRules)
            
            val result = alarmSchedulingService.processMatchesAndScheduleAlarms(matches, TAG)
            
            settingsRepository.updateLastSyncTime()
            
            val workTime = System.currentTimeMillis() - startTime
            Logger.i(TAG, "Completed in ${workTime}ms. Scheduled: ${result.scheduledCount}, Updated: ${result.updatedCount}")
            
            Result.success()
            
        } catch (e: Exception) {
            Logger.e(TAG, "Calendar refresh failed", e)
            Result.retry()
        }
    }
}