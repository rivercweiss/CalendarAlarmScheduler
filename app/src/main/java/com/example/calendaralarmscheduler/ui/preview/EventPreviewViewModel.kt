@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package com.example.calendaralarmscheduler.ui.preview

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.domain.RuleAlarmManager
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import com.example.calendaralarmscheduler.receivers.AlarmReceiver
import com.example.calendaralarmscheduler.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Sealed interface representing different UI states for a data type T
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>
}

data class EventWithAlarms(
    val event: CalendarEvent,
    val alarms: List<ScheduledAlarm>,
    val matchingRules: List<Rule>
)

data class EventFilter(
    val showOnlyMatchingRules: Boolean = false
)

data class AlarmSystemStatus(
    val totalAlarms: Int,
    val activeAlarms: Int,
    val systemAlarms: Int,
    val missedAlarms: Int,
    val failedScheduling: List<AlarmSchedulingFailure>,
    val lastCheck: Long = System.currentTimeMillis()
)

data class AlarmSchedulingFailure(
    val alarmId: String,
    val eventTitle: String,
    val failureReason: String,
    val failureTime: Long,
    val retryCount: Int
)

@HiltViewModel
class EventPreviewViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val ruleRepository: RuleRepository,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val alarmManager: AlarmManager,
    private val ruleAlarmManager: RuleAlarmManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val ruleMatcher = RuleMatcher()
    
    // Enhanced recovery tracking to prevent duplicate recovery attempts
    private val recentlyRecovered = mutableSetOf<String>()
    private val recoveryAttempts = mutableMapOf<String, Int>()
    private val lastRecoveryTimes = mutableMapOf<String, Long>()
    private var lastRecoveryClear = 0L
    private var lastFullRecoveryAttempt = 0L
    private val RECOVERY_TRACKING_TIMEOUT_MS = 5 * 60 * 1000L // 5 minutes
    private val RECOVERY_COOLDOWN_MS = 30 * 1000L // 30 seconds between full recovery attempts
    private val MAX_RECOVERY_ATTEMPTS = 3 // Maximum recovery attempts per alarm
    private val RECOVERY_BACKOFF_MS = 60 * 1000L // 1 minute backoff between attempts
    
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()
    
    private val _currentFilter = MutableStateFlow(EventFilter())
    val currentFilter: StateFlow<EventFilter> = _currentFilter.asStateFlow()
    
    private val _eventsUiState = MutableStateFlow<UiState<List<EventWithAlarms>>>(UiState.Loading)
    val eventsUiState: StateFlow<UiState<List<EventWithAlarms>>> = _eventsUiState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5000,
                replayExpirationMillis = 0
            ),
            initialValue = UiState.Loading
        )
    
    private val _alarmSystemStatus = MutableStateFlow(AlarmSystemStatus(0, 0, 0, 0, emptyList()))
    val alarmSystemStatus: StateFlow<AlarmSystemStatus> = _alarmSystemStatus
        .debounce(1000) // Debounce for 1 second to prevent rapid status updates
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5000,
                replayExpirationMillis = 0 // Don't replay old values after timeout
            ),
            initialValue = AlarmSystemStatus(0, 0, 0, 0, emptyList())
        )
    
    private val _schedulingFailures = MutableStateFlow<List<AlarmSchedulingFailure>>(emptyList())
    val schedulingFailures: StateFlow<List<AlarmSchedulingFailure>> = _schedulingFailures
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5000,
                replayExpirationMillis = 0
            ),
            initialValue = emptyList()
        )
    
    private var unfilteredEvents: List<EventWithAlarms> = emptyList()
    
    init {
        viewModelScope.launch {
            ruleRepository.getAllRules()
                .debounce(500)
                .collect { rules ->
                    android.util.Log.d("EventPreviewViewModel", "Rules changed - refreshing events")
                    val currentAlarms = alarmRepository.getActiveAlarmsSync()
                    refreshEvents(rules, currentAlarms)
                }
        }
    }
    
    
    val rules: StateFlow<List<Rule>> = ruleRepository.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5000,
                replayExpirationMillis = 0 // Don't replay old values after timeout to save memory
            ),
            initialValue = emptyList()
        )
    
    fun refreshEvents() {
        android.util.Log.d("EventPreviewViewModel", "User refresh - fetching fresh calendar data")
        _eventsUiState.value = UiState.Loading
        
        viewModelScope.launch {
            try {
                val currentRules = ruleRepository.getAllRules().first()
                val currentAlarms = alarmRepository.getActiveAlarms().first()
                
                refreshEvents(currentRules, currentAlarms)
                updateAlarmSystemStatus(currentAlarms)
                
                android.util.Log.d("EventPreviewViewModel", "Calendar data refresh completed")
                _errorMessage.emit("✅ Calendar data refreshed")
                
            } catch (e: Exception) {
                android.util.Log.e("EventPreviewViewModel", "Calendar refresh failed", e)
                val errorMessage = "Failed to refresh calendar data: ${e.message}"
                _eventsUiState.value = UiState.Error(errorMessage)
                _errorMessage.emit("❌ $errorMessage")
            }
        }
    }
    
    private suspend fun refreshEvents(
        rules: List<Rule>,
        alarms: List<com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm>
    ) {
        if (!PermissionUtils.hasCalendarPermission(context)) {
            _eventsUiState.value = UiState.Error("Calendar permission is required to view events")
            return
        }
        
        val events = calendarRepository.getEventsInLookAheadWindow()
        android.util.Log.d("EventPreviewViewModel", "Fetched ${events.size} calendar events")
        
        val eventsWithAlarms = events.map { event ->
            val matchResults = ruleMatcher.findMatchingRulesForEvent(event, rules)
            val matchingRules = matchResults.map { it.rule }
            val eventAlarms = alarms.filter { it.eventId == event.id }
                .map { dbAlarm ->
                    ScheduledAlarm(
                        id = dbAlarm.id,
                        eventId = dbAlarm.eventId,
                        ruleId = dbAlarm.ruleId,
                        eventTitle = dbAlarm.eventTitle,
                        eventStartTimeUtc = dbAlarm.eventStartTimeUtc,
                        alarmTimeUtc = dbAlarm.alarmTimeUtc,
                        scheduledAt = dbAlarm.scheduledAt,
                        userDismissed = dbAlarm.userDismissed,
                        pendingIntentRequestCode = dbAlarm.pendingIntentRequestCode,
                        lastEventModified = dbAlarm.lastEventModified
                    )
                }
            EventWithAlarms(event, eventAlarms, matchingRules)
        }
        
        unfilteredEvents = eventsWithAlarms
        _eventsUiState.value = UiState.Success(applyFilter(unfilteredEvents))
        
        android.util.Log.d("EventPreviewViewModel", "Built ${eventsWithAlarms.size} events with alarms")
    }
    
    
    
    
    
    
    private fun applyFilter(events: List<EventWithAlarms>): List<EventWithAlarms> {
        val filter = _currentFilter.value
        
        return events.filter { eventWithAlarms ->
            val event = eventWithAlarms.event
            
            // Always filter out past events
            if (event.isInPast()) {
                return@filter false
            }
            
            // Filter by matching rules if toggle is on
            if (filter.showOnlyMatchingRules) {
                if (eventWithAlarms.matchingRules.isEmpty()) {
                    return@filter false
                }
            }
            
            true
        }.sortedBy { it.event.startTimeUtc }
    }
    
    fun updateFilter(newFilter: EventFilter) {
        android.util.Log.d("EventPreviewViewModel", "updateFilter: showOnlyMatching=${newFilter.showOnlyMatchingRules}, unfilteredEvents.size=${unfilteredEvents.size}")
        _currentFilter.value = newFilter
        
        // Apply filter to ORIGINAL unfiltered data to ensure proper toggle behavior
        // This fixes the issue where toggling back to "show all" would lose events
        if (unfilteredEvents.isNotEmpty()) {
            // Use the original unfiltered events and apply the new filter
            val filteredEvents = applyFilter(unfilteredEvents)
            android.util.Log.d("EventPreviewViewModel", "updateFilter: filtered ${unfilteredEvents.size} -> ${filteredEvents.size} events")
            _eventsUiState.value = UiState.Success(filteredEvents)
        } else {
            android.util.Log.d("EventPreviewViewModel", "No unfiltered data available for filter update")
        }
    }
    
    fun toggleMatchingRulesFilter() {
        val current = _currentFilter.value
        android.util.Log.d("EventPreviewViewModel", "toggleMatchingRulesFilter: ${current.showOnlyMatchingRules} -> ${!current.showOnlyMatchingRules}")
        updateFilter(current.copy(showOnlyMatchingRules = !current.showOnlyMatchingRules))
    }
    
    fun clearError() {
        // No longer needed with SharedFlow - events are automatically consumed
    }
    
    suspend fun getCalendarsWithNames(): Map<Long, String> {
        return try {
            calendarRepository.getCalendarsWithNames()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Schedules alarms for events that match rules but don't have alarms yet.
     * This is called during UI refresh to ensure real-time alarm creation.
     */
    private suspend fun scheduleAlarmsForMatchingEvents(rules: List<Rule>) {
        try {
            android.util.Log.i("EventPreviewViewModel", "Starting immediate alarm scheduling for matching events")
            
            // Get calendar events in the lookahead window
            val events = calendarRepository.getEventsInLookAheadWindow()
            if (events.isEmpty()) {
                android.util.Log.d("EventPreviewViewModel", "No events found in lookahead window")
                return
            }
            
            // Find matching rules using the same logic as CalendarRefreshWorker
            val enabledRules = rules.filter { it.enabled && it.isValid() }
            if (enabledRules.isEmpty()) {
                android.util.Log.d("EventPreviewViewModel", "No enabled rules found")
                return
            }
            
            val matchResults = ruleMatcher.findMatchingRules(events, enabledRules)
            android.util.Log.d("EventPreviewViewModel", "Found ${matchResults.size} rule matches")
            
            if (matchResults.isEmpty()) {
                android.util.Log.d("EventPreviewViewModel", "No matching events found for enabled rules")
                return
            }
            
            // Get existing alarms to filter out duplicates and dismissed alarms
            val existingAlarms = alarmRepository.getAllAlarms().first()
            
            // Filter out user-dismissed alarms (unless event was modified)
            val filteredMatches = ruleMatcher.filterOutDismissedAlarms(matchResults, existingAlarms)
            android.util.Log.d("EventPreviewViewModel", "After filtering dismissed alarms: ${filteredMatches.size} matches")
            
            if (filteredMatches.isEmpty()) {
                android.util.Log.d("EventPreviewViewModel", "No matches remaining after filtering")
                return
            }
            
            // Use the rule alarm manager to process matches
            val result = ruleAlarmManager.processMatchesAndScheduleAlarms(
                filteredMatches,
                logPrefix = "EventPreviewViewModel"
            )
            
            // Show user feedback about scheduling results
            if (result.scheduledCount > 0 || result.updatedCount > 0) {
                val message = buildString {
                    if (result.scheduledCount > 0) {
                        append("${result.scheduledCount} alarm(s) scheduled")
                    }
                    if (result.updatedCount > 0) {
                        if (result.scheduledCount > 0) append(", ")
                        append("${result.updatedCount} alarm(s) updated")
                    }
                }
                _errorMessage.emit("✅ $message")
            } else if (result.skippedCount > 0) {
                android.util.Log.d("EventPreviewViewModel", "All ${result.skippedCount} matching alarms already exist")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("EventPreviewViewModel", "Error scheduling alarms for matching events", e)
            _errorMessage.emit("Error scheduling alarms: ${e.message}")
        }
    }
    
    /**
     * Manually trigger alarm scheduling - exposed for UI use
     */
    fun scheduleAlarmsNow() {
        viewModelScope.launch {
            try {
                val rules = ruleRepository.getAllRulesSync()
                scheduleAlarmsForMatchingEvents(rules)
                
                // Refresh the UI to show newly scheduled alarms
                val updatedRules = ruleRepository.getAllRules().first()
                val updatedAlarms = alarmRepository.getActiveAlarms().first()
                refreshEvents(updatedRules, updatedAlarms)
            } catch (e: Exception) {
                _errorMessage.emit("Error scheduling alarms: ${e.message}")
                android.util.Log.e("EventPreviewViewModel", "Error in scheduleAlarmsNow", e)
            }
        }
    }
    
    // Alarm Monitoring Methods
    private fun updateAlarmSystemStatus(databaseAlarms: List<com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm>) {
        viewModelScope.launch {
            try {
                val systemAlarmsCount = countSystemAlarms(databaseAlarms)
                val activeDbAlarms = databaseAlarms.filter { !it.userDismissed && it.alarmTimeUtc > System.currentTimeMillis() }
                val missedAlarms = databaseAlarms.filter { !it.userDismissed && it.alarmTimeUtc < System.currentTimeMillis() }
                
                val status = AlarmSystemStatus(
                    totalAlarms = databaseAlarms.size,
                    activeAlarms = activeDbAlarms.size,
                    systemAlarms = systemAlarmsCount,
                    missedAlarms = missedAlarms.size,
                    failedScheduling = _schedulingFailures.value
                )
                
                _alarmSystemStatus.value = status
            } catch (e: Exception) {
                android.util.Log.e("EventPreviewViewModel", "Error updating alarm system status", e)
            }
        }
    }
    
    private fun countSystemAlarms(databaseAlarms: List<com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm>): Int {
        var count = 0
        for (alarm in databaseAlarms) {
            if (!alarm.userDismissed && alarm.alarmTimeUtc > System.currentTimeMillis()) {
                if (isAlarmScheduledInSystem(alarm)) {
                    count++
                }
            }
        }
        return count
    }
    
    private fun clearExpiredRecoveryTracking() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRecoveryClear > RECOVERY_TRACKING_TIMEOUT_MS) {
            val beforeSize = recentlyRecovered.size + recoveryAttempts.size + lastRecoveryTimes.size
            
            // Clear expired entries
            recentlyRecovered.clear()
            recoveryAttempts.clear()
            lastRecoveryTimes.clear()
            lastRecoveryClear = currentTime
            
            android.util.Log.d("EventPreviewViewModel", "Cleared recovery tracking cache (${beforeSize} entries)")
        }
    }
    
    private fun shouldAttemptRecovery(alarmId: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val attempts = recoveryAttempts[alarmId] ?: 0
        val lastAttempt = lastRecoveryTimes[alarmId] ?: 0
        
        // Skip if max attempts reached
        if (attempts >= MAX_RECOVERY_ATTEMPTS) {
            android.util.Log.d("EventPreviewViewModel", "Skipping recovery for $alarmId - max attempts reached ($attempts)")
            return false
        }
        
        // Skip if within backoff period
        if (currentTime - lastAttempt < RECOVERY_BACKOFF_MS) {
            android.util.Log.d("EventPreviewViewModel", "Skipping recovery for $alarmId - within backoff period")
            return false
        }
        
        return true
    }
    
    private fun isAlarmScheduledInSystem(alarm: com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm): Boolean {
        return try {
            // Convert database alarm to domain model to use AlarmScheduler's consistent logic
            val domainAlarm = ScheduledAlarm(
                id = alarm.id,
                eventId = alarm.eventId,
                ruleId = alarm.ruleId,
                eventTitle = alarm.eventTitle,
                eventStartTimeUtc = alarm.eventStartTimeUtc,
                alarmTimeUtc = alarm.alarmTimeUtc,
                scheduledAt = alarm.scheduledAt,
                userDismissed = alarm.userDismissed,
                pendingIntentRequestCode = alarm.pendingIntentRequestCode,
                lastEventModified = alarm.lastEventModified
            )
            
            // Use AlarmScheduler's isAlarmScheduled method to ensure consistent intent matching
            val isScheduled = alarmScheduler.isAlarmScheduled(domainAlarm)
            
            // Enhanced logging for debugging missing alarms
            if (!isScheduled) {
                android.util.Log.d("EventPreviewViewModel", 
                    "Alarm not found in system - Event: ${alarm.eventTitle}, " +
                    "ID: ${alarm.id}, RequestCode: ${alarm.pendingIntentRequestCode}, " +
                    "AlarmTime: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(alarm.alarmTimeUtc))}")
            }
            
            isScheduled
        } catch (e: Exception) {
            android.util.Log.e("EventPreviewViewModel", "Error checking if alarm is scheduled in system: ${alarm.eventTitle}", e)
            false
        }
    }
    
    fun checkAlarmSystemStatus() {
        viewModelScope.launch {
            try {
                val alarms = alarmRepository.getActiveAlarmsSync()
                updateAlarmSystemStatus(alarms)
                detectMissingAlarms(alarms)
            } catch (e: Exception) {
                _errorMessage.emit("Error checking alarm system status: ${e.message}")
                android.util.Log.e("EventPreviewViewModel", "Error checking alarm system status", e)
            }
        }
    }
    
    private fun detectMissingAlarms(alarms: List<com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm>) {
        val missingAlarms = mutableListOf<AlarmSchedulingFailure>()
        
        for (alarm in alarms) {
            if (!alarm.userDismissed && alarm.alarmTimeUtc > System.currentTimeMillis()) {
                if (!isAlarmScheduledInSystem(alarm)) {
                    missingAlarms.add(
                        AlarmSchedulingFailure(
                            alarmId = alarm.id,
                            eventTitle = alarm.eventTitle,
                            failureReason = "Alarm missing from system AlarmManager",
                            failureTime = System.currentTimeMillis(),
                            retryCount = 0
                        )
                    )
                }
            }
        }
        
        if (missingAlarms.isNotEmpty()) {
            _schedulingFailures.value = _schedulingFailures.value + missingAlarms
            android.util.Log.w("EventPreviewViewModel", "Detected ${missingAlarms.size} missing alarms")
        }
    }
    
    fun testAlarmScheduling() {
        viewModelScope.launch {
            try {
                val testAlarmTime = System.currentTimeMillis() + (5 * 60 * 1000) // 5 minutes from now
                val testEventTitle = "Test Alarm - ${System.currentTimeMillis()}"
                
                // Create a test alarm
                val testAlarm = ScheduledAlarm(
                    id = "test-${System.currentTimeMillis()}",
                    eventId = "test-event-${System.currentTimeMillis()}",
                    ruleId = "test-rule",
                    eventTitle = testEventTitle,
                    eventStartTimeUtc = testAlarmTime,
                    alarmTimeUtc = testAlarmTime,
                    pendingIntentRequestCode = ScheduledAlarm.generateRequestCode("test-event", "test-rule"),
                    lastEventModified = System.currentTimeMillis()
                )
                
                val success = alarmScheduler.scheduleAlarm(testAlarm)
                
                if (success) {
                    _errorMessage.emit("Test alarm scheduled for 5 minutes from now")
                } else {
                    _schedulingFailures.value = _schedulingFailures.value + listOf(
                        AlarmSchedulingFailure(
                            alarmId = "test-${System.currentTimeMillis()}",
                            eventTitle = testEventTitle,
                            failureReason = "Failed to schedule test alarm",
                            failureTime = System.currentTimeMillis(),
                            retryCount = 0
                        )
                    )
                    _errorMessage.emit("Failed to schedule test alarm")
                }
            } catch (e: Exception) {
                _errorMessage.emit("Error testing alarm scheduling: ${e.message}")
                android.util.Log.e("EventPreviewViewModel", "Error testing alarm scheduling", e)
            }
        }
    }
    
    fun clearSchedulingFailures() {
        _schedulingFailures.value = emptyList()
    }
    
    fun retryFailedAlarms() {
        viewModelScope.launch {
            val failures = _schedulingFailures.value
            val retriedFailures = mutableListOf<AlarmSchedulingFailure>()
            
            for (failure in failures) {
                try {
                    // Try to find the alarm in the database and reschedule it
                    val alarm = alarmRepository.getAlarmById(failure.alarmId)
                    if (alarm != null && !alarm.userDismissed && alarm.alarmTimeUtc > System.currentTimeMillis()) {
                        val success = alarmScheduler.scheduleAlarm(alarm)
                        
                        if (!success) {
                            retriedFailures.add(failure.copy(retryCount = failure.retryCount + 1))
                        }
                    }
                } catch (e: Exception) {
                    retriedFailures.add(failure.copy(retryCount = failure.retryCount + 1))
                    android.util.Log.e("EventPreviewViewModel", "Error retrying alarm ${failure.alarmId}", e)
                }
            }
            
            _schedulingFailures.value = retriedFailures
            
            if (retriedFailures.size < failures.size) {
                _errorMessage.emit("Retried ${failures.size - retriedFailures.size} failed alarms")
            }
        }
    }
    
    /**
     * Automatically reschedule all alarms that exist in database but are missing from system.
     * This is called when the system detects missing alarms to provide automatic recovery.
     */
    fun rescheduleAllMissingAlarms() {
        val currentTime = System.currentTimeMillis()
        
        // Prevent rapid-fire recovery attempts
        if (currentTime - lastFullRecoveryAttempt < RECOVERY_COOLDOWN_MS) {
            android.util.Log.d("EventPreviewViewModel", "Skipping recovery attempt - within cooldown period")
            return
        }
        
        viewModelScope.launch {
            try {
                lastFullRecoveryAttempt = currentTime
                android.util.Log.i("EventPreviewViewModel", "Starting automatic missing alarm recovery")
                
                // Clear expired recovery tracking entries
                clearExpiredRecoveryTracking()
                
                val alarms = alarmRepository.getActiveAlarmsSync()
                
                // Process alarms for rescheduling
                
                var rescheduledCount = 0
                var skippedCount = 0
                var collisionResolvedCount = 0
                val failures = mutableListOf<AlarmSchedulingFailure>()
                
                for (alarm in alarms) {
                    if (!alarm.userDismissed && alarm.alarmTimeUtc > System.currentTimeMillis()) {
                        
                        // Enhanced recovery tracking - check attempts, backoff, etc.
                        if (!shouldAttemptRecovery(alarm.id)) {
                            skippedCount++
                            continue
                        }
                        
                        if (!isAlarmScheduledInSystem(alarm)) {
                            try {
                                // Update recovery tracking for this attempt
                                val currentAttempts = recoveryAttempts.getOrDefault(alarm.id, 0) + 1
                                recoveryAttempts[alarm.id] = currentAttempts
                                lastRecoveryTimes[alarm.id] = currentTime
                                
                                android.util.Log.d("EventPreviewViewModel", "Attempting recovery for ${alarm.eventTitle} (attempt $currentAttempts/${MAX_RECOVERY_ATTEMPTS})")
                                
                                val success = alarmScheduler.scheduleAlarm(alarm)
                                if (success) {
                                    rescheduledCount++
                                    android.util.Log.d("EventPreviewViewModel", "✅ Successfully rescheduled missing alarm: ${alarm.eventTitle}")
                                }
                            } catch (e: Exception) {
                                failures.add(
                                    AlarmSchedulingFailure(
                                        alarmId = alarm.id,
                                        eventTitle = alarm.eventTitle,
                                        failureReason = "Auto-reschedule exception: ${e.message}",
                                        failureTime = System.currentTimeMillis(),
                                        retryCount = 0
                                    )
                                )
                                android.util.Log.e("EventPreviewViewModel", "Error auto-rescheduling alarm ${alarm.eventTitle}", e)
                            }
                        }
                    }
                }
                
                // Update scheduling failures if any occurred
                if (failures.isNotEmpty()) {
                    _schedulingFailures.value = _schedulingFailures.value + failures
                }
                
                android.util.Log.i("EventPreviewViewModel", "Automatic alarm recovery completed: $rescheduledCount alarms rescheduled, $collisionResolvedCount collisions resolved, $skippedCount skipped (recently recovered), ${failures.size} failures")
                
                // Allow time for the system to register newly scheduled alarms before checking status
                if (rescheduledCount > 0) {
                    android.util.Log.d("EventPreviewViewModel", "Waiting 3 seconds for system to register newly scheduled alarms...")
                    delay(3000) // Give Android system time to register PendingIntents
                }
                
                // Refresh alarm status after attempting to reschedule
                val refreshedAlarms = alarmRepository.getActiveAlarmsSync()
                updateAlarmSystemStatus(refreshedAlarms)
                
            } catch (e: Exception) {
                android.util.Log.e("EventPreviewViewModel", "Error in automatic missing alarm recovery", e)
                _errorMessage.emit("Error recovering missing alarms: ${e.message}")
            }
        }
    }
}