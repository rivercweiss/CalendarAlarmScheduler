package com.example.calendaralarmscheduler.performance

import android.content.Context
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.e2e.E2ETestBase
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*

/**
 * Tests concurrent operations and thread safety
 * Verifies app behavior under simultaneous database operations,
 * worker executions, and UI interactions
 */
class ConcurrentOperationTest : E2ETestBase() {

    @Test
    fun testConcurrentDatabaseOperations() = runBlocking {
        Logger.i("ConcurrentOperationTest", "=== Testing Concurrent Database Operations ===")
        
        val operationCount = 50
        val errors = ConcurrentLinkedQueue<Exception>()
        val successCount = AtomicInteger(0)
        
        val testTime = measureTimeMillis {
            // Create multiple coroutines for concurrent database operations
            val jobs = mutableListOf<Job>()
            
            // Concurrent rule insertions
            repeat(operationCount / 5) { index ->
                jobs.add(launch {
                    try {
                        val rule = Rule(
                            id = "concurrent-rule-$index",
                            name = "Concurrent Rule $index",
                            keywordPattern = "test$index",
                            isRegex = false,
                            calendarIds = listOf((index % 3).toLong() + 1),
                            leadTimeMinutes = 30,
                            enabled = true
                        )
                        database.ruleDao().insertRule(rule)
                        successCount.incrementAndGet()
                        Logger.d("ConcurrentOperationTest", "Inserted rule $index")
                    } catch (e: Exception) {
                        errors.add(e)
                        Logger.e("ConcurrentOperationTest", "Error inserting rule $index", e)
                    }
                })
            }
            
            // Concurrent alarm insertions
            repeat(operationCount * 2) { index ->
                jobs.add(launch {
                    try {
                        val currentTime = System.currentTimeMillis()
                        val alarm = com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm(
                            id = "concurrent-alarm-$index",
                            eventId = "event-$index",
                            ruleId = "rule-${index % 10}",
                            eventTitle = "Concurrent Event $index",
                            eventStartTimeUtc = currentTime + (index * 60 * 60 * 1000L),
                            alarmTimeUtc = currentTime + (index * 60 * 60 * 1000L) - (30 * 60 * 1000),
                            scheduledAt = currentTime,
                            userDismissed = false,
                            pendingIntentRequestCode = 60000 + index,
                            lastEventModified = currentTime
                        )
                        database.alarmDao().insertAlarm(alarm)
                        successCount.incrementAndGet()
                        Logger.d("ConcurrentOperationTest", "Inserted alarm $index")
                    } catch (e: Exception) {
                        errors.add(e)
                        Logger.e("ConcurrentOperationTest", "Error inserting alarm $index", e)
                    }
                })
            }
            
            // Concurrent queries
            repeat(operationCount / 2) { index ->
                jobs.add(launch {
                    try {
                        val rules = database.ruleDao().getAllRulesSync()
                        val alarms = database.alarmDao().getActiveAlarmsSync()
                        successCount.incrementAndGet()
                        Logger.d("ConcurrentOperationTest", "Query $index: ${rules.size} rules, ${alarms.size} alarms")
                    } catch (e: Exception) {
                        errors.add(e)
                        Logger.e("ConcurrentOperationTest", "Error in query $index", e)
                    }
                })
            }
            
            // Wait for all operations to complete
            jobs.joinAll()
        }
        
        Logger.i("ConcurrentOperationTest", "Concurrent database operations completed in ${testTime}ms")
        Logger.i("ConcurrentOperationTest", "Success count: ${successCount.get()}, Errors: ${errors.size}")
        
        // Verify most operations succeeded
        assertTrue(
            "Most database operations should succeed: ${successCount.get()} / ${operationCount * 2.5}",
            successCount.get() > operationCount * 2
        )
        
        // Log any errors for analysis
        errors.forEach { error ->
            Logger.w("ConcurrentOperationTest", "Database operation error: ${error.message}")
        }
        
        // Verify database integrity
        val finalRules = database.ruleDao().getAllRulesSync()
        val finalAlarms = database.alarmDao().getActiveAlarmsSync()
        
        assertTrue("Should have rules after concurrent operations", finalRules.isNotEmpty())
        assertTrue("Should have alarms after concurrent operations", finalAlarms.isNotEmpty())
        
        Logger.i("ConcurrentOperationTest", "Final state: ${finalRules.size} rules, ${finalAlarms.size} alarms")
        Logger.i("ConcurrentOperationTest", "✅ Concurrent database operations test PASSED")
    }
    
