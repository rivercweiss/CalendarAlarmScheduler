package com.example.calendaralarmscheduler.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

object TimezoneUtils {
    private const val TAG = "TimezoneUtils"

    /**
     * Convert local time to UTC
     * @param localTimeMillis Time in local timezone
     * @param timezoneId The timezone ID (e.g., "America/New_York")
     * @return UTC time in milliseconds
     */
    fun toUTC(localTimeMillis: Long, timezoneId: String? = null): Long {
        return try {
            val zoneId = if (timezoneId != null) {
                ZoneId.of(timezoneId)
            } else {
                ZoneId.systemDefault()
            }
            
            val localDateTime = Instant.ofEpochMilli(localTimeMillis)
                .atZone(zoneId)
                .toLocalDateTime()
            
            val zonedDateTime = localDateTime.atZone(zoneId)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Error converting to UTC: $e, returning original time")
            localTimeMillis
        }
    }

    /**
     * Convert UTC time to local time in specified timezone
     * @param utcTimeMillis UTC time in milliseconds
     * @param timezoneId Target timezone ID
     * @return Local time in specified timezone
     */
    fun fromUTC(utcTimeMillis: Long, timezoneId: String? = null): Long {
        return try {
            val zoneId = if (timezoneId != null) {
                ZoneId.of(timezoneId)
            } else {
                ZoneId.systemDefault()
            }
            
            val utcInstant = Instant.ofEpochMilli(utcTimeMillis)
            val zonedDateTime = utcInstant.atZone(zoneId)
            
            // Return the epoch millisecond representation in the target timezone
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Error converting from UTC: $e, returning original time")
            utcTimeMillis
        }
    }

    /**
     * Get current UTC time in milliseconds
     */
    fun getCurrentUTC(): Long {
        return System.currentTimeMillis()
    }

    /**
     * Get current local time as ZonedDateTime
     */
    fun getCurrentLocal(): ZonedDateTime {
        return ZonedDateTime.now()
    }

    /**
     * Check if current time is during daylight saving time
     */
    fun isCurrentlyDST(zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        return ZonedDateTime.now(zoneId).zone.rules.isDaylightSavings(Instant.now())
    }

    /**
     * Get timezone offset from UTC in minutes for a given time
     * @param timeMillis The time to check
     * @param zoneId The timezone
     * @return Offset in minutes (positive = ahead of UTC, negative = behind UTC)
     */
    fun getTimezoneOffsetMinutes(timeMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Int {
        val instant = Instant.ofEpochMilli(timeMillis)
        val zonedDateTime = instant.atZone(zoneId)
        return zonedDateTime.offset.totalSeconds / 60
    }

    /**
     * Format time for display with timezone information
     * @param timeMillis UTC time in milliseconds
     * @param zoneId Target timezone for display
     * @param includeTimezone Whether to include timezone abbreviation
     * @return Formatted time string
     */
    fun formatTimeWithTimezone(
        timeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        includeTimezone: Boolean = true
    ): String {
        val zonedDateTime = Instant.ofEpochMilli(timeMillis).atZone(zoneId)
        
        val formatter = if (includeTimezone) {
            DateTimeFormatter.ofPattern("MMM dd, yyyy h:mm a z")
        } else {
            DateTimeFormatter.ofPattern("MMM dd, yyyy h:mm a")
        }
        
        return zonedDateTime.format(formatter)
    }

    /**
     * Format time for display in local timezone
     */
    fun formatLocalTime(timeMillis: Long): String {
        return formatTimeWithTimezone(timeMillis, ZoneId.systemDefault(), true)
    }

    /**
     * Check if two timestamps are on the same date in local timezone
     */
    fun isSameLocalDate(time1Millis: Long, time2Millis: Long): Boolean {
        val date1 = Instant.ofEpochMilli(time1Millis).atZone(ZoneId.systemDefault()).toLocalDate()
        val date2 = Instant.ofEpochMilli(time2Millis).atZone(ZoneId.systemDefault()).toLocalDate()
        return date1 == date2
    }

    /**
     * Get start of day in UTC for a given local date
     */
    fun getStartOfDayUTC(localDate: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return localDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Get end of day in UTC for a given local date
     */
    fun getEndOfDayUTC(localDate: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return localDate.atTime(23, 59, 59, 999_999_999)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Handle all-day events time conversion
     * All-day events are typically stored as midnight UTC on the event date
     */
    fun convertAllDayEventTime(allDayTimeMillis: Long, targetZoneId: ZoneId = ZoneId.systemDefault()): Long {
        // All-day events are usually midnight UTC, convert to local midnight
        val utcDate = Instant.ofEpochMilli(allDayTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return utcDate.atStartOfDay(targetZoneId).toInstant().toEpochMilli()
    }

    /**
     * Register timezone change listener
     */
    fun registerTimezoneChangeListener(context: Context, onTimezoneChanged: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        Log.i(TAG, "Timezone changed, triggering callback")
                        onTimezoneChanged()
                    }
                    Intent.ACTION_TIME_CHANGED -> {
                        Log.i(TAG, "System time changed, triggering callback")
                        onTimezoneChanged()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }

        context.registerReceiver(receiver, filter)
        return receiver
    }

    /**
     * Unregister timezone change listener
     */
    fun unregisterTimezoneChangeListener(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered: $e")
        }
    }

    /**
     * Check if a timezone ID is valid
     */
    fun isValidTimezone(timezoneId: String?): Boolean {
        if (timezoneId.isNullOrBlank()) return false
        
        return try {
            ZoneId.of(timezoneId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get all available timezone IDs
     */
    fun getAvailableTimezoneIds(): Set<String> {
        return ZoneId.getAvailableZoneIds()
    }

    /**
     * Get common timezone IDs for selection
     */
    fun getCommonTimezoneIds(): List<String> {
        return listOf(
            "America/New_York",
            "America/Chicago",
            "America/Denver", 
            "America/Phoenix",
            "America/Los_Angeles",
            "Europe/London",
            "Europe/Paris",
            "Europe/Berlin",
            "Asia/Tokyo",
            "Asia/Shanghai",
            "Asia/Kolkata",
            "Australia/Sydney",
            "Pacific/Auckland"
        )
    }

    /**
     * Get user-friendly timezone display name
     */
    fun getTimezoneDisplayName(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zonedDateTime = ZonedDateTime.now(zoneId)
        val offset = zonedDateTime.offset
        val isDST = zoneId.rules.isDaylightSavings(Instant.now())
        
        return "${zoneId.id} (UTC${offset}) ${if (isDST) "DST" else ""}"
    }

    /**
     * Calculate time difference between two timezones in minutes
     */
    fun getTimezoneOffsetDifference(
        fromZoneId: ZoneId,
        toZoneId: ZoneId,
        atTime: Instant = Instant.now()
    ): Int {
        val fromOffset = fromZoneId.rules.getOffset(atTime).totalSeconds
        val toOffset = toZoneId.rules.getOffset(atTime).totalSeconds
        return (toOffset - fromOffset) / 60
    }
}