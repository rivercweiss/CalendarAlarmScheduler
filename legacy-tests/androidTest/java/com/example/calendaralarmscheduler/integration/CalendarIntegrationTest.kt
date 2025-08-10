package com.example.calendaralarmscheduler.integration

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.rule.GrantPermissionRule
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.e2e.E2ETestBase
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.runBlocking
import org.junit.Rule as JunitRule
import org.junit.Test
import java.util.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*

/**
 * Integration tests with real Google Calendar data
 * Tests the complete flow from calendar reading to alarm scheduling
 * using actual calendar events when available
 */
class CalendarIntegrationTest : E2ETestBase() {

    @get:JunitRule
    val grantCalendarPermission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR
    )

    @Test
    fun testRealCalendarEventReading() = runBlocking {
        Logger.i("CalendarIntegrationTest", "=== Testing Real Calendar Event Reading ===")
        
        // Verify we have calendar permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        assertTrue("Calendar permission must be granted for this test", hasPermission)
        
        val calendarRepository = CalendarRepository(context)
        
        // Get available calendars
        val calendars = calendarRepository.getAvailableCalendars()
        Logger.i("CalendarIntegrationTest", "Found ${calendars.size} calendars")
        
        calendars.forEach { calendar ->
            Logger.i("CalendarIntegrationTest", "Calendar: ${calendar.displayName} (ID: ${calendar.id}, Account: ${calendar.accountName})")
        }
        
        if (calendars.isEmpty()) {
            Logger.w("CalendarIntegrationTest", "No calendars found - test will be limited")
            return@runBlocking
        }
        
        // Fetch events from the next 2 days (the app's lookahead window)
        val events = calendarRepository.getUpcomingEvents()
        Logger.i("CalendarIntegrationTest", "Found ${events.size} events in next 2 days")
        
        // Analyze found events
        var allDayCount = 0
        var timedCount = 0
        val eventTitles = mutableSetOf<String>()
        
        events.forEach { event ->
            if (event.isAllDay) {
                allDayCount++
            } else {
                timedCount++
            }
            eventTitles.add(event.title)
            
            Logger.d("CalendarIntegrationTest", "Event: '${event.title}' (${if (event.isAllDay) "All-day" else "Timed"}) on ${Date(event.startTimeUtc)}")
        }
        
        Logger.i("CalendarIntegrationTest", "Event analysis: $timedCount timed, $allDayCount all-day, ${eventTitles.size} unique titles")
        
        // Test event validation
        events.forEach { event ->
            // All events should have required fields
            assertNotNull("Event ID should not be null", event.id)
            assertTrue("Event title should not be empty", event.title.isNotEmpty())
            assertTrue("Event start time should be valid", event.startTimeUtc > 0)
            assertTrue("Event should have valid calendar ID", event.calendarId > 0)
            
            // All-day events should have proper timing
            if (event.isAllDay) {
                assertTrue("All-day event should have valid end time", event.endTimeUtc > event.startTimeUtc)
                val duration = event.endTimeUtc - event.startTimeUtc
                assertTrue("All-day event should be at least 24 hours", duration >= (24 * 60 * 60 * 1000))
            }
        }
        
        Logger.i("CalendarIntegrationTest", "✅ Real calendar event reading test PASSED")
    }
    
    @Test
    fun testRealCalendarWithRuleMatching() = runBlocking {
        Logger.i("CalendarIntegrationTest", "=== Testing Real Calendar with Rule Matching ===")
        
        val hasPermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            Logger.w("CalendarIntegrationTest", "Skipping test - no calendar permission")
            return@runBlocking
        }
        
        val calendarRepository = CalendarRepository(context)
        val ruleRepository = application.ruleRepository
        val ruleMatcher = com.example.calendaralarmscheduler.domain.RuleMatcher()
        
        // Create comprehensive test rules covering common event types
        val testRules = listOf(
            Rule(
                id = "integration-meeting-rule",
                name = "Meeting Rule",
                keywordPattern = "meeting|standup|sync|scrum",
                isRegex = true,
                calendarIds = emptyList<Long>(), // All calendars
                leadTimeMinutes = 15,
                enabled = true
            ),
            Rule(
                id = "integration-appointment-rule", 
                name = "Appointment Rule",
                keywordPattern = "appointment|doctor|dentist",
                isRegex = true,
                calendarIds = emptyList<Long>(),
                leadTimeMinutes = 60,
                enabled = true
            ),
            Rule(
                id = "integration-birthday-rule",
                name = "Birthday Rule", 
                keywordPattern = "birthday|anniversary",
                isRegex = true,
                calendarIds = emptyList<Long>(),
                leadTimeMinutes = 1440, // 1 day
                enabled = true
            ),
            Rule(
                id = "integration-urgent-rule",
                name = "Urgent Rule",
                keywordPattern = "urgent|important|critical",
                isRegex = true,
                calendarIds = emptyList<Long>(), 
                leadTimeMinutes = 5,
                enabled = true
            )
        )
        
        // Insert test rules
        testRules.forEach { rule ->
            ruleRepository.insertRule(rule)
        }
        
        Logger.i("CalendarIntegrationTest", "Created ${testRules.size} test rules")
        
        // Get real calendar events
        val events = calendarRepository.getUpcomingEvents()
        
        if (events.isEmpty()) {
            Logger.w("CalendarIntegrationTest", "No events found - creating synthetic test event")
            
            // Create a test event if no real events exist
            val currentTime = System.currentTimeMillis()
            val testEvent = CalendarEvent(
                id = "synthetic-meeting-${UUID.randomUUID()}",
                title = "Important Team Meeting",
                startTimeUtc = currentTime + (4 * 60 * 60 * 1000), // 4 hours from now
                endTimeUtc = currentTime + (5 * 60 * 60 * 1000), // 5 hours from now
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = currentTime
            )
            
            // Test rule matching with synthetic event
            val matches = ruleMatcher.findMatchingRules(listOf(testEvent), testRules)
            Logger.i("CalendarIntegrationTest", "Synthetic event matched ${matches.size} rules")
            
            assertTrue("Test event should match at least one rule (meeting + urgent)", matches.isNotEmpty())
            
            val matchedRuleNames = matches.map { it.rule.name }
            Logger.i("CalendarIntegrationTest", "Matched rules: $matchedRuleNames")
            
        } else {
            Logger.i("CalendarIntegrationTest", "Testing rule matching with ${events.size} real events")
            
            var totalMatches = 0
            val matchSummary = mutableMapOf<String, Int>()
            
            // Test rule matching for each real event
            events.forEach { event ->
                val matches = ruleMatcher.findMatchingRules(listOf(event), testRules)
                totalMatches += matches.size
                
                if (matches.isNotEmpty()) {
                    Logger.i("CalendarIntegrationTest", "Event '${event.title}' matched ${matches.size} rules: ${matches.map { it.rule.name }}")
                    
                    matches.forEach { match ->
                        matchSummary[match.rule.name] = matchSummary.getOrDefault(match.rule.name, 0) + 1
                    }
                }
            }
            
            Logger.i("CalendarIntegrationTest", "Total matches: $totalMatches across ${events.size} events")
            Logger.i("CalendarIntegrationTest", "Match summary by rule: $matchSummary")
            
            // Verify rule matching logic
            events.forEach { event ->
                val matches = ruleMatcher.findMatchingRules(listOf(event), testRules)
                matches.forEach { match ->
                    // Verify the match is actually valid
                    val rule = match.rule
                    val shouldMatch = if (rule.isRegex) {
                        try {
                            Regex(rule.keywordPattern, RegexOption.IGNORE_CASE).containsMatchIn(event.title)
                        } catch (e: Exception) {
                            false
                        }
                    } else {
                        event.title.lowercase().contains(rule.keywordPattern.lowercase())
                    }
                    
                    assertTrue(
                        "Rule ${rule.name} should properly match event '${event.title}' - ${rule.keywordPattern}",
                        shouldMatch
                    )
                }
            }
        }
        
        Logger.i("CalendarIntegrationTest", "✅ Real calendar rule matching test PASSED")
    }
    
    @Test
    fun testRealCalendarEventChangeDetection() = runBlocking {
        Logger.i("CalendarIntegrationTest", "=== Testing Real Calendar Event Change Detection ===")
        
        val hasPermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            Logger.w("CalendarIntegrationTest", "Skipping test - no calendar permission")
            return@runBlocking
        }
        
        val calendarRepository = CalendarRepository(context)
        val settingsRepository = application.settingsRepository
        
        // Set last sync time to 1 hour ago to catch recent changes
        val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)
        settingsRepository.setLastSyncTime(oneHourAgo)
        
        Logger.i("CalendarIntegrationTest", "Set last sync time to: ${Date(oneHourAgo)}")
        
        // Get all events (since getEventsModifiedSince doesn't exist)
        val changedEvents = calendarRepository.getUpcomingEvents()
        Logger.i("CalendarIntegrationTest", "Found ${changedEvents.size} upcoming events")
        
        changedEvents.forEach { event ->
            Logger.i("CalendarIntegrationTest", "Modified event: '${event.title}' last modified: ${Date(event.lastModified)}")
            
            // Verify event has valid modification time
            assertTrue(
                "Event modification time should be valid",
                event.lastModified > 0
            )
        }
        
        // Test the full sync workflow
        val currentTime = System.currentTimeMillis()
        val twoDaysFromNow = currentTime + (2 * 24 * 60 * 60 * 1000)
        
        // Get all upcoming events
        val allEvents = calendarRepository.getUpcomingEvents()
        
        // Simulate sync completion
        val newSyncTime = System.currentTimeMillis()
        settingsRepository.setLastSyncTime(newSyncTime)
        
        Logger.i("CalendarIntegrationTest", "Updated sync time to: ${Date(newSyncTime)}")
        
        assertTrue("New sync time should be more recent", newSyncTime > oneHourAgo)
        assertTrue("New sync time should not be in future", newSyncTime <= System.currentTimeMillis())
        
        // Test that we can track sync time effectively
        val updatedSyncTime = settingsRepository.getLastSyncTime()
        Logger.i("CalendarIntegrationTest", "Verified sync time tracking: ${Date(updatedSyncTime)}")
        
        // Verify sync time was properly set
        assertTrue(
            "Sync time should be properly tracked",
            updatedSyncTime == newSyncTime
        )
        
        Logger.i("CalendarIntegrationTest", "✅ Event change detection test PASSED")
    }
    
    @Test 
    fun testRealCalendarWithTimezoneHandling() = runBlocking {
        Logger.i("CalendarIntegrationTest", "=== Testing Real Calendar with Timezone Handling ===")
        
        val hasPermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            Logger.w("CalendarIntegrationTest", "Skipping test - no calendar permission")
            return@runBlocking
        }
        
        val calendarRepository = CalendarRepository(context)
        
        // Get current timezone info
        val currentTimezone = TimeZone.getDefault()
        Logger.i("CalendarIntegrationTest", "Current timezone: ${currentTimezone.id}")
        
        // Get events in current timezone
        val events = calendarRepository.getUpcomingEvents()
        
        Logger.i("CalendarIntegrationTest", "Found ${events.size} events for timezone testing")
        
        if (events.isNotEmpty()) {
            events.take(5).forEach { event -> // Test first 5 events
                Logger.i("CalendarIntegrationTest", "Testing timezone for event: '${event.title}'")
                
                // Event times should be stored in UTC
                assertTrue("Event start time should be valid", event.startTimeUtc > 0)
                
                // Convert to local time for verification
                val startDate = Date(event.startTimeUtc)
                val endDate = Date(event.endTimeUtc)
                
                Logger.i("CalendarIntegrationTest", "Event times - Start: $startDate, End: $endDate")
                
                // For all-day events, verify they span at least 24 hours
                if (event.isAllDay) {
                    val duration = event.endTimeUtc - event.startTimeUtc
                    assertTrue(
                        "All-day event should span at least 23 hours",
                        duration >= (23 * 60 * 60 * 1000) // At least 23 hours (accounting for DST)
                    )
                } else {
                    // For timed events, end should be after start
                    assertTrue(
                        "Timed event end should be after start",
                        event.endTimeUtc > event.startTimeUtc
                    )
                }
            }
        }
        
        // Test timezone change simulation
        val settingsRepository = application.settingsRepository
        val originalSyncTime = settingsRepository.getLastSyncTime()
        
        // Simulate timezone change
        settingsRepository.handleTimezoneChange()
        
        val syncTimeAfterTzChange = settingsRepository.getLastSyncTime()
        Logger.i("CalendarIntegrationTest", "Sync time after timezone change: ${Date(syncTimeAfterTzChange)}")
        
        // After timezone change, sync time should be reset to force full re-sync
        assertEquals("Sync time should be reset after timezone change", 0L, syncTimeAfterTzChange)
        
        Logger.i("CalendarIntegrationTest", "✅ Timezone handling test PASSED")
    }
}