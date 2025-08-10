package com.example.calendaralarmscheduler.domain.models

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

data class CalendarEvent(
    val id: String,
    val title: String,
    val startTimeUtc: Long,
    val endTimeUtc: Long,
    val calendarId: Long,
    val isAllDay: Boolean,
    val timezone: String?,
    val lastModified: Long,
    val description: String? = null,
    val location: String? = null
) {
    
    fun isInPast(): Boolean {
        return startTimeUtc < System.currentTimeMillis()
    }
    
    fun getStartTimeInTimezone(zoneId: ZoneId): ZonedDateTime {
        return Instant.ofEpochMilli(startTimeUtc).atZone(zoneId)
    }
    
    fun getEndTimeInTimezone(zoneId: ZoneId): ZonedDateTime {
        return Instant.ofEpochMilli(endTimeUtc).atZone(zoneId)
    }
    
    fun getLocalStartTime(): ZonedDateTime {
        return getStartTimeInTimezone(ZoneId.systemDefault())
    }
    
    fun getLocalEndTime(): ZonedDateTime {
        return getEndTimeInTimezone(ZoneId.systemDefault())
    }
    
    fun getDurationMinutes(): Long {
        return (endTimeUtc - startTimeUtc) / (60 * 1000)
    }
    
    fun isMultiDay(): Boolean {
        val startLocal = getLocalStartTime()
        val endLocal = getLocalEndTime()
        return startLocal.toLocalDate() != endLocal.toLocalDate()
    }
    
    fun computeAlarmTimeUtc(leadTimeMinutes: Int): Long {
        return startTimeUtc - (leadTimeMinutes * 60 * 1000L)
    }
    
    fun computeAllDayAlarmTimeUtc(defaultTimeHour: Int, defaultTimeMinute: Int, leadTimeMinutes: Int): Long {
        if (!isAllDay) {
            return computeAlarmTimeUtc(leadTimeMinutes)
        }
        
        // For multi-day all-day events, always alarm on the first day only
        val localStartDate = getLocalStartTime().toLocalDate()
        val localEndDate = getLocalEndTime().toLocalDate()
        
        // Use the start date for the alarm time, regardless of how many days the event spans
        val defaultTime = localStartDate.atTime(defaultTimeHour, defaultTimeMinute)
        val defaultTimeUtc = defaultTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        // For all-day events, fire alarm exactly at the chosen time (no lead time applied)
        return defaultTimeUtc
    }
    
    companion object {
        fun fromCalendarContract(
            id: String,
            title: String,
            dtstart: Long,
            dtend: Long,
            calendarId: Long,
            allDay: Int,
            eventTimezone: String?,
            lastModified: Long,
            description: String? = null,
            location: String? = null
        ): CalendarEvent {
            val isAllDay = allDay == 1
            
            val startUtc = if (isAllDay) {
                dtstart
            } else {
                if (eventTimezone != null) {
                    convertToUtc(dtstart, eventTimezone)
                } else {
                    dtstart
                }
            }
            
            val endUtc = if (isAllDay) {
                dtend
            } else {
                if (eventTimezone != null) {
                    convertToUtc(dtend, eventTimezone)
                } else {
                    dtend
                }
            }
            
            return CalendarEvent(
                id = id,
                title = title,
                startTimeUtc = startUtc,
                endTimeUtc = endUtc,
                calendarId = calendarId,
                isAllDay = isAllDay,
                timezone = eventTimezone,
                lastModified = lastModified,
                description = description,
                location = location
            )
        }
        
        private fun convertToUtc(timeMillis: Long, timezoneId: String): Long {
            return try {
                val timezone = TimeZone.getTimeZone(timezoneId)
                val instant = Instant.ofEpochMilli(timeMillis)
                val zonedDateTime = instant.atZone(ZoneId.of(timezoneId))
                zonedDateTime.toInstant().toEpochMilli()
            } catch (e: Exception) {
                timeMillis
            }
        }
    }
}

fun CalendarEvent.addMinutes(minutes: Int): CalendarEvent {
    return this.copy(
        startTimeUtc = startTimeUtc + (minutes * 60 * 1000L),
        endTimeUtc = endTimeUtc + (minutes * 60 * 1000L)
    )
}

fun CalendarEvent.toUtcString(): String {
    val startInstant = Instant.ofEpochMilli(startTimeUtc)
    val endInstant = Instant.ofEpochMilli(endTimeUtc)
    return "$title: ${startInstant} to ${endInstant} UTC"
}

fun CalendarEvent.toLocalString(): String {
    val startLocal = getLocalStartTime()
    val endLocal = getLocalEndTime()
    val timezone = startLocal.zone.id
    return "$title: ${startLocal.toLocalDateTime()} to ${endLocal.toLocalDateTime()} ($timezone)"
}