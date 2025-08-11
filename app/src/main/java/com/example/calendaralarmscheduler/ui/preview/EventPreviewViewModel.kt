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
import com.example.calendaralarmscheduler.domain.AlarmSchedulingService
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import com.example.calendaralarmscheduler.receivers.AlarmReceiver
import com.example.calendaralarmscheduler.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
    private val alarmSchedulingService: AlarmSchedulingService,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val ruleMatcher = RuleMatcher()
    
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()
    
    private val _currentFilter = MutableStateFlow(EventFilter())
    val currentFilter: StateFlow<EventFilter> = _currentFilter.asStateFlow()
    
    private val _eventsUiState = MutableStateFlow<UiState<List<EventWithAlarms>>>(UiState.Loading)
    val eventsUiState: StateFlow<UiState<List<EventWithAlarms>>> = _eventsUiState.asStateFlow()
    
    private val _alarmSystemStatus = MutableStateFlow(AlarmSystemStatus(0, 0, 0, 0, emptyList()))
    val alarmSystemStatus: StateFlow<AlarmSystemStatus> = _alarmSystemStatus.asStateFlow()
    
    private val _schedulingFailures = MutableStateFlow<List<AlarmSchedulingFailure>>(emptyList())
    val schedulingFailures: StateFlow<List<AlarmSchedulingFailure>> = _schedulingFailures.asStateFlow()
    
    // Store unfiltered events data to enable proper filter toggling
    private var unfilteredEvents: List<EventWithAlarms> = emptyList()
    
    init {
        // Single unified data loading observer - no more conflicting refresh paths
        viewModelScope.launch {
            combine(
                ruleRepository.getAllRules(),
                alarmRepository.getActiveAlarms()
            ) { rules, alarms ->
                android.util.Log.d("EventPreviewViewModel", "Data changed - triggering unified refresh")
                refreshEventsUnified(rules, alarms, includeAlarmScheduling = false)
            }
            // Moderate debounce to prevent excessive processing while keeping responsiveness
            .debounce(300)
            .collect { }
        }
    }
    
    val rules: StateFlow<List<Rule>> = ruleRepository.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun refreshEvents() {
        // Use the SAME approach as filter toggle - just refresh UI with cached data
        // No database calls, no memory checks, no heavy operations
        android.util.Log.d("EventPreviewViewModel", "Refresh button pressed - refreshing UI display")
        
        if (unfilteredEvents.isNotEmpty()) {
            // Use cached data like the filter toggle does
            val currentFilter = _currentFilter.value
            val filteredEvents = applyFilter(unfilteredEvents)
            _eventsUiState.value = UiState.Success(filteredEvents)
            android.util.Log.d("EventPreviewViewModel", "Refresh completed using cached data")
        } else {
            // No cached data available - let user know data is loading
            // The combine observer will automatically load fresh data
            _eventsUiState.value = UiState.Loading
            android.util.Log.d("EventPreviewViewModel", "No cached data available, showing loading state")
        }
    }
    
    // Unified refresh control - prevents all types of concurrent data operations
    private var isRefreshing = false
    private var lastRefreshRequest = 0L
    
    /**
     * UNIFIED DATA LOADING METHOD - All refresh operations delegate to this method
     * This is the single source of truth for data loading to prevent conflicts
     */
    private fun refreshEventsUnified(
        rules: List<Rule>? = null, 
        alarms: List<com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm>? = null,
        includeAlarmScheduling: Boolean = false
    ) {
        if (!PermissionUtils.hasCalendarPermission(context)) {
            _eventsUiState.value = UiState.Error("Calendar permission is required to view events")
            return
        }
        
        // Request deduplication - prevent rapid successive calls
        val now = System.currentTimeMillis()
        if (isRefreshing && (now - lastRefreshRequest) < 1000) {
            android.util.Log.d("EventPreviewViewModel", "Skipping duplicate refresh request")
            return
        }
        lastRefreshRequest = now
        
        // Memory check - only block if truly critical (99%+) and only for heavy operations
        if (includeAlarmScheduling) {
            val runtime = Runtime.getRuntime()
            val memoryUsagePercent = ((runtime.maxMemory() - runtime.freeMemory()).toDouble() / runtime.maxMemory().toDouble()) * 100
            if (memoryUsagePercent > 99) {
                android.util.Log.w("EventPreviewViewModel", "Skipping heavy refresh due to critical memory usage: ${memoryUsagePercent.toInt()}%")
                _eventsUiState.value = UiState.Error("Low memory - please close other apps")
                return
            }
        }
        // Lightweight refreshes (no alarm scheduling) skip memory check entirely
        
        viewModelScope.launch {
            isRefreshing = true
            android.util.Log.d("EventPreviewViewModel", "Starting unified refresh (scheduling: $includeAlarmScheduling)")
            
            try {
                // Get data - use provided data if available, otherwise fetch
                val currentRules = rules ?: ruleRepository.getAllRules().first()
                val currentAlarms = alarms ?: alarmRepository.getActiveAlarms().first()
                
                // Schedule alarms if requested (for user-initiated refreshes)
                if (includeAlarmScheduling && currentRules.isNotEmpty()) {
                    scheduleAlarmsForMatchingEvents(currentRules)
                    // Get updated alarm data after scheduling
                    val updatedAlarms = alarmRepository.getActiveAlarms().first()
                    buildEventsWithAlarms(currentRules, updatedAlarms)
                } else {
                    // Read-only refresh (for combine observer)
                    buildEventsWithAlarms(currentRules, currentAlarms)
                }
                
                updateAlarmSystemStatus(currentAlarms)
                android.util.Log.d("EventPreviewViewModel", "Unified refresh completed successfully")
                
            } catch (e: Exception) {
                val errorMessage = "Error loading events: ${e.message}"
                android.util.Log.e("EventPreviewViewModel", "Unified refresh failed", e)
                _errorMessage.emit(errorMessage)
                _eventsUiState.value = UiState.Error(errorMessage, e)
                unfilteredEvents = emptyList() // Clear stale cache
            } finally {
                isRefreshing = false
            }
        }
    }
    
    /**
     * Core data processing method - builds EventWithAlarms from rules and alarms
     * This replaces the old refreshEventsWithAlarmsReadOnly and refreshEventsWithAlarms methods
     */
    private suspend fun buildEventsWithAlarms(
        rules: List<Rule>, 
        alarms: List<com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm>
    ) {
        val events = calendarRepository.getEventsInLookAheadWindow()
        val eventsWithAlarms = mutableListOf<EventWithAlarms>()
        
        for (event in events) {
            // Find matching rules for this event
            val matchResults = ruleMatcher.findMatchingRulesForEvent(event, rules)
            val matchingRules = matchResults.map { it.rule }
            
            // Find alarms for this event
            val eventAlarms = alarms
                .filter { it.eventId == event.id }
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
            
            eventsWithAlarms.add(EventWithAlarms(event, eventAlarms, matchingRules))
        }
        
        // Store unfiltered events for filter toggling
        unfilteredEvents = eventsWithAlarms
        
        // Apply current filter and update UI
        _eventsUiState.value = UiState.Success(applyFilter(eventsWithAlarms))
    }
    
    /**
     * Lightweight refresh that only updates UI without heavy alarm scheduling.
     * Use this for tab switches and filter toggles.
     * Now delegates to unified refresh method.
     */
    private fun refreshEventsLightweight() {
        android.util.Log.d("EventPreviewViewModel", "Lightweight refresh requested")
        refreshEventsUnified(includeAlarmScheduling = false)
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
        _currentFilter.value = newFilter
        
        // Apply filter to ORIGINAL unfiltered data to ensure proper toggle behavior
        // This fixes the issue where toggling back to "show all" would lose events
        if (unfilteredEvents.isNotEmpty()) {
            // Use the original unfiltered events and apply the new filter
            val filteredEvents = applyFilter(unfilteredEvents)
            _eventsUiState.value = UiState.Success(filteredEvents)
        } else {
            // No unfiltered data available yet - check if we can use current data or need to refresh
            val currentUiState = _eventsUiState.value
            if (currentUiState is UiState.Loading) {
                // If still loading, do nothing - filter will be applied when data arrives
            } else {
                // No unfiltered data available, trigger a lightweight refresh
                // This ensures the filter toggle works even when starting fresh
                refreshEventsLightweight()
            }
        }
    }
    
    fun toggleMatchingRulesFilter() {
        val current = _currentFilter.value
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
            val existingAlarmsDb = alarmRepository.getAllAlarms().first()
            val existingAlarms = existingAlarmsDb.map { dbAlarm ->
                com.example.calendaralarmscheduler.domain.models.ScheduledAlarm(
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
            
            // Filter out user-dismissed alarms (unless event was modified)
            val filteredMatches = ruleMatcher.filterOutDismissedAlarms(matchResults, existingAlarms)
            android.util.Log.d("EventPreviewViewModel", "After filtering dismissed alarms: ${filteredMatches.size} matches")
            
            if (filteredMatches.isEmpty()) {
                android.util.Log.d("EventPreviewViewModel", "No matches remaining after filtering")
                return
            }
            
            // Use the shared scheduling service to process matches
            val result = alarmSchedulingService.processMatchesAndScheduleAlarms(
                filteredMatches,
                logPrefix = "EventPreviewViewModel"
            )
            
            // Show user feedback about scheduling results
            if (result.success) {
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
            } else {
                _errorMessage.emit("⚠️ ${result.message}")
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
                
                // Refresh the UI to show newly scheduled alarms (lightweight since alarms just got scheduled)
                refreshEventsLightweight()
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
    
    private fun isAlarmScheduledInSystem(alarm: com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm): Boolean {
        return try {
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("ALARM_ID", alarm.id)
                putExtra("EVENT_TITLE", alarm.eventTitle)
                putExtra("RULE_ID", alarm.ruleId)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.pendingIntentRequestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            pendingIntent != null
        } catch (e: Exception) {
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
                
                val success = alarmScheduler.scheduleTestAlarm(
                    testEventTitle = testEventTitle,
                    testAlarmTime = testAlarmTime
                )
                
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
                        val success = alarmScheduler.scheduleAlarm(
                            eventId = alarm.eventId,
                            ruleId = alarm.ruleId,
                            eventTitle = alarm.eventTitle,
                            alarmTimeUtc = alarm.alarmTimeUtc,
                            requestCode = alarm.pendingIntentRequestCode
                        )
                        
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
}