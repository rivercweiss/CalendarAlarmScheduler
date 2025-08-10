package com.example.calendaralarmscheduler.e2e

import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.IOException

/**
 * Base class for End-to-End integration tests
 * Provides common setup, teardown, and utilities for testing the complete app workflow
 */
abstract class E2ETestBase {

    // Core testing components
    protected lateinit var context: Context
    protected lateinit var application: CalendarAlarmApplication
    protected lateinit var database: AppDatabase
    protected lateinit var uiDevice: UiDevice
    protected lateinit var uiAutomation: UiAutomation

    // Test controllers
    protected lateinit var applicationController: ApplicationTestController
    protected lateinit var calendarTestProvider: CalendarTestProvider
    protected lateinit var alarmTestVerifier: AlarmTestVerifier

    // Grant all required permissions for testing
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.READ_CALENDAR,
        android.Manifest.permission.WAKE_LOCK,
        android.Manifest.permission.VIBRATE,
        android.Manifest.permission.USE_EXACT_ALARM,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    @Before
    open fun setUp() {
        // Initialize core test components
        context = ApplicationProvider.getApplicationContext()
        application = context as CalendarAlarmApplication
        database = application.database
        
        // Initialize UI testing components
        uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

        // Grant additional permissions via ADB if needed
        grantRequiredPermissions()

        // Initialize test controllers
        applicationController = ApplicationTestController(context, uiDevice)
        calendarTestProvider = CalendarTestProvider(context)
        alarmTestVerifier = AlarmTestVerifier(context)

        // Clear any existing test data
        cleanupTestData()

        // Ensure device is ready for testing
        prepareDeviceForTesting()
    }

    @After
    open fun tearDown() {
        try {
            // Clean up test data
            cleanupTestData()
            
            // Reset any device states we modified
            resetDeviceState()
            
            // Clear any test alarms
            alarmTestVerifier.clearAllTestAlarms()
            
        } catch (e: Exception) {
            // Log cleanup failures but don't fail the test
            android.util.Log.e("E2ETestBase", "Cleanup failed", e)
        }
    }

    /**
     * Grant additional permissions that require special handling
     */
    private fun grantRequiredPermissions() {
        try {
            // SCHEDULE_EXACT_ALARM requires special handling on Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                executeAdbCommand("pm grant ${context.packageName} android.permission.SCHEDULE_EXACT_ALARM")
            }
            
            // Request to disable battery optimization for reliable testing
            executeAdbCommand("dumpsys deviceidle whitelist +${context.packageName}")
            
        } catch (e: Exception) {
            android.util.Log.w("E2ETestBase", "Failed to grant some permissions via ADB: ${e.message}")
        }
    }

    /**
     * Execute ADB command and return output
     */
    protected fun executeAdbCommand(command: String): String {
        return try {
            uiAutomation.executeShellCommand(command).toString()
        } catch (e: IOException) {
            android.util.Log.e("E2ETestBase", "ADB command failed: $command", e)
            ""
        }
    }

    /**
     * Clean up all test data from database and system
     */
    private fun cleanupTestData() = runBlocking {
        try {
            // Clear database
            database.ruleDao().deleteAllRules()
            database.alarmDao().deleteAllAlarms()
            
            // Clear any test calendar events if we created them
            calendarTestProvider.cleanupTestEvents()
            
        } catch (e: Exception) {
            android.util.Log.w("E2ETestBase", "Failed to cleanup test data", e)
        }
    }

    /**
     * Prepare device for consistent testing
     */
    private fun prepareDeviceForTesting() {
        try {
            // Wake up the device
            if (!uiDevice.isScreenOn) {
                uiDevice.wakeUp()
                uiDevice.waitForIdle(2000)
            }
            
            // Dismiss any system dialogs
            uiDevice.pressBack()
            uiDevice.pressHome()
            uiDevice.waitForIdle(1000)
            
            // Clear notifications
            uiDevice.openNotification()
            uiDevice.waitForIdle(1000)
            uiDevice.pressBack()
            
        } catch (e: Exception) {
            android.util.Log.w("E2ETestBase", "Failed to prepare device", e)
        }
    }

    /**
     * Reset device state after testing
     */
    private fun resetDeviceState() {
        try {
            // Return to home screen
            uiDevice.pressHome()
            uiDevice.waitForIdle(1000)
            
        } catch (e: Exception) {
            android.util.Log.w("E2ETestBase", "Failed to reset device state", e)
        }
    }

    /**
     * Wait for condition with timeout
     */
    protected fun waitForCondition(
        timeoutMs: Long = 10000,
        intervalMs: Long = 500,
        condition: () -> Boolean
    ): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) {
                return true
            }
            Thread.sleep(intervalMs)
        }
        return false
    }

    /**
     * Check if permission is granted
     */
    protected fun isPermissionGranted(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get current alarm count from system
     */
    protected fun getSystemAlarmCount(): Int {
        return alarmTestVerifier.getScheduledAlarmCount()
    }

    /**
     * Assert that condition becomes true within timeout
     */
    protected fun assertEventuallyTrue(
        message: String = "Condition was not met within timeout",
        timeoutMs: Long = 10000,
        condition: () -> Boolean
    ) {
        if (!waitForCondition(timeoutMs, condition = condition)) {
            throw AssertionError(message)
        }
    }

    /**
     * Take screenshot for debugging test failures
     */
    protected fun takeScreenshot(name: String) {
        try {
            val screenshot = uiAutomation.takeScreenshot()
            // In a real implementation, you'd save this to external storage
            android.util.Log.i("E2ETestBase", "Screenshot taken: $name")
        } catch (e: Exception) {
            android.util.Log.w("E2ETestBase", "Failed to take screenshot: $name", e)
        }
    }
}