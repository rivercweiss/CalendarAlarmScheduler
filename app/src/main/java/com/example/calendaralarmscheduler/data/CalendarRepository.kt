package com.example.calendaralarmscheduler.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CalendarRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        private const val TAG = "CalendarRepository"
        
        // 2-day lookahead window as specified in requirements
        private val LOOKAHEAD_WINDOW_MS = TimeUnit.DAYS.toMillis(2)
        
        
        private val INSTANCES_PROJECTION = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION
        )
    }

    suspend fun getUpcomingEvents(
        calendarIds: List<Long>? = null,
        lastSyncTime: Long? = null
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val endTime = currentTime + LOOKAHEAD_WINDOW_MS
        
        try {
            val events = mutableListOf<CalendarEvent>()
            
            // Build URI with time range - this is the primary filter
            val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(currentTime.toString())
                .appendPath(endTime.toString())
                .build()
            
            Log.d(TAG, "Querying calendar events from $currentTime to $endTime")
            Log.d(TAG, "Time window: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(currentTime))} to ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(endTime))}")
            
            // Build selection for calendar ID filter if specified
            val selection = if (!calendarIds.isNullOrEmpty()) {
                val placeholders = calendarIds.joinToString(",") { "?" }
                "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            } else null
            
            val selectionArgs = if (!calendarIds.isNullOrEmpty()) {
                calendarIds.map { it.toString() }.toTypedArray()
            } else null
            
            Log.d(TAG, "Query URI: $instancesUri")
            Log.d(TAG, "Selection: $selection")
            Log.d(TAG, "Selection args: ${selectionArgs?.contentToString()}")
            
            contentResolver.query(
                instancesUri,
                INSTANCES_PROJECTION,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                Log.d(TAG, "Cursor returned ${cursor.count} rows")
                events.addAll(parseInstancesCursor(cursor))
            } ?: Log.w(TAG, "Query returned null cursor")
            
            Log.d(TAG, "Retrieved ${events.size} upcoming events")
            events.forEach { event ->
                Log.d(TAG, "Event: ${event.title} at ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(event.startTimeUtc))}")
            }
            events
        } catch (e: SecurityException) {
            Log.e(TAG, "Calendar permission not granted", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendar events", e)
            emptyList()
        }
    }

    /**
     * Get all available calendars on the device
     */
    suspend fun getAvailableCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        try {
            val calendars = mutableListOf<CalendarInfo>()
            
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.VISIBLE
            )
            
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
                    val accountName = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME))
                    val accountType = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE))
                    val color = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR))
                    
                    calendars.add(
                        CalendarInfo(
                            id = id,
                            displayName = displayName ?: "Unknown Calendar",
                            accountName = accountName ?: "Unknown Account",
                            accountType = accountType ?: "Unknown",
                            color = color
                        )
                    )
                }
            }
            
            Log.d(TAG, "Retrieved ${calendars.size} available calendars")
            calendars
        } catch (e: SecurityException) {
            Log.e(TAG, "Calendar permission not granted", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendars", e)
            emptyList()
        }
    }

    private fun parseInstancesCursor(cursor: Cursor): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        while (cursor.moveToNext()) {
            try {
                val eventId = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)) ?: "Untitled Event"
                val dtstart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN))
                val dtend = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.END))
                val calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID))
                val allDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY))
                val eventTimezone = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_TIMEZONE))
                val lastModified = System.currentTimeMillis()
                val description = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION))
                val location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION))
                
                val event = CalendarEvent.fromCalendarContract(
                    id = eventId,
                    title = title,
                    dtstart = dtstart,
                    dtend = dtend,
                    calendarId = calendarId,
                    allDay = allDay,
                    eventTimezone = eventTimezone,
                    lastModified = lastModified,
                    description = description,
                    location = location
                )
                
                Log.d(TAG, "Parsed event: ${event.title} (ID: ${event.id}, calendar: ${event.calendarId}, start: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(event.startTimeUtc))})")
                events.add(event)
                
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing calendar event from cursor", e)
            }
        }
        
        return events
    }


    /**
     * Get events in the lookahead window (alias for getUpcomingEvents for consistency)
     */
    suspend fun getEventsInLookAheadWindow(): List<CalendarEvent> = getUpcomingEvents()
    
    /**
     * Get calendars as a map of ID to display name
     */
    suspend fun getCalendarsWithNames(): Map<Long, String> = withContext(Dispatchers.IO) {
        try {
            val calendars = mutableMapOf<Long, String>()
            
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            )
            
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))
                    calendars[id] = displayName ?: "Unknown Calendar"
                }
            }
            
            calendars
        } catch (e: SecurityException) {
            Log.e(TAG, "Calendar permission not granted", e)
            emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying calendar names", e)
            emptyMap()
        }
    }

    /**
     * Check if we have calendar read permission
     */
    fun hasCalendarPermission(): Boolean {
        return try {
            // Try a minimal query to test permission
            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                null,
                null,
                null
            )?.use { 
                true 
            } ?: false
        } catch (e: SecurityException) {
            false
        }
    }
    

    data class CalendarInfo(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val color: Int
    )
}