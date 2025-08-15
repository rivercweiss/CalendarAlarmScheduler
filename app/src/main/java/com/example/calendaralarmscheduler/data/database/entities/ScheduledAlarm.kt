package com.example.calendaralarmscheduler.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
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
    fun isInPast(): Boolean = alarmTimeUtc < System.currentTimeMillis()
    fun isInFuture(): Boolean = alarmTimeUtc > System.currentTimeMillis()
    fun isActive(): Boolean = !userDismissed && alarmTimeUtc > System.currentTimeMillis()
    fun getLocalAlarmTime(): ZonedDateTime = Instant.ofEpochMilli(alarmTimeUtc).atZone(ZoneId.systemDefault())
    fun getLocalEventStartTime(): ZonedDateTime = Instant.ofEpochMilli(eventStartTimeUtc).atZone(ZoneId.systemDefault())
    fun getLeadTimeMinutes(): Long = (eventStartTimeUtc - alarmTimeUtc) / (60 * 1000)
    
    fun formatTimeUntilAlarm(): String {
        val timeUntil = alarmTimeUtc - System.currentTimeMillis()
        return when {
            timeUntil <= 0 -> "Now"
            timeUntil < 60 * 1000 -> "< 1 min"
            timeUntil < 60 * 60 * 1000 -> "${timeUntil / (60 * 1000)} min"
            else -> "${timeUntil / (60 * 60 * 1000)}h ${(timeUntil / (60 * 1000)) % 60}m"
        }
    }
    
    companion object {
        fun generateRequestCode(eventId: String, ruleId: String): Int = (eventId + ruleId).hashCode()
    }
}