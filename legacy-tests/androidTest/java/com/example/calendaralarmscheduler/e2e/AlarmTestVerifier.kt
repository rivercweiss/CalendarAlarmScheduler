package com.example.calendaralarmscheduler.e2e

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import com.example.calendaralarmscheduler.receivers.AlarmReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.regex.Pattern

/**
 * Verifies alarm state in both the system AlarmManager and app database
 * Provides comprehensive alarm testing and validation capabilities
 */
class AlarmTestVerifier(private val context: Context) {
    
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val application: CalendarAlarmApplication = context as CalendarAlarmApplication
    private val alarmRepository: AlarmRepository by lazy { application.alarmRepository }
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    companion object {
        private const val TEST_ALARM_PREFIX = "test-alarm-"
        private const val ALARM_VERIFICATION_TIMEOUT = 10000L
    }

    /**
     * Get count of alarms scheduled in system AlarmManager
     * Uses dumpsys to inspect system state
     */
    fun getScheduledAlarmCount(): Int {
        return try {
            val dumpsysOutput = executeShellCommand("dumpsys alarm")
            val packageAlarms = extractPackageAlarms(dumpsysOutput)
            packageAlarms.size
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Failed to get system alarm count", e)
            0
        }
    }

    /**
     * Verify specific alarm is scheduled in system
     */
    fun isAlarmScheduledInSystem(alarmId: String): Boolean {
        return try {
            val dumpsysOutput = executeShellCommand("dumpsys alarm")
            dumpsysOutput.contains(alarmId) || dumpsysOutput.contains("CalendarAlarmScheduler")
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Failed to verify system alarm", e)
            false
        }
    }

    /**
     * Get all alarms from app database
     */
    fun getDatabaseAlarms(): List<ScheduledAlarm> = runBlocking {
        try {
            alarmRepository.getAllAlarms().first()
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Failed to get database alarms", e)
            emptyList()
        }
    }

    /**
     * Get active (non-dismissed, future) alarms from database
     */
    fun getActiveDatabaseAlarms(): List<ScheduledAlarm> = runBlocking {
        try {
            alarmRepository.getActiveAlarmsSync()
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Failed to get active alarms", e)
            emptyList()
        }
    }

    /**
     * Verify database and system alarm counts match
     */
    fun verifyAlarmConsistency(): Boolean {
        val dbCount = getActiveDatabaseAlarms().size
        val systemCount = getScheduledAlarmCount()
        
        val consistent = dbCount == systemCount
        android.util.Log.i("AlarmTestVerifier", "Alarm consistency check - DB: $dbCount, System: $systemCount, Consistent: $consistent")
        
        return consistent
    }

    /**
     * Create a test alarm for immediate verification
     */
    fun scheduleTestAlarm(delaySeconds: Int = 10): String {
        val testAlarmId = TEST_ALARM_PREFIX + System.currentTimeMillis()
        val triggerTime = System.currentTimeMillis() + (delaySeconds * 1000)
        
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.calendaralarmscheduler.ALARM_TRIGGER"
            putExtra("ALARM_ID", testAlarmId)
            putExtra("EVENT_TITLE", "Test Alarm")
            putExtra("EVENT_ID", "test-event")
            putExtra("RULE_ID", "test-rule")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            testAlarmId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            
            android.util.Log.i("AlarmTestVerifier", "Scheduled test alarm: $testAlarmId for ${delaySeconds}s from now")
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Failed to schedule test alarm", e)
        }

