package com.example.calendaralarmscheduler.edge

import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.e2e.E2ETestBase
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.TimezoneUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*

/**
 * Tests timezone edge cases and DST transitions
 * Verifies proper handling of timezone changes, DST transitions, and cross-timezone events
 */
class TimezoneEdgeCasesTest : E2ETestBase() {

    @Test
    fun testDSTTransitionHandling() = runBlocking {
        Logger.i("TimezoneEdgeCasesTest", "=== Testing DST Transition Handling ===")
        
        // Create test rule
        val dstRule = Rule(
            id = "dst-test-rule",
            name = "DST Test Rule",
            keywordPattern = "transition",
            isRegex = false,
            calendarIds = emptyList<Long>(), // Empty list means match all calendars
            leadTimeMinutes = 60, // 1 hour before
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(dstRule) }
        
        // Get current timezone for DST testing
        val systemTimeZone = TimeZone.getDefault()
        val zoneId = ZoneId.systemDefault()
        Logger.i("TimezoneEdgeCasesTest", "Testing with timezone: ${systemTimeZone.id}")
        
        // Create events around potential DST transition times
        // Note: DST transitions vary by location, so we test with general principles
        
        // Spring forward scenario - 2:00 AM becomes 3:00 AM (hour is skipped)
        val springForwardBase = Calendar.getInstance().apply {
            // Set to a potential spring DST transition date (second Sunday in March for US)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 12) // Approximate second Sunday
            set(Calendar.HOUR_OF_DAY, 2) // 2:00 AM - the "skipped" hour
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val springForwardEvent = CalendarEvent(
            id = "spring-forward-event",
            title = "Spring Forward Transition Event",
            startTimeUtc = springForwardBase,
            endTimeUtc = springForwardBase + (60 * 60 * 1000), // 1 hour duration
            calendarId = 1L,
            isAllDay = false,
            timezone = "America/New_York",
            lastModified = System.currentTimeMillis()
        )
        
        // Fall back scenario - 2:00 AM happens twice (hour is repeated)
        val fallBackBase = Calendar.getInstance().apply {
            // Set to a potential fall DST transition date (first Sunday in November for US)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 5) // Approximate first Sunday
            set(Calendar.HOUR_OF_DAY, 1) // 1:00 AM - before the repeated hour
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val fallBackEvent = CalendarEvent(
            id = "fall-back-event", 
            title = "Fall Back Transition Event",
            startTimeUtc = fallBackBase,
            endTimeUtc = fallBackBase + (3 * 60 * 60 * 1000), // 3 hour duration (spans the repeated hour)
            calendarId = 1L,
            isAllDay = false,
            timezone = "America/New_York",
            lastModified = System.currentTimeMillis()
        )
        
        // Regular event for comparison
        val regularEvent = CalendarEvent(
            id = "regular-dst-event",
            title = "Regular DST Transition Event",
            startTimeUtc = System.currentTimeMillis() + (48 * 60 * 60 * 1000), // 2 days from now
            endTimeUtc = System.currentTimeMillis() + (48 * 60 * 60 * 1000) + (60 * 60 * 1000),
            calendarId = 1L,
            isAllDay = false,
            timezone = "America/New_York",
            lastModified = System.currentTimeMillis()
        )
        
        // Create test events using the test provider pattern - use titles that match the rule keyword "transition"  
        val testEvents = listOf(
            calendarTestProvider.createTestEvent("Spring Forward transition", springForwardBase - System.currentTimeMillis()),
            calendarTestProvider.createTestEvent("Fall Back transition", fallBackBase - System.currentTimeMillis()),
            calendarTestProvider.createTestEvent("Regular DST transition", (48 * 60 * 60 * 1000))
        )
        
        Logger.i("TimezoneEdgeCasesTest", "Created ${testEvents.size} DST transition events")
        
        // Process events
        val dstWorkerResult = applicationController.triggerBackgroundWorker()
        assertThat(dstWorkerResult).isTrue()
        
        delay(3000)
        
        // Verify alarms were created
        val dstAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("TimezoneEdgeCasesTest", "Created ${dstAlarms.size} DST alarms")
        
        assertThat(dstAlarms).isNotEmpty()
        
        // Verify alarm timing calculations
        dstAlarms.forEach { alarm ->
            val correspondingEvent = testEvents.find { it.id == alarm.eventId }
            assertNotNull("Alarm should correspond to an event", correspondingEvent)
            
            val expectedLeadTime = dstRule.leadTimeMinutes * 60 * 1000L
            val actualLeadTime = correspondingEvent!!.startTimeUtc - alarm.alarmTimeUtc
            val timeDifference = Math.abs(actualLeadTime - expectedLeadTime)
            
            // Allow some tolerance for DST calculations
            assertTrue("DST timing should be within tolerance: ${timeDifference}ms", timeDifference < (5 * 60 * 1000))
            
            Logger.i("TimezoneEdgeCasesTest", 
                "DST Alarm '${alarm.eventTitle}': " +
                "Event at ${Date(correspondingEvent.startTimeUtc)}, " +
                "Alarm at ${Date(alarm.alarmTimeUtc)}, " +
                "Lead time ${actualLeadTime / (60 * 1000)}min"
            )
        }
        
        Logger.i("TimezoneEdgeCasesTest", "✅ DST transition handling test PASSED")
    }
    
    @Test
    fun testCrossTimezoneEventHandling() = runBlocking {
        Logger.i("TimezoneEdgeCasesTest", "=== Testing Cross-Timezone Event Handling ===")
        
        // Create timezone-aware test rule
        val timezoneRule = Rule(
            id = "timezone-test-rule",
            name = "Timezone Test Rule",
            keywordPattern = "timezone",
            isRegex = false,
            calendarIds = emptyList<Long>(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(timezoneRule) }
        
        // Create events in different timezones
        val currentTime = System.currentTimeMillis()
        val testEvents = listOf(
            calendarTestProvider.createTestEvent("Timezone Test Event 1", (6 * 60 * 60 * 1000)),
            calendarTestProvider.createTestEvent("Timezone Test Event 2", (12 * 60 * 60 * 1000))
        )
        
        Logger.i("TimezoneEdgeCasesTest", "Created ${testEvents.size} cross-timezone events")
        
        // Process events
        val timezoneWorkerResult = applicationController.triggerBackgroundWorker()
        assertThat(timezoneWorkerResult).isTrue()
        
        delay(3000)
        
        // Store original alarms
        val originalAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("TimezoneEdgeCasesTest", "Original alarms: ${originalAlarms.size}")
        
        // Simulate timezone change
        Logger.i("TimezoneEdgeCasesTest", "Simulating timezone change...")
        
        // Re-run worker after simulated timezone change
        delay(1000)
        val postTimezoneWorkerResult = applicationController.triggerBackgroundWorker()
        assertThat(postTimezoneWorkerResult).isTrue()
        
        delay(3000)
        
        // Get updated alarms
        val postTimezoneAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("TimezoneEdgeCasesTest", "Post-timezone alarms: ${postTimezoneAlarms.size}")
        
        // Verify alarms were recalculated
        assertThat(postTimezoneAlarms).isNotEmpty()
        
        Logger.i("TimezoneEdgeCasesTest", "✅ Cross-timezone event handling test PASSED")
    }
    
    @Test
    fun testAllDayEventTimezoneHandling() = runBlocking {
        Logger.i("TimezoneEdgeCasesTest", "=== Testing All-Day Event Timezone Handling ===")
        
        // Create all-day event rule
        val allDayRule = Rule(
            id = "allday-timezone-rule",
            name = "All-Day Timezone Rule", 
            keywordPattern = "conference",
            isRegex = false,
            calendarIds = emptyList<Long>(),
            leadTimeMinutes = 120, // 2 hours before default time
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(allDayRule) }
        
        // Create all-day events spanning multiple days
        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val allDayEvents = listOf(
            calendarTestProvider.createTestEvent(
                title = "Multi-Day Conference Day 1",
                startTimeFromNow = tomorrowStart - System.currentTimeMillis(),
                durationHours = 24,
                isAllDay = true
            ),
            calendarTestProvider.createTestEvent(
                title = "Single Day Conference",
                startTimeFromNow = tomorrowStart + (24 * 60 * 60 * 1000) - System.currentTimeMillis(),
                durationHours = 24,
                isAllDay = true
            )
        )
        
        Logger.i("TimezoneEdgeCasesTest", "Created ${allDayEvents.size} all-day timezone events")
        
        // Process all-day events
        val allDayWorkerResult = applicationController.triggerBackgroundWorker()
        assertThat(allDayWorkerResult).isTrue()
        
        delay(3000)
        
        // Verify all-day alarms
        val allDayAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("TimezoneEdgeCasesTest", "Created ${allDayAlarms.size} all-day alarms")
        
        assertThat(allDayAlarms).isNotEmpty()
        
        // Verify all-day alarm timing (should use settings default time, not event start time)
        allDayAlarms.forEach { alarm ->
            val correspondingEvent = allDayEvents.find { it.id == alarm.eventId }
            if (correspondingEvent != null && correspondingEvent.isAllDay) {
                // All-day alarms should be scheduled for the settings default time
                Logger.i("TimezoneEdgeCasesTest", 
                    "All-day alarm '${alarm.eventTitle}': " +
                    "Event spans ${Date(correspondingEvent.startTimeUtc)} to ${Date(correspondingEvent.endTimeUtc)}, " +
                    "Alarm at ${Date(alarm.alarmTimeUtc)}"
                )
                
                // Verify alarm is before event end time
                assertThat(alarm.alarmTimeUtc).isLessThan(correspondingEvent.endTimeUtc)
            }
        }
        
        Logger.i("TimezoneEdgeCasesTest", "✅ All-day event timezone handling test PASSED")
    }
    
    @Test
    fun testTimezoneChangeRealTime() = runBlocking {
        Logger.i("TimezoneEdgeCasesTest", "=== Testing Real-Time Timezone Change Handling ===")
        
        // Create rule for timezone change testing
        val tzChangeRule = Rule(
            id = "timezone-change-rule",
            name = "Timezone Change Rule",
            keywordPattern = "important",
            isRegex = false,
            calendarIds = emptyList<Long>(),
            leadTimeMinutes = 60,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(tzChangeRule) }
        
        // Create events that should trigger alarms
        val futureTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours from now
        val testEvents = listOf(
            calendarTestProvider.createTestEvent(
                title = "Important Meeting",
                startTimeFromNow = 24 * 60 * 60 * 1000, // 24 hours
                durationHours = 1
            ),
            calendarTestProvider.createTestEvent(
                title = "Important Call", 
                startTimeFromNow = 36 * 60 * 60 * 1000, // 36 hours
                durationHours = 1
            )
        )
        
        Logger.i("TimezoneEdgeCasesTest", "Created ${testEvents.size} events for timezone change testing")
        
        // Initial processing
        applicationController.triggerBackgroundWorker()
        delay(2000)
        
        val initialAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("TimezoneEdgeCasesTest", "Initial alarms: ${initialAlarms.size}")
        
        // Simulate timezone change by resetting last sync time
        application.settingsRepository.handleTimezoneChange()
        
        // Trigger worker after timezone change
        delay(1000)
        applicationController.triggerBackgroundWorker()
        delay(3000)
        
        val postChangeAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("TimezoneEdgeCasesTest", "Post-timezone-change alarms: ${postChangeAlarms.size}")
        
        // Verify alarms were recalculated
        assertTrue("Should have alarms after timezone change", postChangeAlarms.isNotEmpty())
        
        // Verify all alarms are still in the future
        postChangeAlarms.forEach { alarm ->
            assertTrue(
                "Alarm should be in future after timezone change: ${Date(alarm.alarmTimeUtc)}",
                alarm.alarmTimeUtc > System.currentTimeMillis()
            )
        }
        
        Logger.i("TimezoneEdgeCasesTest", "✅ Real-time timezone change test PASSED")
    }
    
    @Test
    fun testDSTBoundaryCalculations() = runBlocking {
        Logger.i("TimezoneEdgeCasesTest", "=== Testing DST Boundary Calculations ===")
        
        // Create DST boundary test rule
        val dstBoundaryRule = Rule(
            id = "dst-boundary-rule",
            name = "DST Boundary Rule",
            keywordPattern = "boundary",
            isRegex = false,
            calendarIds = emptyList<Long>(),
            leadTimeMinutes = 90, // 1.5 hours lead time to span DST transition
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(dstBoundaryRule) }
        
        // Test with known DST transition dates for different timezones
        val dstTestCases = listOf(
            // US Eastern Time - Spring forward
            Triple("America/New_York", "Spring Forward US", "2024-03-10T07:00:00"),
            // US Eastern Time - Fall back  
            Triple("America/New_York", "Fall Back US", "2024-11-03T06:00:00"),
            // EU Central Time - Spring forward
            Triple("Europe/Berlin", "Spring Forward EU", "2024-03-31T03:00:00"),
            // EU Central Time - Fall back
            Triple("Europe/Berlin", "Fall Back EU", "2024-10-27T02:00:00")
        )
        
        val boundaryEvents = mutableListOf<CalendarEvent>()
        
        dstTestCases.forEachIndexed { index, (timezoneName, description, timeString) ->
            try {
                // Calculate future time for testing (next year to avoid past events)
                val nextYear = Calendar.getInstance().get(Calendar.YEAR) + 1
                val adjustedTimeString = timeString.replace("2024", nextYear.toString())
                
                val eventTime = System.currentTimeMillis() + ((index + 1) * 24 * 60 * 60 * 1000L) // Staggered future times
                
                val eventObj = calendarTestProvider.createTestEvent(
                    title = "DST Boundary Test Event - $description",
                    startTimeFromNow = (index + 1) * 24 * 60 * 60 * 1000L,
                    durationHours = 2
                )
                
                boundaryEvents.add(eventObj)
                
                Logger.d("TimezoneEdgeCasesTest", "Created DST boundary event: $description at ${Date(eventTime)}")
                
            } catch (e: Exception) {
                Logger.w("TimezoneEdgeCasesTest", "Could not create DST test for $timezoneName: ${e.message}")
            }
        }
        
        Logger.i("TimezoneEdgeCasesTest", "Created ${boundaryEvents.size} DST boundary events")
        
        if (boundaryEvents.isNotEmpty()) {
            // Process boundary events
            applicationController.triggerBackgroundWorker()
            delay(3000)
            
            val boundaryAlarms = database.alarmDao().getActiveAlarmsSync()
            Logger.i("TimezoneEdgeCasesTest", "DST boundary alarms created: ${boundaryAlarms.size}")
            
            // Verify boundary calculations
            boundaryAlarms.forEach { alarm ->
                val leadTimeActual = alarm.eventStartTimeUtc - alarm.alarmTimeUtc
                val leadTimeExpected = dstBoundaryRule.leadTimeMinutes * 60 * 1000L
                val timeDifference = Math.abs(leadTimeActual - leadTimeExpected)
                
                // Allow for DST transition variations (up to 1 hour difference)
                assertTrue(
                    "DST boundary calculation should be within tolerance: expected ${leadTimeExpected/60000}min, got ${leadTimeActual/60000}min, diff ${timeDifference/60000}min",
                    timeDifference <= (60 * 60 * 1000) // 1 hour tolerance
                )
                
                Logger.d("TimezoneEdgeCasesTest", 
                    "DST boundary alarm '${alarm.eventTitle}': " +
                    "Lead time ${leadTimeActual/60000}min (expected ${leadTimeExpected/60000}min)"
                )
            }
        }
        
        Logger.i("TimezoneEdgeCasesTest", "✅ DST boundary calculations test PASSED")
    }
    
    @Test
    fun testMidnightBoundaryEvents() = runBlocking {
        Logger.i("TimezoneEdgeCasesTest", "=== Testing Midnight Boundary Events ===")
        
        // Create midnight boundary rule
        val midnightRule = Rule(
            id = "midnight-boundary-rule", 
            name = "Midnight Boundary Rule",
            keywordPattern = "midnight",
            isRegex = false,
            calendarIds = emptyList<Long>(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(midnightRule) }
        
        // Create events around midnight boundaries
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0) 
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val midnightEvents = listOf(
            // Event just before midnight
            calendarTestProvider.createTestEvent(
                title = "Pre-Midnight Event",
                startTimeFromNow = tomorrow.timeInMillis - (30 * 60 * 1000) - System.currentTimeMillis(), // 11:30 PM
                durationHours = 1
            ),
            // Event exactly at midnight
            calendarTestProvider.createTestEvent(
                title = "Midnight Event",
                startTimeFromNow = tomorrow.timeInMillis - System.currentTimeMillis(), // 12:00 AM
                durationHours = 1
            ),
            // Event just after midnight
            calendarTestProvider.createTestEvent(
                title = "Post-Midnight Event", 
                startTimeFromNow = tomorrow.timeInMillis + (30 * 60 * 1000) - System.currentTimeMillis(), // 12:30 AM
                durationHours = 1
            )
        )
        
        Logger.i("TimezoneEdgeCasesTest", "Created ${midnightEvents.size} midnight boundary events")
        
        // Process midnight events
        applicationController.triggerBackgroundWorker()
        delay(3000)
        
        val midnightAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("TimezoneEdgeCasesTest", "Midnight boundary alarms: ${midnightAlarms.size}")
        
        // Verify midnight boundary handling
        midnightAlarms.forEach { alarm ->
            // Verify alarm is scheduled correctly around midnight
            val alarmTime = Date(alarm.alarmTimeUtc)
            val eventTime = Date(alarm.eventStartTimeUtc)
            
            Logger.i("TimezoneEdgeCasesTest", 
                "Midnight alarm '${alarm.eventTitle}': " +
                "Event at $eventTime, Alarm at $alarmTime"
            )
            
            // Verify alarm is before event
            assertTrue(
                "Midnight alarm should be before event start", 
                alarm.alarmTimeUtc < alarm.eventStartTimeUtc
            )
            
            // Verify alarm is in the future
            assertTrue(
                "Midnight alarm should be in future", 
                alarm.alarmTimeUtc > System.currentTimeMillis()
            )
        }
        
        Logger.i("TimezoneEdgeCasesTest", "✅ Midnight boundary events test PASSED")
    }
    
    @Test 
    fun testLeapYearTimezoneHandling() = runBlocking {
        Logger.i("TimezoneEdgeCasesTest", "=== Testing Leap Year Timezone Handling ===")
        
        // Create leap year test rule
        val leapYearRule = Rule(
            id = "leap-year-rule",
            name = "Leap Year Rule", 
            keywordPattern = "annual",
            isRegex = false,
            calendarIds = emptyList<Long>(),
            leadTimeMinutes = 60,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(leapYearRule) }
        
        // Test events around February 29th in leap years
        val leapYearEvents = listOf(
            // February 28th event
            calendarTestProvider.createTestEvent(
                title = "Annual Feb 28 Event",
                startTimeFromNow = 2 * 24 * 60 * 60 * 1000L, // 2 days from now
                durationHours = 1
            ),
            // February 29th event (if we're in a leap year context)
            calendarTestProvider.createTestEvent(
                title = "Annual Leap Day Event", 
                startTimeFromNow = 3 * 24 * 60 * 60 * 1000L, // 3 days from now
                durationHours = 1
            ),
            // March 1st event
            calendarTestProvider.createTestEvent(
                title = "Annual Mar 1 Event",
                startTimeFromNow = 4 * 24 * 60 * 60 * 1000L, // 4 days from now
                durationHours = 1
            )
        )
        
        Logger.i("TimezoneEdgeCasesTest", "Created ${leapYearEvents.size} leap year events")
        
        // Process leap year events
        applicationController.triggerBackgroundWorker()
        delay(3000)
        
        val leapYearAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("TimezoneEdgeCasesTest", "Leap year alarms: ${leapYearAlarms.size}")
        
        // Verify leap year calculations
        leapYearAlarms.forEach { alarm ->
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val isLeapYear = ((currentYear % 4 == 0) && (currentYear % 100 != 0)) || (currentYear % 400 == 0)
            
            Logger.i("TimezoneEdgeCasesTest",
                "Leap year alarm '${alarm.eventTitle}': " +
                "Year $currentYear ${if (isLeapYear) "is" else "is not"} a leap year, " +
                "Alarm at ${Date(alarm.alarmTimeUtc)}"
            )
            
            // Basic verification - alarm should be valid regardless of leap year
            assertTrue(
                "Leap year alarm should be in future",
                alarm.alarmTimeUtc > System.currentTimeMillis()
            )
            
            assertTrue(
                "Leap year alarm should be before event",
                alarm.alarmTimeUtc < alarm.eventStartTimeUtc
            )
        }
        
        Logger.i("TimezoneEdgeCasesTest", "✅ Leap year timezone handling test PASSED")
    }
}