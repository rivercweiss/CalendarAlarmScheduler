package com.example.calendaralarmscheduler.domain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager
) {
    
    companion object {
        private const val TAG = "AlarmScheduler"
        private const val ALARM_RECEIVER_ACTION = "com.example.calendaralarmscheduler.ALARM_TRIGGER"
        
        // Intent extras
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_EVENT_TITLE = "EVENT_TITLE"
        const val EXTRA_EVENT_START_TIME = "EVENT_START_TIME"
        const val EXTRA_RULE_ID = "RULE_ID"
    }
    
    data class ScheduleResult(
        val success: Boolean,
        val message: String,
        val alarm: ScheduledAlarm? = null
    )
    
    suspend fun scheduleAlarm(alarm: ScheduledAlarm): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            if (alarm.isInPast()) {
                return@withContext ScheduleResult(
                    success = false,
                    message = "Cannot schedule alarm for past time: ${alarm.getLocalAlarmTime()}"
                )
            }
            
            // Check permissions before creating PendingIntent to avoid exceptions in tests
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                return@withContext ScheduleResult(
                    success = false,
                    message = "Exact alarm permission not granted"
                )
            }
            
            // Check for request code collision before scheduling
            val collisionCheckResult = checkAndResolveRequestCodeCollision(alarm)
            val finalAlarm = collisionCheckResult.resolvedAlarm
            
            if (!collisionCheckResult.success) {
                return@withContext ScheduleResult(
                    success = false,
                    message = collisionCheckResult.message
                )
            }
            
            if (collisionCheckResult.wasCollisionResolved) {
                Log.w(TAG, "⚠️ Resolved request code collision for ${finalAlarm.eventTitle}: ${collisionCheckResult.message}")
            }
            
            val intent = createAlarmIntent(finalAlarm)
            val pendingIntent = createPendingIntent(finalAlarm, intent)
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                finalAlarm.alarmTimeUtc,
                pendingIntent
            )
            
            // Verify the alarm was actually scheduled by checking if PendingIntent still exists
            val verificationIntent = createAlarmIntent(finalAlarm)
            val verificationPendingIntent = PendingIntent.getBroadcast(
                context,
                finalAlarm.pendingIntentRequestCode,
                verificationIntent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (verificationPendingIntent != null) {
                Log.d(TAG, "✓ Verified alarm scheduled for ${finalAlarm.eventTitle} at ${finalAlarm.getLocalAlarmTime()} (RequestCode: ${finalAlarm.pendingIntentRequestCode})")
                return@withContext ScheduleResult(
                    success = true,
                    message = "Alarm scheduled and verified successfully",
                    alarm = finalAlarm
                )
            } else {
                Log.w(TAG, "⚠️ Alarm appeared to schedule but verification failed for ${finalAlarm.eventTitle}")
                return@withContext ScheduleResult(
                    success = false,
                    message = "Alarm scheduling verification failed - PendingIntent not found after scheduling"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for ${alarm.eventTitle}", e)
            
            // Attempt retry with RetryManager for retriable exceptions
            if (com.example.calendaralarmscheduler.utils.RetryManager.isRetriableException(e)) {
                val retryResult = com.example.calendaralarmscheduler.utils.RetryManager.withRetry(
                    operation = "schedule_alarm_${alarm.id}",
                    maxRetries = 2,
                    onRetry = { attempt, error ->
                        Log.i(TAG, "Retrying alarm scheduling for ${alarm.eventTitle} (attempt $attempt): ${error.message}")
                    }
                ) {
                    scheduleAlarmInternal(alarm)
                }
                
                if (retryResult.isSuccess) {
                    return@withContext ScheduleResult(
                        success = true,
                        message = "Alarm scheduled successfully after retry",
                        alarm = alarm
                    )
                } else {
                    return@withContext ScheduleResult(
                        success = false,
                        message = "Failed to schedule alarm after retries: ${retryResult.exceptionOrNull()?.message}"
                    )
                }
            } else {
                return@withContext ScheduleResult(
                    success = false,
                    message = "Failed to schedule alarm: ${e.message}"
                )
            }
        }
    }
    
    suspend fun cancelAlarm(alarm: ScheduledAlarm): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            val intent = createAlarmIntent(alarm)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.pendingIntentRequestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                
                Log.d(TAG, "Cancelled alarm for ${alarm.eventTitle}")
                
                return@withContext ScheduleResult(
                    success = true,
                    message = "Alarm cancelled successfully"
                )
            } else {
                return@withContext ScheduleResult(
                    success = true,
                    message = "Alarm was not scheduled (no pending intent found)"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm for ${alarm.eventTitle}", e)
            return@withContext ScheduleResult(
                success = false,
                message = "Failed to cancel alarm: ${e.message}"
            )
        }
    }
    
    suspend fun rescheduleAlarm(oldAlarm: ScheduledAlarm, newAlarm: ScheduledAlarm): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            // Cancel the old alarm first
            val cancelResult = cancelAlarm(oldAlarm)
            if (!cancelResult.success) {
                Log.w(TAG, "Warning: Could not cancel old alarm: ${cancelResult.message}")
            }
            
            // Schedule the new alarm
            val scheduleResult = scheduleAlarm(newAlarm)
            
            if (scheduleResult.success) {
                Log.d(TAG, "Rescheduled alarm for ${newAlarm.eventTitle}")
                return@withContext ScheduleResult(
                    success = true,
                    message = "Alarm rescheduled successfully",
                    alarm = newAlarm
                )
            } else {
                return@withContext scheduleResult
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reschedule alarm", e)
            return@withContext ScheduleResult(
                success = false,
                message = "Failed to reschedule alarm: ${e.message}"
            )
        }
    }
    
    suspend fun scheduleMultipleAlarms(alarms: List<ScheduledAlarm>): List<ScheduleResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScheduleResult>()
        
        for (alarm in alarms) {
            results.add(scheduleAlarm(alarm))
        }
        
        val successCount = results.count { it.success }
        Log.d(TAG, "Scheduled $successCount out of ${alarms.size} alarms")
        
        return@withContext results
    }
    
    suspend fun cancelMultipleAlarms(alarms: List<ScheduledAlarm>): List<ScheduleResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScheduleResult>()
        
        for (alarm in alarms) {
            results.add(cancelAlarm(alarm))
        }
        
        val successCount = results.count { it.success }
        Log.d(TAG, "Cancelled $successCount out of ${alarms.size} alarms")
        
        return@withContext results
    }
    
    fun isAlarmScheduled(alarm: ScheduledAlarm): Boolean {
        return try {
            val intent = createAlarmIntent(alarm)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.pendingIntentRequestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            pendingIntent != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if alarm is scheduled", e)
            false
        }
    }
    
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    private fun createAlarmIntent(alarm: ScheduledAlarm): Intent {
        return Intent(ALARM_RECEIVER_ACTION).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_EVENT_TITLE, alarm.eventTitle)
            putExtra(EXTRA_EVENT_START_TIME, alarm.eventStartTimeUtc)
            putExtra(EXTRA_RULE_ID, alarm.ruleId)
        }
    }
    
    private fun createPendingIntent(alarm: ScheduledAlarm, intent: Intent): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            alarm.pendingIntentRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    suspend fun validateAndScheduleAlarms(
        alarms: List<ScheduledAlarm>,
        onValidationError: (ScheduledAlarm, String) -> Unit = { _, _ -> }
    ): List<ScheduleResult> = withContext(Dispatchers.IO) {
        val validAlarms = alarms.filter { alarm ->
            when {
                alarm.isInPast() -> {
                    onValidationError(alarm, "Alarm time is in the past")
                    false
                }
                alarm.userDismissed -> {
                    onValidationError(alarm, "Alarm was dismissed by user")
                    false
                }
                else -> true
            }
        }
        
        return@withContext scheduleMultipleAlarms(validAlarms)
    }
    
    suspend fun cleanupPastAlarms(alarms: List<ScheduledAlarm>): List<ScheduleResult> = withContext(Dispatchers.IO) {
        val pastAlarms = alarms.filter { it.isInPast() }
        return@withContext cancelMultipleAlarms(pastAlarms)
    }
    
    suspend fun scheduleSnoozeAlarm(originalAlarmId: String, snoozeTimeUtc: Long): ScheduleResult = withContext(Dispatchers.IO) {
        try {
            // Create a temporary snooze alarm
            val snoozeAlarm = ScheduledAlarm(
                id = "${originalAlarmId}_snooze_${System.currentTimeMillis()}",
                eventId = "snooze_${originalAlarmId}",
                ruleId = "snooze_rule",
                eventTitle = "Snoozed Alarm",
                eventStartTimeUtc = snoozeTimeUtc,
                alarmTimeUtc = snoozeTimeUtc,
                scheduledAt = System.currentTimeMillis(),
                userDismissed = false,
                pendingIntentRequestCode = generateSnoozeRequestCode(originalAlarmId),
                lastEventModified = System.currentTimeMillis()
            )
            
            return@withContext scheduleAlarm(snoozeAlarm)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule snooze alarm for $originalAlarmId", e)
            return@withContext ScheduleResult(
                success = false,
                message = "Failed to schedule snooze alarm: ${e.message}"
            )
        }
    }
    
    private fun generateSnoozeRequestCode(originalAlarmId: String): Int {
        return "snooze_${originalAlarmId}".hashCode()
    }
    
    fun getNextAlarmTime(alarms: List<ScheduledAlarm>): Long? {
        return alarms
            .filter { it.isActive() }
            .minByOrNull { it.alarmTimeUtc }
            ?.alarmTimeUtc
    }
    
    /**
     * Check if an alarm is actually scheduled in the system (has active PendingIntent)
     * This helps detect user-dismissed alarms by comparing database state vs system state
     */
    fun isAlarmActiveInSystem(alarm: ScheduledAlarm): Boolean {
        return isAlarmScheduled(alarm)
    }
    
    /**
     * Check multiple alarms and return which ones are missing from the system
     * (indicating they were manually dismissed by the user)
     */
    suspend fun detectDismissedAlarms(alarms: List<ScheduledAlarm>): List<ScheduledAlarm> = withContext(Dispatchers.IO) {
        val dismissedAlarms = mutableListOf<ScheduledAlarm>()
        
        for (alarm in alarms) {
            // Only check non-dismissed, future alarms
            if (!alarm.userDismissed && !alarm.isInPast()) {
                if (!isAlarmActiveInSystem(alarm)) {
                    Log.d(TAG, "Detected dismissed alarm: ${alarm.eventTitle} (ID: ${alarm.id})")
                    dismissedAlarms.add(alarm)
                }
            }
        }
        
        Log.d(TAG, "Detected ${dismissedAlarms.size} dismissed alarms out of ${alarms.size} checked")
        return@withContext dismissedAlarms
    }
    
    /**
     * Monitor system alarm state and detect user dismissals
     * Returns a list of alarms that should be marked as dismissed in the database
     */
    suspend fun monitorSystemState(databaseAlarms: List<ScheduledAlarm>): SystemStateResult = withContext(Dispatchers.IO) {
        try {
            val activeAlarms = databaseAlarms.filter { !it.userDismissed && !it.isInPast() }
            val dismissedAlarms = detectDismissedAlarms(activeAlarms)
            
            return@withContext SystemStateResult(
                success = true,
                dismissedAlarms = dismissedAlarms,
                message = "System state monitored successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error monitoring system state", e)
            return@withContext SystemStateResult(
                success = false,
                dismissedAlarms = emptyList(),
                message = "Failed to monitor system state: ${e.message}"
            )
        }
    }
    
    data class SystemStateResult(
        val success: Boolean,
        val dismissedAlarms: List<ScheduledAlarm>,
        val message: String
    )
    
    /**
     * Result of collision check and resolution
     */
    data class CollisionCheckResult(
        val success: Boolean,
        val message: String,
        val resolvedAlarm: ScheduledAlarm,
        val wasCollisionResolved: Boolean = false
    )
    
    /**
     * Enhanced collision detection and resolution system.
     * Checks for request code collisions and resolves them using improved algorithms.
     */
    private suspend fun checkAndResolveRequestCodeCollision(alarm: ScheduledAlarm): CollisionCheckResult = withContext(Dispatchers.IO) {
        try {
            val originalRequestCode = alarm.pendingIntentRequestCode
            var currentAlarm = alarm
            var attempt = 0
            val maxAttempts = 20 // Increased attempts for better collision resolution
            val collisionMap = mutableMapOf<Int, String>() // Track what's using each request code
            
            while (attempt < maxAttempts) {
                // Check if a PendingIntent with this request code already exists
                val testIntent = createAlarmIntent(currentAlarm)
                val existingPendingIntent = PendingIntent.getBroadcast(
                    context,
                    currentAlarm.pendingIntentRequestCode,
                    testIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (existingPendingIntent == null) {
                    // No collision - request code is available
                    if (attempt == 0) {
                        // Original request code was fine
                        return@withContext CollisionCheckResult(
                            success = true,
                            message = "No collision detected",
                            resolvedAlarm = currentAlarm,
                            wasCollisionResolved = false
                        )
                    } else {
                        // We resolved a collision
                        Log.i(TAG, "✓ Resolved request code collision for ${alarm.eventTitle}: ${originalRequestCode} -> ${currentAlarm.pendingIntentRequestCode} (attempt $attempt)")
                        return@withContext CollisionCheckResult(
                            success = true,
                            message = "Collision resolved: changed request code from $originalRequestCode to ${currentAlarm.pendingIntentRequestCode}",
                            resolvedAlarm = currentAlarm,
                            wasCollisionResolved = true
                        )
                    }
                }
                
                // Collision detected - track what's colliding and try alternative request code
                attempt++
                collisionMap[currentAlarm.pendingIntentRequestCode] = "Unknown existing alarm"
                
                // Use improved collision resolution algorithm
                val alternativeRequestCode = generateImprovedAlternativeRequestCode(originalRequestCode, attempt, alarm.id)
                
                Log.w(TAG, "⚠️ Request code collision detected for ${alarm.eventTitle}: ${currentAlarm.pendingIntentRequestCode} -> trying $alternativeRequestCode (attempt $attempt)")
                
                // Create new alarm with alternative request code
                currentAlarm = currentAlarm.copy(pendingIntentRequestCode = alternativeRequestCode)
            }
            
            // Failed to resolve collision after max attempts
            Log.e(TAG, "❌ Failed to resolve request code collision for ${alarm.eventTitle} after $maxAttempts attempts")
            return@withContext CollisionCheckResult(
                success = false,
                message = "Failed to resolve request code collision after $maxAttempts attempts",
                resolvedAlarm = alarm,
                wasCollisionResolved = false
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking request code collision for ${alarm.eventTitle}", e)
            return@withContext CollisionCheckResult(
                success = false,
                message = "Error checking collision: ${e.message}",
                resolvedAlarm = alarm,
                wasCollisionResolved = false
            )
        }
    }
    
    /**
     * Improved alternative request code generation using multiple strategies
     */
    private fun generateImprovedAlternativeRequestCode(originalRequestCode: Int, attempt: Int, alarmId: String): Int {
        // Strategy 1: Use alarm ID hash with attempt multiplier (first few attempts)
        if (attempt <= 5) {
            val idHash = alarmId.hashCode()
            val combined = (idHash.toLong() * 31 + originalRequestCode.toLong() * attempt).toInt()
            return if (combined == 0) attempt + 1 else combined
        }
        
        // Strategy 2: Use timestamp-based generation (middle attempts)
        if (attempt <= 10) {
            val timestamp = System.currentTimeMillis()
            val timeHash = (timestamp % Int.MAX_VALUE).toInt()
            val combined = (timeHash * 17 + originalRequestCode + attempt * 1009).toInt()
            return if (combined == 0) attempt + 1000 else combined
        }
        
        // Strategy 3: Random-like generation with large offsets (final attempts)
        val prime = 1000003 // Large prime number
        val combined = (originalRequestCode.toLong() * prime + attempt * 97 + alarmId.length * 307).toInt()
        return if (combined == 0) attempt + 10000 else combined
    }
    
    /**
     * Detect existing request code collisions in a list of alarms
     */
    suspend fun detectRequestCodeCollisions(alarms: List<ScheduledAlarm>): List<Pair<ScheduledAlarm, ScheduledAlarm>> = withContext(Dispatchers.IO) {
        val collisions = mutableListOf<Pair<ScheduledAlarm, ScheduledAlarm>>()
        val requestCodeMap = mutableMapOf<Int, ScheduledAlarm>()
        
        for (alarm in alarms) {
            val existing = requestCodeMap[alarm.pendingIntentRequestCode]
            if (existing != null) {
                // Found collision
                collisions.add(Pair(existing, alarm))
                Log.w(TAG, "⚠️ Detected request code collision: ${existing.eventTitle} and ${alarm.eventTitle} both use ${alarm.pendingIntentRequestCode}")
            } else {
                requestCodeMap[alarm.pendingIntentRequestCode] = alarm
            }
        }
        
        return@withContext collisions
    }
    
    /**
     * Schedule alarm with individual parameters (for retry logic)
     */
    suspend fun scheduleAlarm(
        eventId: String,
        ruleId: String,
        eventTitle: String,
        alarmTimeUtc: Long,
        requestCode: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (alarmTimeUtc <= System.currentTimeMillis()) {
                Log.w(TAG, "Cannot schedule alarm for past time")
                return@withContext false
            }
            
            // Use the standardized intent creation method
            val alarmId = "$eventId-$ruleId"
            val tempAlarm = ScheduledAlarm(
                id = alarmId,
                eventId = eventId,
                ruleId = ruleId,
                eventTitle = eventTitle,
                eventStartTimeUtc = alarmTimeUtc,
                alarmTimeUtc = alarmTimeUtc,
                scheduledAt = System.currentTimeMillis(),
                userDismissed = false,
                pendingIntentRequestCode = requestCode,
                lastEventModified = System.currentTimeMillis()
            )
            
            val intent = createAlarmIntent(tempAlarm)
            val pendingIntent = createPendingIntent(tempAlarm, intent)
            
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                    Log.w(TAG, "Cannot schedule exact alarms without permission")
                    return@withContext false
                }
                else -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTimeUtc,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled alarm for $eventTitle at ${java.util.Date(alarmTimeUtc)}")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling alarm", e)
            return@withContext false
        }
    }
    
    /**
     * System state validation and health check
     */
    suspend fun validateSystemState(databaseAlarms: List<ScheduledAlarm>): SystemStateValidationResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        try {
            Log.d(TAG, "Starting system state validation for ${databaseAlarms.size} alarms")
            
            // Check 1: Basic permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                issues.add("Exact alarm permission not granted")
            }
            
            // Check 2: Active alarms consistency  
            val activeDbAlarms = databaseAlarms.filter { !it.userDismissed && !it.isInPast() }
            var systemAlarmsFound = 0
            var missingAlarms = 0
            val collisions = mutableListOf<String>()
            
            // Check 3: Request code collisions
            val requestCodeCollisions = detectRequestCodeCollisions(activeDbAlarms)
            if (requestCodeCollisions.isNotEmpty()) {
                collisions.addAll(requestCodeCollisions.map { (alarm1, alarm2) ->
                    "Request code collision: ${alarm1.eventTitle} and ${alarm2.eventTitle} both use ${alarm1.pendingIntentRequestCode}"
                })
            }
            
            // Check 4: System vs database consistency
            for (alarm in activeDbAlarms) {
                if (isAlarmScheduled(alarm)) {
                    systemAlarmsFound++
                } else {
                    missingAlarms++
                }
            }
            
            // Compile results
            if (missingAlarms > 0) {
                warnings.add("$missingAlarms alarms missing from system AlarmManager")
            }
            
            if (collisions.isNotEmpty()) {
                issues.addAll(collisions)
            }
            
            val healthScore = if (activeDbAlarms.isEmpty()) 100 else 
                ((systemAlarmsFound.toFloat() / activeDbAlarms.size) * 100).toInt()
            
            Log.d(TAG, "System state validation completed: Health=${healthScore}%, Found=${systemAlarmsFound}/${activeDbAlarms.size}, Issues=${issues.size}, Warnings=${warnings.size}")
            
            return@withContext SystemStateValidationResult(
                isHealthy = issues.isEmpty(),
                healthScore = healthScore,
                totalAlarms = activeDbAlarms.size,
                systemAlarms = systemAlarmsFound,
                missingAlarms = missingAlarms,
                issues = issues,
                warnings = warnings
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during system state validation", e)
            return@withContext SystemStateValidationResult(
                isHealthy = false,
                healthScore = 0,
                totalAlarms = 0,
                systemAlarms = 0,
                missingAlarms = 0,
                issues = listOf("System validation failed: ${e.message}"),
                warnings = emptyList()
            )
        }
    }
    
    data class SystemStateValidationResult(
        val isHealthy: Boolean,
        val healthScore: Int, // 0-100 percentage
        val totalAlarms: Int,
        val systemAlarms: Int,
        val missingAlarms: Int,
        val issues: List<String>,
        val warnings: List<String>
    )

    /**
     * Schedule a test alarm for manual testing
     */
    suspend fun scheduleTestAlarm(
        testEventTitle: String,
        testAlarmTime: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "=== SCHEDULING TEST ALARM ===")
            Log.i(TAG, "Test event title: '$testEventTitle'")
            Log.i(TAG, "Test alarm time: ${java.util.Date(testAlarmTime)} (UTC: $testAlarmTime)")
            Log.i(TAG, "Current time: ${java.util.Date(System.currentTimeMillis())} (UTC: ${System.currentTimeMillis()})")
            Log.i(TAG, "Time until alarm: ${(testAlarmTime - System.currentTimeMillis()) / 1000} seconds")
            
            // Check if time is in the past
            if (testAlarmTime <= System.currentTimeMillis()) {
                Log.e(TAG, "❌ Test alarm time is in the past!")
                return@withContext false
            }
            
            // Check permissions first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "❌ Cannot schedule test alarm without exact alarm permission")
                return@withContext false
            }
            
            val testRequestCode = (testAlarmTime % Int.MAX_VALUE).toInt()
            val testAlarmId = "test-${System.currentTimeMillis()}"
            
            Log.d(TAG, "Test alarm ID: $testAlarmId")
            Log.d(TAG, "Test request code: $testRequestCode")
            
            // Create standardized test alarm object
            val testAlarm = ScheduledAlarm(
                id = testAlarmId,
                eventId = "test_event",
                ruleId = "test_rule",
                eventTitle = testEventTitle,
                eventStartTimeUtc = testAlarmTime + 60_000, // Event "starts" 1 minute after alarm
                alarmTimeUtc = testAlarmTime,
                scheduledAt = System.currentTimeMillis(),
                userDismissed = false,
                pendingIntentRequestCode = testRequestCode,
                lastEventModified = System.currentTimeMillis()
            )
            
            // Add test alarm marker to intent
            val intent = createAlarmIntent(testAlarm).apply {
                putExtra("IS_TEST_ALARM", true)
                Log.d(TAG, "Test alarm intent action: $action")
                Log.d(TAG, "Test alarm intent component: $component")
                Log.d(TAG, "Test alarm intent package: $`package`")
            }
            
            val pendingIntent = createPendingIntent(testAlarm, intent)
            Log.d(TAG, "Test alarm PendingIntent created: $pendingIntent")
            
            // Schedule the alarm
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                testAlarmTime,
                pendingIntent
            )
            
            Log.i(TAG, "✅ Test alarm scheduled successfully!")
            Log.i(TAG, "Alarm will trigger at: ${java.util.Date(testAlarmTime)}")
            
            // Verify the alarm was scheduled
            val isScheduled = isAlarmScheduled(testAlarm)
            Log.d(TAG, "Alarm verification - is scheduled in system: $isScheduled")
            
            if (!isScheduled) {
                Log.w(TAG, "⚠️ Warning: Alarm verification failed - alarm may not be properly scheduled")
            }
            
            Log.i(TAG, "=== TEST ALARM SCHEDULING COMPLETE ===")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error scheduling test alarm", e)
            return@withContext false
        }
    }
    
    /**
     * Internal method for scheduling alarms (used by retry logic)
     */
    private suspend fun scheduleAlarmInternal(alarm: ScheduledAlarm) {
        if (alarm.isInPast()) {
            throw IllegalArgumentException("Cannot schedule alarm for past time: ${alarm.getLocalAlarmTime()}")
        }
        
        val intent = createAlarmIntent(alarm)
        val pendingIntent = createPendingIntent(alarm, intent)
        
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms() -> {
                throw SecurityException("Exact alarm permission not granted")
            }
            else -> {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarm.alarmTimeUtc,
                    pendingIntent
                )
                
                // Verify the alarm was actually scheduled
                val verificationIntent = createAlarmIntent(alarm)
                val verificationPendingIntent = PendingIntent.getBroadcast(
                    context,
                    alarm.pendingIntentRequestCode,
                    verificationIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (verificationPendingIntent != null) {
                    Log.d(TAG, "✓ Verified alarm scheduled for ${alarm.eventTitle} at ${alarm.getLocalAlarmTime()}")
                } else {
                    throw IllegalStateException("Alarm scheduling verification failed for ${alarm.eventTitle} - PendingIntent not found after scheduling")
                }
            }
        }
    }
}