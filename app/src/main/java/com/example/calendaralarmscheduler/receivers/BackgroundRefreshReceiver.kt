package com.example.calendaralarmscheduler.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.domain.RuleAlarmManager
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BackgroundRefreshReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_PERIODIC_REFRESH = "com.example.calendaralarmscheduler.PERIODIC_REFRESH"
        const val ACTION_IMMEDIATE_REFRESH = "com.example.calendaralarmscheduler.IMMEDIATE_REFRESH"
        private const val TAG = "BackgroundRefreshReceiver"
    }
    
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PERIODIC_REFRESH -> {
                Logger.i(TAG, "Periodic calendar refresh triggered")
                performRefresh(context, isPeriodicRefresh = true)
            }
            ACTION_IMMEDIATE_REFRESH -> {
                Logger.i(TAG, "Immediate calendar refresh triggered")
                performRefresh(context, isPeriodicRefresh = false)
            }
            else -> {
                Logger.w(TAG, "Unknown action received: ${intent.action}")
            }
        }
    }
    
    private fun performRefresh(context: Context, isPeriodicRefresh: Boolean) {
        val result = goAsync()
        
        scope.launch {
            try {
                val startTime = System.currentTimeMillis()
                Logger.i(TAG, "Starting calendar refresh")
                
                val app = context.applicationContext as CalendarAlarmApplication
                val calendarRepository = app.calendarRepository
                val ruleRepository = app.ruleRepository
                val alarmRepository = app.alarmRepository
                val alarmScheduler = app.alarmScheduler
                val settingsRepository = app.settingsRepository
                val dayTrackingRepository = app.dayTrackingRepository
                val backgroundRefreshManager = app.backgroundRefreshManager
                val ruleAlarmManager = RuleAlarmManager(ruleRepository, alarmRepository, alarmScheduler, calendarRepository, dayTrackingRepository)
                
                // Get enabled rules
                val enabledRules = ruleRepository.getEnabledRules().first()
                if (enabledRules.isEmpty()) {
                    Logger.i(TAG, "No enabled rules found")
                    scheduleNextRefreshIfNeeded(backgroundRefreshManager, settingsRepository, isPeriodicRefresh)
                    return@launch
                }
                
                // Get calendar events
                val lastSyncTime = settingsRepository.getLastSyncTime()
                val events = calendarRepository.getUpcomingEvents(
                    lastSyncTime = if (lastSyncTime > 0) lastSyncTime else null
                )
                
                Logger.d(TAG, "Found ${events.size} events, ${enabledRules.size} rules")
                
                if (events.isEmpty()) {
                    settingsRepository.updateLastSyncTime()
                    scheduleNextRefreshIfNeeded(backgroundRefreshManager, settingsRepository, isPeriodicRefresh)
                    return@launch
                }
                
                // Find matches and schedule alarms
                val ruleMatcher = RuleMatcher(dayTrackingRepository)
                val matches = ruleMatcher.findMatchingRules(events, enabledRules)
                
                val refreshResult = ruleAlarmManager.processMatchesAndScheduleAlarms(matches, TAG)
                
                settingsRepository.updateLastSyncTime()
                
                val workTime = System.currentTimeMillis() - startTime
                Logger.i(TAG, "Completed in ${workTime}ms. Scheduled: ${refreshResult.scheduledCount}, Updated: ${refreshResult.updatedCount}")
                
                // Schedule next refresh if this was a periodic refresh
                scheduleNextRefreshIfNeeded(backgroundRefreshManager, settingsRepository, isPeriodicRefresh)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Calendar refresh failed", e)
                // On error, still schedule next refresh for periodic refreshes to maintain reliability
                try {
                    val app = context.applicationContext as CalendarAlarmApplication
                    scheduleNextRefreshIfNeeded(app.backgroundRefreshManager, app.settingsRepository, isPeriodicRefresh)
                } catch (scheduleException: Exception) {
                    Logger.e(TAG, "Failed to schedule next refresh after error", scheduleException)
                }
            } finally {
                result.finish()
            }
        }
    }
    
    private suspend fun scheduleNextRefreshIfNeeded(
        backgroundRefreshManager: com.example.calendaralarmscheduler.workers.BackgroundRefreshManager,
        settingsRepository: com.example.calendaralarmscheduler.data.SettingsRepository,
        isPeriodicRefresh: Boolean
    ) {
        if (isPeriodicRefresh) {
            try {
                val refreshInterval = settingsRepository.getRefreshIntervalMinutes()
                backgroundRefreshManager.scheduleNextPeriodicRefresh(refreshInterval)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to schedule next periodic refresh", e)
            }
        }
    }
}