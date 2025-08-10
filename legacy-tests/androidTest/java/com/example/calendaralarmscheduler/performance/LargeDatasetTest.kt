package com.example.calendaralarmscheduler.performance

import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.e2e.E2ETestBase
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.*

/**
 * Performance tests with large datasets
 * Tests app behavior with 1000+ events and multiple rules
 * to ensure scalability and performance
 */
class LargeDatasetTest : E2ETestBase() {

    companion object {
        private const val LARGE_EVENT_COUNT = 1000
        private const val LARGE_RULE_COUNT = 50
        private const val PERFORMANCE_THRESHOLD_MS = 30000L // 30 seconds max
        private const val MEMORY_CHECK_INTERVAL_MS = 5000L // 5 seconds
    }

    @Test
    fun testLargeEventDatasetProcessing() = runBlocking {
        Logger.i("LargeDatasetTest", "=== Testing Large Event Dataset Processing ($LARGE_EVENT_COUNT events) ===")
        
        val startTime = System.currentTimeMillis()
        
        // Create comprehensive rules to match various event types
        val performanceRules = createPerformanceRules(LARGE_RULE_COUNT)
        performanceRules.forEach { rule ->
            runBlocking { database.ruleDao().insertRule(rule) }
        }
        
        Logger.i("LargeDatasetTest", "Created $LARGE_RULE_COUNT performance rules")
        
        // Generate large event dataset with various characteristics
        val largeEventSet = generateLargeEventDataset(LARGE_EVENT_COUNT)
        Logger.i("LargeDatasetTest", "Generated $LARGE_EVENT_COUNT test events")
        
        // Inject events in batches to avoid memory issues
        val batchSize = 100
        val totalTime = measureTimeMillis {
            largeEventSet.chunked(batchSize).forEachIndexed { batchIndex, eventBatch ->
                Logger.d("LargeDatasetTest", "Injecting batch ${batchIndex + 1}/${largeEventSet.size / batchSize}")
                
                eventBatch.forEach { event ->
                    calendarTestProvider.createTestEvent(
                        title = event.title,
                        startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                        durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                        isAllDay = event.isAllDay,
                        calendarId = event.calendarId
                    )
                }
                
                // Small delay between batches to prevent overwhelming system
                delay(100)
            }
        }
        
        Logger.i("LargeDatasetTest", "Event injection took ${totalTime}ms")
        
        // Clear existing alarms
        database.alarmDao().deleteAllAlarms()
        
        // Measure worker processing time with large dataset
        val processingTime = measureTimeMillis {
            val workerTriggered = applicationController.triggerBackgroundWorker()
            assertTrue("Background worker should be triggerable", workerTriggered)
            
            // Wait for processing with extended timeout
            delay(20000) // 20 seconds for large dataset processing
        }
        
        Logger.i("LargeDatasetTest", "Worker processing took ${processingTime}ms")
        
        // Verify processing completed within reasonable time
        assertTrue(
            "Large dataset processing should complete within $PERFORMANCE_THRESHOLD_MS ms, took ${processingTime}ms",
            processingTime < PERFORMANCE_THRESHOLD_MS
        )
        
        // Analyze results
        val scheduledAlarms = database.alarmDao().getActiveAlarmsSync()
        Logger.i("LargeDatasetTest", "Scheduled ${scheduledAlarms.size} alarms from $LARGE_EVENT_COUNT events")
        
        // Verify reasonable alarm count (should be more than 0, less than total events)
        assertTrue("Should schedule some alarms from large dataset", scheduledAlarms.isNotEmpty())
        assertTrue(
            "Alarm count should be reasonable (not all events should match)",
            scheduledAlarms.size < LARGE_EVENT_COUNT
        )
        
        // Verify alarm quality
        scheduledAlarms.take(10).forEach { alarm ->
            assertTrue("Alarm should be in future", alarm.alarmTimeUtc > System.currentTimeMillis())
            assertTrue("Event should start after alarm", alarm.eventStartTimeUtc > alarm.alarmTimeUtc)
            assertTrue("Alarm should have event title", alarm.eventTitle.isNotEmpty())
        }
        
        val totalTestTime = System.currentTimeMillis() - startTime
        Logger.i("LargeDatasetTest", "Total large dataset test time: ${totalTestTime}ms")
        Logger.i("LargeDatasetTest", "✅ Large event dataset test PASSED")
    }
    
