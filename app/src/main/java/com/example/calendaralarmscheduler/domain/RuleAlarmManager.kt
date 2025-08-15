package com.example.calendaralarmscheduler.domain

import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.flow.first

/**
 * Service that manages the relationship between rules and their associated alarms.
 * Integrates alarm scheduling logic with rule management operations.
 * Ensures that when rules are modified, the corresponding alarms are properly 
 * updated, cancelled, or rescheduled as needed.
 */
class RuleAlarmManager(
    private val ruleRepository: RuleRepository,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val calendarRepository: CalendarRepository
) {
    
    data class RuleUpdateResult(
        val success: Boolean,
        val message: String,
        val alarmsAffected: Int = 0,
        val alarmsCancelled: Int = 0,
        val alarmsScheduled: Int = 0
    )
    
    data class SchedulingResult(
        val scheduledCount: Int = 0,
        val updatedCount: Int = 0,
        val skippedCount: Int = 0,
        val failedCount: Int = 0
    )
    
    /**
     * Updates a rule's enabled status and handles all associated alarm operations.
     * When disabling: cancels all system alarms and removes database entries.
     * When enabling: reschedules alarms for all matching events.
     */
    suspend fun updateRuleEnabled(rule: Rule, enabled: Boolean): RuleUpdateResult {
        val logPrefix = "RuleAlarmManager_updateRuleEnabled"
        Logger.i(logPrefix, "Updating rule '${rule.name}' enabled status: $enabled")
        
        return try {
            if (!enabled) {
                // Disabling rule - cancel all associated alarms
                cancelAlarmsForRule(rule)
            } else {
                // Enabling rule - reschedule alarms for matching events  
                rescheduleAlarmsForRule(rule)
            }
        } catch (e: Exception) {
            Logger.e(logPrefix, "Failed to update rule '${rule.name}' with alarm management", e)
            RuleUpdateResult(
                success = false,
                message = "Failed to update rule: ${e.message}"
            )
        }
    }
    
    
    /**
     * Disables a rule and cancels all its associated alarms.
     */
    private suspend fun cancelAlarmsForRule(rule: Rule): RuleUpdateResult {
        val logPrefix = "RuleAlarmManager_cancelAlarmsForRule"
        Logger.d(logPrefix, "Cancelling all alarms for rule: ${rule.name}")
        
        try {
            // Get all alarms for this rule before deletion
            val existingAlarms = alarmRepository.getAlarmsByRuleId(rule.id).first()
            Logger.d(logPrefix, "Found ${existingAlarms.size} existing alarms for rule ${rule.name}")
            
            if (existingAlarms.isNotEmpty()) {
                // Convert database alarms to domain models for cancellation
                val domainAlarms = existingAlarms.map { dbAlarm ->
                    ScheduledAlarm(
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
                
                // Cancel alarms from system AlarmManager
                var successfulCancels = 0
                for (alarm in domainAlarms) {
                    if (alarmScheduler.cancelAlarm(alarm)) {
                        successfulCancels++
                    }
                }
                Logger.d(logPrefix, "Successfully cancelled $successfulCancels out of ${domainAlarms.size} system alarms")
                
                // Remove alarms from database
                alarmRepository.deleteAlarmsByRuleId(rule.id)
                Logger.d(logPrefix, "Deleted ${existingAlarms.size} alarm database entries for rule ${rule.id}")
            }
            
            // Update the rule to disabled status
            val updatedRule = rule.copy(enabled = false)
            ruleRepository.updateRule(updatedRule)
            Logger.i(logPrefix, "Successfully disabled rule '${rule.name}' and cancelled ${existingAlarms.size} alarms")
            
            return RuleUpdateResult(
                success = true,
                message = "Rule disabled and ${existingAlarms.size} alarm(s) cancelled",
                alarmsAffected = existingAlarms.size,
                alarmsCancelled = existingAlarms.size
            )
            
        } catch (e: Exception) {
            Logger.e(logPrefix, "Failed to cancel alarms for rule '${rule.name}'", e)
            return RuleUpdateResult(
                success = false,
                message = "Failed to cancel alarms: ${e.message}"
            )
        }
    }
    
    /**
     * Enables a rule and schedules alarms for all matching events.
     */
    private suspend fun rescheduleAlarmsForRule(rule: Rule): RuleUpdateResult {
        val logPrefix = "RuleAlarmManager_rescheduleAlarmsForRule"
        Logger.d(logPrefix, "Rescheduling alarms for enabled rule: ${rule.name}")
        
        try {
            // First update the rule to enabled status
            val updatedRule = rule.copy(enabled = true)
            ruleRepository.updateRule(updatedRule)
            Logger.d(logPrefix, "Updated rule '${rule.name}' to enabled status")
            
            // Get calendar events to check for matches
            val events = calendarRepository.getEventsInLookAheadWindow()
            if (events.isEmpty()) {
                Logger.d(logPrefix, "No events found in lookahead window")
                return RuleUpdateResult(
                    success = true,
                    message = "Rule enabled, no events to schedule alarms for",
                    alarmsAffected = 0
                )
            }
            
            // Find matching events for this specific rule
            val ruleMatcher = RuleMatcher()
            val matchResults = ruleMatcher.findMatchingEventsForRule(updatedRule, events)
            Logger.d(logPrefix, "Found ${matchResults.size} matching events for rule '${rule.name}'")
            
            if (matchResults.isEmpty()) {
                Logger.d(logPrefix, "No matching events found for rule '${rule.name}'")
                return RuleUpdateResult(
                    success = true,
                    message = "Rule enabled, no matching events found",
                    alarmsAffected = 0
                )
            }
            
            // Get existing alarms to filter out duplicates and dismissed alarms
            val existingAlarmsDb = alarmRepository.getAllAlarms().first()
            val existingAlarms = existingAlarmsDb.map { dbAlarm ->
                ScheduledAlarm(
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
            
            // Filter out dismissed alarms
            val filteredMatches = ruleMatcher.filterOutDismissedAlarms(matchResults, existingAlarms)
            Logger.d(logPrefix, "After filtering dismissed alarms: ${filteredMatches.size} matches to process")
            
            // Process matches and schedule alarms
            val schedulingResult = processMatchesAndScheduleAlarms(
                filteredMatches,
                logPrefix = logPrefix
            )
            
            val totalAlarmsAffected = schedulingResult.scheduledCount + schedulingResult.updatedCount
            Logger.i(logPrefix, "Successfully enabled rule '${rule.name}' and scheduled $totalAlarmsAffected alarm(s)")
            
            return RuleUpdateResult(
                success = schedulingResult.failedCount == 0,
                message = if (schedulingResult.failedCount == 0) {
                    "Rule enabled and ${totalAlarmsAffected} alarm(s) scheduled"
                } else {
                    "Rule enabled but ${schedulingResult.failedCount} alarm(s) failed"
                },
                alarmsAffected = totalAlarmsAffected,
                alarmsScheduled = schedulingResult.scheduledCount
            )
            
        } catch (e: Exception) {
            Logger.e(logPrefix, "Failed to reschedule alarms for rule '${rule.name}'", e)
            return RuleUpdateResult(
                success = false,
                message = "Failed to schedule alarms: ${e.message}"
            )
        }
    }
    
    /**
     * Updates a rule and handles all associated alarm operations.
     * Cancels old alarms and schedules new ones based on the updated rule.
     */
    suspend fun updateRuleWithAlarmManagement(oldRule: Rule, newRule: Rule): RuleUpdateResult {
        val logPrefix = "RuleAlarmManager_updateRuleWithAlarmManagement"
        Logger.i(logPrefix, "Updating rule '${oldRule.name}' with alarm management")
        
        return try {
            // Cancel all existing alarms for this rule
            val cancelResult = cancelAlarmsForRule(oldRule)
            
            // Update the rule in the database
            ruleRepository.updateRule(newRule)
            Logger.d(logPrefix, "Updated rule '${newRule.name}' in database")
            
            if (newRule.enabled) {
                // If the new rule is enabled, schedule alarms for matching events
                val scheduleResult = rescheduleAlarmsForRule(newRule)
                
                val totalAlarmsAffected = cancelResult.alarmsCancelled + scheduleResult.alarmsScheduled
                Logger.i(logPrefix, "Rule update complete: cancelled ${cancelResult.alarmsCancelled}, scheduled ${scheduleResult.alarmsScheduled}")
                
                return RuleUpdateResult(
                    success = scheduleResult.success,
                    message = if (scheduleResult.success) {
                        "Rule updated: ${cancelResult.alarmsCancelled} alarm(s) cancelled, ${scheduleResult.alarmsScheduled} alarm(s) scheduled"
                    } else {
                        "Rule updated but some alarms failed: ${scheduleResult.message}"
                    },
                    alarmsAffected = totalAlarmsAffected,
                    alarmsCancelled = cancelResult.alarmsCancelled,
                    alarmsScheduled = scheduleResult.alarmsScheduled
                )
            } else {
                // Rule is disabled, just return cancel result
                Logger.i(logPrefix, "Rule update complete: rule disabled, ${cancelResult.alarmsCancelled} alarm(s) cancelled")
                
                return RuleUpdateResult(
                    success = cancelResult.success,
                    message = "Rule updated and disabled: ${cancelResult.alarmsCancelled} alarm(s) cancelled",
                    alarmsAffected = cancelResult.alarmsAffected,
                    alarmsCancelled = cancelResult.alarmsCancelled,
                    alarmsScheduled = 0
                )
            }
            
        } catch (e: Exception) {
            Logger.e(logPrefix, "Failed to update rule '${oldRule.name}' with alarm management", e)
            RuleUpdateResult(
                success = false,
                message = "Failed to update rule: ${e.message}"
            )
        }
    }
    
    /**
     * Deletes a rule and cancels all its associated alarms.
     */
    suspend fun deleteRuleWithAlarmCleanup(rule: Rule): RuleUpdateResult {
        val logPrefix = "RuleAlarmManager_deleteRuleWithAlarmCleanup"
        Logger.i(logPrefix, "Deleting rule '${rule.name}' with alarm cleanup")
        
        try {
            // First cancel all alarms (reuse the cancel logic)
            val cancelResult = cancelAlarmsForRule(rule)
            if (!cancelResult.success) {
                Logger.w(logPrefix, "Failed to cancel alarms, but proceeding with rule deletion")
            }
            
            // Delete the rule
            ruleRepository.deleteRule(rule)
            Logger.i(logPrefix, "Successfully deleted rule '${rule.name}' and cleaned up ${cancelResult.alarmsCancelled} alarms")
            
            return RuleUpdateResult(
                success = true,
                message = "Rule deleted and ${cancelResult.alarmsCancelled} alarm(s) cancelled",
                alarmsAffected = cancelResult.alarmsAffected,
                alarmsCancelled = cancelResult.alarmsCancelled
            )
            
        } catch (e: Exception) {
            Logger.e(logPrefix, "Failed to delete rule '${rule.name}' with alarm cleanup", e)
            return RuleUpdateResult(
                success = false,
                message = "Failed to delete rule: ${e.message}"
            )
        }
    }
    
    /**
     * Process rule matches and schedule alarms with simplified result tracking.
     */
    suspend fun processMatchesAndScheduleAlarms(
        matches: List<RuleMatcher.MatchResult>,
        logPrefix: String = "RuleAlarmManager"
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