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
        // Memory management constants
        private const val MEMORY_PRESSURE_THRESHOLD = 85.0
        private const val CRITICAL_MEMORY_THRESHOLD = 95.0
        private const val MAX_EVENTS_BATCH_SIZE = 50 // Process events in batches to prevent memory bloat
        private const val LARGE_COLLECTION_THRESHOLD = 100 // Consider streaming if more than this many events
    }
    
    /**
     * Memory monitoring utilities for background worker
     */
    private fun getCurrentMemoryUsagePercent(): Double {
        val runtime = Runtime.getRuntime()
        return ((runtime.maxMemory() - runtime.freeMemory()).toDouble() / runtime.maxMemory().toDouble()) * 100
    }
    
    private fun logMemoryUsage(operation: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.maxMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
        
        Logger.d("CalendarRefreshWorker_Memory", 
            "$operation - Memory: ${usedMemory / 1024 / 1024}MB/${maxMemory / 1024 / 1024}MB (${usagePercent.toInt()}%)")
    }
    
    private suspend fun performMemoryCleanupIfNeeded(): Boolean {
        val memoryUsage = getCurrentMemoryUsagePercent()
        if (memoryUsage > MEMORY_PRESSURE_THRESHOLD) {
            Logger.w("CalendarRefreshWorker_Memory", "Memory pressure detected: ${memoryUsage.toInt()}%")
            
            // Force garbage collection
            System.gc()
            kotlinx.coroutines.delay(1000) // Give GC time to work
            
            val afterGcMemory = getCurrentMemoryUsagePercent()
            Logger.d("CalendarRefreshWorker_Memory", "After GC: ${afterGcMemory.toInt()}%")
            
            if (afterGcMemory > CRITICAL_MEMORY_THRESHOLD) {
                Logger.e("CalendarRefreshWorker_Memory", "Critical memory usage after GC: ${afterGcMemory.toInt()}%")
                return false // Indicate memory is still critical
            }
        }
        return true
    }
    
    override suspend fun doWork(): Result {
        val startTime = System.currentTimeMillis()
        Logger.i("CalendarRefreshWorker_doWork", "Starting calendar refresh background work")
        
        // Initial memory check
        logMemoryUsage("Worker start")
        if (!performMemoryCleanupIfNeeded()) {
            Logger.e("CalendarRefreshWorker_doWork", "Critical memory usage at start - aborting work")
            return Result.retry() // Retry later when system may have more memory
        }
        
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
            
            // Memory check after retrieving events
            logMemoryUsage("After event retrieval")
            if (!performMemoryCleanupIfNeeded()) {
                Logger.e("CalendarRefreshWorker_doWork", "Critical memory usage after event retrieval")
                return Result.retry()
            }
            
            if (events.isEmpty()) {
                Logger.i("CalendarRefreshWorker_doWork", "No new or modified events found")
                settingsRepository.updateLastSyncTime()
                errorNotificationManager.clearErrorNotifications()
                return Result.success()
            }
            
            // Check if we need batch processing for large collections
            if (events.size > LARGE_COLLECTION_THRESHOLD) {
                Logger.i("CalendarRefreshWorker_doWork", "Large event collection detected (${events.size}). Using batch processing.")
                return processEventsInBatches(events, enabledRules, alarmRepository, ruleMatcher, 
                    alarmSchedulingService, settingsRepository, errorNotificationManager, startTime)
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
    
    /**
     * Process large event collections in memory-efficient batches
     */
    private suspend fun processEventsInBatches(
        events: List<com.example.calendaralarmscheduler.domain.models.CalendarEvent>,
        enabledRules: List<com.example.calendaralarmscheduler.data.database.entities.Rule>,
        alarmRepository: com.example.calendaralarmscheduler.data.AlarmRepository,
        ruleMatcher: RuleMatcher,
        alarmSchedulingService: AlarmSchedulingService,
        settingsRepository: com.example.calendaralarmscheduler.data.SettingsRepository,
        errorNotificationManager: com.example.calendaralarmscheduler.utils.ErrorNotificationManager,
        startTime: Long
    ): Result {
        Logger.i("CalendarRefreshWorker_BatchProcessing", "Processing ${events.size} events in batches of $MAX_EVENTS_BATCH_SIZE")
        
        var totalScheduledCount = 0
        var totalUpdatedCount = 0
        var totalSkippedCount = 0
        var totalFailedCount = 0
        val allFailedEvents = mutableListOf<String>()
        
        // Get existing alarms once for all batches
        val existingAlarmsDb = alarmRepository.getAllAlarms().first()
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
        
        // Process events in batches
        val eventBatches = events.chunked(MAX_EVENTS_BATCH_SIZE)
        Logger.i("CalendarRefreshWorker_BatchProcessing", "Split into ${eventBatches.size} batches")
        
        for ((batchIndex, eventBatch) in eventBatches.withIndex()) {
            Logger.d("CalendarRefreshWorker_BatchProcessing", "Processing batch ${batchIndex + 1}/${eventBatches.size} (${eventBatch.size} events)")
            
            // Memory check before each batch
            logMemoryUsage("Before batch ${batchIndex + 1}")
            if (!performMemoryCleanupIfNeeded()) {
                Logger.e("CalendarRefreshWorker_BatchProcessing", "Critical memory usage at batch ${batchIndex + 1} - stopping")
                break
            }
            
            try {
                // Find matching rules for this batch
                val batchMatchResults = ruleMatcher.findMatchingRules(eventBatch, enabledRules)
                
                // Filter out dismissed alarms for this batch
                val filteredMatches = ruleMatcher.filterOutDismissedAlarms(batchMatchResults, existingAlarms)
                
                if (filteredMatches.isNotEmpty()) {
                    // Process this batch
                    val batchResult = alarmSchedulingService.processMatchesAndScheduleAlarms(
                        filteredMatches,
                        logPrefix = "CalendarRefreshWorker_Batch${batchIndex + 1}"
                    )
                    
                    // Accumulate results
                    totalScheduledCount += batchResult.scheduledCount
                    totalUpdatedCount += batchResult.updatedCount
                    totalSkippedCount += batchResult.skippedCount
                    totalFailedCount += batchResult.failedCount
                    allFailedEvents.addAll(batchResult.failedEvents)
                }
                
                // Clear temporary batch data to free memory
                // (Kotlin will GC these automatically, but being explicit helps)
                
            } catch (e: Exception) {
                Logger.e("CalendarRefreshWorker_BatchProcessing", "Error processing batch ${batchIndex + 1}", e)
                // Continue with next batch rather than failing completely
            }
            
            // Memory cleanup between batches
            if (batchIndex % 3 == 0) { // Every 3 batches
                performMemoryCleanupIfNeeded()
            }
        }
        
        // Update last sync time
        settingsRepository.updateLastSyncTime()
        
        // Handle error notifications
        if (totalFailedCount > 0) {
            Logger.w("CalendarRefreshWorker_BatchProcessing", "Failed to process $totalFailedCount alarms across all batches")
            if (allFailedEvents.isNotEmpty()) {
                errorNotificationManager.showAlarmSchedulingError(allFailedEvents.first())
            }
        } else {
            errorNotificationManager.clearErrorNotifications()
        }
        
        // Perform health check
        val healthCheckResult = performAlarmHealthCheck(alarmRepository, 
            com.example.calendaralarmscheduler.domain.AlarmScheduler(applicationContext, 
                applicationContext.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager))
        
        val workTime = System.currentTimeMillis() - startTime
        Logger.i("CalendarRefreshWorker_BatchProcessing", 
            "Batch processing completed in ${workTime}ms. " +
            "Total - Scheduled: $totalScheduledCount, Updated: $totalUpdatedCount, " +
            "Skipped: $totalSkippedCount, Failed: $totalFailedCount")
        
        return Result.success()
    }
}