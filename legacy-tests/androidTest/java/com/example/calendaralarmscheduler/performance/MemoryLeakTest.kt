package com.example.calendaralarmscheduler.performance

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.e2e.E2ETestBase
import com.example.calendaralarmscheduler.ui.MainActivity
import com.example.calendaralarmscheduler.ui.alarm.AlarmActivity
import com.example.calendaralarmscheduler.ui.settings.SettingsViewModel
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.*
import org.junit.Test
import java.lang.ref.WeakReference
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*

/**
 * Tests for memory leaks and resource cleanup
 * Verifies ViewModels, Activities, and database connections are properly cleaned up
 */
class MemoryLeakTest : E2ETestBase() {

    @Test
    fun testViewModelMemoryLeaks() = runBlocking {
        Logger.i("MemoryLeakTest", "=== Testing ViewModel Memory Leaks ===")
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Initial memory: ${initialMemory / (1024 * 1024)}MB")
        
        val viewModelReferences = mutableListOf<WeakReference<SettingsViewModel>>()
        
        // Create and destroy ViewModels multiple times
        repeat(20) { index ->
            val settingsRepository = SettingsRepository(context)
            val workerManager = com.example.calendaralarmscheduler.workers.WorkerManager(context)
            val alarmScheduler = application.alarmScheduler
            
            val viewModel = SettingsViewModel(settingsRepository, workerManager, alarmScheduler)
            viewModelReferences.add(WeakReference(viewModel))
            
            // Simulate ViewModel usage
            viewModel.setRefreshInterval(30)
            viewModel.setAllDayDefaultTime(20, 0)
            viewModel.getCurrentRefreshInterval()
            
            // Simulate ViewModel clearing
            @Suppress("UNUSED_VALUE")
            val nullViewModel: SettingsViewModel? = null
            
            Logger.d("MemoryLeakTest", "Created and nullified ViewModel $index")
        }
        
        // Force garbage collection
        repeat(5) {
            System.gc()
            delay(1000)
        }
        
        val postGcMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Post-GC memory: ${postGcMemory / (1024 * 1024)}MB")
        
        // Check how many ViewModels were garbage collected
        val remainingViewModels = viewModelReferences.count { it.get() != null }
        Logger.i("MemoryLeakTest", "ViewModels remaining after GC: $remainingViewModels / ${viewModelReferences.size}")
        
        // Most ViewModels should be garbage collected
        assertTrue(
            "Most ViewModels should be garbage collected: $remainingViewModels remaining",
            remainingViewModels < viewModelReferences.size / 2
        )
        
        // Memory growth should be reasonable
        val memoryGrowth = postGcMemory - initialMemory
        val maxAcceptableGrowthMB = 50 // 50MB max growth
        
        assertTrue(
            "Memory growth should be reasonable: ${memoryGrowth / (1024 * 1024)}MB (max: ${maxAcceptableGrowthMB}MB)",
            memoryGrowth < (maxAcceptableGrowthMB * 1024 * 1024)
        )
        
        Logger.i("MemoryLeakTest", "✅ ViewModel memory leak test PASSED")
    }
    