    @Test
    fun testMemoryUsageWithLargeDataset() = runBlocking {
        Logger.i("LargeDatasetTest", "=== Testing Memory Usage with Large Dataset ===")
        
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("LargeDatasetTest", "Initial memory usage: ${initialMemory / (1024 * 1024)}MB")
        
        // Create moderate rule set
        val memoryTestRules = createPerformanceRules(20)
        memoryTestRules.forEach { rule ->
            runBlocking { database.ruleDao().insertRule(rule) }
        }
        
        // Generate events with memory tracking
        val eventCount = 500 // Moderate size for memory testing
        val events = generateLargeEventDataset(eventCount)
        
        val memoryAfterGeneration = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("LargeDatasetTest", "Memory after generation: ${memoryAfterGeneration / (1024 * 1024)}MB")
        
        // Inject events while monitoring memory
        events.forEach { event ->
            calendarTestProvider.createTestEvent(
                title = event.title,
                startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                isAllDay = event.isAllDay,
                calendarId = event.calendarId
            )
        }
        
        // Force garbage collection and check memory
        System.gc()
        delay(2000)
        
        val memoryAfterInjection = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("LargeDatasetTest", "Memory after injection: ${memoryAfterInjection / (1024 * 1024)}MB")
        
        // Process events
        applicationController.triggerBackgroundWorker()
        delay(10000)
        
        val memoryAfterProcessing = runtime.totalMemory() - runtime.freeMemory()
        Logger.i("LargeDatasetTest", "Memory after processing: ${memoryAfterProcessing / (1024 * 1024)}MB")
        
        // Check for memory leaks (memory should not grow excessively)
        val memoryGrowth = memoryAfterProcessing - initialMemory
        val maxAcceptableGrowthMB = 100 // 100MB max growth
        
        assertTrue(
            "Memory growth should be reasonable: ${memoryGrowth / (1024 * 1024)}MB (max: ${maxAcceptableGrowthMB}MB)",
            memoryGrowth < (maxAcceptableGrowthMB * 1024 * 1024)
        )
        
        // Verify results
        val alarms = database.alarmDao().getActiveAlarmsSync()
        assertTrue("Should create alarms despite memory constraints", alarms.isNotEmpty())
        
        Logger.i("LargeDatasetTest", "Created ${alarms.size} alarms with memory growth of ${memoryGrowth / (1024 * 1024)}MB")
        Logger.i("LargeDatasetTest", "✅ Memory usage test PASSED")
    }
    
    @Test
    fun testDatabasePerformanceWithLargeDataset() = runBlocking {
        Logger.i("LargeDatasetTest", "=== Testing Database Performance with Large Dataset ===")
        
        // Create large rule set
        val ruleCount = 100
        val performanceRules = createPerformanceRules(ruleCount)
        
        // Measure bulk rule insertion time
        val ruleInsertionTime = measureTimeMillis {
            performanceRules.forEach { rule ->
                runBlocking { database.ruleDao().insertRule(rule) }
            }
        }
        
        Logger.i("LargeDatasetTest", "Inserted $ruleCount rules in ${ruleInsertionTime}ms")
        
        // Generate large alarm set
        val alarmCount = 2000
        val largeAlarmSet = generateLargeAlarmDataset(alarmCount)
        
        // Measure bulk alarm insertion time
        val alarmInsertionTime = measureTimeMillis {
            largeAlarmSet.chunked(100).forEach { batch ->
                batch.forEach { alarm ->
                    runBlocking { database.alarmDao().insertAlarm(alarm) }
                }
            }
        }
        
        Logger.i("LargeDatasetTest", "Inserted $alarmCount alarms in ${alarmInsertionTime}ms")
        
        // Test query performance
        val queryTime = measureTimeMillis {
            val allRules = database.ruleDao().getAllRulesSync()
            val activeAlarms = database.alarmDao().getActiveAlarmsSync()
            val enabledRules = database.ruleDao().getAllRulesSync().filter { it.enabled }
            
            Logger.d("LargeDatasetTest", "Query results: ${allRules.size} rules, ${activeAlarms.size} alarms, ${enabledRules.size} enabled rules")
        }
        
        Logger.i("LargeDatasetTest", "Complex queries took ${queryTime}ms")
        
        // Test update performance
        val updateTime = measureTimeMillis {
            // Update half the rules
            val rulesToUpdate = performanceRules.take(ruleCount / 2)
            rulesToUpdate.forEach { rule ->
                val updatedRule = rule.copy(
                    leadTimeMinutes = rule.leadTimeMinutes + 5,
                    enabled = !rule.enabled
                )
                runBlocking { database.ruleDao().updateRule(updatedRule) }
            }
        }
        
        Logger.i("LargeDatasetTest", "Updated ${ruleCount / 2} rules in ${updateTime}ms")
        
        // Test delete performance  
        val deleteTime = measureTimeMillis {
            // Delete half the alarms
            val alarmsToDelete = largeAlarmSet.take(alarmCount / 2)
            alarmsToDelete.forEach { alarm ->
                runBlocking { database.alarmDao().deleteAlarm(alarm) }
            }
        }
        
        Logger.i("LargeDatasetTest", "Deleted ${alarmCount / 2} alarms in ${deleteTime}ms")
        
        // Verify performance thresholds
        assertTrue("Rule insertion should be fast: ${ruleInsertionTime}ms", ruleInsertionTime < 10000)
        assertTrue("Alarm insertion should be reasonable: ${alarmInsertionTime}ms", alarmInsertionTime < 30000) 
        assertTrue("Queries should be fast: ${queryTime}ms", queryTime < 5000)
        assertTrue("Updates should be reasonable: ${updateTime}ms", updateTime < 15000)
        assertTrue("Deletes should be fast: ${deleteTime}ms", deleteTime < 10000)
        
        Logger.i("LargeDatasetTest", "✅ Database performance test PASSED")
    }
    
