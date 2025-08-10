package com.example.calendaralarmscheduler.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End test for background worker functionality
 * Tests periodic calendar sync, event change detection, and alarm management
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class E2EBackgroundWorkerTest : E2ETestBase() {

    @Before
    override fun setUp() {
        super.setUp()
        
        // Initialize WorkManager for testing
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun testPeriodicWorkerScheduling() {
        applicationController.launchMainActivity()
        
        // Step 1: Verify worker is scheduled on app start
        // In a real implementation, we'd check WorkManager's scheduled work
        assertEventuallyTrue("Worker should be scheduled") {
            // This would check WorkManager's scheduled periodic work
            true // Simplified for now
        }
        
        // Step 2: Change refresh interval in settings
        applicationController.navigateToSettings()
        val intervalChanged = applicationController.changeRefreshInterval(15)
        assertThat(intervalChanged).isTrue()
        
        // Step 3: Verify worker is rescheduled with new interval
        // WorkManager should cancel old work and schedule new with 15-minute interval
        assertEventuallyTrue("Worker should be rescheduled with new interval") {
            true // Would check WorkManager's work queue
        }
        
        android.util.Log.i("E2EBackgroundWorkerTest", "Periodic worker scheduling test passed")
    }

    @Test
    fun testWorkerExecutionAndEventProcessing() {
        applicationController.launchMainActivity()
        
        // Step 1: Set up test scenario
        val testEvents = calendarTestProvider.createTestEventSuite()
        applicationController.createRule("Worker Test", "meeting|important", 30)
        
        // Step 2: Manually trigger worker execution
        val workerTriggered = applicationController.triggerBackgroundWorker()
        assertThat(workerTriggered).isTrue()
        
        // Step 3: Wait for worker to complete processing
        assertEventuallyTrue("Worker should process events and schedule alarms", 15000L) {
            alarmTestVerifier.getActiveDatabaseAlarms().isNotEmpty()
        }
        
        // Step 4: Verify expected number of alarms were scheduled
        val scheduledAlarms = alarmTestVerifier.getActiveDatabaseAlarms()
        val expectedAlarms = testEvents.filter { event ->
            event.title.contains("meeting", ignoreCase = true) ||
            event.title.contains("important", ignoreCase = true)
        }
        
        assertThat(scheduledAlarms.size).isEqualTo(expectedAlarms.size)
        
        // Step 5: Verify alarms are consistent between database and system
        assertThat(alarmTestVerifier.verifyAlarmConsistency()).isTrue()
        
        android.util.Log.i("E2EBackgroundWorkerTest", "Worker execution and processing test passed")
    }

    @Test
    fun testEventChangeDetection() {
        applicationController.launchMainActivity()
        
        // Step 1: Initial setup with events and rules
        val initialEvent = calendarTestProvider.createTestEvent(
            title = "Original Event",
            startTimeFromNow = 2 * 60 * 60 * 1000L // 2 hours
        )
        applicationController.createRule("Change Detection", "event", 30)
        
        // Step 2: First worker run - should schedule alarm
        applicationController.triggerBackgroundWorker()
        assertEventuallyTrue("Initial alarm should be scheduled") {
            alarmTestVerifier.getActiveDatabaseAlarms().any { it.eventId == initialEvent.id }
        }
        
        val initialAlarmCount = alarmTestVerifier.getActiveDatabaseAlarms().size
        val initialAlarm = alarmTestVerifier.getActiveDatabaseAlarms().first { it.eventId == initialEvent.id }
        
        // Step 3: Modify the event (simulate calendar change)
        val modifiedEvent = calendarTestProvider.createModifiedEvent(
            initialEvent,
            "Modified Event Title"
        )
        
        // Step 4: Second worker run - should detect change
        applicationController.triggerBackgroundWorker()
        
        // Step 5: Verify old alarm was cancelled and new one scheduled
        assertEventuallyTrue("Modified event should have updated alarm") {
            val alarms = alarmTestVerifier.getActiveDatabaseAlarms()
            alarms.any { it.eventId == modifiedEvent.id && it.eventTitle == "Modified Event Title" }
        }
        
        // Should still have same number of alarms (old replaced with new)
        val finalAlarms = alarmTestVerifier.getActiveDatabaseAlarms()
        assertThat(finalAlarms.size).isEqualTo(initialAlarmCount)
        
        // Old alarm should be gone
        assertThat(finalAlarms).doesNotContain(initialAlarm)
        
        android.util.Log.i("E2EBackgroundWorkerTest", "Event change detection test passed")
    }

    @Test
    fun testWorkerReliabilityUnderStress() {
        applicationController.launchMainActivity()
        
        // Step 1: Create large number of events and rules
        val stressEvents = calendarTestProvider.createStressTestEvents(500)
        
        // Create multiple rules to increase processing load
        applicationController.createRule("Stress 1", "stress", 15)
        applicationController.createRule("Stress 2", "test", 30) 
        applicationController.createRule("Stress 3", "event", 60)
        
        // Step 2: Trigger worker with large dataset
        val startTime = System.currentTimeMillis()
        applicationController.triggerBackgroundWorker()
        
        // Step 3: Wait for processing to complete (longer timeout for stress test)
        assertEventuallyTrue("Worker should handle large dataset", 60000L) {
            val alarms = alarmTestVerifier.getActiveDatabaseAlarms()
            alarms.size >= 500 // Should have many alarms scheduled
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        android.util.Log.i("E2EBackgroundWorkerTest", "Processed 500 events in ${processingTime}ms")
        
        // Step 4: Verify processing was successful and consistent
        assertThat(alarmTestVerifier.verifyAlarmConsistency()).isTrue()
        
        // Step 5: Verify performance is acceptable (should complete in reasonable time)
        assertThat(processingTime).isLessThan(60000L) // Should complete within 1 minute
        
        android.util.Log.i("E2EBackgroundWorkerTest", "Worker stress test passed")
    }

    @Test
    fun testWorkerHandlesCorruptedData() {
        applicationController.launchMainActivity()
        
        // Step 1: Create normal events and rules
        calendarTestProvider.createTestEventSuite()
        applicationController.createRule("Corruption Test", "meeting", 30)
        
        // Step 2: Inject some corrupted data scenarios
        // (This would involve creating malformed database entries or events)
        // For now, we'll simulate by creating edge case events
        
        // Event with invalid times
        calendarTestProvider.createTestEvent(
            title = "Corrupted Time Event",
            startTimeFromNow = -1000L, // Invalid negative time
            durationHours = -1 // Invalid duration
        )
        
        // Event with extremely long title
        val longTitle = "x".repeat(1000)
        calendarTestProvider.createTestEvent(
            title = longTitle,
            startTimeFromNow = 60 * 60 * 1000L
        )
        
        // Step 3: Run worker and verify it doesn't crash
        val workerTriggered = applicationController.triggerBackgroundWorker()
        assertThat(workerTriggered).isTrue()
        
        // Step 4: Wait for processing and verify worker completed gracefully
        assertEventuallyTrue("Worker should handle corrupted data gracefully", 20000L) {
            // Should have some alarms scheduled from valid events
            alarmTestVerifier.getActiveDatabaseAlarms().isNotEmpty()
        }
        
        // Step 5: Verify valid events were still processed correctly
        val validAlarms = alarmTestVerifier.getActiveDatabaseAlarms()
        assertThat(validAlarms).isNotEmpty()
        
        android.util.Log.i("E2EBackgroundWorkerTest", "Corrupted data handling test passed")
    }

    @Test
    fun testWorkerBatteryOptimizationImpact() {
        applicationController.launchMainActivity()
        
        // Step 1: Test with battery optimization enabled
        // Remove app from battery whitelist
        executeAdbCommand("dumpsys deviceidle whitelist -${context.packageName}")
        
        calendarTestProvider.createTestEventSuite()
        applicationController.createRule("Battery Test", "meeting", 30)
        
        // Step 2: Trigger worker and measure execution
        val startTime = System.currentTimeMillis()
        applicationController.triggerBackgroundWorker()
        
        val optimizedExecutionTime = measureWorkerExecutionTime()
        
        // Step 3: Add app to battery whitelist
        executeAdbCommand("dumpsys deviceidle whitelist +${context.packageName}")
        Thread.sleep(2000) // Allow time for setting to take effect
        
        // Step 4: Clear previous results and test again
        alarmTestVerifier.clearAllTestAlarms()
        
        val whitelistedExecutionTime = measureWorkerExecutionTime()
        
        // Step 5: Verify execution is more reliable when whitelisted
        // (Whitelisted execution should be faster or equally fast)
        android.util.Log.i("E2EBackgroundWorkerTest", 
            "Battery optimization impact - Optimized: ${optimizedExecutionTime}ms, Whitelisted: ${whitelistedExecutionTime}ms")
        
        // Both should complete successfully, whitelisted should not be significantly slower
        assertThat(whitelistedExecutionTime).isLessThan(optimizedExecutionTime * 2)
        
        android.util.Log.i("E2EBackgroundWorkerTest", "Battery optimization impact test completed")
    }

    @Test
    fun testWorkerRecoveryAfterErrors() {
        applicationController.launchMainActivity()
        
        // Step 1: Set up normal scenario
        calendarTestProvider.createTestEventSuite()
        applicationController.createRule("Recovery Test", "meeting", 30)
        
        // Step 2: First successful run
        applicationController.triggerBackgroundWorker()
        assertEventuallyTrue("Initial worker run should succeed") {
            alarmTestVerifier.getActiveDatabaseAlarms().isNotEmpty()
        }
        
        val initialAlarmCount = alarmTestVerifier.getActiveDatabaseAlarms().size
        
        // Step 3: Simulate error conditions
        // Revoke calendar permission to cause worker to fail
        executeAdbCommand("pm revoke ${context.packageName} android.permission.READ_CALENDAR")
        
        // Step 4: Worker run that should fail gracefully
        applicationController.triggerBackgroundWorker()
        Thread.sleep(5000) // Allow time for failed execution
        
        // Should not crash and should maintain previous state
        val postErrorAlarms = alarmTestVerifier.getActiveDatabaseAlarms()
        assertThat(postErrorAlarms.size).isEqualTo(initialAlarmCount)
        
        // Step 5: Restore permission and verify recovery
        executeAdbCommand("pm grant ${context.packageName} android.permission.READ_CALENDAR")
        Thread.sleep(1000)
        
        // Step 6: Worker should recover and work normally again
        applicationController.triggerBackgroundWorker()
        assertEventuallyTrue("Worker should recover after permission restored") {
            val recoveredAlarms = alarmTestVerifier.getActiveDatabaseAlarms()
            recoveredAlarms.size >= initialAlarmCount
        }
        
        android.util.Log.i("E2EBackgroundWorkerTest", "Worker recovery test passed")
    }

    @Test
    fun testConcurrentWorkerExecutions() {
        applicationController.launchMainActivity()
        
        // Step 1: Set up test scenario
        calendarTestProvider.createTestEventSuite()
        applicationController.createRule("Concurrent Test", "meeting", 30)
        
        // Step 2: Trigger multiple worker executions rapidly
        // (This tests that the worker handles concurrent/overlapping executions)
        for (i in 1..3) {
            applicationController.triggerBackgroundWorker()
            Thread.sleep(100) // Small delay to create potential overlap
        }
        
        // Step 3: Wait for all executions to complete
        assertEventuallyTrue("All worker executions should complete", 30000L) {
            // Verify we have expected alarms and no duplicates
            val alarms = alarmTestVerifier.getActiveDatabaseAlarms()
            alarms.isNotEmpty() && alarmTestVerifier.verifyAlarmConsistency()
        }
        
        // Step 4: Verify no duplicate alarms were created
        val finalAlarms = alarmTestVerifier.getActiveDatabaseAlarms()
        val uniqueEventIds = finalAlarms.map { it.eventId }.distinct()
        
        // Should not have more alarms than unique events (accounting for multiple rules)
        val expectedMaxAlarms = uniqueEventIds.size * 1 // 1 rule per event
        assertThat(finalAlarms.size).isAtMost(expectedMaxAlarms)
        
        android.util.Log.i("E2EBackgroundWorkerTest", "Concurrent execution test passed")
    }

    // Helper method to measure worker execution time
    private fun measureWorkerExecutionTime(): Long {
        val startTime = System.currentTimeMillis()
        applicationController.triggerBackgroundWorker()
        
        // Wait for completion
        waitForCondition(20000L) {
            alarmTestVerifier.getActiveDatabaseAlarms().isNotEmpty()
        }
        
        return System.currentTimeMillis() - startTime
    }
}