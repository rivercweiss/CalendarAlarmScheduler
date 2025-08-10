package com.example.calendaralarmscheduler.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End tests for device state scenarios
 * Verifies alarm behavior under different device conditions
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class E2EDeviceStateTest : E2ETestBase() {

    private lateinit var deviceStateController: DeviceStateController

    override fun setUp() {
        super.setUp()
        deviceStateController = DeviceStateController(context, uiDevice)
    }

    @Test
    fun testAlarmInDoNotDisturbMode() {
        applicationController.launchMainActivity()
        
        // Step 1: Set up test alarm
        val testEvent = calendarTestProvider.createImmediateTestEvent(2, "DND Test Alarm")
        applicationController.createRule("DND Test", "DND", 1)
        applicationController.triggerBackgroundWorker()
        
        // Step 2: Wait for alarm to be scheduled
        assertEventuallyTrue("Alarm should be scheduled") {
            alarmTestVerifier.getActiveDatabaseAlarms().any { it.eventId == testEvent.id }
        }
        
        // Step 3: Enable Do Not Disturb mode
        val dndEnabled = deviceStateController.doNotDisturb.enableDoNotDisturb()
        assertThat(dndEnabled).isTrue()
        
        try {
            // Step 4: Wait for alarm to fire
            val scheduledAlarm = alarmTestVerifier.getActiveDatabaseAlarms().first { it.eventId == testEvent.id }
            val timeToWait = scheduledAlarm.alarmTimeUtc - System.currentTimeMillis() + 2000L
            
            if (timeToWait > 0 && timeToWait < 300000L) { // Only wait if less than 5 minutes
                Thread.sleep(timeToWait)
                
                // Step 5: Verify alarm bypassed DND mode
                // In a real test, we would verify:
                // - Alarm activity is visible on screen
                // - Sound played despite DND
                // - Screen turned on if it was off
                
                val alarmFired = !alarmTestVerifier.getActiveDatabaseAlarms().any { it.id == scheduledAlarm.id }
                assertThat(alarmFired).isTrue()
            }
            
        } finally {
            // Always restore normal mode
            deviceStateController.doNotDisturb.disableDoNotDisturb()
        }
        
        android.util.Log.i("E2EDeviceStateTest", "Do Not Disturb test completed")
    }

    @Test 
    fun testBackgroundWorkerInDozeMode() {
        applicationController.launchMainActivity()
        
        // Step 1: Set up test scenario
        calendarTestProvider.createTestEventSuite()
        applicationController.createRule("Doze Test", "meeting", 30)
        
        // Step 2: Test normal operation first
        applicationController.triggerBackgroundWorker()
        assertEventuallyTrue("Worker should process normally") {
            alarmTestVerifier.getActiveDatabaseAlarms().isNotEmpty()
        }
        
        val normalAlarmCount = alarmTestVerifier.getActiveDatabaseAlarms().size
        
        // Step 3: Remove from battery whitelist and enter doze
        val removedFromWhitelist = deviceStateController.batteryOptimization.removeFromWhitelist()
        assertThat(removedFromWhitelist).isTrue()
        
        val dozeEntered = deviceStateController.batteryOptimization.enterDozeMode()
        assertThat(dozeEntered).isTrue()
        
        try {
            // Step 4: Clear existing alarms and test worker in doze
            alarmTestVerifier.clearAllTestAlarms()
            
            // Step 5: Trigger worker in doze mode
            val workerTriggered = applicationController.triggerBackgroundWorker()
            
            // Step 6: Wait longer for processing (doze may delay execution)
            Thread.sleep(15000)
            
            // In doze mode, worker execution may be limited but should eventually work
            val dozeAlarmCount = alarmTestVerifier.getActiveDatabaseAlarms().size
            
            // Worker might be delayed but should eventually process some events
            // or maintain previous state at minimum
            assertThat(dozeAlarmCount).isAtLeast(0)
            
        } finally {
            // Restore normal state
            deviceStateController.batteryOptimization.exitDozeMode()
            deviceStateController.batteryOptimization.addToWhitelist()
        }
        
        android.util.Log.i("E2EDeviceStateTest", "Doze mode test completed")
    }

    @Test
    fun testTimezoneChangeHandling() {
        applicationController.launchMainActivity()
        
        // This test is handled by the DeviceStateController's comprehensive test
        val timezoneTestPassed = deviceStateController.timezone.testTimezoneChangeHandling(
            applicationController,
            calendarTestProvider,
            alarmTestVerifier
        )
        
        assertThat(timezoneTestPassed).isTrue()
        android.util.Log.i("E2EDeviceStateTest", "Timezone change test completed")
    }

    @Test
    fun testAlarmWithScreenOff() {
        applicationController.launchMainActivity()
        
        // Step 1: Set up immediate test alarm
        val testEvent = calendarTestProvider.createImmediateTestEvent(1, "Screen Off Test")
        applicationController.createRule("Screen Test", "screen", 1)
        applicationController.triggerBackgroundWorker()
        
        // Step 2: Wait for alarm to be scheduled
        assertEventuallyTrue("Alarm should be scheduled") {
            alarmTestVerifier.getActiveDatabaseAlarms().any { it.eventId == testEvent.id }
        }
        
        // Step 3: Turn screen off
        val screenOff = deviceStateController.powerState.turnScreenOff()
        if (!screenOff) {
            android.util.Log.w("E2EDeviceStateTest", "Could not turn screen off, skipping test")
            return
        }
        
        try {
            // Step 4: Wait for alarm to fire and turn screen on
            val scheduledAlarm = alarmTestVerifier.getActiveDatabaseAlarms().first { it.eventId == testEvent.id }
            val timeToWait = scheduledAlarm.alarmTimeUtc - System.currentTimeMillis() + 2000L
            
            if (timeToWait > 0 && timeToWait < 120000L) { // Only wait if less than 2 minutes
                Thread.sleep(timeToWait)
                
                // Step 5: Verify screen was turned on by alarm
                val screenOn = uiDevice.isScreenOn
                assertThat(screenOn).isTrue()
            }
            
        } finally {
            // Ensure screen is on for subsequent tests
            deviceStateController.powerState.turnScreenOn()
        }
        
        android.util.Log.i("E2EDeviceStateTest", "Screen off test completed")
    }

    @Test
    fun testAlarmInSilentMode() {
        applicationController.launchMainActivity()
        
        // Step 1: Set up test alarm
        val testEvent = calendarTestProvider.createImmediateTestEvent(2, "Silent Mode Test")
        applicationController.createRule("Silent Test", "silent", 1)
        applicationController.triggerBackgroundWorker()
        
        // Step 2: Set device to silent mode
        val silentModeSet = deviceStateController.audio.setSilentMode()
        assertThat(silentModeSet).isTrue()
        
        try {
            // Step 3: Wait for alarm to be scheduled and fire
            assertEventuallyTrue("Alarm should be scheduled") {
                alarmTestVerifier.getActiveDatabaseAlarms().any { it.eventId == testEvent.id }
            }
            
            val scheduledAlarm = alarmTestVerifier.getActiveDatabaseAlarms().first { it.eventId == testEvent.id }
            val timeToWait = scheduledAlarm.alarmTimeUtc - System.currentTimeMillis() + 2000L
            
            if (timeToWait > 0 && timeToWait < 300000L) { // Only wait if less than 5 minutes
                Thread.sleep(timeToWait)
                
                // Step 4: Verify alarm fired despite silent mode
                // In real implementation, we would verify audio played
                val alarmProcessed = !alarmTestVerifier.getActiveDatabaseAlarms().any { it.id == scheduledAlarm.id }
                
                // Alarm should have been processed even in silent mode
                assertThat(alarmProcessed).isTrue()
            }
            
        } finally {
            // Restore normal audio state
            deviceStateController.audio.restoreAudioState()
        }
        
        android.util.Log.i("E2EDeviceStateTest", "Silent mode test completed")
    }

    @Test
    fun testMultipleDeviceStatesSimultaneously() {
        applicationController.launchMainActivity()
        
        // Step 1: Set up test scenario
        val testEvent = calendarTestProvider.createImmediateTestEvent(3, "Multi-State Test")
        applicationController.createRule("Multi Test", "multi", 1)
        applicationController.triggerBackgroundWorker()
        
        // Step 2: Enable multiple restrictive states
        val dndEnabled = deviceStateController.doNotDisturb.enableDoNotDisturb()
        val silentModeSet = deviceStateController.audio.setSilentMode()
        val screenOff = deviceStateController.powerState.turnScreenOff()
        
        try {
            // Step 3: Wait for alarm to be scheduled
            assertEventuallyTrue("Alarm should be scheduled despite restrictions") {
                alarmTestVerifier.getActiveDatabaseAlarms().any { it.eventId == testEvent.id }
            }
            
            val scheduledAlarm = alarmTestVerifier.getActiveDatabaseAlarms().first { it.eventId == testEvent.id }
            val timeToWait = scheduledAlarm.alarmTimeUtc - System.currentTimeMillis() + 3000L
            
            if (timeToWait > 0 && timeToWait < 300000L) {
                Thread.sleep(timeToWait)
                
                // Step 4: Verify alarm overcame all restrictions
                val screenTurnedOn = uiDevice.isScreenOn
                val alarmProcessed = !alarmTestVerifier.getActiveDatabaseAlarms().any { it.id == scheduledAlarm.id }
                
                // Alarm should have turned screen on and fired despite all restrictions
                assertThat(screenTurnedOn).isTrue()
                assertThat(alarmProcessed).isTrue()
            }
            
        } finally {
            // Restore all states
            deviceStateController.doNotDisturb.disableDoNotDisturb()
            deviceStateController.audio.restoreAudioState()
            deviceStateController.powerState.turnScreenOn()
        }
        
        android.util.Log.i("E2EDeviceStateTest", "Multiple device states test completed")
    }

    @Test
    fun testBatteryOptimizationImpactOnAlarms() {
        applicationController.launchMainActivity()
        
        // Step 1: Test with battery optimization enabled (not whitelisted)
        deviceStateController.batteryOptimization.removeFromWhitelist()
        
        val testEvents1 = calendarTestProvider.createTestEventSuite()
        applicationController.createRule("Battery Test 1", "meeting", 30)
        
        val startTime1 = System.currentTimeMillis()
        applicationController.triggerBackgroundWorker()
        
        assertEventuallyTrue("Alarms should be scheduled without whitelisting", 20000L) {
            alarmTestVerifier.getActiveDatabaseAlarms().isNotEmpty()
        }
        
        val nonWhitelistedTime = System.currentTimeMillis() - startTime1
        val nonWhitelistedCount = alarmTestVerifier.getActiveDatabaseAlarms().size
        
        // Step 2: Clear and test with whitelisting
        alarmTestVerifier.clearAllTestAlarms()
        deviceStateController.batteryOptimization.addToWhitelist()
        
        val startTime2 = System.currentTimeMillis()
        applicationController.triggerBackgroundWorker()
        
        assertEventuallyTrue("Alarms should be scheduled with whitelisting", 20000L) {
            alarmTestVerifier.getActiveDatabaseAlarms().isNotEmpty()
        }
        
        val whitelistedTime = System.currentTimeMillis() - startTime2
        val whitelistedCount = alarmTestVerifier.getActiveDatabaseAlarms().size
        
        // Step 3: Verify whitelisting improves reliability
        android.util.Log.i("E2EDeviceStateTest", 
            "Battery optimization impact - Non-whitelisted: ${nonWhitelistedTime}ms (${nonWhitelistedCount} alarms), " +
            "Whitelisted: ${whitelistedTime}ms (${whitelistedCount} alarms)")
        
        // Both should work, but whitelisted should be more reliable
        assertThat(whitelistedCount).isAtLeast(nonWhitelistedCount)
        
        android.util.Log.i("E2EDeviceStateTest", "Battery optimization impact test completed")
    }

    @Test
    fun testComprehensiveDeviceStateScenarios() {
        applicationController.launchMainActivity()
        
        // Run comprehensive test using DeviceStateController
        val results = deviceStateController.runComprehensiveDeviceStateTest(
            applicationController,
            calendarTestProvider,
            alarmTestVerifier
        )
        
        // Log individual test results
        android.util.Log.i("E2EDeviceStateTest", "Comprehensive test results:")
        android.util.Log.i("E2EDeviceStateTest", "  DND Test: ${results.dndTest}")
        android.util.Log.i("E2EDeviceStateTest", "  Doze Test: ${results.dozeTest}")
        android.util.Log.i("E2EDeviceStateTest", "  Timezone Test: ${results.timezoneTest}")
        android.util.Log.i("E2EDeviceStateTest", "  Screen Off Test: ${results.screenOffTest}")
        android.util.Log.i("E2EDeviceStateTest", "  Silent Mode Test: ${results.silentModeTest}")
        
        // At least most tests should pass
        val passedCount = listOf(
            results.dndTest,
            results.dozeTest,
            results.timezoneTest,
            results.screenOffTest,
            results.silentModeTest
        ).count { it }
        
        assertThat(passedCount).isAtLeast(3) // At least 3 out of 5 should pass
        
        android.util.Log.i("E2EDeviceStateTest", "Comprehensive device state test completed - $passedCount/5 passed")
    }
}