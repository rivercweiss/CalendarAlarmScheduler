package com.example.calendaralarmscheduler.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Entity(tableName = "rules")
@TypeConverters(Rule.Converters::class)
data class Rule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val keywordPattern: String,
    val isRegex: Boolean,                    // Auto-detected
    val calendarIds: List<Long>,             // Per-rule calendar filter
    val leadTimeMinutes: Int,                // 1 minute to 7 days
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    class Converters {
        @TypeConverter
        fun fromLongList(value: List<Long>): String {
            return Json.encodeToString(value)
        }

        @TypeConverter
        fun toLongList(value: String): List<Long> {
            return Json.decodeFromString(value)
        }
    }

    companion object {
        const val MIN_LEAD_TIME_MINUTES = 1
        const val MAX_LEAD_TIME_MINUTES = 7 * 24 * 60 // 7 days in minutes
    }
}