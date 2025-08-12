package com.example.calendaralarmscheduler

import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.util.*

/**
 * Test utility for working with predefined test calendar events.
 * Validates and provides access to deterministic calendar data created by setup_test_calendar.sh.
 * No longer creates calendar events - works with pre-populated test data.
 */
class CalendarTestDataProvider {
    
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contentResolver: ContentResolver = context.contentResolver
    
    companion object {
        // Test calendar baseline: Monday Aug 11, 2025 9:00 AM PDT
        private const val BASELINE_EPOCH = 1723392000000L
        private const val ONE_HOUR = 3600000L
        
        // Expected test event titles for validation (proper titles with spaces)
        private val EXPECTED_TEST_EVENTS = listOf(
            "Important Client Call",
            "Doctor Appointment Follow-up", 
            "Team Meeting Weekly",
            "Important Project Review",
            "Morning Standup Meeting",
            "Doctor Visit Annual",
            "Important Conference Call",
            "Weekly Team Meeting",
            "Important Budget Meeting",
            "Doctor Consultation",
            "Important Quarterly Review",
            "Conference Day Important",
            "Training Workshop",
            "Important Meeting Doctor Review",
            "Important Doctor Consultation Meeting"
        )
    }
    
    /**
     * Simplified CalendarEvent data class for testing
     */
    data class CalendarEvent(
        val id: Long,
        val title: String,
        val description: String,
        val startTime: Long,
        val endTime: Long,
        val isAllDay: Boolean,
        val location: String,
        val calendarId: Long,
        val lastModified: Long
    )
    
    /**
     * Validate that test calendar environment is properly set up
     */
    fun validateTestCalendarSetup(): Boolean {
        return try {
            val testEvents = queryTestEvents()
            val foundTitles = testEvents.map { it.title }
            
            Log.i("CalendarTestData", "Found ${testEvents.size} total calendar events")
            
            // Check for presence of key test events
            val keyEventsFound = EXPECTED_TEST_EVENTS.count { expectedTitle ->
                foundTitles.any { it.contains(expectedTitle) }
            }
            
            Log.i("CalendarTestData", "Found $keyEventsFound/${EXPECTED_TEST_EVENTS.size} expected test events")
            
            // Log some example events for debugging
            testEvents.take(5).forEach { event ->
                Log.i("CalendarTestData", "  Event: ${event.title} at ${Date(event.startTime)}")
            }
            
            // Validate minimum test coverage
            keyEventsFound >= 10 // At least 10 key test events should exist
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to validate test calendar setup", e)
            false
        }
    }
    