    @Test
    fun testActivityMemoryLeaks() = runBlocking {
        Logger.i("MemoryLeakTest", "=== Testing Activity Memory Leaks ===")
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Initial memory: ${initialMemory / (1024 * 1024)}MB")
        
        val activityReferences = mutableListOf<WeakReference<Activity>>()
        
        // Create and destroy MainActivity multiple times
        repeat(10) { index ->
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            
            scenario.onActivity { activity ->
                activityReferences.add(WeakReference(activity))
                Logger.d("MemoryLeakTest", "MainActivity $index created")
                
                // Simulate activity usage
                applicationController.navigateToRules()
                applicationController.navigateToSettings()
                applicationController.navigateToEventPreview()
            }
            
            // Navigate through lifecycle states
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.moveToState(Lifecycle.State.DESTROYED)
            scenario.close()
            
            Logger.d("MemoryLeakTest", "MainActivity $index destroyed")
            delay(500)
        }
        
        // Create and destroy AlarmActivity multiple times
        repeat(5) { index ->
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("ALARM_ID", "memory-test-$index")
                putExtra("EVENT_TITLE", "Memory Test Event $index")
                putExtra("EVENT_START_TIME", System.currentTimeMillis() + (60 * 60 * 1000))
                putExtra("RULE_ID", "test-rule")
            }
            
            val alarmScenario = ActivityScenario.launch<AlarmActivity>(alarmIntent)
            
            alarmScenario.onActivity { activity ->
                activityReferences.add(WeakReference(activity))
                Logger.d("MemoryLeakTest", "AlarmActivity $index created")
            }
            
            delay(1000) // Let alarm activity initialize
            
            alarmScenario.moveToState(Lifecycle.State.DESTROYED)
            alarmScenario.close()
            
            Logger.d("MemoryLeakTest", "AlarmActivity $index destroyed")
            delay(500)
        }
        
        // Force garbage collection
        repeat(5) {
            System.gc()
            delay(1000)
        }
        
        val postGcMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Post-GC memory: ${postGcMemory / (1024 * 1024)}MB")
        
        // Check how many Activities were garbage collected
        val remainingActivities = activityReferences.count { it.get() != null }
        Logger.i("MemoryLeakTest", "Activities remaining after GC: $remainingActivities / ${activityReferences.size}")
        
        // Most Activities should be garbage collected
        assertTrue(
            "Most Activities should be garbage collected: $remainingActivities remaining",
            remainingActivities < activityReferences.size / 3
        )
        
        // Memory growth should be reasonable
        val memoryGrowth = postGcMemory - initialMemory
        val maxAcceptableGrowthMB = 75 // 75MB max growth for activities
        
        assertTrue(
            "Memory growth should be reasonable: ${memoryGrowth / (1024 * 1024)}MB (max: ${maxAcceptableGrowthMB}MB)",
            memoryGrowth < (maxAcceptableGrowthMB * 1024 * 1024)
        )
        
