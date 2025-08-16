package com.example.calendaralarmscheduler.domain

import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import com.example.calendaralarmscheduler.data.DayTrackingRepository
import com.example.calendaralarmscheduler.utils.Logger
import java.util.UUID

class RuleMatcher(
    private val dayTrackingRepository: DayTrackingRepository? = null
) {
    
    data class MatchResult(
        val event: CalendarEvent,
        val rule: Rule,
        val scheduledAlarm: ScheduledAlarm
    )
    
    fun findMatchingRules(
        events: List<CalendarEvent>,
        rules: List<Rule>,
        defaultAllDayHour: Int = 20,
        defaultAllDayMinute: Int = 0
    ): List<MatchResult> {
        val results = mutableListOf<MatchResult>()
        
        val enabledRules = rules.filter { it.enabled && it.isValid() }
        val futureEvents = events.filter { !it.isInPast() }
        
        for (event in futureEvents) {
            for (rule in enabledRules) {
                if (rule.matchesEvent(event)) {
                    // Check if this rule should only trigger for first event of day
                    if (rule.firstEventOfDayOnly && dayTrackingRepository?.hasRuleTriggeredToday(rule.id) == true) {
                        Logger.d("RuleMatcher", "Skipping rule '${rule.name}' for event '${event.title}' - already triggered today")
                        continue
                    }
                    
                    val alarmTimeUtc = if (event.isAllDay) {
                        event.computeAllDayAlarmTimeUtc(defaultAllDayHour, defaultAllDayMinute, 0)
                    } else {
                        event.computeAlarmTimeUtc(rule.leadTimeMinutes)
                    }
                    
                    val scheduledAlarm = ScheduledAlarm(
                        id = UUID.randomUUID().toString(),
                        eventId = event.id,
                        ruleId = rule.id,
                        eventTitle = event.title,
                        eventStartTimeUtc = event.startTimeUtc,
                        alarmTimeUtc = alarmTimeUtc,
                        pendingIntentRequestCode = ScheduledAlarm.generateRequestCode(event.id, rule.id),
                        lastEventModified = event.lastModified
                    )
                    
                    // Only include if alarm time is in the future
                    if (!scheduledAlarm.isInPast()) {
                        results.add(MatchResult(event, rule, scheduledAlarm))
                    }
                }
            }
        }
        
        return results.sortedBy { it.scheduledAlarm.alarmTimeUtc }
    }
    
    fun findMatchingRulesForEvent(
        event: CalendarEvent,
        rules: List<Rule>,
        defaultAllDayHour: Int = 20,
        defaultAllDayMinute: Int = 0
    ): List<MatchResult> {
        val results = mutableListOf<MatchResult>()
        
        if (event.isInPast()) {
            return results
        }
        
        val enabledRules = rules.filter { it.enabled && it.isValid() }
        
        for (rule in enabledRules) {
            if (rule.matchesEvent(event)) {
                // Check if this rule should only trigger for first event of day
                if (rule.firstEventOfDayOnly && dayTrackingRepository?.hasRuleTriggeredToday(rule.id) == true) {
                    Logger.d("RuleMatcher", "Skipping rule '${rule.name}' for event '${event.title}' - already triggered today")
                    continue
                }
                
                val alarmTimeUtc = if (event.isAllDay) {
                    event.computeAllDayAlarmTimeUtc(defaultAllDayHour, defaultAllDayMinute, 0)
                } else {
                    event.computeAlarmTimeUtc(rule.leadTimeMinutes)
                }
                
                val scheduledAlarm = ScheduledAlarm(
                    id = UUID.randomUUID().toString(),
                    eventId = event.id,
                    ruleId = rule.id,
                    eventTitle = event.title,
                    eventStartTimeUtc = event.startTimeUtc,
                    alarmTimeUtc = alarmTimeUtc,
                    pendingIntentRequestCode = ScheduledAlarm.generateRequestCode(event.id, rule.id),
                    lastEventModified = event.lastModified
                )
                
                // Only include if alarm time is in the future
                if (!scheduledAlarm.isInPast()) {
                    results.add(MatchResult(event, rule, scheduledAlarm))
                }
            }
        }
        
        return results.sortedBy { it.scheduledAlarm.alarmTimeUtc }
    }
    
    fun findMatchingEventsForRule(
        rule: Rule,
        events: List<CalendarEvent>,
        defaultAllDayHour: Int = 20,
        defaultAllDayMinute: Int = 0
    ): List<MatchResult> {
        val results = mutableListOf<MatchResult>()
        
        if (!rule.enabled || !rule.isValid()) {
            return results
        }
        
        // Check if this rule should only trigger for first event of day
        if (rule.firstEventOfDayOnly && dayTrackingRepository?.hasRuleTriggeredToday(rule.id) == true) {
            Logger.d("RuleMatcher", "Skipping rule '${rule.name}' - already triggered today")
            return results
        }
        
        val futureEvents = events.filter { !it.isInPast() }
        
        for (event in futureEvents) {
            if (rule.matchesEvent(event)) {
                val alarmTimeUtc = if (event.isAllDay) {
                    event.computeAllDayAlarmTimeUtc(defaultAllDayHour, defaultAllDayMinute, 0)
                } else {
                    event.computeAlarmTimeUtc(rule.leadTimeMinutes)
                }
                
                val scheduledAlarm = ScheduledAlarm(
                    id = UUID.randomUUID().toString(),
                    eventId = event.id,
                    ruleId = rule.id,
                    eventTitle = event.title,
                    eventStartTimeUtc = event.startTimeUtc,
                    alarmTimeUtc = alarmTimeUtc,
                    pendingIntentRequestCode = ScheduledAlarm.generateRequestCode(event.id, rule.id),
                    lastEventModified = event.lastModified
                )
                
                // Only include if alarm time is in the future
                if (!scheduledAlarm.isInPast()) {
                    results.add(MatchResult(event, rule, scheduledAlarm))
                    
                    // For "first event of day only" rules, only return the first match
                    if (rule.firstEventOfDayOnly) {
                        Logger.d("RuleMatcher", "Found first matching event for '${rule.name}': '${event.title}'")
                        break
                    }
                }
            }
        }
        
        return results.sortedBy { it.scheduledAlarm.alarmTimeUtc }
    }
    
    fun validateRulePattern(pattern: String, isRegex: Boolean): ValidationResult {
        if (pattern.isBlank()) {
            return ValidationResult.Invalid("Pattern cannot be empty")
        }
        
        if (isRegex) {
            return try {
                Regex(pattern, RegexOption.IGNORE_CASE)
                ValidationResult.Valid
            } catch (e: Exception) {
                ValidationResult.Invalid("Invalid regex pattern: ${e.message}")
            }
        }
        
        return ValidationResult.Valid
    }
    
    fun testRuleAgainstEvent(rule: Rule, event: CalendarEvent): Boolean {
        return rule.matchesEvent(event)
    }
    
    fun findDuplicateAlarms(
        newMatches: List<MatchResult>,
        existingAlarms: List<ScheduledAlarm>
    ): List<ScheduledAlarm> {
        val newRequestCodes = newMatches.map { it.scheduledAlarm.pendingIntentRequestCode }.toSet()
        return existingAlarms.filter { alarm ->
            alarm.pendingIntentRequestCode in newRequestCodes
        }
    }
    
    fun filterOutDismissedAlarms(
        matches: List<MatchResult>,
        existingAlarms: List<ScheduledAlarm>
    ): List<MatchResult> {
        val dismissedKeys = existingAlarms
            .filter { it.userDismissed }
            .map { "${it.eventId}-${it.ruleId}-${it.lastEventModified}" }
            .toSet()
            
        return matches.filter { match ->
            val key = "${match.event.id}-${match.rule.id}-${match.event.lastModified}"
            key !in dismissedKeys
        }
    }
    
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
        
        fun isValid(): Boolean = this is Valid
        fun getErrorMessage(): String? = (this as? Invalid)?.message
    }
    
    /**
     * Mark a rule as triggered for today (called when alarm is actually scheduled)
     */
    fun markRuleTriggeredToday(ruleId: String) {
        dayTrackingRepository?.markRuleTriggeredToday(ruleId)
        Logger.d("RuleMatcher", "Marked rule '$ruleId' as triggered today")
    }
    
    companion object {
        fun autoDetectRegex(pattern: String): Boolean {
            return Rule.autoDetectRegex(pattern)
        }
        
        fun isRegex(pattern: String): Boolean {
            return autoDetectRegex(pattern)
        }
    }
}