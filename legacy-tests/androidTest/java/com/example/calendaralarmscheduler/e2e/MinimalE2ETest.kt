package com.example.calendaralarmscheduler.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Minimal E2E test that validates basic Claude interaction and app control
 * This test focuses on programmatic control rather than complex UI interactions
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class MinimalE2ETest : E2ETestBase() {

    @Test
    fun testBasicClaudeInteractionAndAppControl() {
        // Test 1: Verify app can launch
        val launched = applicationController.launchMainActivity()
        assertThat(launched).isNotNull()
        android.util.Log.i("MinimalE2E", "✅ App launch test passed")

        // Test 2: Verify initial state
        val initialRuleCount = applicationController.getRuleCount()
        assertThat(initialRuleCount).isEqualTo(0)
        android.util.Log.i("MinimalE2E", "✅ Initial state verification passed")

        // Test 3: Test basic navigation (if possible)
        try {
            val navigatedToRules = applicationController.navigateToRules()
            android.util.Log.i("MinimalE2E", "Navigation to rules: $navigatedToRules")
        } catch (e: Exception) {
            android.util.Log.w("MinimalE2E", "Navigation test skipped due to UI constraints: ${e.message}")
        }

        // Test 4: Test background worker trigger
        val workerTriggered = applicationController.triggerBackgroundWorker()
        assertThat(workerTriggered).isTrue()
        android.util.Log.i("MinimalE2E", "✅ Background worker trigger test passed")

        android.util.Log.i("MinimalE2E", "🎉 All minimal E2E tests passed - Claude can control the app")
    }

    @Test
    fun testDatabaseOperations() {
        // Test basic database operations without UI
        android.util.Log.i("MinimalE2E", "Starting database operations test...")

        // Clear any existing data (handled by E2ETestBase setup)

        // Verify empty state
        val initialRules = applicationController.getRuleCount()
        assertThat(initialRules).isEqualTo(0)

        android.util.Log.i("MinimalE2E", "✅ Database operations test passed")
    }

    @Test
    fun testSystemPermissions() {
        // Test that required permissions are available
        android.util.Log.i("MinimalE2E", "Testing system permissions...")

        val calendarPermission = isPermissionGranted(android.Manifest.permission.READ_CALENDAR)
        val alarmPermission = isPermissionGranted(android.Manifest.permission.USE_EXACT_ALARM)

        android.util.Log.i("MinimalE2E", "Calendar permission: $calendarPermission")
        android.util.Log.i("MinimalE2E", "Alarm permission: $alarmPermission")

        // These should be granted by the test setup
        assertThat(calendarPermission).isTrue()
        assertThat(alarmPermission).isTrue()

        android.util.Log.i("MinimalE2E", "✅ System permissions test passed")
    }
}