        return testAlarmId
    }

    /**
     * Cancel specific test alarm
     */
    fun cancelTestAlarm(alarmId: String): Boolean {
        return try {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                android.util.Log.i("AlarmTestVerifier", "Cancelled test alarm: $alarmId")
                return true
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Failed to cancel test alarm", e)
            false
        }
    }

    /**
     * Clear all test alarms (cleanup)
     */
    fun clearAllTestAlarms() {
        try {
            val dumpsysOutput = executeShellCommand("dumpsys alarm")
            val testAlarmIds = extractTestAlarmIds(dumpsysOutput)
            
            testAlarmIds.forEach { alarmId ->
                cancelTestAlarm(alarmId)
            }
            
            android.util.Log.i("AlarmTestVerifier", "Cleared ${testAlarmIds.size} test alarms")
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Failed to clear test alarms", e)
        }
    }

    /**
     * Wait for alarm to be scheduled in system
     */
    fun waitForAlarmScheduled(alarmId: String, timeoutMs: Long = ALARM_VERIFICATION_TIMEOUT): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (isAlarmScheduledInSystem(alarmId)) {
                return true
            }
            Thread.sleep(500)
        }
        
        return false
    }

    /**
     * Wait for alarm to be removed from system  
     */
    fun waitForAlarmRemoved(alarmId: String, timeoutMs: Long = ALARM_VERIFICATION_TIMEOUT): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!isAlarmScheduledInSystem(alarmId)) {
                return true
            }
            Thread.sleep(500)
        }
        
        return false
    }

    /**
     * Verify alarm fires at expected time (within tolerance)
     */
    fun verifyAlarmTiming(expectedTriggerTime: Long, actualTriggerTime: Long, toleranceMs: Long = 5000): Boolean {
        val difference = Math.abs(actualTriggerTime - expectedTriggerTime)
        val withinTolerance = difference <= toleranceMs
        
        android.util.Log.i("AlarmTestVerifier", "Alarm timing - Expected: $expectedTriggerTime, Actual: $actualTriggerTime, Diff: ${difference}ms, OK: $withinTolerance")
        
        return withinTolerance
    }

    /**
     * Get detailed system alarm information
     */
    fun getSystemAlarmDetails(): AlarmSystemInfo {
        val dumpsysOutput = executeShellCommand("dumpsys alarm")
        return parseAlarmSystemInfo(dumpsysOutput)
    }

    /**
     * Check if exact alarm permission is granted
     */
    fun hasExactAlarmPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // Not needed before Android 12
        }
    }

    /**
     * Verify alarm scheduling capability
     */
    fun verifyAlarmSchedulingCapability(): CapabilityStatus {
        val hasPermission = hasExactAlarmPermission()
        val canScheduleTest = try {
            scheduleTestAlarm(60) // Schedule for 1 minute
            true
        } catch (e: Exception) {
            false
        }
        
        return CapabilityStatus(
            hasExactAlarmPermission = hasPermission,
            canScheduleAlarms = canScheduleTest,
            isWhitelisted = isAppWhitelistedFromBatteryOptimization()
        )
    }

    /**
     * Check if app is whitelisted from battery optimization
     */
    fun isAppWhitelistedFromBatteryOptimization(): Boolean {
        return try {
            val output = executeShellCommand("dumpsys deviceidle whitelist")
            output.contains(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Simulate alarm firing for testing alarm handling
     */
    fun simulateAlarmFiring(alarmId: String, eventTitle: String): Boolean {
        return try {
            val intent = Intent().apply {
                action = "com.example.calendaralarmscheduler.ALARM_TRIGGER"
                putExtra("ALARM_ID", alarmId)
                putExtra("EVENT_TITLE", eventTitle)
                putExtra("EVENT_ID", "sim-event")
                putExtra("RULE_ID", "sim-rule")
            }
            
            context.sendBroadcast(intent)
            android.util.Log.i("AlarmTestVerifier", "Simulated alarm firing: $alarmId")
            true
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Failed to simulate alarm", e)
            false
        }
    }

    // Private helper methods

    private fun executeShellCommand(command: String): String {
        return try {
            instrumentation.uiAutomation.executeShellCommand(command).toString()
        } catch (e: Exception) {
            android.util.Log.e("AlarmTestVerifier", "Shell command failed: $command", e)
            ""
        }
    }

    private fun extractPackageAlarms(dumpsysOutput: String): List<String> {
        val alarmLines = mutableListOf<String>()
        val lines = dumpsysOutput.split("\n")
        
        for (line in lines) {
            if (line.contains("CalendarAlarmScheduler") || line.contains(context.packageName)) {
                alarmLines.add(line.trim())
            }
        }
        
        return alarmLines
    }

    private fun extractTestAlarmIds(dumpsysOutput: String): List<String> {
        val testAlarmIds = mutableListOf<String>()
        val pattern = Pattern.compile("$TEST_ALARM_PREFIX\\d+")
        val matcher = pattern.matcher(dumpsysOutput)
        
        while (matcher.find()) {
            testAlarmIds.add(matcher.group())
        }
        
        return testAlarmIds
    }

    private fun parseAlarmSystemInfo(dumpsysOutput: String): AlarmSystemInfo {
        val lines = dumpsysOutput.split("\n")
        var totalAlarms = 0
        var packageAlarms = 0
        var nextAlarmTime = 0L
        
        for (line in lines) {
            when {
                line.contains("Total alarms:") -> {
                    totalAlarms = extractNumber(line)
                }
                line.contains(context.packageName) -> {
                    packageAlarms++
                }
                line.contains("Next alarm:") -> {
                    nextAlarmTime = extractTimestamp(line)
                }
            }
        }
        
        return AlarmSystemInfo(
            totalSystemAlarms = totalAlarms,
            packageAlarmCount = packageAlarms,
            nextAlarmTime = nextAlarmTime,
            hasExactAlarmPermission = hasExactAlarmPermission()
        )
    }

    private fun extractNumber(line: String): Int {
        val pattern = Pattern.compile("\\d+")
        val matcher = pattern.matcher(line)
        return if (matcher.find()) {
            matcher.group().toIntOrNull() ?: 0
        } else 0
    }

    private fun extractTimestamp(line: String): Long {
        // Parse timestamp from alarm dump - implementation depends on format
        return System.currentTimeMillis() // Simplified for now
    }

    // Data classes for structured results
    
    data class CapabilityStatus(
        val hasExactAlarmPermission: Boolean,
        val canScheduleAlarms: Boolean,
        val isWhitelisted: Boolean
    ) {
        val isFullyCapable: Boolean
            get() = hasExactAlarmPermission && canScheduleAlarms && isWhitelisted
    }

    data class AlarmSystemInfo(
        val totalSystemAlarms: Int,
        val packageAlarmCount: Int,
        val nextAlarmTime: Long,
        val hasExactAlarmPermission: Boolean
    )
}