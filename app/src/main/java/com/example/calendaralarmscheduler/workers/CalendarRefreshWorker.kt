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
    
    
    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        Logger.i("CalendarRefreshWorker_doWork", "Starting calendar refresh background work")
        
        return try {
            val app = applicationContext as CalendarAlarmApplication
            val calendarRepository = app.calendarRepository
            val ruleRepository = app.ruleRepository
            val alarmRepository = app.alarmRepository
            val alarmScheduler = app.alarmScheduler
            val settingsRepository = app.settingsRepository
            val ruleMatcher = RuleMatcher()
            val alarmSchedulingService = AlarmSchedulingService(alarmRepository, alarmScheduler)
            val errorNotificationManager = com.example.calendaralarmscheduler.utils.ErrorNotificationManager(applicationContext)
            
            // Get last sync time for efficient change detection
            val lastSyncTime = settingsRepository.getLastSyncTime()
            Logger.d("CalendarRefreshWorker_doWork", "Last sync time: $lastSyncTime")
            
            // Get all enabled rules
            val enabledRules = ruleRepository.getEnabledRules().first()
            if (enabledRules.isEmpty()) {
                Logger.i("CalendarRefreshWorker_doWork", "No enabled rules found, skipping calendar refresh")
                return Result.success()
            }
            
            Logger.d("CalendarRefreshWorker_doWork", "Found ${enabledRules.size} enabled rules")
            
            // Get calendar events with LAST_MODIFIED > lastSyncTime for efficient change detection
            val events = try {
                calendarRepository.getUpcomingEvents(
                    calendarIds = null, // Get from all calendars, will be filtered by rules
                    lastSyncTime = if (lastSyncTime > 0) lastSyncTime else null
                )
            } catch (e: Exception) {
                Logger.e("CalendarRefreshWorker_doWork", "Critical failure retrieving calendar events", e)
                errorNotificationManager.showCalendarProviderError()
                return Result.retry()
            }
            
            Logger.d("CalendarRefreshWorker_doWork", "Retrieved ${events.size} events (modified since last sync)")
            
            if (events.isEmpty()) {
                Logger.i("CalendarRefreshWorker_doWork", "No new or modified events found")
                settingsRepository.updateLastSyncTime()
                errorNotificationManager.clearErrorNotifications()
                return Result.success()
            }
            
            // Find matching events and rules
            val matchResults = ruleMatcher.findMatchingRules(events, enabledRules)
            Logger.d("CalendarRefreshWorker_doWork", "Found ${matchResults.size} rule matches")
            
            if (matchResults.isEmpty()) {
                Logger.i("CalendarRefreshWorker_doWork", "No matching events found for enabled rules")
                settingsRepository.updateLastSyncTime()
                return Result.success()
            }
            
            // Get existing alarms to check for duplicates and dismissed alarms
            val existingAlarmsDb = alarmRepository.getAllAlarms().first()
            
            // Convert database entities to domain models for filtering
            val existingAlarms = existingAlarmsDb.map { dbAlarm ->
                com.example.calendaralarmscheduler.domain.models.ScheduledAlarm(
                    id = dbAlarm.id,
                    eventId = dbAlarm.eventId,
                    ruleId = dbAlarm.ruleId,
                    eventTitle = dbAlarm.eventTitle,
                    eventStartTimeUtc = dbAlarm.eventStartTimeUtc,
                    alarmTimeUtc = dbAlarm.alarmTimeUtc,
                    scheduledAt = dbAlarm.scheduledAt,
                    userDismissed = dbAlarm.userDismissed,
                    pendingIntentRequestCode = dbAlarm.pendingIntentRequestCode,
                    lastEventModified = dbAlarm.lastEventModified
                )
            }
            
            // Detect manually dismissed alarms by comparing database state vs system state
            Logger.d("CalendarRefreshWorker_doWork", "Checking for manually dismissed alarms...")
            val systemStateResult = alarmScheduler.monitorSystemState(existingAlarms)
            
            if (systemStateResult.success && systemStateResult.dismissedAlarms.isNotEmpty()) {
                Logger.i("CalendarRefreshWorker_doWork", "Detected ${systemStateResult.dismissedAlarms.size} manually dismissed alarms")
                
                // Mark dismissed alarms in database
                alarmRepository.handleDismissedAlarms(systemStateResult.dismissedAlarms)
                
                // Log dismissed alarms for debugging
                systemStateResult.dismissedAlarms.forEach { dismissedAlarm ->
                    Logger.d("CalendarRefreshWorker_doWork", "Marked alarm as dismissed: ${dismissedAlarm.eventTitle} (ID: ${dismissedAlarm.id})")
                }
            }
            
            // Filter out user-dismissed alarms (unless event was modified)
            val filteredMatches = ruleMatcher.filterOutDismissedAlarms(matchResults, existingAlarms)
            Logger.d("CalendarRefreshWorker_doWork", "After filtering dismissed alarms: ${filteredMatches.size} matches")
            
            // Use all filtered matches (no conflict resolution - create alarms for all matching rules)
            val resolvedMatches = filteredMatches
            Logger.d("CalendarRefreshWorker_doWork", "Ready to schedule alarms for all matching rules: ${resolvedMatches.size} matches")
            
            // Use the shared scheduling service to process all matches
            val schedulingResult = alarmSchedulingService.processMatchesAndScheduleAlarms(
                resolvedMatches,
                logPrefix = "CalendarRefreshWorker"
            )
            
            val scheduledCount = schedulingResult.scheduledCount
            val updatedCount = schedulingResult.updatedCount
            val skippedCount = schedulingResult.skippedCount
            val failedCount = schedulingResult.failedCount
            val failedEvents = schedulingResult.failedEvents
            
            // Update last sync time
            settingsRepository.updateLastSyncTime()
            
            // Handle error notifications for persistent failures
            if (failedCount > 0) {
                Logger.w("CalendarRefreshWorker_doWork", "Failed to process $failedCount alarms: ${failedEvents.joinToString(", ")}")
                
                // Show notification for the first failed event as an example
                if (failedEvents.isNotEmpty()) {
                    errorNotificationManager.showAlarmSchedulingError(failedEvents.first())
                }
            } else {
                // Clear error notifications if everything succeeded
                errorNotificationManager.clearErrorNotifications()
            }
            
            // Perform proactive alarm health monitoring - check for any missing alarms and auto-repair
            Logger.d("CalendarRefreshWorker_doWork", "Starting proactive alarm health check...")
            val healthCheckResult = performAlarmHealthCheck(alarmRepository, alarmScheduler)
            if (healthCheckResult.rescheduledCount > 0) {
                Logger.i("CalendarRefreshWorker_doWork", "Alarm health check: automatically rescheduled ${healthCheckResult.rescheduledCount} missing alarms")
            }
            if (healthCheckResult.failedCount > 0) {
                Logger.w("CalendarRefreshWorker_doWork", "Alarm health check: failed to reschedule ${healthCheckResult.failedCount} alarms")
            }
            
            val workTime = System.currentTimeMillis() - startTime
            Logger.logPerformance("CalendarRefreshWorker", "doWork()", workTime)
            Logger.i("CalendarRefreshWorker_doWork", 
                "Calendar refresh completed in ${workTime}ms. " +
                "Scheduled: $scheduledCount, Updated: $updatedCount, Skipped: $skippedCount, Failed: $failedCount")
            
            Result.success()
            
        } catch (e: Exception) {
            val workTime = System.currentTimeMillis() - startTime
            Logger.e("CalendarRefreshWorker_doWork", "Calendar refresh work failed after ${workTime}ms", e)
            Result.retry()
        }
    }
    
    /**
     * Proactive alarm health monitoring and repair
     */
    private suspend fun performAlarmHealthCheck(
        alarmRepository: com.example.calendaralarmscheduler.data.AlarmRepository,
        alarmScheduler: com.example.calendaralarmscheduler.domain.AlarmScheduler
    ): AlarmHealthCheckResult {
        return try {
            val activeAlarms = alarmRepository.getActiveAlarmsSync()
            var rescheduledCount = 0
            var failedCount = 0
            
            Logger.d("CalendarRefreshWorker_healthCheck", "Checking ${activeAlarms.size} active alarms")
            
            for (alarm in activeAlarms) {
                if (!alarm.userDismissed && alarm.alarmTimeUtc > System.currentTimeMillis()) {
                    // Check if alarm exists in system
                    val intent = android.content.Intent(applicationContext, com.example.calendaralarmscheduler.receivers.AlarmReceiver::class.java)
                    intent.putExtra("ALARM_ID", alarm.id)
                    intent.putExtra("EVENT_TITLE", alarm.eventTitle)
                    intent.putExtra("RULE_ID", alarm.ruleId)
                    
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        applicationContext,
                        alarm.pendingIntentRequestCode,
                        intent,
                        android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    if (pendingIntent == null) {
                        // Alarm is missing from system - attempt to reschedule
                        Logger.w("CalendarRefreshWorker_healthCheck", "Missing alarm detected for ${alarm.eventTitle} - attempting automatic reschedule")
                        
                        try {
                            val domainAlarm = com.example.calendaralarmscheduler.domain.models.ScheduledAlarm(
                                id = alarm.id,
                                eventId = alarm.eventId,
                                ruleId = alarm.ruleId,
                                eventTitle = alarm.eventTitle,
                                eventStartTimeUtc = alarm.eventStartTimeUtc,
                                alarmTimeUtc = alarm.alarmTimeUtc,
                                scheduledAt = alarm.scheduledAt,
                                userDismissed = alarm.userDismissed,
                                pendingIntentRequestCode = alarm.pendingIntentRequestCode,
                                lastEventModified = alarm.lastEventModified
                            )
                            
                            val result = alarmScheduler.scheduleAlarm(domainAlarm)
                            if (result.success) {
                                rescheduledCount++
                                Logger.d("CalendarRefreshWorker_healthCheck", "Successfully rescheduled missing alarm: ${alarm.eventTitle}")
                            } else {
                                failedCount++
                                Logger.e("CalendarRefreshWorker_healthCheck", "Failed to reschedule missing alarm ${alarm.eventTitle}: ${result.message}")
                            }
                        } catch (e: Exception) {
                            failedCount++
                            Logger.e("CalendarRefreshWorker_healthCheck", "Exception while rescheduling missing alarm ${alarm.eventTitle}", e)
                        }
                    }
                }
            }
            
            AlarmHealthCheckResult(rescheduledCount, failedCount)
        } catch (e: Exception) {
            Logger.e("CalendarRefreshWorker_healthCheck", "Error during alarm health check", e)
            AlarmHealthCheckResult(0, 0)
        }
    }
    
    private data class AlarmHealthCheckResult(
        val rescheduledCount: Int,
        val failedCount: Int
    )
}