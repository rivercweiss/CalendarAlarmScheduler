package com.example.calendaralarmscheduler.edge

import android.app.AlarmManager
import android.content.Context
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm as DbScheduledAlarm
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import com.example.calendaralarmscheduler.e2e.E2ETestBase
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*

/**
 * Tests system alarm limits and behavior with maximum alarms
 * Verifies app behavior when approaching Android's alarm scheduling limits
 */
class SystemAlarmLimitsTest : E2ETestBase() {

    companion object {
        // Conservative estimate of system alarm limits
        // Actual limits vary by Android version and manufacturer
        private const val CONSERVATIVE_ALARM_LIMIT = 500
        private const val STRESS_TEST_ALARM_COUNT = 1000
    }

    @Test
    fun testAlarmLimitDetection() = runBlocking {
        Logger.i("SystemAlarmLimitsTest", "=== Testing Alarm Limit Detection ===")
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmScheduler = application.alarmScheduler
        
        // Create rule for limit testing
        val limitRule = Rule(
            id = "limit-test-rule",
            name = "Limit Test Rule",
            keywordPattern = "limit",
            isRegex = false,
            calendarIds = emptyList<Long>(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        database.ruleDao().insertRule(limitRule)
        
        // Create many alarms to approach system limits
        val testAlarms = mutableListOf<ScheduledAlarm>()
        val currentTime = System.currentTimeMillis()
        
        for (i in 1..CONSERVATIVE_ALARM_LIMIT) {
            val alarm = ScheduledAlarm(
                id = "limit-alarm-$i",
                eventId = "limit-event-$i",
                ruleId = limitRule.id,
                eventTitle = "Limit Test Event $i",
                eventStartTimeUtc = currentTime + (i * 60 * 60 * 1000L), // Spread over hours
                alarmTimeUtc = currentTime + (i * 60 * 60 * 1000L) - (30 * 60 * 1000), // 30 min before
                scheduledAt = currentTime,
                userDismissed = false,
                pendingIntentRequestCode = 100000 + i,
                lastEventModified = currentTime
            )
            testAlarms.add(alarm)
        }
        
        Logger.i("SystemAlarmLimitsTest", "Created ${testAlarms.size} test alarms for limit testing")
        
        var successfulSchedules = 0
        var failedSchedules = 0
        
        // Attempt to schedule all alarms
        testAlarms.chunked(50).forEachIndexed { chunkIndex, chunk ->
            Logger.d("SystemAlarmLimitsTest", "Scheduling chunk ${chunkIndex + 1}/${testAlarms.size / 50}")
            
            chunk.forEach { alarm ->
                try {
                    val result = alarmScheduler.scheduleAlarm(alarm)
                    if (result.success) {
                        val dbAlarm = DbScheduledAlarm(
                            id = alarm.id,
                            eventId = alarm.eventId,
                            ruleId = alarm.ruleId,
                            eventTitle = alarm.eventTitle,
                            eventStartTimeUtc = alarm.eventStartTimeUtc,
                            alarmTimeUtc = alarm.alarmTimeUtc,
                            scheduledAt = alarm.scheduledAt,
                            userDismissed = alarm.userDismissed,
                            pendingIntentRequestCode = alarm.pendingIntentRequestCode,
                            lastEventModified = alarm.lastEventModified
                        )
                        database.alarmDao().insertAlarm(dbAlarm)
                        successfulSchedules++
                    } else {
                        failedSchedules++
                        Logger.d("SystemAlarmLimitsTest", "Schedule failed: ${result.message}")
                    }
                } catch (e: Exception) {
                    failedSchedules++
                    Logger.d("SystemAlarmLimitsTest", "Schedule exception: ${e.message}")
                }
            }
            
            // Brief pause between chunks
            delay(100)
        }
        
        Logger.i("SystemAlarmLimitsTest", "Alarm scheduling results: $successfulSchedules successful, $failedSchedules failed")
        
        // Verify we can schedule a reasonable number of alarms
        assertTrue(
            "Should be able to schedule at least 100 alarms: $successfulSchedules",
            successfulSchedules > 100
        )
        
        // If we hit limits, verify graceful handling
        if (failedSchedules > 0) {
            Logger.w("SystemAlarmLimitsTest", "Hit system alarm limits - this is expected behavior")
            
            // Verify app continues to function
            val finalAlarms = database.alarmDao().getActiveAlarmsSync()
            assertEquals("Database should match successful schedules", successfulSchedules, finalAlarms.size)
            
            // Verify most recent alarms were prioritized (if limit was hit)
            val sortedAlarms = finalAlarms.sortedBy { it.alarmTimeUtc }
            assertTrue("Should have some scheduled alarms", sortedAlarms.isNotEmpty())
            
            val newestAlarmTime = sortedAlarms.last().alarmTimeUtc
            assertTrue(
                "Newest alarm should be in future: ${newestAlarmTime - currentTime}ms",
                newestAlarmTime > currentTime
            )
        }
        
        Logger.i("SystemAlarmLimitsTest", "✅ Alarm limit detection test PASSED")
    }
    
    @Test
    fun testAlarmPrioritization() = runBlocking {
        Logger.i("SystemAlarmLimitsTest", "=== Testing Alarm Prioritization ===")
        
        // Create rules with different priorities (lead times)
        val priorityRules = listOf(
            Rule(
                id = "urgent-rule",
                name = "Urgent Rule",
                keywordPattern = "urgent",
                isRegex = false,
                calendarIds = emptyList<Long>(),
                leadTimeMinutes = 5, // Very short lead time - high priority
                enabled = true
            ),
            Rule(
                id = "normal-rule",
                name = "Normal Rule", 
                keywordPattern = "normal",
                isRegex = false,
                calendarIds = emptyList<Long>(),
                leadTimeMinutes = 60, // Medium lead time
                enabled = true
            ),
            Rule(
                id = "advance-rule",
                name = "Advance Rule",
                keywordPattern = "advance",
                isRegex = false,
                calendarIds = emptyList<Long>(),
                leadTimeMinutes = 1440, // 1 day lead time - lower priority
                enabled = true
            )
        )
        
        priorityRules.forEach { rule ->
            database.ruleDao().insertRule(rule)
        }
        
        // Create events with different priorities
        val currentTime = System.currentTimeMillis()
        val priorityEvents = listOf(
            // Urgent events (near future)
            CalendarEvent(
                id = "urgent-event-1",
                title = "Urgent Meeting 1",
                startTimeUtc = currentTime + (2 * 60 * 60 * 1000), // 2 hours from now
                endTimeUtc = currentTime + (3 * 60 * 60 * 1000),
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = currentTime
            ),
            CalendarEvent(
                id = "urgent-event-2",
                title = "Urgent Call 2",
                startTimeUtc = currentTime + (3 * 60 * 60 * 1000), // 3 hours from now
                endTimeUtc = currentTime + (4 * 60 * 60 * 1000),
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = currentTime
            ),
            // Normal events (medium future)
            CalendarEvent(
                id = "normal-event-1", 
                title = "Normal Meeting 1",
                startTimeUtc = currentTime + (12 * 60 * 60 * 1000), // 12 hours from now
                endTimeUtc = currentTime + (13 * 60 * 60 * 1000),
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = currentTime
            ),
            CalendarEvent(
                id = "normal-event-2",
                title = "Normal Review 2", 
                startTimeUtc = currentTime + (18 * 60 * 60 * 1000), // 18 hours from now
                endTimeUtc = currentTime + (19 * 60 * 60 * 1000),
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = currentTime
            ),
            // Advance events (far future)
            CalendarEvent(
                id = "advance-event-1",
                title = "Advance Planning 1",
                startTimeUtc = currentTime + (48 * 60 * 60 * 1000), // 2 days from now
                endTimeUtc = currentTime + (49 * 60 * 60 * 1000),
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = currentTime
            ),
            CalendarEvent(
                id = "advance-event-2",
                title = "Advance Workshop 2",
                startTimeUtc = currentTime + (72 * 60 * 60 * 1000), // 3 days from now
                endTimeUtc = currentTime + (73 * 60 * 60 * 1000),
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = currentTime
            )
        )
        
        // Use createTestEvent to ensure events are properly registered with the test provider
        priorityEvents.forEach { event ->
            calendarTestProvider.createTestEvent(
                title = event.title,
                startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                isAllDay = event.isAllDay,
                calendarId = event.calendarId
            )
        }
        
        Logger.i("SystemAlarmLimitsTest", "Created ${priorityEvents.size} priority test events")
        
        // Process events
        val priorityWorkerResult = applicationController.triggerBackgroundWorker()
        assertTrue("Priority worker should succeed", priorityWorkerResult)
        delay(3000)
        
        // Analyze alarm prioritization
        val priorityAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("SystemAlarmLimitsTest", "Created ${priorityAlarms.size} priority alarms")
        
        assertTrue("Should create priority alarms", priorityAlarms.isNotEmpty())
        
        // Group alarms by priority (based on lead time)
        val urgentAlarms = priorityAlarms.filter { it.ruleId == "urgent-rule" }
        val normalAlarms = priorityAlarms.filter { it.ruleId == "normal-rule" }
        val advanceAlarms = priorityAlarms.filter { it.ruleId == "advance-rule" }
        
        Logger.i("SystemAlarmLimitsTest", 
            "Alarm distribution: ${urgentAlarms.size} urgent, ${normalAlarms.size} normal, ${advanceAlarms.size} advance"
        )
        
        // Verify urgent alarms are scheduled correctly
        urgentAlarms.forEach { alarm ->
            val leadTime = alarm.eventStartTimeUtc - alarm.alarmTimeUtc
            val expectedLeadTime = 5 * 60 * 1000L // 5 minutes
            val timeDifference = Math.abs(leadTime - expectedLeadTime)
            
            assertTrue(
                "Urgent alarm should have 5-minute lead time: ${leadTime / (60 * 1000)}min",
                timeDifference < (60 * 1000) // 1 minute tolerance
            )
        }
        
        // If system is under stress, urgent events should be prioritized
        if (priorityAlarms.size < priorityEvents.size) {
            Logger.w("SystemAlarmLimitsTest", "Not all events scheduled - testing prioritization")
            
            // Urgent events should have higher scheduling success rate
            val urgentScheduleRate = urgentAlarms.size.toDouble() / priorityEvents.count { it.title.contains("Urgent") }
            val advanceScheduleRate = advanceAlarms.size.toDouble() / priorityEvents.count { it.title.contains("Advance") }
            
            assertTrue(
                "Urgent events should have higher scheduling priority: urgent ${urgentScheduleRate}, advance ${advanceScheduleRate}",
                urgentScheduleRate >= advanceScheduleRate
            )
        }
        
        Logger.i("SystemAlarmLimitsTest", "✅ Alarm prioritization test PASSED")
    }
    
    @Test
    fun testAlarmCleanupUnderLimits() = runBlocking {
        Logger.i("SystemAlarmLimitsTest", "=== Testing Alarm Cleanup Under Limits ===")
        
        // Create rule for cleanup testing
        val cleanupRule = Rule(
            id = "cleanup-rule",
            name = "Cleanup Test Rule",
            keywordPattern = "cleanup",
            isRegex = false,
            calendarIds = emptyList(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        database.ruleDao().insertRule(cleanupRule)
        
        val currentTime = System.currentTimeMillis()
        
        // Create mix of past, current, and future alarms
        val testAlarms = listOf(
            // Past alarms (should be cleaned up)
            ScheduledAlarm(
                id = "past-alarm-1",
                eventId = "past-event-1",
                ruleId = cleanupRule.id,
                eventTitle = "Past Cleanup Event 1",
                eventStartTimeUtc = currentTime - (2 * 60 * 60 * 1000), // 2 hours ago
                alarmTimeUtc = currentTime - (2 * 60 * 60 * 1000) - (30 * 60 * 1000), // 2.5 hours ago
                scheduledAt = currentTime - (3 * 60 * 60 * 1000),
                userDismissed = false,
                pendingIntentRequestCode = 200001,
                lastEventModified = currentTime - (4 * 60 * 60 * 1000)
            ),
            ScheduledAlarm(
                id = "past-alarm-2",
                eventId = "past-event-2",
                ruleId = cleanupRule.id,
                eventTitle = "Past Cleanup Event 2",
                eventStartTimeUtc = currentTime - (60 * 60 * 1000), // 1 hour ago
                alarmTimeUtc = currentTime - (60 * 60 * 1000) - (30 * 60 * 1000), // 1.5 hours ago
                scheduledAt = currentTime - (2 * 60 * 60 * 1000),
                userDismissed = false,
                pendingIntentRequestCode = 200002,
                lastEventModified = currentTime - (3 * 60 * 60 * 1000)
            ),
            // Current/future alarms (should be kept)
            ScheduledAlarm(
                id = "future-alarm-1",
                eventId = "future-event-1",
                ruleId = cleanupRule.id,
                eventTitle = "Future Cleanup Event 1",
                eventStartTimeUtc = currentTime + (2 * 60 * 60 * 1000), // 2 hours from now
                alarmTimeUtc = currentTime + (90 * 60 * 1000), // 1.5 hours from now
                scheduledAt = currentTime,
                userDismissed = false,
                pendingIntentRequestCode = 200003,
                lastEventModified = currentTime
            ),
            ScheduledAlarm(
                id = "future-alarm-2",
                eventId = "future-event-2",
                ruleId = cleanupRule.id,
                eventTitle = "Future Cleanup Event 2", 
                eventStartTimeUtc = currentTime + (4 * 60 * 60 * 1000), // 4 hours from now
                alarmTimeUtc = currentTime + (3.5 * 60 * 60 * 1000).toLong(), // 3.5 hours from now
                scheduledAt = currentTime,
                userDismissed = false,
                pendingIntentRequestCode = 200004,
                lastEventModified = currentTime
            ),
            // Dismissed alarms (should be cleaned up)
            ScheduledAlarm(
                id = "dismissed-alarm-1",
                eventId = "dismissed-event-1",
                ruleId = cleanupRule.id,
                eventTitle = "Dismissed Cleanup Event 1",
                eventStartTimeUtc = currentTime + (60 * 60 * 1000), // 1 hour from now
                alarmTimeUtc = currentTime + (30 * 60 * 1000), // 30 min from now
                scheduledAt = currentTime - (60 * 60 * 1000),
                userDismissed = true, // Dismissed by user
                pendingIntentRequestCode = 200005,
                lastEventModified = currentTime - (30 * 60 * 1000)
            )
        )
        
        // Insert all test alarms
        testAlarms.forEach { alarm ->
            val dbAlarm = DbScheduledAlarm(
                id = alarm.id,
                eventId = alarm.eventId,
                ruleId = alarm.ruleId,
                eventTitle = alarm.eventTitle,
                eventStartTimeUtc = alarm.eventStartTimeUtc,
                alarmTimeUtc = alarm.alarmTimeUtc,
                scheduledAt = alarm.scheduledAt,
                userDismissed = alarm.userDismissed,
                pendingIntentRequestCode = alarm.pendingIntentRequestCode,
                lastEventModified = alarm.lastEventModified
            )
            database.alarmDao().insertAlarm(dbAlarm)
        }
        
        Logger.i("SystemAlarmLimitsTest", "Inserted ${testAlarms.size} test alarms for cleanup testing")
        
        val initialAlarms = database.alarmDao().getAllAlarmsSync()
        val initialAlarmCount = initialAlarms.size
        assertEquals("All test alarms should be inserted", testAlarms.size, initialAlarmCount)
        
        // Trigger cleanup process (usually done by worker)
        val alarmRepository = application.alarmRepository
        alarmRepository.deleteExpiredAlarms()
        
        // Wait for cleanup to complete
        delay(1000)
        
        // Verify cleanup results
        val allAlarmsAfterCleanup = database.alarmDao().getAllAlarmsSync()
        val activeAlarmsAfterCleanup = database.alarmDao().getActiveAlarmsSync()
        
        Logger.i("SystemAlarmLimitsTest", 
            "After cleanup: ${allAlarmsAfterCleanup.size} total alarms, ${activeAlarmsAfterCleanup.size} active alarms"
        )
        
        // Should have fewer total alarms (past and dismissed cleaned up)
        assertTrue(
            "Cleanup should remove some alarms: ${allAlarmsAfterCleanup.size} < ${testAlarms.size}",
            allAlarmsAfterCleanup.size < testAlarms.size
        )
        
        // Active alarms should only be future, non-dismissed alarms
        val expectedActiveCount = testAlarms.count { 
            it.eventStartTimeUtc > currentTime && !it.userDismissed 
        }
        
        assertEquals(
            "Active alarms should match expected count: expected $expectedActiveCount, got ${activeAlarmsAfterCleanup.size}",
            expectedActiveCount,
            activeAlarmsAfterCleanup.size
        )
        
        // Verify correct alarms remain
        val remainingAlarmIds = activeAlarmsAfterCleanup.map { it.id }.toSet()
        assertTrue("Future alarm 1 should remain", remainingAlarmIds.contains("future-alarm-1"))
        assertTrue("Future alarm 2 should remain", remainingAlarmIds.contains("future-alarm-2"))
        assertFalse("Past alarm 1 should be cleaned up", remainingAlarmIds.contains("past-alarm-1"))
        assertFalse("Past alarm 2 should be cleaned up", remainingAlarmIds.contains("past-alarm-2"))
        assertFalse("Dismissed alarm should be cleaned up", remainingAlarmIds.contains("dismissed-alarm-1"))
        
        Logger.i("SystemAlarmLimitsTest", "✅ Alarm cleanup under limits test PASSED")
    }
    
    @Test
    fun testSystemAlarmRecovery() = runBlocking {
        Logger.i("SystemAlarmLimitsTest", "=== Testing System Alarm Recovery ===")
        
        // Create rule for recovery testing
        val recoveryRule = Rule(
            id = "recovery-rule",
            name = "Recovery Test Rule",
            keywordPattern = "recovery",
            isRegex = false,
            calendarIds = emptyList(),
            leadTimeMinutes = 60,
            enabled = true
        )
        
        database.ruleDao().insertRule(recoveryRule)
        
        val currentTime = System.currentTimeMillis()
        
        // Create test alarms
        val recoveryAlarms = (1..10).map { index ->
            ScheduledAlarm(
                id = "recovery-alarm-$index",
                eventId = "recovery-event-$index",
                ruleId = recoveryRule.id,
                eventTitle = "Recovery Test Event $index",
                eventStartTimeUtc = currentTime + (index * 60 * 60 * 1000L), // Spread over hours
                alarmTimeUtc = currentTime + (index * 60 * 60 * 1000L) - (60 * 60 * 1000), // 1 hour before
                scheduledAt = currentTime,
                userDismissed = false,
                pendingIntentRequestCode = 300000 + index,
                lastEventModified = currentTime
            )
        }
        
        // Insert alarms in database
        recoveryAlarms.forEach { alarm ->
            val dbAlarm = DbScheduledAlarm(
                id = alarm.id,
                eventId = alarm.eventId,
                ruleId = alarm.ruleId,
                eventTitle = alarm.eventTitle,
                eventStartTimeUtc = alarm.eventStartTimeUtc,
                alarmTimeUtc = alarm.alarmTimeUtc,
                scheduledAt = alarm.scheduledAt,
                userDismissed = alarm.userDismissed,
                pendingIntentRequestCode = alarm.pendingIntentRequestCode,
                lastEventModified = alarm.lastEventModified
            )
            database.alarmDao().insertAlarm(dbAlarm)
        }
        
        Logger.i("SystemAlarmLimitsTest", "Created ${recoveryAlarms.size} recovery test alarms")
        
        // Simulate system alarm scheduling
        val alarmScheduler = application.alarmScheduler
        var successfulRecoveries = 0
        
        recoveryAlarms.forEach { alarm ->
            try {
                val result = alarmScheduler.scheduleAlarm(alarm)
                if (result.success) {
                    successfulRecoveries++
                } else {
                    Logger.d("SystemAlarmLimitsTest", "Recovery failed for ${alarm.id}: ${result.message}")
                }
            } catch (e: Exception) {
                Logger.d("SystemAlarmLimitsTest", "Recovery exception for ${alarm.id}: ${e.message}")
            }
        }
        
        Logger.i("SystemAlarmLimitsTest", "Successfully recovered $successfulRecoveries / ${recoveryAlarms.size} alarms")
        
        // Should recover most alarms
        assertTrue(
            "Should recover most alarms: $successfulRecoveries / ${recoveryAlarms.size}",
            successfulRecoveries >= recoveryAlarms.size * 0.8 // At least 80%
        )
        
        // Test recovery after simulated reboot
        // (In real scenario, this would be triggered by BootReceiver)
        
        // Get all active alarms from database
        val alarmsToRecover = database.alarmDao().getActiveAlarmsSync()
        Logger.i("SystemAlarmLimitsTest", "Attempting to recover ${alarmsToRecover.size} alarms after 'reboot'")
        
        var rebootRecoveries = 0
        for (alarm in alarmsToRecover) {
            // Only recover future alarms
            if (alarm.alarmTimeUtc > currentTime) {
                try {
                    // Convert to domain model
                    val domainAlarm = ScheduledAlarm(
                        id = alarm.id,
                        eventId = alarm.eventId,
                        ruleId = alarm.ruleId,
                        eventTitle = alarm.eventTitle,
                        eventStartTimeUtc = alarm.eventStartTimeUtc,
                        alarmTimeUtc = alarm.alarmTimeUtc,
                        scheduledAt = alarm.scheduledAt,
                        userDismissed = alarm.userDismissed,
                        pendingIntentRequestCode = alarm.pendingIntentRequestCode,
                        lastEventModified = alarm.lastEventModified
                    )
                    val result = alarmScheduler.scheduleAlarm(domainAlarm)
                    if (result.success) {
                        rebootRecoveries++
                    }
                } catch (e: Exception) {
                    Logger.d("SystemAlarmLimitsTest", "Reboot recovery exception for ${alarm.id}: ${e.message}")
                }
            }
        }
        
        Logger.i("SystemAlarmLimitsTest", "Successfully recovered $rebootRecoveries alarms after 'reboot'")
        
        val futureAlarms = alarmsToRecover.count { it.alarmTimeUtc > currentTime }
        assertTrue(
            "Should recover most future alarms after reboot: $rebootRecoveries / $futureAlarms",
            rebootRecoveries >= futureAlarms * 0.8 // At least 80% of future alarms
        )
        
        Logger.i("SystemAlarmLimitsTest", "✅ System alarm recovery test PASSED")
    }
}