    /**
     * Get existing primary calendar ID
     */
    fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        
        return try {
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val calendarId = cursor.getLong(0)
                    val displayName = cursor.getString(1)
                    val accountName = cursor.getString(2)
                    
                    Log.i("CalendarTestData", "Found calendar: $displayName (ID: $calendarId, Account: $accountName)")
                    calendarId
                } else null
            }
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to get primary calendar", e)
            null
        }
    }
    
    /**
     * Get events matching a specific keyword pattern (for rule testing)
     * Note: Keywords match space-separated titles (Important matches "Important Client Call")
     */
    fun getEventsMatchingKeyword(keyword: String): List<CalendarEvent> {
        return try {
            queryEvents().filter { event ->
                // Match keyword in space-separated titles (case insensitive)
                val title = event.title.lowercase()
                val keywordLower = keyword.lowercase()
                title.contains(keywordLower)
            }.also { matchingEvents ->
                Log.i("CalendarTestData", "Found ${matchingEvents.size} events matching '$keyword'")
                matchingEvents.take(3).forEach { event ->
                    Log.i("CalendarTestData", "  Match: ${event.title}")
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to get events matching '$keyword'", e)
            emptyList()
        }
    }
    
    /**
     * Query and validate predefined test events exist (replaces createTestEventSuite)
     */
    fun queryTestEvents(): List<CalendarEvent> {
        return try {
            val allEvents = queryEvents()
            Log.i("CalendarTestData", "Found ${allEvents.size} total calendar events")
            
            // Log sample of events for debugging
            allEvents.take(10).forEach { event ->
                Log.i("CalendarTestData", "  Event: ${event.title} at ${Date(event.startTime)}")
            }
            
            allEvents
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to query test events", e)
            emptyList()
        }
    }
    
    /**
     * Get predefined stress test events (replaces createStressTestEventSuite)
     */
    fun getStressTestEvents(): List<CalendarEvent> {
        return try {
            queryEvents().filter { event ->
                event.title.startsWith("Stress Test Event")
            }.also { stressEvents ->
                Log.i("CalendarTestData", "Found ${stressEvents.size} stress test events")
            }
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to get stress test events", e)
            emptyList()
        }
    }
    
    /**
     * Get predefined future events for background refresh testing
     */
    fun getFutureEvents(): List<CalendarEvent> {
        return try {
            val now = System.currentTimeMillis()
            val threeDays = 3 * 24 * 60 * 60 * 1000L
            
            queryEvents().filter { event ->
                event.startTime > (now + threeDays) // Events more than 3 days out
            }.also { futureEvents ->
                Log.i("CalendarTestData", "Found ${futureEvents.size} future events (3+ days out)")
            }
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to get future events", e)
            emptyList()
        }
    }
    
    /**
     * Get events that should match multiple rules (for comprehensive testing)
     * Note: Works with space-separated titles
     */
    fun getMultiRuleMatchingEvents(): List<CalendarEvent> {
        return try {
            queryEvents().filter { event ->
                // Events containing multiple keywords (space-separated titles)
                val title = event.title.lowercase()
                val hasImportant = title.contains("important")
                val hasMeeting = title.contains("meeting") 
                val hasDoctor = title.contains("doctor")
                
                // Count how many keywords match
                val matchCount = listOf(hasImportant, hasMeeting, hasDoctor).count { it }
                matchCount >= 2 // Events matching 2+ keywords
            }.also { multiMatchEvents ->
                Log.i("CalendarTestData", "Found ${multiMatchEvents.size} events matching multiple rules")
                multiMatchEvents.forEach { event ->
                    Log.i("CalendarTestData", "  Multi-match: ${event.title}")
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to get multi-rule matching events", e)
            emptyList()
        }
    }
    
    /**
     * Query events for verification
     */
    fun queryEvents(calendarId: Long? = null, fromTime: Long = System.currentTimeMillis()): List<CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_ID
        )
        
        val selection = if (calendarId != null) {
            "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DTSTART} >= ?"
        } else {
            "${CalendarContract.Events.DTSTART} >= ?"
        }
        
        val selectionArgs = if (calendarId != null) {
            arrayOf(calendarId.toString(), fromTime.toString())
        } else {
            arrayOf(fromTime.toString())
        }
        
        val events = mutableListOf<CalendarEvent>()
        
        try {
            contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val event = CalendarEvent(
                        id = cursor.getLong(0),
                        title = cursor.getString(1) ?: "",
                        description = cursor.getString(2) ?: "",
                        startTime = cursor.getLong(3),
                        endTime = cursor.getLong(4),
                        isAllDay = cursor.getInt(5) == 1,
                        location = cursor.getString(6) ?: "",
                        calendarId = cursor.getLong(7),
                        lastModified = System.currentTimeMillis()
                    )
                    events.add(event)
                }
            }
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to query events", e)
        }
        
        return events
    }
    
    /**
     * Get all events (including past ones) for comprehensive testing
     */
    fun queryAllEvents(): List<CalendarEvent> {
        return queryEvents(fromTime = 0) // Get all events from epoch
    }
    
    /**
     * Clean up any accidentally created test data (main test events should persist)
     */
    fun cleanup() {
        Log.i("CalendarTestData", "Cleanup completed - test calendar events preserved")
        // Note: We no longer clean up test events since they're predefined
        // The setup_test_calendar.sh script manages the test data lifecycle
    }
}