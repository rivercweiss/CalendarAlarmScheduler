package com.example.calendaralarmscheduler.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.calendaralarmscheduler.data.database.entities.Rule
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@Entity(tableName = "alarms")
data class ScheduledAlarm(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val eventId: String,
    val ruleId: String,
    val eventTitle: String,
    val eventStartTimeUtc: Long,
    val alarmTimeUtc: Long,
    val scheduledAt: Long = System.currentTimeMillis(),
    val userDismissed: Boolean = false,
    val pendingIntentRequestCode: Int,
    val lastEventModified: Long
) {
    
    fun isInPast(): Boolean {
        return alarmTimeUtc < System.currentTimeMillis()
    }
    
    fun isInFuture(): Boolean {
        return alarmTimeUtc > System.currentTimeMillis()
    }
    
    fun isActive(): Boolean {
        return !userDismissed && isInFuture()
    }
    
    fun getAlarmTimeInTimezone(zoneId: ZoneId): ZonedDateTime {
        return Instant.ofEpochMilli(alarmTimeUtc).atZone(zoneId)
    }
    
    fun getEventStartTimeInTimezone(zoneId: ZoneId): ZonedDateTime {
        return Instant.ofEpochMilli(eventStartTimeUtc).atZone(zoneId)
    }
    
    fun getLocalAlarmTime(): ZonedDateTime {
        return getAlarmTimeInTimezone(ZoneId.systemDefault())
    }
    
    fun getLocalEventStartTime(): ZonedDateTime {
        return getEventStartTimeInTimezone(ZoneId.systemDefault())
    }
    
    fun getTimeUntilAlarmMillis(): Long {
        return alarmTimeUtc - System.currentTimeMillis()
    }
    
    fun getTimeUntilAlarmMinutes(): Long {
        return getTimeUntilAlarmMillis() / (60 * 1000L)
    }
    
    fun getLeadTimeMinutes(): Long {
        return (eventStartTimeUtc - alarmTimeUtc) / (60 * 1000L)
    }
    
    fun formatTimeUntilAlarm(): String {
        val minutesUntil = getTimeUntilAlarmMinutes()
        
        return when {
            minutesUntil <= 0 -> "Now"
            minutesUntil < 60 -> "$minutesUntil minute${if (minutesUntil != 1L) "s" else ""}"
            minutesUntil < 24 * 60 -> {
                val hours = minutesUntil / 60
                val remainingMinutes = minutesUntil % 60
                if (remainingMinutes == 0L) {
                    "$hours hour${if (hours != 1L) "s" else ""}"
                } else {
                    "$hours hour${if (hours != 1L) "s" else ""} $remainingMinutes minute${if (remainingMinutes != 1L) "s" else ""}"
                }
            }
            else -> {
                val days = minutesUntil / (24 * 60)
                val remainingHours = (minutesUntil % (24 * 60)) / 60
                buildString {
                    append("$days day${if (days != 1L) "s" else ""}")
                    if (remainingHours > 0) {
                        append(" $remainingHours hour${if (remainingHours != 1L) "s" else ""}")
                    }
                }
            }
        }
    }
    
    fun shouldBeRescheduled(currentEventModified: Long): Boolean {
        return !userDismissed && currentEventModified > lastEventModified
    }
    
    companion object {
        fun create(
            event: CalendarEvent,
            rule: Rule,
            alarmTimeUtc: Long
        ): ScheduledAlarm {
            val requestCode = generateRequestCode(event.id, rule.id)
            
            return ScheduledAlarm(
                eventId = event.id,
                ruleId = rule.id,
                eventTitle = event.title,
                eventStartTimeUtc = event.startTimeUtc,
                alarmTimeUtc = alarmTimeUtc,
                pendingIntentRequestCode = requestCode,
                lastEventModified = event.lastModified
            )
        }
        
        fun generateRequestCode(eventId: String, ruleId: String): Int {
            return (eventId + ruleId).hashCode()
        }
        
        fun fromEventAndRule(
            event: CalendarEvent,
            rule: Rule,
            defaultAllDayHour: Int = 20,
            defaultAllDayMinute: Int = 0
        ): ScheduledAlarm {
            val alarmTimeUtc = if (event.isAllDay) {
                event.computeAllDayAlarmTimeUtc(defaultAllDayHour, defaultAllDayMinute, rule.leadTimeMinutes)
            } else {
                event.computeAlarmTimeUtc(rule.leadTimeMinutes)
            }
            
            return create(event, rule, alarmTimeUtc)
        }
    }
}

fun ScheduledAlarm.markDismissed(): ScheduledAlarm {
    return this.copy(userDismissed = true)
}

fun ScheduledAlarm.updateForEventChange(newEvent: CalendarEvent, rule: Rule, defaultAllDayHour: Int = 20, defaultAllDayMinute: Int = 0): ScheduledAlarm {
    val newAlarmTimeUtc = if (newEvent.isAllDay) {
        newEvent.computeAllDayAlarmTimeUtc(defaultAllDayHour, defaultAllDayMinute, rule.leadTimeMinutes)
    } else {
        newEvent.computeAlarmTimeUtc(rule.leadTimeMinutes)
    }
    
    return this.copy(
        eventTitle = newEvent.title,
        eventStartTimeUtc = newEvent.startTimeUtc,
        alarmTimeUtc = newAlarmTimeUtc,
        lastEventModified = newEvent.lastModified,
        userDismissed = false // Reset dismissal status for changed events
    )
}