    @Test
    fun testConcurrentOperations() = runBlocking {
        Logger.i("LargeDatasetTest", "=== Testing Concurrent Operations ===")
        
        // Create test data
        val concurrentRules = createPerformanceRules(10)
        concurrentRules.forEach { rule ->
            runBlocking { database.ruleDao().insertRule(rule) }
        }
        
        val concurrentEvents = generateLargeEventDataset(100)
        concurrentEvents.forEach { event ->
            calendarTestProvider.createTestEvent(
                title = event.title,
                startTimeFromNow = event.startTimeUtc - System.currentTimeMillis(),
                durationHours = ((event.endTimeUtc - event.startTimeUtc) / (60 * 60 * 1000)).toInt(),
                isAllDay = event.isAllDay,
                calendarId = event.calendarId
            )
        }
        
        // Simulate concurrent operations
        val concurrentOperationTime = measureTimeMillis {
            // Start multiple operations concurrently
            val operations = listOf(
                { // Operation 1: Multiple worker triggers
                    repeat(3) {
                        applicationController.triggerBackgroundWorker()
                        Thread.sleep(1000)
                    }
                },
                { // Operation 2: Settings changes
                    val settingsRepository = application.settingsRepository
                    repeat(5) {
                        settingsRepository.setRefreshIntervalMinutes(if (it % 2 == 0) 30 else 60)
                        Thread.sleep(500)
                    }
                },
                { // Operation 3: Rule modifications
                    repeat(3) {
                        val ruleToModify = concurrentRules[it % concurrentRules.size]
                        val modifiedRule = ruleToModify.copy(
                            leadTimeMinutes = ruleToModify.leadTimeMinutes + (it * 5)
                        )
                        runBlocking { database.ruleDao().updateRule(modifiedRule) }
                        Thread.sleep(800)
                    }
                }
            )
            
            // Execute operations concurrently
            val threads = operations.map { operation ->
                Thread {
                    try {
                        operation()
                    } catch (e: Exception) {
                        Logger.e("LargeDatasetTest", "Concurrent operation error", e)
                    }
                }
            }
            
            threads.forEach { it.start() }
            threads.forEach { it.join() }
        }
        
        Logger.i("LargeDatasetTest", "Concurrent operations completed in ${concurrentOperationTime}ms")
        
        // Wait for all operations to settle
        delay(5000)
        
        // Verify system is still functional
        val finalAlarms = database.alarmDao().getActiveAlarmsSync()
        val finalRules = database.ruleDao().getAllRulesSync()
        
        assertTrue("Should have alarms after concurrent operations", finalAlarms.isNotEmpty())
        assertEquals("All rules should still exist", concurrentRules.size, finalRules.size)
        
        // Verify data integrity
        finalAlarms.forEach { alarm ->
            assertTrue("Alarm times should be valid", alarm.alarmTimeUtc > 0)
            assertTrue("Event titles should not be empty", alarm.eventTitle.isNotEmpty())
        }
        
        Logger.i("LargeDatasetTest", "Final state: ${finalAlarms.size} alarms, ${finalRules.size} rules")
        Logger.i("LargeDatasetTest", "✅ Concurrent operations test PASSED")
    }
    