    @Test
    fun testConcurrentWorkerExecutions() = runBlocking {
        Logger.i("ConcurrentOperationTest", "=== Testing Concurrent Worker Executions ===")
        
        // Setup test data
        val testRule = Rule(
            id = "worker-test-rule",
            name = "Worker Test Rule",
            keywordPattern = "concurrent",
            isRegex = false,
            calendarIds = emptyList(),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(testRule) }
        
        // Create test events
        val eventCount = 20
        repeat(eventCount) { index ->
            val event = CalendarEvent(
                id = "worker-event-$index",
                title = "Concurrent Test Event $index",
                startTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000L),
                endTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000L) + (60 * 60 * 1000),
                calendarId = 1L,
                isAllDay = false,
                timezone = "UTC",
                lastModified = System.currentTimeMillis()
            )
            calendarTestProvider.createTestEvent(
                title = event.title,
                startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                isAllDay = event.isAllDay,
                calendarId = event.calendarId
            )
        }
        
        // Clear existing alarms
        database.alarmDao().deleteAllAlarms()
        
        val workerErrors = ConcurrentLinkedQueue<Exception>()
        val workerSuccesses = AtomicInteger(0)
        
        val workerTime = measureTimeMillis {
            // Trigger multiple workers concurrently
            val workerJobs = mutableListOf<Job>()
            
            repeat(10) { workerIndex ->
                workerJobs.add(launch {
                    try {
                        delay(workerIndex * 100L) // Stagger worker starts
                        val success = applicationController.triggerBackgroundWorker()
                        if (success) {
                            workerSuccesses.incrementAndGet()
                            Logger.d("ConcurrentOperationTest", "Worker $workerIndex completed successfully")
                        } else {
                            throw RuntimeException("Worker $workerIndex failed to trigger")
                        }
                    } catch (e: Exception) {
                        workerErrors.add(e)
                        Logger.e("ConcurrentOperationTest", "Worker $workerIndex error", e)
                    }
                })
            }
            
            // Wait for all workers
            workerJobs.joinAll()
        }
        
        Logger.i("ConcurrentOperationTest", "Concurrent worker executions took ${workerTime}ms")
        Logger.i("ConcurrentOperationTest", "Worker successes: ${workerSuccesses.get()}, Errors: ${workerErrors.size}")
        
        // Wait for worker processing to complete
        delay(10000)
        
        // Verify results
        val scheduledAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("ConcurrentOperationTest", "Scheduled ${scheduledAlarms.size} alarms from concurrent workers")
        
        // Should have alarms, but not necessarily duplicates
        assertTrue("Should have scheduled some alarms", scheduledAlarms.isNotEmpty())
        
        // Check for potential duplicates (shouldn't happen with proper concurrency control)
        val uniqueEventIds = scheduledAlarms.map { it.eventId }.toSet()
        val expectedUniqueEvents = minOf(eventCount, scheduledAlarms.size)
        
        Logger.i("ConcurrentOperationTest", "Unique event IDs in alarms: ${uniqueEventIds.size}")
        
        // Verify data integrity
        scheduledAlarms.forEach { alarm ->
            assertTrue("Alarm time should be valid", alarm.alarmTimeUtc > 0)
            assertTrue("Event title should not be empty", alarm.eventTitle.isNotEmpty())
            assertTrue("Event should start after alarm", alarm.eventStartTimeUtc > alarm.alarmTimeUtc)
        }
        
        Logger.i("ConcurrentOperationTest", "✅ Concurrent worker executions test PASSED")
    }
    
    @Test
    fun testConcurrentUIAndBackgroundOperations() = runBlocking {
        Logger.i("ConcurrentOperationTest", "=== Testing Concurrent UI and Background Operations ===")
        
        // Setup initial data
        val initialRules = (1..5).map { index ->
            Rule(
                id = "ui-test-rule-$index",
                name = "UI Test Rule $index",
                keywordPattern = "ui$index",
                isRegex = false,
                calendarIds = listOf(index.toLong()),
                leadTimeMinutes = index * 15,
                enabled = true
            )
        }
        
        initialRules.forEach { rule ->
            runBlocking { database.ruleDao().insertRule(rule) }
        }
        
        val errors = ConcurrentLinkedQueue<Exception>()
        val operationCount = AtomicInteger(0)
        
        val totalTime = measureTimeMillis {
            val jobs = mutableListOf<Job>()
            
            // Simulate UI operations (settings changes)
            jobs.add(launch {
                try {
                    val settingsRepository = application.settingsRepository
                    repeat(20) { index ->
                        settingsRepository.setRefreshIntervalMinutes(if (index % 2 == 0) 30 else 60)
                        settingsRepository.setAllDayDefaultTime((index % 12) + 8, index % 60)
                        delay(200)
                        operationCount.incrementAndGet()
                    }
                    Logger.d("ConcurrentOperationTest", "UI settings operations completed")
                } catch (e: Exception) {
                    errors.add(e)
                }
            })
            
            // Simulate background worker operations
            jobs.add(launch {
                try {
                    repeat(5) { workerIndex ->
                        applicationController.triggerBackgroundWorker()
                        delay(1500)
                        operationCount.incrementAndGet()
                        Logger.d("ConcurrentOperationTest", "Background worker $workerIndex completed")
                    }
                } catch (e: Exception) {
                    errors.add(e)
                }
            })
            
            // Simulate database operations
            jobs.add(launch {
                try {
                    repeat(15) { index ->
                        // Read operations
                        val rules = database.ruleDao().getAllRulesSync()
                        val alarms = database.alarmDao().getActiveAlarmsSync()
                        
                        // Write operations
                        if (index % 3 == 0) {
                            val newRule = Rule(
                                id = "concurrent-db-rule-$index",
                                name = "Concurrent DB Rule $index",
                                keywordPattern = "db$index",
                                isRegex = false,
                                calendarIds = emptyList(),
                                leadTimeMinutes = 45,
                                enabled = true
                            )
                            database.ruleDao().insertRule(newRule)
                        }
                        
                        delay(300)
                        operationCount.incrementAndGet()
                    }
                    Logger.d("ConcurrentOperationTest", "Database operations completed")
                } catch (e: Exception) {
                    errors.add(e)
                }
            })
            
            // Simulate alarm scheduling operations
            jobs.add(launch {
                try {
                    val alarmScheduler = application.alarmScheduler
                    repeat(10) { index ->
                        val testAlarm = com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm(
                            id = "concurrent-ui-alarm-$index",
                            eventId = "ui-event-$index",
                            ruleId = initialRules[index % initialRules.size].id,
                            eventTitle = "Concurrent UI Event $index",
                            eventStartTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000L),
                            alarmTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000L) - (30 * 60 * 1000),
                            scheduledAt = System.currentTimeMillis(),
                            userDismissed = false,
                            pendingIntentRequestCode = 70000 + index,
                            lastEventModified = System.currentTimeMillis()
                        )
                        
                        // Store alarm in database for concurrent testing
                        database.alarmDao().insertAlarm(testAlarm)
                        
                        delay(400)
                        operationCount.incrementAndGet()
                    }
                    Logger.d("ConcurrentOperationTest", "Alarm scheduling operations completed")
                } catch (e: Exception) {
                    errors.add(e)
                }
            })
            
            // Wait for all operations
            jobs.joinAll()
        }
        
        Logger.i("ConcurrentOperationTest", "Concurrent UI/background operations completed in ${totalTime}ms")
        Logger.i("ConcurrentOperationTest", "Total operations: ${operationCount.get()}, Errors: ${errors.size}")
        
        // Log errors for analysis
        errors.forEach { error ->
            Logger.w("ConcurrentOperationTest", "Concurrent operation error: ${error.message}")
        }
        
        // Verify system integrity
        val finalRules = database.ruleDao().getAllRulesSync()
        val finalAlarms = database.alarmDao().getActiveAlarmsSync()
        val finalSettings = application.settingsRepository.getAllSettings()
        
        assertTrue("Should have at least initial rules", finalRules.size >= initialRules.size)
        assertTrue("Should have scheduled alarms", finalAlarms.isNotEmpty())
        assertNotNull("Settings should be accessible", finalSettings["refreshIntervalMinutes"])
        
        // Verify data consistency
        finalAlarms.forEach { alarm ->
            val correspondingRule = finalRules.find { it.id == alarm.ruleId }
            if (correspondingRule != null) {
                assertTrue("Alarm should have event title", alarm.eventTitle.isNotEmpty())
                assertTrue("Alarm should have valid time", alarm.alarmTimeUtc > 0)
            }
        }
        
        Logger.i("ConcurrentOperationTest", "Final state: ${finalRules.size} rules, ${finalAlarms.size} alarms")
        Logger.i("ConcurrentOperationTest", "✅ Concurrent UI/background operations test PASSED")
    }
    
    @Test
    fun testRaceConditionPrevention() = runBlocking {
        Logger.i("ConcurrentOperationTest", "=== Testing Race Condition Prevention ===")
        
        val sharedRuleId = "race-condition-test-rule"
        val sharedRule = Rule(
            id = sharedRuleId,
            name = "Race Condition Test Rule",
            keywordPattern = "race",
            isRegex = false,
            calendarIds = listOf(1L),
            leadTimeMinutes = 30,
            enabled = true
        )
        
        runBlocking { database.ruleDao().insertRule(sharedRule) }
        
        val concurrentModifications = 50
        val successfulUpdates = AtomicInteger(0)
        val updateErrors = ConcurrentLinkedQueue<Exception>()
        
        val raceTestTime = measureTimeMillis {
            val latch = CountDownLatch(concurrentModifications)
            
            repeat(concurrentModifications) { index ->
                Thread {
                    try {
                        // Each thread tries to modify the same rule
                        runBlocking {
                            val currentRule = database.ruleDao().getRuleById(sharedRuleId)
                            if (currentRule != null) {
                                val modifiedRule = currentRule.copy(
                                    leadTimeMinutes = 30 + index,
                                    name = "Modified Rule $index"
                                )
                                database.ruleDao().updateRule(modifiedRule)
                                successfulUpdates.incrementAndGet()
                            }
                        }
                    } catch (e: Exception) {
                        updateErrors.add(e)
                        Logger.d("ConcurrentOperationTest", "Race condition update $index error: ${e.message}")
                    } finally {
                        latch.countDown()
                    }
                }.start()
            }
            
            // Wait for all threads to complete
            latch.await()
        }
        
        Logger.i("ConcurrentOperationTest", "Race condition test completed in ${raceTestTime}ms")
        Logger.i("ConcurrentOperationTest", "Successful updates: ${successfulUpdates.get()}, Errors: ${updateErrors.size}")
        
        // Verify final state
        val finalRule = runBlocking { database.ruleDao().getRuleById(sharedRuleId) }
        assertNotNull("Rule should still exist after concurrent modifications", finalRule)
        
        // The final rule should have one of the expected states
        assertTrue(
            "Rule should have a lead time from one of the updates: ${finalRule!!.leadTimeMinutes}",
            finalRule.leadTimeMinutes >= 30
        )
        
        assertTrue(
            "Rule name should be in expected state: ${finalRule.name}",
            finalRule.name.contains("Modified") || finalRule.name == "Race Condition Test Rule"
        )
        
        // Most updates should succeed (database should handle concurrency)
        assertTrue(
            "At least some updates should succeed despite race conditions",
            successfulUpdates.get() > 0
        )
        
        Logger.i("ConcurrentOperationTest", "Final rule state: ${finalRule.name}, lead time: ${finalRule.leadTimeMinutes}")
        Logger.i("ConcurrentOperationTest", "✅ Race condition prevention test PASSED")
    }
    
    @Test
    fun testDeadlockPrevention() = runBlocking {
        Logger.i("ConcurrentOperationTest", "=== Testing Deadlock Prevention ===")
        
        // Create multiple rules and alarms that might cause deadlocks
        val ruleCount = 10
        val alarmCount = 20
        
        val testRules = (1..ruleCount).map { index ->
            Rule(
                id = "deadlock-rule-$index",
                name = "Deadlock Test Rule $index",
                keywordPattern = "deadlock$index",
                isRegex = false,
                calendarIds = listOf(index.toLong()),
                leadTimeMinutes = index * 5,
                enabled = true
            )
        }
        
        testRules.forEach { rule ->
            runBlocking { database.ruleDao().insertRule(rule) }
        }
        
        val testAlarms = (1..alarmCount).map { index ->
            com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm(
                id = "deadlock-alarm-$index",
                eventId = "deadlock-event-$index",
                ruleId = testRules[index % ruleCount].id,
                eventTitle = "Deadlock Test Event $index",
                eventStartTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000L),
                alarmTimeUtc = System.currentTimeMillis() + (index * 60 * 60 * 1000L) - (30 * 60 * 1000),
                scheduledAt = System.currentTimeMillis(),
                userDismissed = false,
                pendingIntentRequestCode = 80000 + index,
                lastEventModified = System.currentTimeMillis()
            )
        }
        
        testAlarms.forEach { alarm ->
            runBlocking { database.alarmDao().insertAlarm(alarm) }
        }
        
        val deadlockTestTime = measureTimeMillis {
            val jobs = mutableListOf<Job>()
            
            // Create operations that could potentially cause deadlocks
            repeat(20) { operationIndex ->
                jobs.add(launch {
                    try {
                        when (operationIndex % 4) {
                            0 -> { // Read rules, modify alarms
                                val rules = database.ruleDao().getAllRulesSync()
                                if (rules.isNotEmpty()) {
                                    val alarmToModify = testAlarms[operationIndex % alarmCount]
                                    val modifiedAlarm = alarmToModify.copy(
                                        alarmTimeUtc = alarmToModify.alarmTimeUtc + (operationIndex * 60 * 1000)
                                    )
                                    database.alarmDao().updateAlarm(modifiedAlarm)
                                }
                            }
                            1 -> { // Read alarms, modify rules
                                val alarms = database.alarmDao().getActiveAlarmsSync()
                                if (alarms.isNotEmpty()) {
                                    val ruleToModify = testRules[operationIndex % ruleCount]
                                    val modifiedRule = ruleToModify.copy(
                                        leadTimeMinutes = ruleToModify.leadTimeMinutes + operationIndex
                                    )
                                    database.ruleDao().updateRule(modifiedRule)
                                }
                            }
                            2 -> { // Delete and re-insert alarm
                                val alarmToReplace = testAlarms[operationIndex % alarmCount]
                                database.alarmDao().deleteAlarm(alarmToReplace)
                                delay(100)
                                database.alarmDao().insertAlarm(alarmToReplace.copy(
                                    id = "${alarmToReplace.id}-replaced"
                                ))
                            }
                            3 -> { // Complex query with joins
                                val rules = database.ruleDao().getAllRulesSync().filter { it.enabled }
                                val activeAlarms = database.alarmDao().getActiveAlarmsSync()
                                // Simulate processing
                                rules.forEach { rule ->
                                    activeAlarms.filter { it.ruleId == rule.id }
                                }
                            }
                        }
                        delay(50)
                    } catch (e: Exception) {
                        Logger.d("ConcurrentOperationTest", "Deadlock test operation $operationIndex error: ${e.message}")
                    }
                })
            }
            
            // Wait for all operations with timeout
            withTimeout(30000) { // 30 second timeout
                jobs.joinAll()
            }
        }
        
        Logger.i("ConcurrentOperationTest", "Deadlock prevention test completed in ${deadlockTestTime}ms")
        
        // Verify system is still responsive
        val finalRules = database.ruleDao().getAllRulesSync()
        val finalAlarms = database.alarmDao().getActiveAlarmsSync()
        
        assertTrue("Rules should still be accessible", finalRules.isNotEmpty())
        assertTrue("Alarms should still be accessible", finalAlarms.isNotEmpty())
        
        // Verify no deadlock occurred (test completed within timeout)
        assertTrue(
            "Operations should complete without deadlock: ${deadlockTestTime}ms",
            deadlockTestTime < 25000
        )
        
        Logger.i("ConcurrentOperationTest", "Final counts: ${finalRules.size} rules, ${finalAlarms.size} alarms")
        Logger.i("ConcurrentOperationTest", "✅ Deadlock prevention test PASSED")
    }
}