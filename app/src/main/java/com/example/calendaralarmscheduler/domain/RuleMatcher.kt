package com.example.calendaralarmscheduler.domain

import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm

class RuleMatcher {
    
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
                    val scheduledAlarm = ScheduledAlarm.fromEventAndRule(
                        event = event,
                        rule = rule,
                        defaultAllDayHour = defaultAllDayHour,
                        defaultAllDayMinute = defaultAllDayMinute
                    )
                    
                    // Only include if alarm time is in the future
                    if (scheduledAlarm.isInFuture()) {
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
                val scheduledAlarm = ScheduledAlarm.fromEventAndRule(
                    event = event,
                    rule = rule,
                    defaultAllDayHour = defaultAllDayHour,
                    defaultAllDayMinute = defaultAllDayMinute
                )
                
                // Only include if alarm time is in the future
                if (scheduledAlarm.isInFuture()) {
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
        
        val futureEvents = events.filter { !it.isInPast() }
        
        for (event in futureEvents) {
            if (rule.matchesEvent(event)) {
                val scheduledAlarm = ScheduledAlarm.fromEventAndRule(
                    event = event,
                    rule = rule,
                    defaultAllDayHour = defaultAllDayHour,
                    defaultAllDayMinute = defaultAllDayMinute
                )
                
                // Only include if alarm time is in the future
                if (scheduledAlarm.isInFuture()) {
                    results.add(MatchResult(event, rule, scheduledAlarm))
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
    
    fun resolveConflicts(
        matches: List<MatchResult>,
        duplicateHandlingMode: com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode
    ): List<MatchResult> {
        if (duplicateHandlingMode == com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode.ALLOW_MULTIPLE) {
            return matches
        }
        
        // Group matches by event ID to find conflicts
        val groupedByEvent = matches.groupBy { it.event.id }
        
        return groupedByEvent.flatMap { (_, eventMatches) ->
            if (eventMatches.size <= 1) {
                // No conflict for this event
                eventMatches
            } else {
                // Resolve conflict based on mode
                when (duplicateHandlingMode) {
                    com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode.EARLIEST_ONLY -> {
                        listOf(eventMatches.minByOrNull { it.scheduledAlarm.alarmTimeUtc }!!)
                    }
                    com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode.LATEST_ONLY -> {
                        listOf(eventMatches.maxByOrNull { it.scheduledAlarm.alarmTimeUtc }!!)
                    }
                    com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode.SHORTEST_LEAD_TIME -> {
                        listOf(eventMatches.minByOrNull { it.rule.leadTimeMinutes }!!)
                    }
                    com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode.LONGEST_LEAD_TIME -> {
                        listOf(eventMatches.maxByOrNull { it.rule.leadTimeMinutes }!!)
                    }
                    else -> eventMatches // Should never happen, but fallback to allow all
                }
            }
        }
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val message: String) : ValidationResult()
        
        fun isValid(): Boolean = this is Valid
        fun getErrorMessage(): String? = (this as? Invalid)?.message
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