        Logger.i("MemoryLeakTest", "✅ Activity memory leak test PASSED")
    }
    
    @Test
    fun testDatabaseConnectionLeaks() = runBlocking {
        Logger.i("MemoryLeakTest", "=== Testing Database Connection Leaks ===")
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Initial memory: ${initialMemory / (1024 * 1024)}MB")
        
        val repositoryReferences = mutableListOf<WeakReference<*>>()
        
        // Create many repository instances to test connection pooling
        repeat(50) { index ->
            val ruleRepository = application.ruleRepository
            val alarmRepository = application.alarmRepository
            val calendarRepository = application.calendarRepository
            
            repositoryReferences.add(WeakReference(ruleRepository))
            repositoryReferences.add(WeakReference(alarmRepository))
            repositoryReferences.add(WeakReference(calendarRepository))
            
            // Perform database operations
            val testRule = Rule(
                id = "leak-test-rule-$index",
                name = "Leak Test Rule $index",
                keywordPattern = "leak$index",
                isRegex = false,
                calendarIds = emptyList(),
                leadTimeMinutes = 30,
                enabled = true
            )
            
            // Insert and query to exercise connections
            runBlocking { database.ruleDao().insertRule(testRule) }
            val retrievedRules = database.ruleDao().getAllRulesSync()
            runBlocking { database.ruleDao().deleteRule(testRule) }
            
            assertTrue("Database operations should work", retrievedRules.isNotEmpty())
            
            if (index % 10 == 0) {
                Logger.d("MemoryLeakTest", "Completed database operations batch $index")
                delay(100) // Brief pause every 10 operations
            }
        }
        
        // Force garbage collection
        repeat(5) {
            System.gc()
            delay(1000)
        }
        
        val postGcMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Post-GC memory: ${postGcMemory / (1024 * 1024)}MB")
        
        // Memory growth should be minimal for database operations
        val memoryGrowth = postGcMemory - initialMemory
        val maxAcceptableGrowthMB = 30 // 30MB max growth for DB operations
        
        assertTrue(
            "Database operations should not cause significant memory growth: ${memoryGrowth / (1024 * 1024)}MB (max: ${maxAcceptableGrowthMB}MB)",
            memoryGrowth < (maxAcceptableGrowthMB * 1024 * 1024)
        )
        
        // Test database still functional after intensive operations
        val finalTestRule = Rule(
            id = "final-test-rule",
            name = "Final Test Rule",
            keywordPattern = "final",
            isRegex = false,
            calendarIds = emptyList(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(finalTestRule) }
        val finalRules = database.ruleDao().getAllRulesSync()
        assertTrue("Database should still be functional", finalRules.any { it.id == "final-test-rule" })
        
        Logger.i("MemoryLeakTest", "✅ Database connection leak test PASSED")
    }
    
    @Test
    fun testLargeEventProcessingMemoryLeaks() = runBlocking {
        Logger.i("MemoryLeakTest", "=== Testing Large Event Processing Memory Leaks ===")
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Initial memory: ${initialMemory / (1024 * 1024)}MB")
        
        // Create test rules
        val testRules = (1..10).map { index ->
            Rule(
                id = "memory-rule-$index",
                name = "Memory Test Rule $index",
                keywordPattern = "memory$index",
                isRegex = false,
                calendarIds = emptyList(),
                leadTimeMinutes = 30,
                enabled = true
            )
        }
        
        testRules.forEach { rule ->
            runBlocking { database.ruleDao().insertRule(rule) }
        }
        
        // Process large event sets multiple times
        repeat(10) { batchIndex ->
            Logger.d("MemoryLeakTest", "Processing event batch $batchIndex")
            
            // Generate large event set
            val eventCount = 200
            val events = (1..eventCount).map { eventIndex ->
                CalendarEvent(
                    id = "memory-event-$batchIndex-$eventIndex",
                    title = "Memory Test Event $batchIndex-$eventIndex memory${eventIndex % 10}",
                    startTimeUtc = System.currentTimeMillis() + (eventIndex * 60 * 60 * 1000L),
                    endTimeUtc = System.currentTimeMillis() + (eventIndex * 60 * 60 * 1000L) + (60 * 60 * 1000),
                    isAllDay = eventIndex % 20 == 0,
                    calendarId = (eventIndex % 3).toLong() + 1,
                    timezone = "UTC",
                    lastModified = System.currentTimeMillis()
                )
            }
            
            // Inject events
            events.forEach { event ->
                calendarTestProvider.createTestEvent(
                    title = event.title,
                    startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                    durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                    isAllDay = event.isAllDay,
                    calendarId = event.calendarId
                )
            }
            
            // Process events
            val workerTriggered = applicationController.triggerBackgroundWorker()
            assertTrue("Worker should be triggerable for batch $batchIndex", workerTriggered)
            
            // Wait for processing
            delay(3000)
            
            // Clear processed events to prevent accumulation
            // calendarTestProvider.clearAllTestEvents() // Method may not exist
            runBlocking { database.alarmDao().deleteAllAlarms() }
            
            // Check memory periodically
            if (batchIndex % 3 == 2) {
                val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                Logger.d("MemoryLeakTest", "Memory after batch $batchIndex: ${currentMemory / (1024 * 1024)}MB")
                
                // Force GC periodically
                System.gc()
                delay(1000)
            }
        }
        
        // Final garbage collection
        repeat(5) {
            System.gc()
            delay(1000)
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Final memory: ${finalMemory / (1024 * 1024)}MB")
        
        // Memory growth should be minimal after processing many events
        val memoryGrowth = finalMemory - initialMemory
        val maxAcceptableGrowthMB = 60 // 60MB max growth for large event processing
        
        assertTrue(
            "Large event processing should not cause significant memory growth: ${memoryGrowth / (1024 * 1024)}MB (max: ${maxAcceptableGrowthMB}MB)",
            memoryGrowth < (maxAcceptableGrowthMB * 1024 * 1024)
        )
        
        // Verify system is still functional
        val testRule = Rule(
            id = "post-processing-rule",
            name = "Post Processing Rule",
            keywordPattern = "post",
            isRegex = false,
            calendarIds = emptyList(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(testRule) }
        val rules = database.ruleDao().getAllRulesSync()
        assertTrue("System should still be functional", rules.any { it.id == "post-processing-rule" })
        
        Logger.i("MemoryLeakTest", "✅ Large event processing memory leak test PASSED")
    }
    
    @Test
    fun testWorkerMemoryLeaks() = runBlocking {
        Logger.i("MemoryLeakTest", "=== Testing Worker Memory Leaks ===")
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Initial memory: ${initialMemory / (1024 * 1024)}MB")
        
        // Create test data
        val testRule = Rule(
            id = "worker-memory-rule",
            name = "Worker Memory Rule",
            keywordPattern = "worker",
            isRegex = false,
            calendarIds = emptyList(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(testRule) }
        
        // Create moderate event set
        val events = (1..50).map { index ->
            CalendarEvent(
                id = "worker-event-$index",
                title = "Worker Test Event $index",
                startTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000L),
                endTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000L) + (60 * 60 * 1000),
                isAllDay = false,
                calendarId = 1L,
                timezone = "UTC",
                lastModified = System.currentTimeMillis()
            )
        }
        
        events.forEach { event ->
            calendarTestProvider.createTestEvent(
                title = event.title,
                startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                isAllDay = event.isAllDay,
                calendarId = event.calendarId
            )
        }
        
        // Trigger worker many times
        repeat(30) { workerIndex ->
            Logger.d("MemoryLeakTest", "Triggering worker execution $workerIndex")
            
            val workerTriggered = applicationController.triggerBackgroundWorker()
            assertTrue("Worker should be triggerable for execution $workerIndex", workerTriggered)
            
            // Wait for worker to complete
            delay(2000)
            
            // Check memory periodically
            if (workerIndex % 5 == 4) {
                val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                Logger.d("MemoryLeakTest", "Memory after worker $workerIndex: ${currentMemory / (1024 * 1024)}MB")
                
                // Force GC periodically
                System.gc()
                delay(1000)
            }
        }
        
        // Final garbage collection
        repeat(5) {
            System.gc()
            delay(1000)
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("MemoryLeakTest", "Final memory: ${finalMemory / (1024 * 1024)}MB")
        
        // Memory growth should be minimal for worker executions
        val memoryGrowth = finalMemory - initialMemory
        val maxAcceptableGrowthMB = 40 // 40MB max growth for worker executions
        
        assertTrue(
            "Multiple worker executions should not cause significant memory growth: ${memoryGrowth / (1024 * 1024)}MB (max: ${maxAcceptableGrowthMB}MB)",
            memoryGrowth < (maxAcceptableGrowthMB * 1024 * 1024)
        )
        
        // Verify worker functionality
        val alarms = database.alarmDao().getActiveAlarmsSync()
        assertTrue("Worker should have created alarms", alarms.isNotEmpty())
        
        // Verify alarm quality
        alarms.take(5).forEach { alarm ->
            assertTrue("Alarms should be in future", alarm.alarmTimeUtc > System.currentTimeMillis())
            assertTrue("Alarms should have event titles", alarm.eventTitle.isNotEmpty())
        }
        
        Logger.i("MemoryLeakTest", "Created ${alarms.size} alarms with ${memoryGrowth / (1024 * 1024)}MB memory growth")
        Logger.i("MemoryLeakTest", "✅ Worker memory leak test PASSED")
    }
}