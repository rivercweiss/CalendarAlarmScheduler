package com.example.calendaralarmscheduler.domain

import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.flow.first

/**
 * Service that manages the relationship between rules and their associated alarms.
 * Ensures that when rules are modified, the corresponding alarms are properly 
 * updated, cancelled, or rescheduled as needed.
 */
class RuleAlarmManager(
    private val ruleRepository: RuleRepository,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val calendarRepository: CalendarRepository,
    private val alarmSchedulingService: AlarmSchedulingService
) {
    
    // Prevent duplicate operations on the same rule
    private val activeOperations = mutableSetOf<String>()
    private val operationTimeouts = mutableMapOf<String, Long>()
    private val OPERATION_TIMEOUT_MS = 2000L // 2 second timeout for operations
    
    data class RuleUpdateResult(
        val success: Boolean,
        val message: String,
        val alarmsAffected: Int = 0,
        val alarmsCancelled: Int = 0,
        val alarmsScheduled: Int = 0
    )
    
    /**
     * Updates a rule's enabled status and handles all associated alarm operations.
     * When disabling: cancels all system alarms and removes database entries.
     * When enabling: reschedules alarms for all matching events.
     */
    suspend fun updateRuleEnabled(rule: Rule, enabled: Boolean): RuleUpdateResult {
        val logPrefix = "RuleAlarmManager_updateRuleEnabled"
        val operationKey = "${rule.id}_${enabled}"
        val currentTime = System.currentTimeMillis()
        
        // Clean up expired operations
        cleanupExpiredOperations()
        
        // Check for duplicate operations
        if (activeOperations.contains(operationKey)) {
            Logger.w(logPrefix, "Ignoring duplicate rule update operation for '${rule.name}' (${if (enabled) "enable" else "disable"})")
            return RuleUpdateResult(
                success = true,
                message = "Operation already in progress, ignoring duplicate request",
                alarmsAffected = 0
            )
        }
        
        Logger.i(logPrefix, "Updating rule '${rule.name}' enabled status: $enabled")
        
        return try {
            activeOperations.add(operationKey)
            operationTimeouts[operationKey] = currentTime + OPERATION_TIMEOUT_MS
            
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
        } finally {
            activeOperations.remove(operationKey)
            operationTimeouts.remove(operationKey)
        }
    }
    
    /**
     * Clean up expired operations to prevent memory leaks
     */
    private fun cleanupExpiredOperations() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = operationTimeouts.filter { (_, timeout) -> 
            currentTime > timeout 
        }.keys
        
        expiredKeys.forEach { key ->
            activeOperations.remove(key)
            operationTimeouts.remove(key)
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
                val cancelResults = alarmScheduler.cancelMultipleAlarms(domainAlarms)
                val successfulCancels = cancelResults.count { it.success }
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
            
            // Use the shared scheduling service to schedule alarms
            val schedulingResult = alarmSchedulingService.processMatchesAndScheduleAlarms(
                filteredMatches,
                logPrefix = logPrefix
            )
            
            val totalAlarmsAffected = schedulingResult.scheduledCount + schedulingResult.updatedCount
            Logger.i(logPrefix, "Successfully enabled rule '${rule.name}' and scheduled $totalAlarmsAffected alarm(s)")
            
            return RuleUpdateResult(
                success = schedulingResult.success,
                message = if (schedulingResult.success) {
                    "Rule enabled and ${totalAlarmsAffected} alarm(s) scheduled"
                } else {
                    "Rule enabled but some alarms failed: ${schedulingResult.message}"
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
}