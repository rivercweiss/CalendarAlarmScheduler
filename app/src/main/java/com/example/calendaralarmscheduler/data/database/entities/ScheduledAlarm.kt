package com.example.calendaralarmscheduler.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
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
    val userDismissed: Boolean = false,      // Track manual dismissals
    val pendingIntentRequestCode: Int,
    val lastEventModified: Long              // From CalendarContract
) {
    companion object {
        fun generateRequestCode(eventId: String, ruleId: String): Int {
            return (eventId + ruleId).hashCode()
        }
    }
}