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
        
        // Projection for CalendarContract.Events query
        private val EVENTS_PROJECTION = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.LAST_DATE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION
        )
        
        // Projection for CalendarContract.Instances query (for recurring events)
        private val INSTANCES_PROJECTION = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.LAST_DATE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION
        )
    }

    /**
     * Query calendar events within the 2-day lookahead window
     * @param calendarIds Optional filter by specific calendar IDs
     * @param lastSyncTime Optional filter for events modified after this time
     */
    suspend fun getUpcomingEvents(
        calendarIds: List<Long>? = null,
        lastSyncTime: Long? = null
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val currentTime = System.currentTimeMillis()
            val endTime = currentTime + LOOKAHEAD_WINDOW_MS
            
            // Use Instances API to handle recurring events properly
            val events = mutableListOf<CalendarEvent>()
            
            // Build selection criteria
            val selectionArgs = mutableListOf<String>()
            val selectionBuilder = StringBuilder()
            
            // Time window filter
            selectionBuilder.append("(${CalendarContract.Instances.BEGIN} >= ? AND ${CalendarContract.Instances.BEGIN} <= ?)")
            selectionArgs.add(currentTime.toString())
            selectionArgs.add(endTime.toString())
            
            // Calendar ID filter if specified
            if (!calendarIds.isNullOrEmpty()) {
                val calendarIdPlaceholders = calendarIds.joinToString(",") { "?" }
                selectionBuilder.append(" AND ${CalendarContract.Instances.CALENDAR_ID} IN ($calendarIdPlaceholders)")
                calendarIds.forEach { selectionArgs.add(it.toString()) }
            }
            
            // Last modified filter if specified
            if (lastSyncTime != null) {
                selectionBuilder.append(" AND ${CalendarContract.Instances.LAST_DATE} > ?")
                selectionArgs.add(lastSyncTime.toString())
            }
            
            val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(currentTime.toString())
                .appendPath(endTime.toString())
                .build()
            
            contentResolver.query(
                instancesUri,
                INSTANCES_PROJECTION,
                selectionBuilder.toString(),
                selectionArgs.toTypedArray(),
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                events.addAll(parseInstancesCursor(cursor))
            }
            
            Log.d(TAG, "Retrieved ${events.size} upcoming events")
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

    /**
     * Parse cursor from CalendarContract.Instances query
     */
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
                val lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Instances.LAST_DATE))
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
                
                events.add(event)
                
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing calendar event from cursor", e)
                // Continue parsing other events
            }
        }
        
        return events
    }

    /**
     * Parse cursor from CalendarContract.Events query (for non-recurring events)
     */
    private fun parseEventsCursor(cursor: Cursor): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        while (cursor.moveToNext()) {
            try {
                val eventId = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)) ?: "Untitled Event"
                val dtstart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val dtend = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                val calendarId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))
                val allDay = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY))
                val eventTimezone = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE))
                val lastModified = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.LAST_DATE))
                val description = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION))
                val location = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))
                
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
                
                events.add(event)
                
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing calendar event from cursor", e)
                // Continue parsing other events
            }
        }
        
        return events
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