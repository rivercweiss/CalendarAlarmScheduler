package com.example.calendaralarmscheduler.domain

import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.util.*

class RuleMatcherTest {

    private lateinit var ruleMatcher: RuleMatcher
    
    // Test data setup
    private val now = System.currentTimeMillis()
    private val futureTime = now + (2 * 60 * 60 * 1000) // 2 hours from now
    private val pastTime = now - (2 * 60 * 60 * 1000) // 2 hours ago
    
    private val testCalendarId = 1L
    private val anotherCalendarId = 2L
    
    @Before
    fun setup() {
        ruleMatcher = RuleMatcher()
    }
    
    // === Regex Auto-Detection Tests ===
    
    @Test
    fun `autoDetectRegex returns false for simple strings`() {
        assertThat(Rule.autoDetectRegex("meeting")).isFalse()
        assertThat(Rule.autoDetectRegex("doctor appointment")).isFalse()
        assertThat(Rule.autoDetectRegex("lunch")).isFalse()
    }
    
    @Test
    fun `autoDetectRegex returns true for regex patterns`() {
        assertThat(Rule.autoDetectRegex(".*meeting.*")).isTrue()
        assertThat(Rule.autoDetectRegex("[Mm]eeting")).isTrue()
        assertThat(Rule.autoDetectRegex("(urgent|important)")).isTrue()
        assertThat(Rule.autoDetectRegex("\\d+ meeting")).isTrue()
        assertThat(Rule.autoDetectRegex("meeting{2,}")).isTrue()
        assertThat(Rule.autoDetectRegex("meeting+")).isTrue()
        assertThat(Rule.autoDetectRegex("meeting?")).isTrue()
        assertThat(Rule.autoDetectRegex("^meeting$")).isTrue()
        assertThat(Rule.autoDetectRegex("meeting|lunch")).isTrue()
    }
    
    @Test
    fun `autoDetectRegex handles edge cases`() {
        assertThat(Rule.autoDetectRegex("")).isFalse()
        assertThat(Rule.autoDetectRegex("   ")).isFalse()
        assertThat(Rule.autoDetectRegex("escaped\\*star")).isTrue() // Contains backslash
    }
    
    // === Rule Validation Tests ===
    
    @Test
    fun `validateRulePattern accepts valid simple patterns`() {
        val result = ruleMatcher.validateRulePattern("meeting", false)
        assertThat(result.isValid()).isTrue()
    }
    
    @Test
    fun `validateRulePattern accepts valid regex patterns`() {
        val result = ruleMatcher.validateRulePattern(".*meeting.*", true)
        assertThat(result.isValid()).isTrue()
    }
    
    @Test
    fun `validateRulePattern rejects empty patterns`() {
        val result = ruleMatcher.validateRulePattern("", false)
        assertThat(result.isValid()).isFalse()
        assertThat(result.getErrorMessage()).contains("empty")
    }
    
    @Test
    fun `validateRulePattern rejects blank patterns`() {
        val result = ruleMatcher.validateRulePattern("   ", false)
        assertThat(result.isValid()).isFalse()
    }
    
    @Test
    fun `validateRulePattern rejects invalid regex`() {
        val result = ruleMatcher.validateRulePattern("[unclosed", true)
        assertThat(result.isValid()).isFalse()
        assertThat(result.getErrorMessage()).contains("Invalid regex")
    }
    
    // === Event Matching Tests ===
    
    @Test
    fun `rule matches event with simple string pattern case insensitive`() {
        val rule = createRule("Meeting", keywordPattern = "meeting")
        val event = createEvent("Important Meeting", futureTime)
        
        assertThat(rule.matchesEvent(event)).isTrue()
    }
    
    @Test
    fun `rule matches event with regex pattern`() {
        val rule = createRule("Doctor Rule", keywordPattern = ".*[Dd]octor.*", isRegex = true)
        val event = createEvent("Doctor appointment", futureTime)
        
        assertThat(rule.matchesEvent(event)).isTrue()
    }
    
    @Test
    fun `rule does not match when calendar id differs`() {
        val rule = createRule("Meeting", keywordPattern = "meeting", calendarIds = listOf(testCalendarId))
        val event = createEvent("Meeting", futureTime, calendarId = anotherCalendarId)
        
        assertThat(rule.matchesEvent(event)).isFalse()
    }
    
    @Test
    fun `rule does not match when disabled`() {
        val rule = createRule("Meeting", keywordPattern = "meeting", enabled = false)
        val event = createEvent("Meeting", futureTime)
        
        assertThat(rule.matchesEvent(event)).isFalse()
    }
    
    @Test
    fun `rule matches when calendar id is in list`() {
        val rule = createRule("Meeting", keywordPattern = "meeting", 
                             calendarIds = listOf(testCalendarId, anotherCalendarId))
        val event = createEvent("Meeting", futureTime, calendarId = anotherCalendarId)
        
        assertThat(rule.matchesEvent(event)).isTrue()
    }
    
