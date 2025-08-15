package com.example.calendaralarmscheduler.domain

import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
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
        val failedCount: Int = 0
    )
    
    suspend fun processMatchesAndScheduleAlarms(
        matches: List<RuleMatcher.MatchResult>,
        logPrefix: String = "AlarmSchedulingService"
    ): SchedulingResult {
        var scheduledCount = 0
        var updatedCount = 0
        var skippedCount = 0
        var failedCount = 0
        
        Logger.d(logPrefix, "Processing ${matches.size} matches for alarm scheduling")
        
        try {
            for (match in matches) {
                val event = match.event
                val rule = match.rule
                val newAlarm = match.scheduledAlarm
                
                // Check if alarm already exists for this event/rule combination
                val existingAlarm = alarmRepository.getAlarmByEventAndRule(event.id, rule.id)
                
                if (existingAlarm != null) {
                    // Check if event was modified - this resets dismissal status
                    val eventWasModified = event.lastModified > existingAlarm.lastEventModified
                    
                    if (eventWasModified) {
                        // Cancel old alarm and schedule new one
                        val cancelSuccess = alarmScheduler.cancelAlarm(existingAlarm)
                        if (cancelSuccess) {
                            val scheduleSuccess = alarmScheduler.scheduleAlarm(newAlarm)
                            if (scheduleSuccess) {
                                // Update in database
                                alarmRepository.updateAlarmForChangedEvent(
                                    eventId = event.id,
                                    ruleId = rule.id,
                                    eventTitle = event.title,
                                    eventStartTimeUtc = event.startTimeUtc,
                                    leadTimeMinutes = rule.leadTimeMinutes,
                                    lastEventModified = event.lastModified
                                )
                                
                                // Reset dismissal status for modified events
                                if (existingAlarm.userDismissed) {
                                    alarmRepository.undismissAlarm(existingAlarm.id)
                                    Logger.i(logPrefix, "Reset dismissal status for modified event: ${event.title}")
                                }
                                
                                updatedCount++
                                Logger.d(logPrefix, "Updated alarm for modified event: ${event.title}")
                            } else {
                                Logger.w(logPrefix, "Failed to reschedule alarm for ${event.title}")
                                failedCount++
                            }
                        } else {
                            Logger.w(logPrefix, "Failed to cancel old alarm for ${event.title}")
                            failedCount++
                        }
                    } else if (existingAlarm.userDismissed) {
                        skippedCount++
                        Logger.d(logPrefix, "Skipped dismissed alarm for unmodified event: ${event.title}")
                    } else {
                        skippedCount++
                        Logger.d(logPrefix, "Skipped existing alarm for unmodified event: ${event.title}")
                    }
                } else {
                    // New alarm - schedule it
                    val scheduleSuccess = alarmScheduler.scheduleAlarm(newAlarm)
                    if (scheduleSuccess) {
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
                        Logger.d(logPrefix, "Scheduled new alarm for event: ${event.title}")
                    } else {
                        Logger.w(logPrefix, "Failed to schedule alarm for ${event.title}")
                        failedCount++
                    }
                }
            }
            
            // Clean up old/expired alarms
            alarmRepository.cleanupOldAlarms()
            
            val totalProcessed = scheduledCount + updatedCount + skippedCount + failedCount
            Logger.i(logPrefix, 
                "Alarm scheduling completed. " +
                "Scheduled: $scheduledCount, Updated: $updatedCount, Skipped: $skippedCount, Failed: $failedCount (Total: $totalProcessed)")
            
            return SchedulingResult(
                scheduledCount = scheduledCount,
                updatedCount = updatedCount,
                skippedCount = skippedCount,
                failedCount = failedCount
            )
            
        } catch (e: Exception) {
            Logger.e(logPrefix, "Critical error during alarm scheduling", e)
            return SchedulingResult(
                failedCount = matches.size
            )
        }
    }
}