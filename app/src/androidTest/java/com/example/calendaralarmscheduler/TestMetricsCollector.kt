package com.example.calendaralarmscheduler

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Comprehensive metrics collector for E2E testing.
 * Tracks memory usage, performance metrics, logs, and system statistics.
 */
class TestMetricsCollector {
    
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    // Test session data
    private val testStartTime = System.currentTimeMillis()
    private val testSessionId = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    private val baselineMemory = mutableMapOf<String, Long>()
    private val performanceMetrics = mutableListOf<PerformanceMetric>()
    private val logEntries = mutableListOf<LogEntry>()
    
    data class PerformanceMetric(
        val timestamp: Long,
        val operation: String,
        val duration: Long,
        val memoryUsage: Long,
        val heapSize: Long,
        val heapFree: Long
    )
    
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    )
    
    data class MemorySnapshot(
        val timestamp: Long,
        val totalMemory: Long,
        val freeMemory: Long,
        val usedMemory: Long,
        val heapSize: Long,
        val heapUsed: Long,
        val heapFree: Long,
        val nativeHeapSize: Long,
        val nativeHeapUsed: Long,
        val nativeHeapFree: Long
    )
    
    /**
     * Capture baseline system metrics before test execution
     */
    fun captureBaseline(): MemorySnapshot {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val baseline = MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            totalMemory = memInfo.totalMem,
            freeMemory = memInfo.availMem,
            usedMemory = memInfo.totalMem - memInfo.availMem,
            heapSize = Runtime.getRuntime().totalMemory(),
            heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            heapFree = Runtime.getRuntime().freeMemory(),
            nativeHeapSize = Debug.getNativeHeapSize(),
            nativeHeapUsed = Debug.getNativeHeapAllocatedSize(),
            nativeHeapFree = Debug.getNativeHeapFreeSize()
        )
        
        baselineMemory["totalMemory"] = baseline.totalMemory
        baselineMemory["freeMemory"] = baseline.freeMemory
        baselineMemory["heapSize"] = baseline.heapSize
        baselineMemory["nativeHeapSize"] = baseline.nativeHeapSize
        
        Log.i("TestMetrics_Baseline", "Captured baseline: Total=${baseline.totalMemory / 1024 / 1024}MB, " +
                "Free=${baseline.freeMemory / 1024 / 1024}MB, " +
                "Heap=${baseline.heapSize / 1024 / 1024}MB")
        
        return baseline
    }
    
    /**
     * Capture current memory snapshot
     */
    fun captureMemorySnapshot(): MemorySnapshot {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        return MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            totalMemory = memInfo.totalMem,
            freeMemory = memInfo.availMem,
            usedMemory = memInfo.totalMem - memInfo.availMem,
            heapSize = Runtime.getRuntime().totalMemory(),
            heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
            heapFree = Runtime.getRuntime().freeMemory(),
            nativeHeapSize = Debug.getNativeHeapSize(),
            nativeHeapUsed = Debug.getNativeHeapAllocatedSize(),
            nativeHeapFree = Debug.getNativeHeapFreeSize()
        )
    }
    
    /**
     * Time an operation and record performance metrics
     */
    fun <T> measureOperation(operationName: String, operation: () -> T): T {
        val startTime = System.currentTimeMillis()
        val startSnapshot = captureMemorySnapshot()
        
        val result = operation()
        
        val endTime = System.currentTimeMillis()
        val endSnapshot = captureMemorySnapshot()
        val duration = endTime - startTime
        
        val metric = PerformanceMetric(
            timestamp = startTime,
            operation = operationName,
            duration = duration,
            memoryUsage = endSnapshot.usedMemory - startSnapshot.usedMemory,
            heapSize = endSnapshot.heapSize,
            heapFree = endSnapshot.heapFree
        )
        
        performanceMetrics.add(metric)
        
        Log.i("TestMetrics_Performance", "$operationName completed in ${duration}ms, " +
                "memory delta: ${metric.memoryUsage / 1024 / 1024}MB")
        
        return result
    }
    
    /**
     * Collect application logs with filtering
     */
    fun collectAppLogs(maxEntries: Int = 1000): List<LogEntry> {
        val logs = mutableListOf<LogEntry>()
        
        try {
            val process = Runtime.getRuntime().exec(arrayOf(
                "logcat", "-d", "-t", maxEntries.toString(), 
                "-s", "CalendarAlarmScheduler:*"
            ))
            
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotEmpty()) {
                        val entry = parseLogLine(line)
                        if (entry != null) {
                            logs.add(entry)
                            logEntries.add(entry)
                        }
                    }
                }
            }
            
            process.waitFor()
        } catch (e: IOException) {
            Log.w("TestMetrics_Logs", "Failed to collect logs", e)
        }
        
        return logs
    }
    
    private fun parseLogLine(line: String): LogEntry? {
        // Simple log parsing - adjust regex as needed for your log format
        val regex = """(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\w)/(\w+)\s*\(\s*\d+\):\s*(.*)""".toRegex()
        val match = regex.find(line)
        
        return match?.let {
            val (timestamp, level, tag, message) = it.destructured
            LogEntry(
                timestamp = System.currentTimeMillis(), // Simplified timestamp
                level = level,
                tag = tag,
                message = message.trim()
            )
        }
    }
    
    /**
     * Check for memory leaks by comparing current usage to baseline
     */
    fun detectMemoryLeaks(): MemoryLeakReport {
        val currentSnapshot = captureMemorySnapshot()
        val baselineTotalMemory = baselineMemory["totalMemory"] ?: 0
        val baselineHeapSize = baselineMemory["heapSize"] ?: 0
        
        val memoryGrowth = currentSnapshot.usedMemory - (baselineTotalMemory - (baselineMemory["freeMemory"] ?: 0))
        val heapGrowth = currentSnapshot.heapUsed - (baselineHeapSize - (Runtime.getRuntime().freeMemory()))
        
        // Thresholds for memory leak detection (adjust as needed)
        val memoryLeakThreshold = 50 * 1024 * 1024 // 50MB
        val heapLeakThreshold = 20 * 1024 * 1024   // 20MB
        
        val hasMemoryLeak = memoryGrowth > memoryLeakThreshold
        val hasHeapLeak = heapGrowth > heapLeakThreshold
        
        return MemoryLeakReport(
            hasLeak = hasMemoryLeak || hasHeapLeak,
            memoryGrowth = memoryGrowth,
            heapGrowth = heapGrowth,
            currentSnapshot = currentSnapshot,
            details = buildString {
                append("Memory growth: ${memoryGrowth / 1024 / 1024}MB\n")
                append("Heap growth: ${heapGrowth / 1024 / 1024}MB\n")
                append("Current heap usage: ${currentSnapshot.heapUsed / 1024 / 1024}MB")
            }
        )
    }
    
    data class MemoryLeakReport(
        val hasLeak: Boolean,
        val memoryGrowth: Long,
        val heapGrowth: Long,
        val currentSnapshot: MemorySnapshot,
        val details: String
    )
    
    /**
     * Generate comprehensive test report
     */
    fun generateTestReport(): TestReport {
        val finalSnapshot = captureMemorySnapshot()
        val finalLogs = collectAppLogs(500)
        val memoryLeakReport = detectMemoryLeaks()
        
        return TestReport(
            sessionId = testSessionId,
            testDuration = System.currentTimeMillis() - testStartTime,
            finalMemorySnapshot = finalSnapshot,
            performanceMetrics = performanceMetrics.toList(),
            logEntries = logEntries.takeLast(100),
            memoryLeakReport = memoryLeakReport,
            summary = buildSummary(finalSnapshot, memoryLeakReport)
        )
    }
    
    private fun buildSummary(snapshot: MemorySnapshot, leakReport: MemoryLeakReport): String {
        return buildString {
            appendLine("=== Test Session Summary ===")
            appendLine("Session ID: $testSessionId")
            appendLine("Duration: ${System.currentTimeMillis() - testStartTime}ms")
            appendLine()
            appendLine("Memory Status:")
            appendLine("  Total Memory: ${snapshot.totalMemory / 1024 / 1024}MB")
            appendLine("  Used Memory: ${snapshot.usedMemory / 1024 / 1024}MB")
            appendLine("  Heap Usage: ${snapshot.heapUsed / 1024 / 1024}MB")
            appendLine("  Memory Leaks: ${if (leakReport.hasLeak) "DETECTED" else "None"}")
            appendLine()
            appendLine("Performance:")
            appendLine("  Operations Measured: ${performanceMetrics.size}")
            appendLine("  Average Operation Time: ${performanceMetrics.map { it.duration }.average()}ms")
            appendLine()
            appendLine("Logs Collected: ${logEntries.size}")
        }
    }
    
    data class TestReport(
        val sessionId: String,
        val testDuration: Long,
        val finalMemorySnapshot: MemorySnapshot,
        val performanceMetrics: List<PerformanceMetric>,
        val logEntries: List<LogEntry>,
        val memoryLeakReport: MemoryLeakReport,
        val summary: String
    )
}