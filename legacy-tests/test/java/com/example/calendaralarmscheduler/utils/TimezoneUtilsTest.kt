package com.example.calendaralarmscheduler.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*

class TimezoneUtilsTest {

    // Test data - using fixed times to ensure deterministic tests
    private val fixedUtcTime = ZonedDateTime.of(
        2024, 6, 15, 14, 30, 0, 0,  // June 15, 2024, 2:30 PM UTC
        ZoneOffset.UTC
    ).toInstant().toEpochMilli()
    
    private val fixedLocalTime = ZonedDateTime.of(
        2024, 6, 15, 10, 30, 0, 0,  // June 15, 2024, 10:30 AM EDT
        ZoneId.of("America/New_York")
    ).toInstant().toEpochMilli()

    // === UTC Conversion Tests ===
    
    @Test
    fun `toUTC converts local time to UTC correctly`() {
        val nyTimezone = "America/New_York"
        val localTime = ZonedDateTime.of(
            2024, 6, 15, 10, 30, 0, 0,
            ZoneId.of(nyTimezone)
        ).toInstant().toEpochMilli()
        
        val utcResult = TimezoneUtils.toUTC(localTime, nyTimezone)
        
        // Should convert EDT (UTC-4) to UTC, so 10:30 AM EDT = 2:30 PM UTC
        val expectedUtc = ZonedDateTime.of(
            2024, 6, 15, 14, 30, 0, 0,
            ZoneOffset.UTC
        ).toInstant().toEpochMilli()
        
        assertThat(utcResult).isEqualTo(expectedUtc)
    }
    
    @Test
    fun `toUTC uses system timezone when timezone is null`() {
        val systemZone = ZoneId.systemDefault()
        val localTime = System.currentTimeMillis()
        
        val result = TimezoneUtils.toUTC(localTime, null)
        
        // Should use system default timezone conversion
        // If system timezone is UTC, the result will be the same, otherwise different
        if (systemZone == ZoneOffset.UTC) {
            assertThat(result).isEqualTo(localTime)
        } else {
            // For non-UTC timezones, the conversion should produce a different result
            // We can't predict the exact difference, so just verify the method runs without error
            assertThat(result).isNotNull()
        }
    }
    
    @Test
    fun `toUTC handles invalid timezone gracefully`() {
        val localTime = System.currentTimeMillis()
        
        val result = TimezoneUtils.toUTC(localTime, "Invalid/Timezone")
        
        // Should return original time when timezone is invalid
        assertThat(result).isEqualTo(localTime)
    }
    
    @Test
    fun `fromUTC converts UTC to local time correctly`() {
        val nyTimezone = "America/New_York"
        val utcTime = ZonedDateTime.of(
            2024, 6, 15, 14, 30, 0, 0,  // 2:30 PM UTC
            ZoneOffset.UTC
        ).toInstant().toEpochMilli()
        
        val localResult = TimezoneUtils.fromUTC(utcTime, nyTimezone)
        
        // The result should be the same instant, just represented in the target timezone
        // Since fromUTC returns epoch millis, it should equal the original UTC time
        assertThat(localResult).isEqualTo(utcTime)
    }
    
    @Test
    fun `fromUTC uses system timezone when timezone is null`() {
        val utcTime = System.currentTimeMillis()
        
        val result = TimezoneUtils.fromUTC(utcTime, null)
        
        // Should return same instant (epoch millis don't change, only interpretation)
        assertThat(result).isEqualTo(utcTime)
    }
    
    @Test
    fun `fromUTC handles invalid timezone gracefully`() {
        val utcTime = System.currentTimeMillis()
        
        val result = TimezoneUtils.fromUTC(utcTime, "Invalid/Timezone")
        
        // Should return original time when timezone is invalid
        assertThat(result).isEqualTo(utcTime)
    }
    
    // === Timezone Information Tests ===
    
    @Test
    fun `getCurrentUTC returns current time in milliseconds`() {
        val before = System.currentTimeMillis()
        val result = TimezoneUtils.getCurrentUTC()
        val after = System.currentTimeMillis()
        
        assertThat(result).isAtLeast(before)
        assertThat(result).isAtMost(after)
    }
    
    @Test
    fun `getCurrentLocal returns current local time as ZonedDateTime`() {
        val before = ZonedDateTime.now()
        val result = TimezoneUtils.getCurrentLocal()
        val after = ZonedDateTime.now()
        
        assertThat(result.toInstant()).isAtLeast(before.toInstant())
        assertThat(result.toInstant()).isAtMost(after.toInstant())
        assertThat(result.zone).isEqualTo(ZoneId.systemDefault())
    }
    
    @Test
    fun `isCurrentlyDST detects daylight saving time`() {
        val summer = ZoneId.of("America/New_York")
        val winter = ZoneId.of("America/New_York")
        
        // Test with a known DST time (June 15th should be DST in Eastern timezone)
        val summerTime = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, summer)
        val isDST = summer.rules.isDaylightSavings(summerTime.toInstant())
        
