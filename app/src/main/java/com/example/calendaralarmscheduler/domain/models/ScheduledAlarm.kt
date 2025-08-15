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
            // Generate unique alarm ID first, then use it for request code
            val alarmId = UUID.randomUUID().toString()
            val requestCode = generateRequestCodeFromAlarmId(alarmId)
            
            return ScheduledAlarm(
                id = alarmId,
                eventId = event.id,
                ruleId = rule.id,
                eventTitle = event.title,
                eventStartTimeUtc = event.startTimeUtc,
                alarmTimeUtc = alarmTimeUtc,
                pendingIntentRequestCode = requestCode,
                lastEventModified = event.lastModified
            )
        }
        
        
        /**
         * Enhanced collision-resistant request code generation from alarm ID.
         * Uses multiple hash functions and bit manipulation to minimize collisions.
         */
        fun generateRequestCodeFromAlarmId(alarmId: String): Int {
            try {
                val uuid = UUID.fromString(alarmId)
                
                // Multi-stage hashing approach for better distribution
                
                // Stage 1: Basic XOR of UUID parts
                val xorResult = uuid.mostSignificantBits xor uuid.leastSignificantBits
                
                // Stage 2: Apply additional hash functions
                val hashCode1 = alarmId.hashCode()
                val hashCode2 = alarmId.reversed().hashCode()
                
                // Stage 3: Combine using different operations to spread bits
                val combined = (xorResult.toInt()) xor (hashCode1 * 31) xor (hashCode2 shl 7)
                
                // Stage 4: Ensure non-zero result and good distribution
                var requestCode = combined
                if (requestCode == 0 || requestCode == Int.MIN_VALUE) {
                    // Use a different calculation for edge cases
                    requestCode = (uuid.mostSignificantBits shr 32).toInt() + alarmId.length * 1009
                    if (requestCode == 0) requestCode = 1
                }
                
                // Stage 5: Apply final transformation to improve distribution
                requestCode = Integer.rotateLeft(requestCode, 13) // Rotate bits for better spread
                
                return requestCode
                
            } catch (e: IllegalArgumentException) {
                // Fallback for non-UUID alarm IDs
                android.util.Log.w("ScheduledAlarm", "Invalid UUID format for alarm ID: $alarmId, using fallback")
                return generateFallbackRequestCode(alarmId)
            }
        }
        
        /**
         * Fallback request code generation for non-UUID alarm IDs
         */
        private fun generateFallbackRequestCode(alarmId: String): Int {
            // Use multiple hash approaches for better distribution
            val hash1 = alarmId.hashCode()
            val hash2 = alarmId.reversed().hashCode()
            val hash3 = alarmId.lowercase().hashCode()
            
            val combined = (hash1 * 31) xor (hash2 shl 5) xor (hash3 shr 3)
            
            return if (combined == 0) alarmId.length * 1009 + 1 else combined
        }
        
        /**
         * Enhanced alternative request code generation for collision resolution.
         * Uses progressive strategies to find available request codes.
         */
        fun generateAlternativeRequestCode(originalRequestCode: Int, attempt: Int): Int {
            // Progressive collision resolution strategies
            
            // Strategy 1: Linear probing with prime multiplication (first 3 attempts)
            if (attempt <= 3) {
                val alternative = (originalRequestCode.toLong() * 31 + attempt * 1009).toInt()
                return if (alternative == 0) attempt + 1 else alternative
            }
            
            // Strategy 2: Quadratic probing (attempts 4-8)
            if (attempt <= 8) {
                val quadratic = attempt * attempt * 31 + originalRequestCode + attempt * 7919
                return if (quadratic == 0) attempt + 1000 else quadratic
            }
            
            // Strategy 3: Hash-based alternative with large primes (attempts 9-15)
            if (attempt <= 15) {
                val prime = 982451653 // Large prime
                val hash = (originalRequestCode.toLong() * prime + attempt * 1299709).toInt()
                return if (hash == 0) attempt + 10000 else hash
            }
            
            // Strategy 4: Timestamp-based with rotation (final attempts)
            val timestamp = System.currentTimeMillis() % Int.MAX_VALUE
            val rotated = Integer.rotateRight(originalRequestCode, attempt % 32)
            val combined = (timestamp.toInt() xor rotated + attempt * 97).toInt()
            
            return if (combined == 0) attempt + 100000 else combined
        }
        
        fun fromEventAndRule(
            event: CalendarEvent,
            rule: Rule,
            defaultAllDayHour: Int = 20,
            defaultAllDayMinute: Int = 0
        ): ScheduledAlarm {
            val alarmTimeUtc = if (event.isAllDay) {
                // For all-day events, fire alarm exactly at chosen time (no lead time)
                event.computeAllDayAlarmTimeUtc(defaultAllDayHour, defaultAllDayMinute, 0)
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
        // For all-day events, fire alarm exactly at chosen time (no lead time)
        newEvent.computeAllDayAlarmTimeUtc(defaultAllDayHour, defaultAllDayMinute, 0)
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