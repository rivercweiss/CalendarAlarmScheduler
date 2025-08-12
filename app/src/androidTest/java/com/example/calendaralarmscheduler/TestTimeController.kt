package com.example.calendaralarmscheduler

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Time manipulation utility for E2E testing.
 * Provides controlled time environment for testing time-dependent functionality.
 */
class TestTimeController {
    
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    // Time manipulation state
    private var isTimeManipulated = false
    private var originalTime = System.currentTimeMillis()
    private var timeOffset = 0L
    
    companion object {
        // Common time intervals for testing
        val FIVE_MINUTES = TimeUnit.MINUTES.toMillis(5)
        val FIFTEEN_MINUTES = TimeUnit.MINUTES.toMillis(15)
        val THIRTY_MINUTES = TimeUnit.MINUTES.toMillis(30)
        val ONE_HOUR = TimeUnit.HOURS.toMillis(1)
        val ONE_DAY = TimeUnit.DAYS.toMillis(1)
        val ONE_WEEK = TimeUnit.DAYS.toMillis(7)
        val ONE_MONTH = TimeUnit.DAYS.toMillis(30)
        
        // Test scenarios
        const val SCENARIO_NEAR_FUTURE = "near_future"
        const val SCENARIO_FAR_FUTURE = "far_future"
        const val SCENARIO_NEXT_DAY = "next_day"
        const val SCENARIO_NEXT_WEEK = "next_week"
    }
    
    data class TimeScenario(
        val name: String,
        val description: String,
        val timeOffset: Long,
        val expectedAlarms: Int = 0
    )
    
    /**
     * Get current test time (may be manipulated)
     */
    fun getCurrentTime(): Long {
        return if (isTimeManipulated) {
            originalTime + timeOffset
        } else {
            System.currentTimeMillis()
        }
    }
    
    /**
     * Fast forward time by specified amount
     */
    fun fastForward(milliseconds: Long) {
        timeOffset += milliseconds
        isTimeManipulated = true
        
        Log.i("TestTimeController", "Fast forwarded by ${milliseconds / 1000}s. " +
                "Current test time: ${Date(getCurrentTime())}")
    }
    
    /**
     * Jump to specific time
     */
    fun jumpToTime(targetTime: Long) {
        timeOffset = targetTime - originalTime
        isTimeManipulated = true
        
        Log.i("TestTimeController", "Jumped to time: ${Date(getCurrentTime())}")
    }
    
    /**
     * Reset to real system time
     */
    fun resetTime() {
        timeOffset = 0L
        isTimeManipulated = false
        originalTime = System.currentTimeMillis()
        
        Log.i("TestTimeController", "Reset to system time: ${Date(getCurrentTime())}")
    }
    
    /**
     * Create time scenarios for comprehensive testing
     */
    fun createTimeScenarios(): List<TimeScenario> {
        val baseTime = System.currentTimeMillis()
        
        return listOf(
            TimeScenario(
                name = SCENARIO_NEAR_FUTURE,
                description = "15 minutes in the future",
                timeOffset = FIFTEEN_MINUTES,
                expectedAlarms = 1
            ),
            TimeScenario(
                name = SCENARIO_FAR_FUTURE,
                description = "2 hours in the future", 
                timeOffset = 2 * ONE_HOUR,
                expectedAlarms = 2
            ),
            TimeScenario(
                name = SCENARIO_NEXT_DAY,
                description = "24 hours in the future",
                timeOffset = ONE_DAY,
                expectedAlarms = 3
            ),
            TimeScenario(
                name = SCENARIO_NEXT_WEEK,
                description = "7 days in the future",
                timeOffset = ONE_WEEK,
                expectedAlarms = 5
            )
        )
    }
    
    /**
     * Set up time scenario for testing
     */
    fun setupTimeScenario(scenario: TimeScenario) {
        jumpToTime(originalTime + scenario.timeOffset)
        
        Log.i("TestTimeController", "Setup scenario '${scenario.name}': ${scenario.description}")
    }
    
    /**
     * Simulate time passing for alarm testing
     * Useful for testing alarm scheduling and firing
     */
    fun simulateTimePassage(
        duration: Long, 
        steps: Int = 10,
        onTimeStep: ((currentTime: Long, stepNumber: Int) -> Unit)? = null
    ) {
        val stepSize = duration / steps
        val startTime = getCurrentTime()
        
        Log.i("TestTimeController", "Simulating ${duration / 1000}s time passage in $steps steps")
        
        repeat(steps) { step ->
            fastForward(stepSize)
            val currentTime = getCurrentTime()
            
            onTimeStep?.invoke(currentTime, step + 1)
            
            // Small pause to allow system processing
            Thread.sleep(100)
        }
        
        val totalElapsed = getCurrentTime() - startTime
        Log.i("TestTimeController", "Time simulation complete. Elapsed: ${totalElapsed / 1000}s")
    }
    
    /**
     * Get predefined events at specific time intervals for testing
     * Note: With Option 3 approach, we use predefined calendar events instead of creating them
     */
    fun getTimeBasedTestEvents(calendarProvider: CalendarTestDataProvider): List<CalendarTestDataProvider.CalendarEvent> {
        // Get predefined events that are useful for time-based testing
        val allEvents = calendarProvider.queryAllEvents()
        
        return allEvents.filter { event ->
            // Filter events that are useful for time acceleration testing
            val title = event.title.lowercase()
            title.contains("important") || 
            title.contains("meeting") || 
            title.contains("doctor") ||
            title.contains("conference")
        }.sortedBy { it.startTime }
    }
    
    /**
     * Wait for alarms to potentially fire (with time acceleration)
     */
    fun waitForAlarms(maxWaitTime: Long = 30000, checkInterval: Long = 1000): AlarmWaitResult {
        val startTime = getCurrentTime()
        val endTime = startTime + maxWaitTime
        var alarmsFired = 0
        
        Log.i("TestTimeController", "Waiting for alarms to fire (max ${maxWaitTime / 1000}s)")
        
        while (getCurrentTime() < endTime) {
            // Fast forward time in small increments
            fastForward(checkInterval)
            
            // Check for alarm activity (you would need to monitor AlarmReceiver or system logs)
            // This is a simplified implementation - in practice you'd monitor actual alarm firing
            
            Thread.sleep(100) // Allow system processing
        }
        
        return AlarmWaitResult(
            waited = getCurrentTime() - startTime,
            alarmsFired = alarmsFired,
            timedOut = getCurrentTime() >= endTime
        )
    }
    
    data class AlarmWaitResult(
        val waited: Long,
        val alarmsFired: Int,
        val timedOut: Boolean
    )
    
    /**
     * Generate time-based test report
     */
    fun generateTimeReport(): TimeTestReport {
        return TimeTestReport(
            originalTime = originalTime,
            currentTime = getCurrentTime(),
            isTimeManipulated = isTimeManipulated,
            totalTimeOffset = timeOffset,
            timeOffsetHours = timeOffset.toDouble() / ONE_HOUR,
            summary = buildString {
                appendLine("=== Time Control Report ===")
                appendLine("Original Time: ${Date(originalTime)}")
                appendLine("Current Test Time: ${Date(getCurrentTime())}")
                appendLine("Time Manipulated: $isTimeManipulated")
                appendLine("Total Offset: ${timeOffset / 1000}s (${timeOffset.toDouble() / ONE_HOUR}h)")
            }
        )
    }
    
    data class TimeTestReport(
        val originalTime: Long,
        val currentTime: Long,
        val isTimeManipulated: Boolean,
        val totalTimeOffset: Long,
        val timeOffsetHours: Double,
        val summary: String
    )
}