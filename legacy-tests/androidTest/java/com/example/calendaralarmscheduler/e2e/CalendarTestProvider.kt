package com.example.calendaralarmscheduler.e2e

import android.content.Context
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Provider for test calendar events to support E2E testing
 * Creates predictable calendar events for testing alarm scheduling functionality
 */
class CalendarTestProvider(private val context: Context) {
    
    companion object {
        private const val TEST_CALENDAR_ID = 999L // Use high ID to avoid conflicts
        private val testEvents = mutableListOf<CalendarEvent>()
        private var eventIdCounter = 1000
    }

    /**
     * Create a test calendar event
     */
    fun createTestEvent(
        title: String,
        startTimeFromNow: Long, // milliseconds from now
        durationHours: Int = 1,
        isAllDay: Boolean = false,
        calendarId: Long = TEST_CALENDAR_ID
    ): CalendarEvent {
        val eventId = (eventIdCounter++).toString()
        val now = System.currentTimeMillis()
        val startTime = now + startTimeFromNow
        val endTime = if (isAllDay) {
            startTime + (24 * 60 * 60 * 1000) // Full day
        } else {
            startTime + (durationHours * 60 * 60 * 1000)
        }

        val event = CalendarEvent(
            id = eventId,
            title = title,
            startTimeUtc = startTime,
            endTimeUtc = endTime,
            calendarId = calendarId,
            isAllDay = isAllDay,
            lastModified = now,
            timezone = TimeZone.getDefault().id
        )

        testEvents.add(event)
        android.util.Log.i("CalendarTestProvider", "Created test event: $title at ${formatTime(startTime)}")
        
        return event
    }

    /**
     * Create multiple test events for comprehensive testing
     */
    fun createTestEventSuite(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val now = System.currentTimeMillis()
        val hour = 60 * 60 * 1000L
        val day = 24 * hour

        // Event in 2 hours - should trigger alarm
        events.add(createTestEvent(
            title = "Important Meeting",
            startTimeFromNow = 2 * hour,
            durationHours = 1
        ))

        // Doctor appointment tomorrow - should trigger alarm
        events.add(createTestEvent(
            title = "Doctor Appointment", 
            startTimeFromNow = day + 9 * hour,
            durationHours = 1
        ))

        // All-day conference day after tomorrow
        events.add(createTestEvent(
            title = "All Day Conference",
            startTimeFromNow = 2 * day,
            durationHours = 24,
            isAllDay = true
        ))

        // Team standup (recurring pattern simulation)
        for (i in 0..4) { // Next 5 weekdays
            events.add(createTestEvent(
                title = "Team Standup",
                startTimeFromNow = i * day + 10 * hour,
                durationHours = 1
            ))
        }

        // Event that won't match any rules
        events.add(createTestEvent(
            title = "Personal Event",
            startTimeFromNow = 3 * hour,
            durationHours = 2
        ))

        // Event in the past (should be ignored)
        events.add(createTestEvent(
            title = "Past Meeting", 
            startTimeFromNow = -2 * hour,
            durationHours = 1
        ))

        android.util.Log.i("CalendarTestProvider", "Created test event suite: ${events.size} events")
        return events
    }

    /**
     * Create events with specific keywords for rule testing
     */
    fun createEventsForKeywordTesting(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val hour = 60 * 60 * 1000L

        // Medical keyword events
        events.add(createTestEvent("Doctor Visit", 3 * hour))
        events.add(createTestEvent("Dental Checkup", 4 * hour))
        events.add(createTestEvent("Medical Appointment", 5 * hour))

        // Meeting keyword events  
        events.add(createTestEvent("Board Meeting", 6 * hour))
        events.add(createTestEvent("Team Meeting", 7 * hour))
        events.add(createTestEvent("Client Meeting", 8 * hour))

        // Work keyword events
        events.add(createTestEvent("Work Review", 9 * hour))
        events.add(createTestEvent("Work Session", 10 * hour))

        // Events that shouldn't match common rules
        events.add(createTestEvent("Lunch Break", 11 * hour))
        events.add(createTestEvent("Personal Time", 12 * hour))
        events.add(createTestEvent("Grocery Shopping", 13 * hour))

        android.util.Log.i("CalendarTestProvider", "Created keyword test events: ${events.size} events")
        return events
    }

    /**
     * Create events with regex pattern testing
     */
    fun createEventsForRegexTesting(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val hour = 60 * 60 * 1000L

        // Test case-insensitive matching
        events.add(createTestEvent("IMPORTANT meeting", 2 * hour))
        events.add(createTestEvent("important Meeting", 3 * hour))
        events.add(createTestEvent("Very Important", 4 * hour))

        // Test regex patterns
        events.add(createTestEvent("Call-555-1234", 5 * hour))
        events.add(createTestEvent("Call 555-5678", 6 * hour))
        events.add(createTestEvent("Phone: 555-9999", 7 * hour))

        // Test word boundaries
        events.add(createTestEvent("Meeting Room", 8 * hour))
        events.add(createTestEvent("Team Meeting", 9 * hour))
        events.add(createTestEvent("Meetingroom", 10 * hour)) // Should not match if using \b

        return events
    }

