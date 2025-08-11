package com.example.calendaralarmscheduler

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.util.*

/**
 * Test utility for injecting calendar events and managing test calendar data.
 * Provides controlled calendar environment for comprehensive E2E testing.
 */
class CalendarTestDataProvider {
    
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contentResolver: ContentResolver = context.contentResolver
    private val createdEvents = mutableListOf<Long>()
    private val createdCalendars = mutableListOf<Long>()
    
    companion object {
        private const val TEST_CALENDAR_NAME = "CalendarAlarmScheduler_Test_Calendar"
        private const val TEST_ACCOUNT_NAME = "test_account@calendaralarmscheduler.com"
        private const val TEST_ACCOUNT_TYPE = "com.example.calendaralarmscheduler.test"
    }
    
    data class TestCalendarEvent(
        val title: String,
        val description: String = "",
        val startTime: Long,
        val endTime: Long,
        val allDay: Boolean = false,
        val location: String = "",
        val calendarId: Long? = null
    )
    
    /**
     * Create a test calendar for isolated testing
     */
    fun createTestCalendar(): Long? {
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.NAME, TEST_CALENDAR_NAME)
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Test Calendar for E2E")
                put(CalendarContract.Calendars.ACCOUNT_NAME, TEST_ACCOUNT_NAME)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE)
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0000FF.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, TEST_ACCOUNT_NAME)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.VISIBLE, 1)
            }
            
            val uri = contentResolver.insert(CalendarContract.Calendars.CONTENT_URI, values)
            val calendarId = uri?.lastPathSegment?.toLongOrNull()
            
            if (calendarId != null) {
                createdCalendars.add(calendarId)
                Log.i("CalendarTestData", "Created test calendar with ID: $calendarId")
            }
            
            calendarId
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to create test calendar", e)
            null
        }
    }
    
    /**
     * Get existing calendar ID (uses primary Google Calendar if available)
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
     * Inject a test calendar event
     */
    fun injectEvent(event: TestCalendarEvent): Long? {
        val calendarId = event.calendarId ?: getPrimaryCalendarId() ?: createTestCalendar()
        
        if (calendarId == null) {
            Log.e("CalendarTestData", "No calendar available for event injection")
            return null
        }
        
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Events.TITLE, event.title)
                put(CalendarContract.Events.DESCRIPTION, event.description)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.DTSTART, event.startTime)
                put(CalendarContract.Events.DTEND, event.endTime)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.EVENT_LOCATION, event.location)
                
                if (event.allDay) {
                    put(CalendarContract.Events.ALL_DAY, 1)
                    put(CalendarContract.Events.DTSTART, event.startTime)
                    put(CalendarContract.Events.DTEND, event.endTime)
                } else {
                    put(CalendarContract.Events.ALL_DAY, 0)
                }
                
                put(CalendarContract.Events.HAS_ALARM, 0) // We'll manage alarms ourselves
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            }
            
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull()
            
            if (eventId != null) {
                createdEvents.add(eventId)
                Log.i("CalendarTestData", "Injected event '$event.title' with ID: $eventId")
            }
            
            eventId
        } catch (e: Exception) {
            Log.e("CalendarTestData", "Failed to inject event: ${event.title}", e)
            null
        }
    }
    
    /**
     * Create a comprehensive set of test events for E2E testing
     */
    fun createTestEventSuite(): List<Long> {
        val now = System.currentTimeMillis()
        val oneHour = 60 * 60 * 1000L
        val oneDay = 24 * oneHour
        
        val testEvents = listOf(
            // Events matching typical alarm rules
            TestCalendarEvent(
                title = "Important Meeting",
                description = "Critical business meeting",
                startTime = now + (2 * oneHour),
                endTime = now + (3 * oneHour)
            ),
            TestCalendarEvent(
                title = "Doctor Appointment",
                description = "Annual checkup",
                startTime = now + (4 * oneHour),
                endTime = now + (5 * oneHour)
            ),
            TestCalendarEvent(
                title = "Team Standup",
                description = "Daily team sync",
                startTime = now + oneDay,
                endTime = now + oneDay + oneHour
            ),
            // All-day event
            TestCalendarEvent(
                title = "Conference Day",
                description = "Annual company conference",
                startTime = now + (2 * oneDay),
                endTime = now + (2 * oneDay) + oneDay,
                allDay = true
            ),
            // Far future event for time acceleration testing
            TestCalendarEvent(
                title = "Future Planning Session",
                description = "Long-term planning meeting",
                startTime = now + (30 * oneDay),
                endTime = now + (30 * oneDay) + (2 * oneHour)
            ),
            // Event with location
            TestCalendarEvent(
                title = "Client Presentation",
                description = "Present quarterly results",
                startTime = now + (6 * oneHour),
                endTime = now + (8 * oneHour),
                location = "Conference Room A"
            ),
            // Multiple events on same day
            TestCalendarEvent(
                title = "Morning Briefing",
                description = "Start of day briefing",
                startTime = now + oneDay + (8 * oneHour),
                endTime = now + oneDay + (9 * oneHour)
            ),
            TestCalendarEvent(
                title = "Lunch Meeting",
                description = "Business lunch discussion",
                startTime = now + oneDay + (12 * oneHour),
                endTime = now + oneDay + (13 * oneHour)
            )
        )
        
        val eventIds = mutableListOf<Long>()
        testEvents.forEach { event ->
            injectEvent(event)?.let { eventIds.add(it) }
        }
        
        Log.i("CalendarTestData", "Created test suite with ${eventIds.size} events")
        return eventIds
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
     * Clean up all test data
     */
    fun cleanup() {
        // Remove created events
        createdEvents.forEach { eventId ->
            try {
                val uri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
                val deleted = contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    Log.d("CalendarTestData", "Deleted event ID: $eventId")
                }
            } catch (e: Exception) {
                Log.w("CalendarTestData", "Failed to delete event ID: $eventId", e)
            }
        }
        
        // Remove created calendars
        createdCalendars.forEach { calendarId ->
            try {
                val uri = Uri.withAppendedPath(CalendarContract.Calendars.CONTENT_URI, calendarId.toString())
                val deleted = contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    Log.d("CalendarTestData", "Deleted calendar ID: $calendarId")
                }
            } catch (e: Exception) {
                Log.w("CalendarTestData", "Failed to delete calendar ID: $calendarId", e)
            }
        }
        
        createdEvents.clear()
        createdCalendars.clear()
        
        Log.i("CalendarTestData", "Cleanup completed")
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
}