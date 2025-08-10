package com.example.calendaralarmscheduler.e2e

import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.TimezoneUtils
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.ZoneId
import java.util.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*

/**
 * End-to-End tests for all-day event alarm scheduling functionality
 * Tests the critical requirement that all-day events fire at EXACTLY the configured time
 * with NO lead time applied (unlike regular timed events)
 */
class AllDayEventE2ETest : E2ETestBase() {

    @Test
    fun testAllDayEventAlarmTiming() = runBlocking {
        Logger.i("AllDayEventE2ETest", "=== Testing All-Day Event Alarm Timing ===")
        
        // Setup: Create a rule for all-day events with a significant lead time
        val testRule = Rule(
            id = "all-day-test-rule",
            name = "All Day Test Rule",
            keywordPattern = "conference",
            isRegex = false,
            calendarIds = emptyList(), // All calendars
            leadTimeMinutes = 120, // 2 hours - should NOT be applied to all-day events
            enabled = true
        )
        
        val ruleRepository = application.ruleRepository
        ruleRepository.insertRule(testRule)
        Logger.i("AllDayEventE2ETest", "Created test rule with 120-minute lead time")
        
        // Setup: Set all-day default time to 9:00 AM
        val settingsRepository = application.settingsRepository
        settingsRepository.setAllDayDefaultTime(9, 0) // 9:00 AM
        Logger.i("AllDayEventE2ETest", "Set all-day default time to 9:00 AM")
        
        // Create an all-day event for tomorrow
        val tomorrow = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = tomorrow
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val allDayEvent = CalendarEvent(
            id = "test-all-day-event",
            title = "Annual Conference", // Matches our rule
            startTimeUtc = calendar.timeInMillis,
            endTimeUtc = calendar.timeInMillis + (24 * 60 * 60 * 1000), // 24 hours later
            calendarId = 1L,
            isAllDay = true,
            timezone = TimeZone.getDefault().id,
            lastModified = System.currentTimeMillis()
        )
        
        // Create the event via test provider
        val testEvent = calendarTestProvider.createTestEvent(
            title = "Annual Conference",
            startTimeFromNow = 24 * 60 * 60 * 1000, // Tomorrow
            isAllDay = true
        )
        Logger.i("AllDayEventE2ETest", "Created all-day conference event for tomorrow")
        
        // Trigger background worker to process the event
        val workerTriggered = applicationController.triggerBackgroundWorker()
        assertThat(workerTriggered).isTrue()
        
        // Wait for processing
        Thread.sleep(3000)
        
        // Verify alarm was scheduled
        val alarmRepository = application.alarmRepository
        val scheduledAlarms = alarmRepository.getActiveAlarmsSync()
        assertThat(scheduledAlarms).hasSize(1)
        
        val alarm = scheduledAlarms.first()
        Logger.i("AllDayEventE2ETest", "Found scheduled alarm at: ${Date(alarm.alarmTimeUtc)}")
        
        // CRITICAL TEST: Verify alarm time is exactly 9:00 AM on event day (NO lead time)
        val expectedAlarmTime = Calendar.getInstance().apply {
            timeInMillis = tomorrow
            set(Calendar.HOUR_OF_DAY, 9) // 9:00 AM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val timeDifferenceMs = Math.abs(alarm.alarmTimeUtc - expectedAlarmTime)
        val timeDifferenceMinutes = timeDifferenceMs / (60 * 1000)
        
        Logger.i("AllDayEventE2ETest", "Expected alarm time: ${Date(expectedAlarmTime)}")
        Logger.i("AllDayEventE2ETest", "Actual alarm time: ${Date(alarm.alarmTimeUtc)}")
        Logger.i("AllDayEventE2ETest", "Time difference: ${timeDifferenceMinutes} minutes")
        
        assertThat(timeDifferenceMinutes).isLessThan(1)
        
        // Verify the alarm references the correct rule and event
        assertThat(alarm.ruleId).isEqualTo(testRule.id)
        assertThat(alarm.eventId).isEqualTo(testEvent.id)
        assertThat(alarm.eventTitle).isEqualTo(testEvent.title)
        
        Logger.i("AllDayEventE2ETest", "✅ All-day event alarm timing test PASSED")
    }
    
    @Test
    fun testAllDayEventDefaultTime() = runBlocking {
        Logger.i("AllDayEventE2ETest", "=== Testing All-Day Default Time Configuration ===")
        
        val settingsRepository = application.settingsRepository
        val alarmScheduler = application.alarmScheduler
        
        // Test different default times
        val testTimes = listOf(
            Pair(8, 0),   // 8:00 AM
            Pair(20, 30), // 8:30 PM
            Pair(23, 59)  // 11:59 PM
        )
        
        for ((hour, minute) in testTimes) {
            Logger.i("AllDayEventE2ETest", "Testing default time: $hour:$minute")
            
            // Clear previous alarms
            database.alarmDao().deleteAllAlarms()
            
            // Set new default time
            settingsRepository.setAllDayDefaultTime(hour, minute)
            
            // Verify settings were saved correctly
            assertThat(settingsRepository.getAllDayDefaultHour()).isEqualTo(hour)
            assertThat(settingsRepository.getAllDayDefaultMinute()).isEqualTo(minute)
            
            // Create all-day event
            val tomorrow = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
            val calendar = Calendar.getInstance().apply {
                timeInMillis = tomorrow
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val testEvent = calendarTestProvider.createTestEvent(
                title = "All Day Meeting",
                startTimeFromNow = 24 * 60 * 60 * 1000, // Tomorrow
                isAllDay = true
            )
            
            // Event already created above
            
            // Create matching rule
            val testRule = Rule(
                id = "all-day-rule-$hour-$minute",
                name = "All Day Rule",
                keywordPattern = "meeting",
                isRegex = false,
                calendarIds = emptyList(),
                leadTimeMinutes = 60, // Should be ignored for all-day events
                enabled = true
            )
            
            database.ruleDao().insertRule(testRule)
            
            // Trigger worker
            applicationController.triggerBackgroundWorker()
            Thread.sleep(2000)
            
            // Verify alarm was scheduled for correct time
            val alarms = database.alarmDao().getActiveAlarmsSync()
            assertThat(alarms).hasSize(1)
            
            val alarm = alarms.first()
            val expectedTime = Calendar.getInstance().apply {
                timeInMillis = tomorrow
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val timeDifferenceMs = Math.abs(alarm.alarmTimeUtc - expectedTime)
            assertThat(timeDifferenceMs).isLessThan(60000)
            
            Logger.i("AllDayEventE2ETest", "✅ Default time $hour:$minute test PASSED")
        }
        
        Logger.i("AllDayEventE2ETest", "✅ All default time configurations test PASSED")
    }
    
    @Test
    fun testAllDayEventUserSettings() = runBlocking {
        Logger.i("AllDayEventE2ETest", "=== Testing All-Day Event User Settings Persistence ===")
        
        val settingsRepository = application.settingsRepository
        
        // Set custom all-day time
        val testHour = 19
        val testMinute = 30
        settingsRepository.setAllDayDefaultTime(testHour, testMinute)
        
        // Verify immediate retrieval
        assertThat(settingsRepository.getAllDayDefaultHour()).isEqualTo(testHour)
        assertThat(settingsRepository.getAllDayDefaultMinute()).isEqualTo(testMinute)
        
        // Test formatted time display
        val formattedTime = settingsRepository.getAllDayDefaultTimeFormatted()
        assertThat(formattedTime).isNotNull()
        assertThat(formattedTime).isNotEmpty()
        
        val formatted24Hour = settingsRepository.getAllDayDefaultTimeFormatted24Hour()
        assertThat(formatted24Hour).isEqualTo("19:30")
        
        Logger.i("AllDayEventE2ETest", "Formatted time: '$formattedTime', 24-hour: '$formatted24Hour'")
        
        // Test StateFlow updates (defensive refresh)
        settingsRepository.refreshAllDayTimeStateFlows()
        
        // Verify settings survive a "restart" (creating new repository instance)
        val newSettingsRepository = SettingsRepository(context)
        assertThat(newSettingsRepository.getAllDayDefaultHour()).isEqualTo(testHour)
        assertThat(newSettingsRepository.getAllDayDefaultMinute()).isEqualTo(testMinute)
        
        // Test reset to defaults
        settingsRepository.resetToDefaults()
        assertThat(settingsRepository.getAllDayDefaultHour()).isEqualTo(20)
        assertThat(settingsRepository.getAllDayDefaultMinute()).isEqualTo(0)
        
        Logger.i("AllDayEventE2ETest", "✅ All-day settings persistence test PASSED")
    }
    
    @Test
    fun testAllDayEventTimezoneHandling() = runBlocking {
        Logger.i("AllDayEventE2ETest", "=== Testing All-Day Event Timezone Handling ===")
        
        val settingsRepository = application.settingsRepository
        settingsRepository.setAllDayDefaultTime(10, 0) // 10:00 AM
        
        // Create rule for all-day events
        val testRule = Rule(
            id = "timezone-all-day-rule",
            name = "Timezone All Day Rule",
            keywordPattern = "workshop",
            isRegex = false,
            calendarIds = emptyList(),
            leadTimeMinutes = 90, // Should be ignored
            enabled = true
        )
        
        database.ruleDao().insertRule(testRule)
        
        // Create all-day event for tomorrow
        val tomorrow = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = tomorrow
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val allDayWorkshop = calendarTestProvider.createTestEvent(
            title = "Design Workshop", // Matches rule
            startTimeFromNow = 24 * 60 * 60 * 1000, // Tomorrow
            isAllDay = true
        )
        
        // Trigger worker to process event
        applicationController.triggerBackgroundWorker()
        Thread.sleep(2000)
        
        // Verify alarm was scheduled
        val alarms = database.alarmDao().getActiveAlarmsSync()
        assertThat(alarms).hasSize(1)
        
        val originalAlarm = alarms.first()
        Logger.i("AllDayEventE2ETest", "Original alarm scheduled for: ${Date(originalAlarm.alarmTimeUtc)}")
        
        // Simulate timezone change (this would normally be detected by system receiver)
        settingsRepository.handleTimezoneChange()
        
        // Clear old alarms and reschedule (simulating timezone change handling)
        database.alarmDao().deleteAllAlarms()
        
        // Re-trigger worker after timezone change
        applicationController.triggerBackgroundWorker()
        Thread.sleep(2000)
        
        // Verify new alarm was scheduled
        val newAlarms = database.alarmDao().getActiveAlarmsSync()
        assertThat(newAlarms).hasSize(1)
        
        val newAlarm = newAlarms.first()
        Logger.i("AllDayEventE2ETest", "New alarm scheduled for: ${Date(newAlarm.alarmTimeUtc)}")
        
        // Verify the alarm time still represents 10:00 AM in the current timezone
        val alarmCalendar = Calendar.getInstance().apply {
            timeInMillis = newAlarm.alarmTimeUtc
        }
        
        assertThat(alarmCalendar.get(Calendar.HOUR_OF_DAY)).isEqualTo(10)
        assertThat(alarmCalendar.get(Calendar.MINUTE)).isEqualTo(0)
        
        // Test timezone formatting
        val formattedTime = TimezoneUtils.formatTimeWithTimezone(
            newAlarm.alarmTimeUtc, 
            ZoneId.systemDefault(), 
            true
        )
        assertThat(formattedTime).isNotNull()
        assertThat(formattedTime).contains("10:")
        
        Logger.i("AllDayEventE2ETest", "Formatted alarm time: $formattedTime")
        Logger.i("AllDayEventE2ETest", "✅ All-day timezone handling test PASSED")
    }
}