    /**
     * Create events for timezone testing
     */
    fun createEventsForTimezoneTests(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val hour = 60 * 60 * 1000L

        // Events across different hours to test timezone handling
        for (i in 1..24) {
            events.add(createTestEvent(
                title = "TZ Test Event $i",
                startTimeFromNow = i * hour,
                durationHours = 1
            ))
        }

        return events
    }

    /**
     * Create modified version of existing event (for change detection testing)
     */
    fun createModifiedEvent(originalEvent: CalendarEvent, newTitle: String): CalendarEvent {
        val modifiedEvent = originalEvent.copy(
            title = newTitle,
            lastModified = System.currentTimeMillis()
        )
        
        // Replace in test events list
        val index = testEvents.indexOfFirst { it.id == originalEvent.id }
        if (index >= 0) {
            testEvents[index] = modifiedEvent
        }
        
        return modifiedEvent
    }

    /**
     * Get all test events created
     */
    fun getAllTestEvents(): List<CalendarEvent> {
        return testEvents.toList()
    }

    /**
     * Get test events in time range (simulates calendar provider query)
     */
    fun getTestEventsInRange(startTime: Long, endTime: Long): List<CalendarEvent> {
        return testEvents.filter { event ->
            event.startTimeUtc >= startTime && event.startTimeUtc <= endTime
        }.sortedBy { it.startTimeUtc }
    }

    /**
     * Get events modified after a specific time
     */
    fun getEventsModifiedAfter(lastSyncTime: Long): List<CalendarEvent> {
        return testEvents.filter { it.lastModified > lastSyncTime }
    }

    /**
     * Clean up all test events
     */
    fun cleanupTestEvents() {
        android.util.Log.i("CalendarTestProvider", "Cleaning up ${testEvents.size} test events")
        testEvents.clear()
        eventIdCounter = 1000
    }

    /**
     * Create event that will occur in X minutes (for immediate testing)
     */
    fun createImmediateTestEvent(minutesFromNow: Int, title: String = "Test Alarm Event"): CalendarEvent {
        return createTestEvent(
            title = title,
            startTimeFromNow = minutesFromNow * 60 * 1000L,
            durationHours = 1
        )
    }

    /**
     * Verify if an event exists in our test set
     */
    fun hasTestEvent(eventId: String): Boolean {
        return testEvents.any { it.id == eventId }
    }

    /**
     * Get test event by ID
     */
    fun getTestEvent(eventId: String): CalendarEvent? {
        return testEvents.find { it.id == eventId }
    }

    /**
     * Update event's last modified time (simulates calendar change)
     */
    fun touchEvent(eventId: String) {
        val index = testEvents.indexOfFirst { it.id == eventId }
        if (index >= 0) {
            testEvents[index] = testEvents[index].copy(lastModified = System.currentTimeMillis())
        }
    }

    /**
     * Create events for different calendars
     */
    fun createMultiCalendarEvents(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val hour = 60 * 60 * 1000L

        // Work calendar events
        events.add(createTestEvent("Work Meeting", 2 * hour, calendarId = 1L))
        events.add(createTestEvent("Project Review", 3 * hour, calendarId = 1L))

        // Personal calendar events
        events.add(createTestEvent("Doctor Appointment", 4 * hour, calendarId = 2L))
        events.add(createTestEvent("Dentist Visit", 5 * hour, calendarId = 2L))

        // Family calendar events
        events.add(createTestEvent("Family Dinner", 6 * hour, calendarId = 3L))
        events.add(createTestEvent("School Event", 7 * hour, calendarId = 3L))

        return events
    }

    // Helper methods

    private fun formatTime(timeUtc: Long): String {
        return ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(timeUtc),
            java.time.ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("MMM dd HH:mm"))
    }

    /**
     * Generate events for stress testing (large number of events)
     */
    fun createStressTestEvents(count: Int = 1000): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val hour = 60 * 60 * 1000L
        val day = 24 * hour

        for (i in 1..count) {
            events.add(createTestEvent(
                title = "Stress Test Event $i",
                startTimeFromNow = (i * hour) % (30 * day), // Spread over 30 days
                durationHours = 1,
                calendarId = (i % 5) + 1L // Rotate through 5 calendars
            ))
        }

        android.util.Log.i("CalendarTestProvider", "Created ${events.size} stress test events")
        return events
    }
}