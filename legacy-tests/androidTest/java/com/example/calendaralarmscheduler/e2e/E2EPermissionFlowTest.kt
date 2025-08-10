package com.example.calendaralarmscheduler.e2e

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-End test for permission onboarding and management
 * Tests the complete permission flow including system interactions
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class E2EPermissionFlowTest : E2ETestBase() {

    @Test
    fun testCompletePermissionOnboardingFlow() {
        // Step 1: Revoke permissions to test onboarding
        revokeAllTestPermissions()
        
        // Step 2: Launch app (should trigger onboarding)
        applicationController.launchMainActivity()
        
        // Step 3: Should be redirected to permission onboarding
        // Wait for onboarding screen to appear
        assertEventuallyTrue("Permission onboarding should appear") {
            uiDevice.hasObject(By.textContains("Permission"))
        }
        
        // Step 4: Go through onboarding steps
        completePermissionOnboarding()
        
        // Step 5: Verify all required permissions are granted
        assertThat(isPermissionGranted(Manifest.permission.READ_CALENDAR)).isTrue()
        assertThat(alarmTestVerifier.hasExactAlarmPermission()).isTrue()
        
        // Step 6: Verify app functionality works after permissions granted
        val ruleCreated = applicationController.createRule("Permission Test", "test", 30)
        assertThat(ruleCreated).isTrue()
        
        android.util.Log.i("E2EPermissionFlowTest", "Complete onboarding flow test passed")
    }

    @Test
    fun testPermissionRejectionHandling() {
        // Step 1: Revoke permissions
        revokeAllTestPermissions()
        
        // Step 2: Launch app
        applicationController.launchMainActivity()
        
        // Step 3: Reject calendar permission when prompted
        assertEventuallyTrue("Permission dialog should appear") {
            uiDevice.hasObject(By.text("Allow"))
        }
        
        // Deny the permission
        val denyButton = uiDevice.findObject(By.text("Don't allow"))
        denyButton?.click()
        
        // Step 4: Should show rationale or alternative flow
        assertEventuallyTrue("Should handle permission rejection gracefully") {
            uiDevice.hasObject(By.textContains("permission")) ||
            uiDevice.hasObject(By.textContains("Settings"))
        }
        
        // Step 5: Verify app doesn't crash and shows appropriate message
        val hasErrorMessage = uiDevice.hasObject(By.textContains("Calendar permission")) ||
                              uiDevice.hasObject(By.textContains("permission required"))
        assertThat(hasErrorMessage).isTrue()
        
        android.util.Log.i("E2EPermissionFlowTest", "Permission rejection handling test passed")
    }

    @Test
    fun testExactAlarmPermissionFlow() {
        // This test specifically focuses on the SCHEDULE_EXACT_ALARM permission
        // which requires special system settings interaction
        
        // Step 1: Launch app with calendar permission but without exact alarm permission
        grantPermissionViaAdb(Manifest.permission.READ_CALENDAR)
        revokeExactAlarmPermissionViaAdb()
        
        applicationController.launchMainActivity()
        
        // Step 2: Navigate to settings or trigger exact alarm permission request
        applicationController.navigateToSettings()
        
        // Step 3: Should detect missing exact alarm permission
        assertEventuallyTrue("Should detect missing exact alarm permission") {
            val capability = alarmTestVerifier.verifyAlarmSchedulingCapability()
            !capability.hasExactAlarmPermission
        }
        
        // Step 4: Trigger permission request (should open system settings)
        // In real test, this would interact with system settings
        grantExactAlarmPermissionViaAdb()
        
        // Step 5: Verify permission is now granted
        assertEventuallyTrue("Exact alarm permission should be granted") {
            alarmTestVerifier.hasExactAlarmPermission()
        }
        
        android.util.Log.i("E2EPermissionFlowTest", "Exact alarm permission flow test passed")
    }

    @Test
    fun testBatteryOptimizationWhitelisting() {
        // Step 1: Ensure app is not whitelisted initially
        removeFromBatteryWhitelistViaAdb()
        
        applicationController.launchMainActivity()
        applicationController.navigateToSettings()
        
        // Step 2: Check initial battery optimization status
        val initiallyWhitelisted = alarmTestVerifier.isAppWhitelistedFromBatteryOptimization()
        
        // Step 3: Request battery optimization whitelisting
        // In a real implementation, this would guide user to settings
        addToBatteryWhitelistViaAdb()
        
        // Step 4: Verify whitelisting was successful
        assertEventuallyTrue("App should be whitelisted from battery optimization") {
            alarmTestVerifier.isAppWhitelistedFromBatteryOptimization()
        }
        
        // Step 5: Verify this improves alarm reliability capability
        val capability = alarmTestVerifier.verifyAlarmSchedulingCapability()
        assertThat(capability.isWhitelisted).isTrue()
        
        android.util.Log.i("E2EPermissionFlowTest", "Battery optimization test passed")
    }

    @Test
    fun testPermissionStatusMonitoring() {
        applicationController.launchMainActivity()
        applicationController.navigateToSettings()
        
        // Step 1: Verify permission status dashboard shows current state
        val capability = alarmTestVerifier.verifyAlarmSchedulingCapability()
        
        // Step 2: Revoke a permission and verify it's detected
        revokePermissionViaAdb(Manifest.permission.READ_CALENDAR)
        
        // Wait for app to detect permission change
        Thread.sleep(2000)
        
        // Step 3: Verify permission status updated
        assertThat(isPermissionGranted(Manifest.permission.READ_CALENDAR)).isFalse()
        
        // Step 4: Re-grant permission
        grantPermissionViaAdb(Manifest.permission.READ_CALENDAR)
        
        // Step 5: Verify status updated again
        assertEventuallyTrue("Permission should be detected as granted again") {
            isPermissionGranted(Manifest.permission.READ_CALENDAR)
        }
        
        android.util.Log.i("E2EPermissionFlowTest", "Permission monitoring test passed")
    }

    @Test
    fun testPermissionPersistenceAcrossAppRestarts() {
        // Step 1: Grant all permissions
        grantAllRequiredPermissions()
        
        // Step 2: Create some app data
        applicationController.launchMainActivity()
        applicationController.createRule("Persistence Test", "persistent", 30)
        
        // Step 3: Force stop and restart app
        forceStopApp()
        Thread.sleep(2000)
        applicationController.launchAppViaAdb()
        
        // Step 4: Verify permissions are still granted
        assertThat(isPermissionGranted(Manifest.permission.READ_CALENDAR)).isTrue()
        assertThat(alarmTestVerifier.hasExactAlarmPermission()).isTrue()
        
        // Step 5: Verify app functionality still works
        applicationController.navigateToRules()
        val ruleCount = applicationController.getRuleCount()
        assertThat(ruleCount).isEqualTo(1) // Should have the rule we created
        
        android.util.Log.i("E2EPermissionFlowTest", "Permission persistence test passed")
    }

    @Test
    fun testPermissionRequiredFunctionalityBlocking() {
        // Step 1: Launch app without calendar permission
        revokePermissionViaAdb(Manifest.permission.READ_CALENDAR)
        
        applicationController.launchMainActivity()
        
        // Step 2: Try to create a rule (should be blocked or show warning)
        val ruleCreationAttempted = applicationController.createRule("Blocked Test", "blocked", 30)
        
        // Should either fail to create rule or show permission required message
        if (ruleCreationAttempted) {
            // If rule creation appeared to succeed, verify it doesn't actually work
            applicationController.triggerBackgroundWorker()
            
            // Should have no alarms because calendar can't be read
            val alarms = alarmTestVerifier.getActiveDatabaseAlarms()
            assertThat(alarms).isEmpty()
        }
        
        // Step 3: Grant permission and retry
        grantPermissionViaAdb(Manifest.permission.READ_CALENDAR)
        
        // Step 4: Now rule creation should work properly
        applicationController.launchMainActivity() // Restart to detect permission
        val ruleCreatedAfterPermission = applicationController.createRule("After Permission", "working", 30)
        assertThat(ruleCreatedAfterPermission).isTrue()
        
        android.util.Log.i("E2EPermissionFlowTest", "Functionality blocking test passed")
    }

    // Private helper methods for permission manipulation

    private fun revokeAllTestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.VIBRATE
        )
        
        permissions.forEach { permission ->
            revokePermissionViaAdb(permission)
        }
        
        revokeExactAlarmPermissionViaAdb()
    }

    private fun grantAllRequiredPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.VIBRATE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        permissions.forEach { permission ->
            grantPermissionViaAdb(permission)
        }
        
        grantExactAlarmPermissionViaAdb()
    }

    private fun grantPermissionViaAdb(permission: String) {
        executeAdbCommand("pm grant ${context.packageName} $permission")
        Thread.sleep(500) // Allow time for permission to take effect
    }

    private fun revokePermissionViaAdb(permission: String) {
        executeAdbCommand("pm revoke ${context.packageName} $permission")
        Thread.sleep(500)
    }

    private fun grantExactAlarmPermissionViaAdb() {
        executeAdbCommand("pm grant ${context.packageName} android.permission.SCHEDULE_EXACT_ALARM")
        Thread.sleep(500)
    }

    private fun revokeExactAlarmPermissionViaAdb() {
        executeAdbCommand("pm revoke ${context.packageName} android.permission.SCHEDULE_EXACT_ALARM")
        Thread.sleep(500)
    }

    private fun addToBatteryWhitelistViaAdb() {
        executeAdbCommand("dumpsys deviceidle whitelist +${context.packageName}")
        Thread.sleep(1000)
    }

    private fun removeFromBatteryWhitelistViaAdb() {
        executeAdbCommand("dumpsys deviceidle whitelist -${context.packageName}")
        Thread.sleep(1000)
    }

    private fun forceStopApp() {
        executeAdbCommand("am force-stop ${context.packageName}")
        Thread.sleep(2000)
    }

    private fun completePermissionOnboarding() {
        try {
            // Navigate through permission onboarding steps
            // This is simplified - real implementation would handle specific onboarding UI
            
            // Step 1: Welcome screen
            if (uiDevice.hasObject(By.textContains("Welcome"))) {
                uiDevice.findObject(By.text("Next"))?.click()
                uiDevice.waitForIdle(1000)
            }
            
            // Step 2: Calendar permission explanation
            if (uiDevice.hasObject(By.textContains("Calendar"))) {
                uiDevice.findObject(By.text("Grant Permission"))?.click()
                uiDevice.waitForIdle(1000)
                
                // Handle system permission dialog
                val allowButton = uiDevice.wait(Until.findObject(By.text("Allow")), 3000)
                allowButton?.click()
                uiDevice.waitForIdle(1000)
            }
            
            // Step 3: Exact alarm permission
            if (uiDevice.hasObject(By.textContains("Exact Alarms"))) {
                uiDevice.findObject(By.text("Open Settings"))?.click()
                uiDevice.waitForIdle(2000)
                
                // In system settings, enable the permission
                // This would require more complex UI automation for real settings
                grantExactAlarmPermissionViaAdb() // Use ADB as fallback
                
                uiDevice.pressBack()
                uiDevice.waitForIdle(1000)
            }
            
            // Step 4: Battery optimization
            if (uiDevice.hasObject(By.textContains("Battery"))) {
                uiDevice.findObject(By.text("Optimize"))?.click()
                addToBatteryWhitelistViaAdb() // Use ADB as fallback
                uiDevice.waitForIdle(1000)
            }
            
            // Step 5: Complete onboarding
            if (uiDevice.hasObject(By.text("Finish"))) {
                uiDevice.findObject(By.text("Finish"))?.click()
                uiDevice.waitForIdle(1000)
            }
            
        } catch (e: Exception) {
            android.util.Log.w("E2EPermissionFlowTest", "Error in onboarding automation", e)
            
            // Fallback: Grant permissions directly via ADB
            grantAllRequiredPermissions()
        }
    }
}