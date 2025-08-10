package com.example.calendaralarmscheduler.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.calendaralarmscheduler.CalendarAlarmApplication
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
            
            // Resolve conflicts based on user preference for duplicate handling
            val duplicateHandlingMode = settingsRepository.duplicateHandlingMode.value
            val resolvedMatches = ruleMatcher.resolveConflicts(filteredMatches, duplicateHandlingMode)
            Logger.d("CalendarRefreshWorker_doWork", "After conflict resolution (${duplicateHandlingMode.displayName}): ${resolvedMatches.size} matches")
            
            var scheduledCount = 0
            var updatedCount = 0
            var skippedCount = 0
            var failedCount = 0
            val failedEvents = mutableListOf<String>()
            
            // Process each match
            for (match in resolvedMatches) {
                val event = match.event
                val rule = match.rule
                val newAlarm = match.scheduledAlarm
                
                // Check if alarm already exists for this event/rule combination
                val existingAlarm = alarmRepository.getAlarmByEventAndRule(event.id, rule.id)
                
                if (existingAlarm != null) {
                    // Check if event was modified - this resets dismissal status for modified events
                    val eventWasModified = event.lastModified > existingAlarm.lastEventModified
                    val alarmWasDismissed = existingAlarm.userDismissed
                    
                    if (eventWasModified) {
                        Logger.d("CalendarRefreshWorker_doWork", "Event modified: ${event.title}, lastModified: ${event.lastModified} > ${existingAlarm.lastEventModified}")
                        
                        // If event was modified, reset dismissal status (treat as new event)
                        if (alarmWasDismissed) {
                            Logger.i("CalendarRefreshWorker_doWork", "Resetting dismissal status for modified event: ${event.title}")
                        }
                        
                        // Convert existing database alarm to domain model for AlarmScheduler
                        val existingDomainAlarm = com.example.calendaralarmscheduler.domain.models.ScheduledAlarm(
                            id = existingAlarm.id,
                            eventId = existingAlarm.eventId,
                            ruleId = existingAlarm.ruleId,
                            eventTitle = existingAlarm.eventTitle,
                            eventStartTimeUtc = existingAlarm.eventStartTimeUtc,
                            alarmTimeUtc = existingAlarm.alarmTimeUtc,
                            scheduledAt = existingAlarm.scheduledAt,
                            userDismissed = existingAlarm.userDismissed,
                            pendingIntentRequestCode = existingAlarm.pendingIntentRequestCode,
                            lastEventModified = existingAlarm.lastEventModified
                        )
                        
                        // Cancel old alarm and schedule new one
                        val cancelResult = alarmScheduler.cancelAlarm(existingDomainAlarm)
                        if (cancelResult.success) {
                            val scheduleResult = alarmScheduler.scheduleAlarm(newAlarm)
                            if (scheduleResult.success) {
                                // Update in database and reset dismissal status
                                alarmRepository.updateAlarmForChangedEvent(
                                    eventId = event.id,
                                    ruleId = rule.id,
                                    eventTitle = event.title,
                                    eventStartTimeUtc = event.startTimeUtc,
                                    leadTimeMinutes = rule.leadTimeMinutes,
                                    lastEventModified = event.lastModified
                                )
                                
                                // Reset dismissal status for modified events
                                if (alarmWasDismissed) {
                                    alarmRepository.undismissAlarm(existingAlarm.id)
                                    Logger.i("CalendarRefreshWorker_doWork", "Reset dismissal status for modified event: ${event.title}")
                                }
                                
                                updatedCount++
                                Logger.d("CalendarRefreshWorker_doWork", "Updated alarm for modified event: ${event.title}")
                            } else {
                                Logger.w("CalendarRefreshWorker_doWork", "Failed to reschedule alarm for ${event.title}: ${scheduleResult.message}")
                                failedCount++
                                failedEvents.add(event.title)
                            }
                        } else {
                            Logger.w("CalendarRefreshWorker_doWork", "Failed to cancel old alarm for ${event.title}: ${cancelResult.message}")
                        }
                    } else if (alarmWasDismissed) {
                        skippedCount++
                        Logger.d("CalendarRefreshWorker_doWork", "Skipped dismissed alarm for unmodified event: ${event.title}")
                    } else {
                        skippedCount++
                        Logger.d("CalendarRefreshWorker_doWork", "Skipped existing alarm for unmodified event: ${event.title}")
                    }
                } else {
                    // New alarm - schedule it
                    val scheduleResult = alarmScheduler.scheduleAlarm(newAlarm)
                    if (scheduleResult.success) {
                        // Save to database
                        alarmRepository.scheduleAlarmForEvent(
                            eventId = event.id,
                            ruleId = rule.id,
                            eventTitle = event.title,
                            eventStartTimeUtc = event.startTimeUtc,
                            leadTimeMinutes = rule.leadTimeMinutes,
                            lastEventModified = event.lastModified
                        )
                        scheduledCount++
                        Logger.d("CalendarRefreshWorker_doWork", "Scheduled new alarm for event: ${event.title}")
                    } else {
                        Logger.w("CalendarRefreshWorker_doWork", "Failed to schedule alarm for ${event.title}: ${scheduleResult.message}")
                        failedCount++
                        failedEvents.add(event.title)
                    }
                }
            }
            
            // Clean up old/expired alarms
            alarmRepository.cleanupOldAlarms()
            
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
}