    @Test
    fun `rule handles regex compilation errors gracefully`() {
        val rule = createRule("Invalid Regex", keywordPattern = "[unclosed", isRegex = true)
        val event = createEvent("Any event", futureTime)
        
        assertThat(rule.matchesEvent(event)).isFalse()
    }
    
    // === Rule-Event Matching Integration Tests ===
    
    @Test
    fun `findMatchingRules filters out past events`() = runTest {
        val rule = createRule("Meeting", keywordPattern = "meeting")
        val pastEvent = createEvent("Past Meeting", pastTime)
        val futureEvent = createEvent("Future Meeting", futureTime)
        
        val results = ruleMatcher.findMatchingRules(
            events = listOf(pastEvent, futureEvent),
            rules = listOf(rule)
        )
        
        assertThat(results).hasSize(1)
        assertThat(results[0].event.title).isEqualTo("Future Meeting")
    }
    
    @Test
    fun `findMatchingRules filters out disabled rules`() = runTest {
        val enabledRule = createRule("Enabled", keywordPattern = "meeting", enabled = true)
        val disabledRule = createRule("Disabled", keywordPattern = "meeting", enabled = false)
        val event = createEvent("Meeting", futureTime)
        
        val results = ruleMatcher.findMatchingRules(
            events = listOf(event),
            rules = listOf(enabledRule, disabledRule)
        )
        
        assertThat(results).hasSize(1)
        assertThat(results[0].rule.name).isEqualTo("Enabled")
    }
    
    @Test
    fun `findMatchingRules filters out invalid rules`() = runTest {
        val validRule = createRule("Valid", keywordPattern = "meeting", leadTimeMinutes = 30)
        val invalidRule = createRule("Invalid", keywordPattern = "", leadTimeMinutes = 30) // Empty pattern
        val event = createEvent("Meeting", futureTime)
        
        val results = ruleMatcher.findMatchingRules(
            events = listOf(event),
            rules = listOf(validRule, invalidRule)
        )
        
        assertThat(results).hasSize(1)
        assertThat(results[0].rule.name).isEqualTo("Valid")
    }
    
    @Test
    fun `findMatchingRules creates multiple alarms for multiple matching rules`() = runTest {
        val rule1 = createRule("Rule 1", keywordPattern = "meeting", leadTimeMinutes = 15)
        val rule2 = createRule("Rule 2", keywordPattern = "meeting", leadTimeMinutes = 30)
        val event = createEvent("Important Meeting", futureTime)
        
        val results = ruleMatcher.findMatchingRules(
            events = listOf(event),
            rules = listOf(rule1, rule2)
        )
        
        assertThat(results).hasSize(2)
        assertThat(results.map { it.rule.name }).containsExactly("Rule 1", "Rule 2")
    }
    
    @Test
    fun `findMatchingRules sorts results by alarm time`() = runTest {
        val rule1 = createRule("Later Alarm", keywordPattern = "meeting", leadTimeMinutes = 15)
        val rule2 = createRule("Earlier Alarm", keywordPattern = "meeting", leadTimeMinutes = 60)
        val event = createEvent("Meeting", futureTime)
        
        val results = ruleMatcher.findMatchingRules(
            events = listOf(event),
            rules = listOf(rule1, rule2)
        )
        
        assertThat(results).hasSize(2)
        // Earlier alarm (60 min lead) should come first
        assertThat(results[0].rule.name).isEqualTo("Earlier Alarm")
        assertThat(results[1].rule.name).isEqualTo("Later Alarm")
    }
    
    // === Duplicate and Conflict Resolution Tests ===
    
    @Test
    fun `resolveConflicts allows multiple when configured`() {
        val rule1 = createRule("Rule 1", keywordPattern = "meeting", leadTimeMinutes = 15)
        val rule2 = createRule("Rule 2", keywordPattern = "meeting", leadTimeMinutes = 30)
        val event = createEvent("Meeting", futureTime)
        
        val matches = listOf(
            RuleMatcher.MatchResult(event, rule1, createScheduledAlarm(event, rule1)),
            RuleMatcher.MatchResult(event, rule2, createScheduledAlarm(event, rule2))
        )
        
        val results = ruleMatcher.resolveConflicts(matches, DuplicateHandlingMode.ALLOW_MULTIPLE)
        
        assertThat(results).hasSize(2)
    }
    
    @Test
    fun `resolveConflicts keeps earliest alarm only`() {
        val rule1 = createRule("Rule 1", keywordPattern = "meeting", leadTimeMinutes = 15)
        val rule2 = createRule("Rule 2", keywordPattern = "meeting", leadTimeMinutes = 60)
        val event = createEvent("Meeting", futureTime)
        
        val alarm1 = createScheduledAlarm(event, rule1)
        val alarm2 = createScheduledAlarm(event, rule2)
        
        val matches = listOf(
            RuleMatcher.MatchResult(event, rule1, alarm1),
            RuleMatcher.MatchResult(event, rule2, alarm2)
        )
        
        val results = ruleMatcher.resolveConflicts(matches, DuplicateHandlingMode.EARLIEST_ONLY)
        
        assertThat(results).hasSize(1)
        assertThat(results[0].rule.leadTimeMinutes).isEqualTo(60) // Earlier alarm time
    }
    
