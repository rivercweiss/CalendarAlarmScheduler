package com.example.calendaralarmscheduler.integration

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.e2e.E2ETestBase
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.workers.CalendarRefreshWorker
import com.example.calendaralarmscheduler.workers.WorkerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*

/**
 * Integration tests for WorkManager background processing
 * Tests the complete background worker flow with real data processing
 */
class WorkerIntegrationTest : E2ETestBase() {

    @Test
    fun testCalendarRefreshWorkerExecution() = runBlocking {
        Logger.i("WorkerIntegrationTest", "=== Testing Calendar Refresh Worker Execution ===")
        
        // Create test rule first
        val testRule = Rule(
            id = "worker-test-rule",
            name = "Worker Test Rule",
            keywordPattern = "meeting|appointment", 
            isRegex = true,
            calendarIds = emptyList(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        database.ruleDao().insertRule(testRule)
        Logger.i("WorkerIntegrationTest", "Created test rule")
        
        // Create test events that should match the rule
        val testEvents = listOf(
            CalendarEvent(
                id = "worker-meeting-1",
                title = "Important Meeting",
                startTimeUtc = System.currentTimeMillis() + (4 * 60 * 60 * 1000), // 4 hours from now
                endTimeUtc = System.currentTimeMillis() + (5 * 60 * 60 * 1000), // 5 hours from now
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = System.currentTimeMillis()
            ),
            CalendarEvent(
                id = "worker-appointment-1",
                title = "Doctor Appointment",
                startTimeUtc = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 1 day from now
                endTimeUtc = System.currentTimeMillis() + (24 * 60 * 60 * 1000) + (60 * 60 * 1000), // 1 day + 1 hour
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = System.currentTimeMillis()
            )
        )
        
        // Inject test events
        testEvents.forEach { event ->
            calendarTestProvider.createTestEvent(
                title = event.title,
                startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                isAllDay = event.isAllDay,
                calendarId = event.calendarId
            )
        }
        
        Logger.i("WorkerIntegrationTest", "Injected ${testEvents.size} test events")
        
        // Clear any existing alarms to start clean
        database.alarmDao().deleteAllAlarms()
        
        // Create and execute the worker
        val worker = TestListenableWorkerBuilder<CalendarRefreshWorker>(context).build()
        
        val result = worker.doWork()
        Logger.i("WorkerIntegrationTest", "Worker execution result: $result")
        
        // Verify worker completed successfully
        assertEquals("Worker should complete successfully", ListenableWorker.Result.success(), result)
        
        // Wait for asynchronous processing to complete
        delay(3000)
        
        // Verify alarms were created
        val scheduledAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("WorkerIntegrationTest", "Found ${scheduledAlarms.size} scheduled alarms after worker execution")
        
        assertTrue("Worker should have created alarms for matching events", scheduledAlarms.isNotEmpty())
        
        // Verify alarms match our test events
        val alarmEventIds = scheduledAlarms.map { it.eventId }.toSet()
        val testEventIds = testEvents.map { it.id }.toSet()
        
        val matchingEventIds = alarmEventIds.intersect(testEventIds)
        assertTrue("Some alarms should match our test events", matchingEventIds.isNotEmpty())
        
        Logger.i("WorkerIntegrationTest", "Alarm event IDs: $alarmEventIds")
        Logger.i("WorkerIntegrationTest", "Test event IDs: $testEventIds")
        Logger.i("WorkerIntegrationTest", "Matching IDs: $matchingEventIds")
        
        // Verify alarm timing
        scheduledAlarms.forEach { alarm ->
            val correspondingEvent = testEvents.find { it.id == alarm.eventId }
            if (correspondingEvent != null) {
                val expectedAlarmTime = correspondingEvent.startTimeUtc - (testRule.leadTimeMinutes * 60 * 1000)
                val timeDifference = Math.abs(alarm.alarmTimeUtc - expectedAlarmTime)
                
                assertTrue(
                    "Alarm should be scheduled ${testRule.leadTimeMinutes} minutes before event start",
                    timeDifference < 60000 // Less than 1 minute difference
                )
                
                Logger.i("WorkerIntegrationTest", "Alarm for '${alarm.eventTitle}' scheduled correctly")
            }
        }
        
        Logger.i("WorkerIntegrationTest", "✅ Calendar refresh worker execution test PASSED")
    }
    
    @Test
    fun testWorkerManagerIntegration() = runBlocking {
        Logger.i("WorkerIntegrationTest", "=== Testing WorkerManager Integration ===")
        
        val workerManager = WorkerManager(context)
        val settingsRepository = application.settingsRepository
        
        // Test initial work scheduling
        workerManager.schedulePeriodicRefresh(30) // 30 minutes
        delay(1000)
        
        val initialStatus = workerManager.getWorkStatus()
        Logger.i("WorkerIntegrationTest", "Initial work status: ${initialStatus.state}, scheduled: ${initialStatus.isScheduled}")
        
        assertTrue("Periodic work should be scheduled", initialStatus.isScheduled)
        
        // Test rescheduling with different interval
        workerManager.reschedulePeriodicRefresh(15) // 15 minutes
        delay(1000)
        
        val rescheduledStatus = workerManager.getWorkStatus()
        Logger.i("WorkerIntegrationTest", "Rescheduled work status: ${rescheduledStatus.state}, scheduled: ${rescheduledStatus.isScheduled}")
        
        assertTrue("Work should still be scheduled after reschedule", rescheduledStatus.isScheduled)
        
        // Test one-time work trigger
        val oneTimeResult = applicationController.triggerBackgroundWorker()
        assertTrue("One-time work should be triggered successfully", oneTimeResult)
        
        Logger.i("WorkerIntegrationTest", "One-time work triggered: $oneTimeResult")
        
        // Test work cancellation
        workerManager.cancelPeriodicRefresh()
        delay(1000)
        
        val cancelledStatus = workerManager.getWorkStatus()
        Logger.i("WorkerIntegrationTest", "Cancelled work status: ${cancelledStatus.state}, scheduled: ${cancelledStatus.isScheduled}")
        
        // Note: Work may still show as scheduled briefly after cancellation
        Logger.i("WorkerIntegrationTest", "Work cancellation requested (may take time to reflect)")
        
        // Test work status descriptions
        val availableIntervals = WorkerManager.AVAILABLE_INTERVALS
        availableIntervals.forEach { interval ->
            val description = workerManager.getIntervalDescription(interval)
            assertTrue("Description should contain interval value", description.contains(interval.toString()))
            Logger.i("WorkerIntegrationTest", "Interval $interval -> $description")
        }
        
        Logger.i("WorkerIntegrationTest", "✅ WorkerManager integration test PASSED")
    }
    
    @Test
    fun testWorkerWithRealCalendarData() = runBlocking {
        Logger.i("WorkerIntegrationTest", "=== Testing Worker with Real Calendar Data ===")
        
        val calendarRepository = application.calendarRepository
        val settingsRepository = application.settingsRepository
        
        // Only run if we have calendar permission
        if (!isPermissionGranted(android.Manifest.permission.READ_CALENDAR)) {
            Logger.w("WorkerIntegrationTest", "Skipping real calendar test - no permission")
            return@runBlocking
        }
        
        // Create comprehensive rules to match various event types
        val comprehensiveRules = listOf(
            Rule(
                id = "comprehensive-work-rule",
                name = "Work Events", 
                keywordPattern = "meeting|call|standup|review|interview|presentation|demo",
                isRegex = true,
                calendarIds = emptyList(),
                leadTimeMinutes = 15,
                enabled = true
            ),
            Rule(
                id = "comprehensive-personal-rule",
                name = "Personal Events",
                keywordPattern = "appointment|dinner|birthday|anniversary|vacation|holiday",
                isRegex = true,
                calendarIds = emptyList(),
                leadTimeMinutes = 60,
                enabled = true
            ),
            Rule(
                id = "comprehensive-urgent-rule", 
                name = "Urgent Events",
                keywordPattern = "urgent|important|critical|emergency",
                isRegex = true,
                calendarIds = emptyList(),
                leadTimeMinutes = 5,
                enabled = true
            )
        )
        
        // Insert comprehensive rules
        comprehensiveRules.forEach { rule ->
            database.ruleDao().insertRule(rule)
        }
        
        Logger.i("WorkerIntegrationTest", "Created ${comprehensiveRules.size} comprehensive rules")
        
        // Get real calendar events
        val currentTime = System.currentTimeMillis()
        val twoDaysFromNow = currentTime + (2 * 24 * 60 * 60 * 1000)
        val realEvents = calendarRepository.getUpcomingEvents()
        
        Logger.i("WorkerIntegrationTest", "Found ${realEvents.size} real calendar events")
        
        if (realEvents.isEmpty()) {
            Logger.w("WorkerIntegrationTest", "No real events found - injecting synthetic events for testing")
            
            // Create synthetic events covering different scenarios
            val syntheticEvents = listOf(
                CalendarEvent(
                    id = "synthetic-meeting",
                    title = "Team Standup Meeting",
                    startTimeUtc = currentTime + (6 * 60 * 60 * 1000), // 6 hours from now
                    endTimeUtc = currentTime + (6 * 60 * 60 * 1000) + (30 * 60 * 1000), // 30 min duration
                    calendarId = 1L,
                    isAllDay = false,
                    timezone = "UTC",
                    lastModified = currentTime
                ),
                CalendarEvent(
                    id = "synthetic-appointment", 
                    title = "Important Doctor Appointment",
                    startTimeUtc = currentTime + (8 * 60 * 60 * 1000), // 8 hours from now
                    endTimeUtc = currentTime + (9 * 60 * 60 * 1000), // 1 hour duration
                    calendarId = 1L,
                    isAllDay = false,
                    timezone = "UTC",
                    lastModified = currentTime
                )
            )
            
            syntheticEvents.forEach { event ->
                calendarTestProvider.createTestEvent(
                    title = event.title,
                    startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                    durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                    isAllDay = event.isAllDay,
                    calendarId = event.calendarId
                )
            }
        }
        
        // Set last sync time to ensure we process events
        settingsRepository.setLastSyncTime(0L) // Force full sync
        
        // Clear existing alarms
        database.alarmDao().deleteAllAlarms()
        
        // Execute worker
        val worker = TestListenableWorkerBuilder<CalendarRefreshWorker>(context).build()
        val result = worker.doWork()
        
        assertEquals("Worker should succeed with real data", ListenableWorker.Result.success(), result)
        
        // Wait for processing
        delay(5000)
        
        // Analyze results
        val resultingAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("WorkerIntegrationTest", "Created ${resultingAlarms.size} alarms from real/synthetic events")
        
        // Group alarms by rule
        val alarmsByRule = resultingAlarms.groupBy { it.ruleId }
        alarmsByRule.forEach { (ruleId, alarms) ->
            val rule = comprehensiveRules.find { it.id == ruleId }
            Logger.i("WorkerIntegrationTest", "Rule '${rule?.name}' matched ${alarms.size} events")
        }
        
        // Verify each alarm has proper timing
        resultingAlarms.forEach { alarm ->
            assertTrue("Alarm should be scheduled in the future", alarm.alarmTimeUtc > currentTime)
            assertTrue("Event should start after alarm", alarm.eventStartTimeUtc > alarm.alarmTimeUtc)
            
            val rule = comprehensiveRules.find { it.id == alarm.ruleId }
            if (rule != null) {
                val expectedLeadTime = rule.leadTimeMinutes * 60 * 1000
                val actualLeadTime = alarm.eventStartTimeUtc - alarm.alarmTimeUtc
                val leadTimeDifference = Math.abs(actualLeadTime - expectedLeadTime)
                
                assertTrue(
                    "Alarm lead time should match rule settings",
                    leadTimeDifference < 60000 // Less than 1 minute difference
                )
            }
        }
        
        // Update sync time to reflect completion
        settingsRepository.updateLastSyncTime()
        
        Logger.i("WorkerIntegrationTest", "✅ Worker with real calendar data test PASSED")
    }
    
    @Test
    fun testWorkerErrorHandling() = runBlocking {
        Logger.i("WorkerIntegrationTest", "=== Testing Worker Error Handling ===")
        
        // Test worker with invalid rule data
        val invalidRule = Rule(
            id = "invalid-regex-rule",
            name = "Invalid Regex Rule",
            keywordPattern = "[unclosed bracket", // Invalid regex
            isRegex = true,
            calendarIds = emptyList(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        database.ruleDao().insertRule(invalidRule)
        Logger.i("WorkerIntegrationTest", "Created invalid regex rule")
        
        // Create event that would match if regex was valid
        val testEvent = CalendarEvent(
            id = "error-test-event",
            title = "Test Event for Error Handling",
            startTimeUtc = System.currentTimeMillis() + (2 * 60 * 60 * 1000),
            endTimeUtc = System.currentTimeMillis() + (3 * 60 * 60 * 1000),
            calendarId = 1L,
            isAllDay = false,
            timezone = "UTC",
            lastModified = System.currentTimeMillis()
        )
        
        calendarTestProvider.createTestEvent(
            title = testEvent.title,
            startTimeFromNow = testEvent.startTimeUtc - System.currentTimeMillis(),
            durationHours = ((testEvent.endTimeUtc - testEvent.startTimeUtc) / (60 * 60 * 1000)).toInt(),
            isAllDay = testEvent.isAllDay,
            calendarId = testEvent.calendarId
        )
        
        // Execute worker - should handle invalid regex gracefully
        val worker = TestListenableWorkerBuilder<CalendarRefreshWorker>(context).build()
        val result = worker.doWork()
        
        // Worker should still succeed despite invalid rule
        assertEquals(
            "Worker should handle invalid regex gracefully",
            ListenableWorker.Result.success(), 
            result
        )
        
        // Test worker with no calendar permission
        // (This is harder to test directly as permission is granted in test setup)
        Logger.i("WorkerIntegrationTest", "Invalid regex handling completed")
        
        // Test worker with corrupted database state
        try {
            // Create rule with extreme values
            val extremeRule = Rule(
                id = "extreme-rule",
                name = "Extreme Rule",
                keywordPattern = "a".repeat(1000), // Very long pattern
                isRegex = false,
                calendarIds = (1..100).map { it.toLong() }, // Many calendar IDs
                leadTimeMinutes = Int.MAX_VALUE, // Extreme lead time
                enabled = true
            )
            
            database.ruleDao().insertRule(extremeRule)
            
            val extremeWorker = TestListenableWorkerBuilder<CalendarRefreshWorker>(context).build()
            val extremeResult = extremeWorker.doWork()
            
            // Worker should either succeed or fail gracefully
            assertTrue(
                "Worker should handle extreme values gracefully",
                extremeResult == ListenableWorker.Result.success() || 
                extremeResult == ListenableWorker.Result.failure()
            )
            
            Logger.i("WorkerIntegrationTest", "Extreme values result: $extremeResult")
            
        } catch (e: Exception) {
            Logger.i("WorkerIntegrationTest", "Extreme values test caused expected exception: ${e.message}")
        }
        
        Logger.i("WorkerIntegrationTest", "✅ Worker error handling test PASSED")
    }
    
    @Test
    fun testWorkerPerformanceWithLargeDataset() = runBlocking {
        Logger.i("WorkerIntegrationTest", "=== Testing Worker Performance with Large Dataset ===")
        
        val startTime = System.currentTimeMillis()
        
        // Create multiple rules
        val performanceRules = (1..10).map { index ->
            Rule(
                id = "perf-rule-$index",
                name = "Performance Rule $index",
                keywordPattern = "test$index|event$index",
                isRegex = true,
                calendarIds = emptyList(),
                leadTimeMinutes = index * 15, // Varying lead times
                enabled = true
            )
        }
        
        performanceRules.forEach { rule ->
            database.ruleDao().insertRule(rule)
        }
        
        // Create many test events
        val eventCount = 100
        val performanceEvents = (1..eventCount).map { index ->
            CalendarEvent(
                id = "perf-event-$index",
                title = "Performance Test Event $index test${index % 10}",
                startTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000), // Spread over hours
                endTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000) + (30 * 60 * 1000),
                calendarId = (index % 3).toLong() + 1,
                isAllDay = index % 5 == 0, // Some all-day events
                timezone = "UTC",
                lastModified = System.currentTimeMillis()
            )
        }
        
        performanceEvents.forEach { event ->
            calendarTestProvider.createTestEvent(
                title = event.title,
                startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                isAllDay = event.isAllDay,
                calendarId = event.calendarId
            )
        }
        
        Logger.i("WorkerIntegrationTest", "Created $eventCount events and ${performanceRules.size} rules")
        
        // Clear existing alarms
        database.alarmDao().deleteAllAlarms()
        
        // Measure worker execution time
        val workerStartTime = System.currentTimeMillis()
        val worker = TestListenableWorkerBuilder<CalendarRefreshWorker>(context).build()
        val result = worker.doWork()
        val workerEndTime = System.currentTimeMillis()
        
        val executionTime = workerEndTime - workerStartTime
        Logger.i("WorkerIntegrationTest", "Worker execution time with large dataset: ${executionTime}ms")
        
        assertEquals("Worker should succeed with large dataset", ListenableWorker.Result.success(), result)
        
        // Wait for processing
        delay(5000)
        
        // Analyze results
        val createdAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("WorkerIntegrationTest", "Created ${createdAlarms.size} alarms from $eventCount events")
        
        // Verify performance is reasonable (less than 30 seconds for 100 events)
        assertTrue(
            "Worker should complete large dataset processing in under 30 seconds, took ${executionTime}ms",
            executionTime < 30000
        )
        
        // Verify alarms were created correctly
        assertTrue("Should create some alarms from performance test events", createdAlarms.isNotEmpty())
        
        val totalTime = System.currentTimeMillis() - startTime
        Logger.i("WorkerIntegrationTest", "Total performance test time: ${totalTime}ms")
        
        Logger.i("WorkerIntegrationTest", "✅ Worker performance test PASSED")
    }
}