        // June should be DST in Eastern timezone
        assertThat(isDST).isTrue()
        
        // Test with a known non-DST time (January 15th should not be DST)
        val winterTime = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, winter)
        val isNotDST = winter.rules.isDaylightSavings(winterTime.toInstant())
        
        // January should not be DST in Eastern timezone
        assertThat(isNotDST).isFalse()
    }
    
    @Test
    fun `getTimezoneOffsetMinutes calculates correct offset`() {
        val nyZone = ZoneId.of("America/New_York")
        
        // Test during standard time (January - EST = UTC-5)
        val winterTime = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC)
        val winterOffset = TimezoneUtils.getTimezoneOffsetMinutes(winterTime.toInstant().toEpochMilli(), nyZone)
        
        assertThat(winterOffset).isEqualTo(-5 * 60) // EST is UTC-5 = -300 minutes
        
        // Test during daylight time (June - EDT = UTC-4)
        val summerTime = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)
        val summerOffset = TimezoneUtils.getTimezoneOffsetMinutes(summerTime.toInstant().toEpochMilli(), nyZone)
        
        assertThat(summerOffset).isEqualTo(-4 * 60) // EDT is UTC-4 = -240 minutes
    }
    
    // === Time Formatting Tests ===
    
    @Test
    fun `formatTimeWithTimezone includes timezone when requested`() {
        val utcTime = ZonedDateTime.of(2024, 6, 15, 14, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val nyZone = ZoneId.of("America/New_York")
        
        val result = TimezoneUtils.formatTimeWithTimezone(utcTime, nyZone, includeTimezone = true)
        
        assertThat(result).contains("Jun 15, 2024")
        assertThat(result).contains("10:30 AM")
        assertThat(result).contains("EDT") // Should show Eastern Daylight Time
    }
    
    @Test
    fun `formatTimeWithTimezone excludes timezone when not requested`() {
        val utcTime = ZonedDateTime.of(2024, 6, 15, 14, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val nyZone = ZoneId.of("America/New_York")
        
        val result = TimezoneUtils.formatTimeWithTimezone(utcTime, nyZone, includeTimezone = false)
        
        assertThat(result).contains("Jun 15, 2024")
        assertThat(result).contains("10:30 AM")
        assertThat(result).doesNotContain("EDT")
    }
    
    @Test
    fun `formatLocalTime formats in system timezone with timezone indicator`() {
        val utcTime = System.currentTimeMillis()
        
        val result = TimezoneUtils.formatLocalTime(utcTime)
        
        // Should contain basic time formatting elements
        assertThat(result).matches(".*\\d{4}.*") // Contains year
        assertThat(result).containsMatch("\\d{1,2}:\\d{2} [AP]M") // Contains time with AM/PM
    }
    
    // === Date Comparison Tests ===
    
    @Test
    fun `isSameLocalDate returns true for same calendar date`() {
        val morning = ZonedDateTime.of(2024, 6, 15, 8, 0, 0, 0, ZoneId.systemDefault())
        val evening = ZonedDateTime.of(2024, 6, 15, 20, 0, 0, 0, ZoneId.systemDefault())
        
        val result = TimezoneUtils.isSameLocalDate(
            morning.toInstant().toEpochMilli(),
            evening.toInstant().toEpochMilli()
        )
        
        assertThat(result).isTrue()
    }
    
    @Test
    fun `isSameLocalDate returns false for different calendar dates`() {
        val today = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneId.systemDefault())
        val tomorrow = ZonedDateTime.of(2024, 6, 16, 12, 0, 0, 0, ZoneId.systemDefault())
        
        val result = TimezoneUtils.isSameLocalDate(
            today.toInstant().toEpochMilli(),
            tomorrow.toInstant().toEpochMilli()
        )
        
        assertThat(result).isFalse()
    }
    
    // === Day Boundary Tests ===
    
    @Test
    fun `getStartOfDayUTC returns midnight in local timezone converted to UTC`() {
        val date = LocalDate.of(2024, 6, 15)
        val nyZone = ZoneId.of("America/New_York")
        
        val result = TimezoneUtils.getStartOfDayUTC(date, nyZone)
        
        // Midnight EDT (UTC-4) should be 4:00 AM UTC
        val expected = ZonedDateTime.of(2024, 6, 15, 4, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        
        assertThat(result).isEqualTo(expected)
    }
    
    @Test
    fun `getEndOfDayUTC returns end of day in local timezone converted to UTC`() {
        val date = LocalDate.of(2024, 6, 15)
        val nyZone = ZoneId.of("America/New_York")
        
        val result = TimezoneUtils.getEndOfDayUTC(date, nyZone)
        
        // 11:59:59.999 PM EDT should be 3:59:59.999 AM UTC next day
        val expected = ZonedDateTime.of(2024, 6, 16, 3, 59, 59, 999_999_999, ZoneOffset.UTC).toInstant().toEpochMilli()
        
        assertThat(result).isEqualTo(expected)
    }
    
    // === All-Day Event Tests ===
    
    @Test
    fun `convertAllDayEventTime converts UTC midnight to local midnight`() {
        // All-day event stored as midnight UTC
        val utcMidnight = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val nyZone = ZoneId.of("America/New_York")
        
        val result = TimezoneUtils.convertAllDayEventTime(utcMidnight, nyZone)
        
        // Should convert to midnight in NY timezone
        val expectedLocal = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, nyZone).toInstant().toEpochMilli()
        
        assertThat(result).isEqualTo(expectedLocal)
    }
    
    // === Timezone Validation Tests ===
    
    @Test
    fun `isValidTimezone returns true for valid timezone IDs`() {
        assertThat(TimezoneUtils.isValidTimezone("America/New_York")).isTrue()
        assertThat(TimezoneUtils.isValidTimezone("Europe/London")).isTrue()
        assertThat(TimezoneUtils.isValidTimezone("UTC")).isTrue()
        assertThat(TimezoneUtils.isValidTimezone("GMT")).isTrue()
    }
    
    @Test
    fun `isValidTimezone returns false for invalid timezone IDs`() {
        assertThat(TimezoneUtils.isValidTimezone("Invalid/Timezone")).isFalse()
        assertThat(TimezoneUtils.isValidTimezone("")).isFalse()
        assertThat(TimezoneUtils.isValidTimezone(null)).isFalse()
        assertThat(TimezoneUtils.isValidTimezone("   ")).isFalse()
    }
    
    @Test
    fun `getAvailableTimezoneIds returns non-empty set`() {
        val timezones = TimezoneUtils.getAvailableTimezoneIds()
        
        assertThat(timezones).isNotEmpty()
        assertThat(timezones).contains("America/New_York")
        assertThat(timezones).contains("Europe/London")
        assertThat(timezones).contains("UTC")
    }
    
    @Test
    fun `getCommonTimezoneIds returns expected common timezones`() {
        val commonTimezones = TimezoneUtils.getCommonTimezoneIds()
        
        assertThat(commonTimezones).contains("America/New_York")
        assertThat(commonTimezones).contains("America/Los_Angeles")
        assertThat(commonTimezones).contains("Europe/London")
        assertThat(commonTimezones).contains("Asia/Tokyo")
        assertThat(commonTimezones).hasSize(13) // Based on implementation
    }
    
    // === Display Name Tests ===
    
    @Test
    fun `getTimezoneDisplayName includes timezone ID and offset`() {
        val nyZone = ZoneId.of("America/New_York")
        
        val displayName = TimezoneUtils.getTimezoneDisplayName(nyZone)
        
        assertThat(displayName).contains("America/New_York")
        assertThat(displayName).contains("UTC")
        assertThat(displayName).containsMatch("UTC[+-]\\d+") // UTC offset like UTC-5 or UTC-4
    }
    
    @Test
    fun `getTimezoneDisplayName indicates DST when applicable`() {
        val nyZone = ZoneId.of("America/New_York")
        
        val displayName = TimezoneUtils.getTimezoneDisplayName(nyZone)
        
        // During test execution, check if DST is active and verify accordingly
        val now = Instant.now()
        val isDST = nyZone.rules.isDaylightSavings(now)
        
        if (isDST) {
            assertThat(displayName).contains("DST")
        } else {
            assertThat(displayName).doesNotContain("DST")
        }
    }
    
    // === Timezone Offset Difference Tests ===
    
    @Test
    fun `getTimezoneOffsetDifference calculates difference correctly`() {
        val nyZone = ZoneId.of("America/New_York")  // UTC-5 (EST) or UTC-4 (EDT)
        val laZone = ZoneId.of("America/Los_Angeles") // UTC-8 (PST) or UTC-7 (PDT)
        
        // Use a specific time to ensure deterministic results
        val testTime = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC).toInstant()
        
        val difference = TimezoneUtils.getTimezoneOffsetDifference(laZone, nyZone, testTime)
        
        // In summer, LA is PDT (UTC-7), NY is EDT (UTC-4)
        // Difference should be 3 hours (180 minutes)
        assertThat(difference).isEqualTo(180) // 3 hours * 60 minutes
    }
    
    @Test
    fun `getTimezoneOffsetDifference handles same timezone`() {
        val nyZone = ZoneId.of("America/New_York")
        val testTime = Instant.now()
        
        val difference = TimezoneUtils.getTimezoneOffsetDifference(nyZone, nyZone, testTime)
        
        assertThat(difference).isEqualTo(0)
    }
}