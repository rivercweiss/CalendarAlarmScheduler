package com.example.calendaralarmscheduler.domain

import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import com.example.calendaralarmscheduler.utils.Logger

/**
 * Service to handle alarm scheduling logic that can be shared between
 * background workers and UI components.
 */
class AlarmSchedulingService(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler
) {
    
    data class SchedulingResult(
        val scheduledCount: Int = 0,
        val updatedCount: Int = 0,
        val skippedCount: Int = 0,
        val failedCount: Int = 0,
        val failedEvents: List<String> = emptyList(),
        val success: Boolean = true,
        val message: String = ""
    )
    
    /**
     * Processes a list of rule matches and schedules/updates alarms as needed.
     * Handles duplicate detection, dismissed alarm checking, and modified event handling.
     */
    suspend fun processMatchesAndScheduleAlarms(
        matches: List<RuleMatcher.MatchResult>,
        logPrefix: String = "AlarmSchedulingService"
    ): SchedulingResult {
        var scheduledCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var failedCount = 0
        val failedEvents = mutableListOf<String>()
        
        Logger.d("${logPrefix}_processMatches", "Processing ${matches.size} matches for alarm scheduling")
        
        try {
            // Process each match
            for (match in matches) {
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
                        Logger.d("${logPrefix}_processMatches", "Event modified: ${event.title}, lastModified: ${event.lastModified} > ${existingAlarm.lastEventModified}")
                        
                        // If event was modified, reset dismissal status (treat as new event)
                        if (alarmWasDismissed) {
                            Logger.i("${logPrefix}_processMatches", "Resetting dismissal status for modified event: ${event.title}")
                        }
                        
                        // Convert existing database alarm to domain model for AlarmScheduler
                        val existingDomainAlarm = ScheduledAlarm(
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
                                    Logger.i("${logPrefix}_processMatches", "Reset dismissal status for modified event: ${event.title}")
                                }
                                
                                updatedCount++
                                Logger.d("${logPrefix}_processMatches", "Updated alarm for modified event: ${event.title}")
                            } else {
                                Logger.w("${logPrefix}_processMatches", "Failed to reschedule alarm for ${event.title}: ${scheduleResult.message}")
                                failedCount++
                                failedEvents.add(event.title)
                            }
                        } else {
                            Logger.w("${logPrefix}_processMatches", "Failed to cancel old alarm for ${event.title}: ${cancelResult.message}")
                            failedCount++
                            failedEvents.add(event.title)
                        }
                    } else if (alarmWasDismissed) {
                        skippedCount++
                        Logger.d("${logPrefix}_processMatches", "Skipped dismissed alarm for unmodified event: ${event.title}")
                    } else {
                        skippedCount++
                        Logger.d("${logPrefix}_processMatches", "Skipped existing alarm for unmodified event: ${event.title}")
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
                        Logger.d("${logPrefix}_processMatches", "Scheduled new alarm for event: ${event.title}")
                    } else {
                        Logger.w("${logPrefix}_processMatches", "Failed to schedule alarm for ${event.title}: ${scheduleResult.message}")
                        failedCount++
                        failedEvents.add(event.title)
                    }
                }
            }
            
            // Clean up old/expired alarms
            alarmRepository.cleanupOldAlarms()
            
            val totalProcessed = scheduledCount + updatedCount + skippedCount + failedCount
            Logger.i("${logPrefix}_processMatches", 
                "Alarm scheduling completed. " +
                "Scheduled: $scheduledCount, Updated: $updatedCount, Skipped: $skippedCount, Failed: $failedCount (Total: $totalProcessed)")
            
            return SchedulingResult(
                scheduledCount = scheduledCount,
                updatedCount = updatedCount,
                skippedCount = skippedCount,
                failedCount = failedCount,
                failedEvents = failedEvents,
                success = failedCount == 0,
                message = if (failedCount == 0) {
                    "Successfully processed $totalProcessed alarm(s)"
                } else {
                    "Processed $totalProcessed alarm(s) with $failedCount failure(s)"
                }
            )
            
        } catch (e: Exception) {
            Logger.e("${logPrefix}_processMatches", "Critical error during alarm scheduling", e)
            return SchedulingResult(
                failedCount = matches.size,
                failedEvents = matches.map { it.event.title },
                success = false,
                message = "Critical error during alarm scheduling: ${e.message}"
            )
        }
    }
}