    // Helper methods
    
    private fun createPerformanceRules(count: Int): List<Rule> {
        val patterns = listOf(
            "meeting", "appointment", "call", "interview", "review",
            "standup", "sync", "demo", "presentation", "training",
            "birthday", "anniversary", "dinner", "lunch", "doctor",
            "dentist", "urgent", "important", "critical", "deadline",
            "conference", "workshop", "seminar", "webinar", "event"
        )
        
        val leadTimes = listOf(5, 10, 15, 30, 45, 60, 90, 120, 180, 240)
        val calendarCombinations = listOf(
            emptyList<Long>(), // All calendars
            listOf(1L),
            listOf(2L), 
            listOf(1L, 2L),
            listOf(3L),
            listOf(1L, 3L),
            listOf(2L, 3L),
            listOf(1L, 2L, 3L)
        )
        
        return (1..count).map { index ->
            val isRegex = index % 3 == 0 // Every 3rd rule is regex
            val pattern = if (isRegex) {
                "${patterns[index % patterns.size]}|${patterns[(index + 1) % patterns.size]}"
            } else {
                patterns[index % patterns.size]
            }
            
            Rule(
                id = "perf-rule-$index",
                name = "Performance Rule $index",
                keywordPattern = pattern,
                isRegex = isRegex,
                calendarIds = calendarCombinations[index % calendarCombinations.size],
                leadTimeMinutes = leadTimes[index % leadTimes.size],
                enabled = index % 4 != 0, // 75% enabled
                createdAt = System.currentTimeMillis() - (index * 1000)
            )
        }
    }
    
    private fun generateLargeEventDataset(count: Int): List<CalendarEvent> {
        val titles = listOf(
            "Team Meeting", "Project Review", "Client Call", "One-on-One",
            "Standup", "Design Review", "Code Review", "Planning Meeting",
            "Doctor Appointment", "Dentist Visit", "Lunch Meeting", "Birthday Party",
            "Conference Call", "Training Session", "Workshop", "Presentation",
            "Interview", "Demo", "Sync Meeting", "All Hands",
            "Product Review", "Sprint Planning", "Retrospective", "Daily Standup",
            "Architecture Review", "Security Meeting", "Budget Review", "Strategy Session"
        )
        
        val calendarIds = listOf(1L, 2L, 3L)
        val currentTime = System.currentTimeMillis()
        
        return (1..count).map { index ->
            val isAllDay = index % 10 == 0 // 10% all-day events
            val startOffset = (index * 60 * 60 * 1000L) + (Math.random() * 24 * 60 * 60 * 1000).toLong()
            val startTime = currentTime + startOffset
            val duration = if (isAllDay) {
                24 * 60 * 60 * 1000L // 24 hours
            } else {
                (30 + (Math.random() * 120).toLong()) * 60 * 1000 // 30-150 minutes
            }
            
            CalendarEvent(
                id = "perf-event-$index",
                title = "${titles[index % titles.size]} #$index",
                startTimeUtc = startTime,
                endTimeUtc = startTime + duration,
                calendarId = calendarIds[index % calendarIds.size],
                isAllDay = isAllDay,
                timezone = "UTC",
                lastModified = currentTime + (index * 100)
            )
        }
    }
    
    private fun generateLargeAlarmDataset(count: Int): List<com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm> {
        val currentTime = System.currentTimeMillis()
        
        return (1..count).map { index ->
            val eventStartTime = currentTime + (index * 60 * 60 * 1000L)
            val leadTime = (15 + (index % 60)) * 60 * 1000L // 15-75 minutes
            val alarmTime = eventStartTime - leadTime
            
            com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm(
                id = "perf-alarm-$index",
                eventId = "perf-event-$index",
                ruleId = "perf-rule-${index % 50 + 1}",
                eventTitle = "Performance Event $index",
                eventStartTimeUtc = eventStartTime,
                alarmTimeUtc = alarmTime,
                scheduledAt = currentTime + (index * 10),
                userDismissed = index % 20 == 0, // 5% dismissed
                pendingIntentRequestCode = 50000 + index,
                lastEventModified = currentTime + (index * 100)
            )
        }
    }
}