    @Test
    fun `resolveConflicts keeps shortest lead time`() {
        val rule1 = createRule("Rule 1", keywordPattern = "meeting", leadTimeMinutes = 15)
        val rule2 = createRule("Rule 2", keywordPattern = "meeting", leadTimeMinutes = 60)
        val event = createEvent("Meeting", futureTime)
        
        val matches = listOf(
            RuleMatcher.MatchResult(event, rule1, createScheduledAlarm(event, rule1)),
            RuleMatcher.MatchResult(event, rule2, createScheduledAlarm(event, rule2))
        )
        
        val results = ruleMatcher.resolveConflicts(matches, DuplicateHandlingMode.SHORTEST_LEAD_TIME)
        
        assertThat(results).hasSize(1)
        assertThat(results[0].rule.leadTimeMinutes).isEqualTo(15) // Shortest lead time
    }
    
    @Test
    fun `resolveConflicts handles no conflicts correctly`() {
        val rule = createRule("Rule 1", keywordPattern = "meeting", leadTimeMinutes = 15)
        val event = createEvent("Meeting", futureTime)
        
        val matches = listOf(
            RuleMatcher.MatchResult(event, rule, createScheduledAlarm(event, rule))
        )
        
        val results = ruleMatcher.resolveConflicts(matches, DuplicateHandlingMode.EARLIEST_ONLY)
        
        assertThat(results).hasSize(1)
        assertThat(results[0].rule.name).isEqualTo("Rule 1")
    }
    
    // === Dismissal Tracking Tests ===
    
    @Test
    fun `filterOutDismissedAlarms removes dismissed alarms with same event modification time`() {
        val rule = createRule("Rule", keywordPattern = "meeting")
        val event = createEvent("Meeting", futureTime, lastModified = 12345L)
        val dismissedAlarm = createScheduledAlarm(event, rule, userDismissed = true, lastEventModified = 12345L)
        
        val matches = listOf(
            RuleMatcher.MatchResult(event, rule, createScheduledAlarm(event, rule))
        )
        
        val results = ruleMatcher.filterOutDismissedAlarms(matches, listOf(dismissedAlarm))
        
        assertThat(results).isEmpty()
    }
    
    @Test
    fun `filterOutDismissedAlarms keeps alarms when event was modified after dismissal`() {
        val rule = createRule("Rule", keywordPattern = "meeting")
        val event = createEvent("Meeting", futureTime, lastModified = 67890L) // Event modified after dismissal
        val dismissedAlarm = createScheduledAlarm(event, rule, userDismissed = true, lastEventModified = 12345L)
        
        val matches = listOf(
            RuleMatcher.MatchResult(event, rule, createScheduledAlarm(event, rule))
        )
        
        val results = ruleMatcher.filterOutDismissedAlarms(matches, listOf(dismissedAlarm))
        
        assertThat(results).hasSize(1)
    }
    
    // === Helper Methods ===
    
    private fun createRule(
        name: String,
        keywordPattern: String,
        isRegex: Boolean = Rule.autoDetectRegex(keywordPattern),
        calendarIds: List<Long> = listOf(testCalendarId),
        leadTimeMinutes: Int = 30,
        enabled: Boolean = true
    ): Rule {
        return Rule(
            id = UUID.randomUUID().toString(),
            name = name,
            keywordPattern = keywordPattern,
            isRegex = isRegex,
            calendarIds = calendarIds,
            leadTimeMinutes = leadTimeMinutes,
            enabled = enabled
        )
    }
    
    private fun createEvent(
        title: String,
        startTime: Long,
        calendarId: Long = testCalendarId,
        lastModified: Long = System.currentTimeMillis(),
        allDay: Boolean = false
    ): CalendarEvent {
        return CalendarEvent(
            id = UUID.randomUUID().toString(),
            title = title,
            startTimeUtc = startTime,
            endTimeUtc = startTime + (60 * 60 * 1000), // 1 hour duration
            calendarId = calendarId,
            lastModified = lastModified,
            isAllDay = allDay,
            location = null,
            description = null,
            timezone = ZoneId.systemDefault().id
        )
    }
    
    private fun createScheduledAlarm(
        event: CalendarEvent,
        rule: Rule,
        userDismissed: Boolean = false,
        lastEventModified: Long = event.lastModified
    ): com.example.calendaralarmscheduler.domain.models.ScheduledAlarm {
        return com.example.calendaralarmscheduler.domain.models.ScheduledAlarm.fromEventAndRule(
            event = event,
            rule = rule,
            defaultAllDayHour = 20,
            defaultAllDayMinute = 0
        ).copy(
            userDismissed = userDismissed,
            lastEventModified = lastEventModified
        )
    }
}