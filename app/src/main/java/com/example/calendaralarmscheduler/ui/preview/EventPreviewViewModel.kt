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
    private val alarmSchedulingService: AlarmSchedulingService,
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
    
    // Store unfiltered events data to enable proper filter toggling
    // Memory optimization: limit cache size and implement cleanup
    private var unfilteredEvents: List<EventWithAlarms> = emptyList()
    private var lastCacheCleanup = 0L
    
    companion object {
        // Memory management constants - optimized for emulator constraints (186MB available)
        private const val MAX_CACHED_EVENTS = 100 // Limit cached events to prevent memory bloat  
        private const val MEMORY_PRESSURE_THRESHOLD = 65 // Start cleanup at 65% memory usage
        private const val CRITICAL_MEMORY_THRESHOLD = 75 // Critical threshold for low-memory fallback
        private const val CACHE_CLEANUP_INTERVAL_MS = 30_000 // Clean cache every 30 seconds
        private const val LOW_MEMORY_GC_DELAY_MS = 2_000 // Delay before requesting GC
    }
    
    init {
        // Optimized data loading - avoid constantly changing Flow combinations
        viewModelScope.launch {
            // Track rules changes separately to avoid constant Flow re-combinations
            ruleRepository.getAllRules()
                .debounce(500) // Increased debounce to reduce rapid-fire updates
                .collect { rules ->
                    android.util.Log.d("EventPreviewViewModel", "Rules changed - triggering READ-ONLY refresh (includeAlarmScheduling=false)")
                    // Get current alarms snapshot instead of reactive flow
                    val currentAlarms = alarmRepository.getActiveAlarmsSync()
                    // CRITICAL: This must NEVER trigger alarm scheduling - only UI updates
                    refreshEventsUnified(rules, currentAlarms, includeAlarmScheduling = false)
                }
        }
        
        // Start proactive memory monitoring
        startMemoryMonitoring()
    }
    
    /**
     * Memory monitoring and management utilities
     */
    private fun getCurrentMemoryUsagePercent(): Double {
        val runtime = Runtime.getRuntime()
        // Correct calculation: (allocated - free) / max possible heap
        return ((runtime.totalMemory() - runtime.freeMemory()).toDouble() / runtime.maxMemory().toDouble()) * 100
    }
    
    private fun logMemoryUsage(operation: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val usagePercent = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
        
        android.util.Log.d("EventPreviewViewModel_Memory", 
            "$operation - Memory: ${usedMemory / 1024 / 1024}MB/${totalMemory / 1024 / 1024}MB (${usagePercent.toInt()}% of ${maxMemory / 1024 / 1024}MB max)")
    }
    
    private suspend fun performMemoryCleanupIfNeeded() {
        val memoryUsage = getCurrentMemoryUsagePercent()
        val currentTime = System.currentTimeMillis()
        
        if (memoryUsage > MEMORY_PRESSURE_THRESHOLD || 
            (currentTime - lastCacheCleanup) > CACHE_CLEANUP_INTERVAL_MS) {
            
            android.util.Log.i("EventPreviewViewModel_Memory", 
                "Performing memory cleanup - usage: ${memoryUsage.toInt()}%")
            
            // Limit cache size
            if (unfilteredEvents.size > MAX_CACHED_EVENTS) {
                // Keep only the most recent events
                unfilteredEvents = unfilteredEvents
                    .sortedByDescending { it.event.startTimeUtc }
                    .take(MAX_CACHED_EVENTS)
                android.util.Log.d("EventPreviewViewModel_Memory", 
                    "Trimmed cache to ${unfilteredEvents.size} events")
            }
            
            lastCacheCleanup = currentTime
            
            // Request garbage collection if memory usage is high
            if (memoryUsage > MEMORY_PRESSURE_THRESHOLD) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(LOW_MEMORY_GC_DELAY_MS.toLong())
                    System.gc()
                    android.util.Log.d("EventPreviewViewModel_Memory", "Requested garbage collection")
                }
            }
        }
    }
    
    private fun startMemoryMonitoring() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000) // Check every 30 seconds
                performMemoryCleanupIfNeeded()
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
        // Fetch FRESH calendar data from the system when user explicitly requests refresh
        android.util.Log.d("EventPreviewViewModel", "Refresh button pressed - fetching fresh calendar data")
        
        // Show loading state for fresh data fetch
        _eventsUiState.value = UiState.Loading
        
        viewModelScope.launch {
            try {
                // Log memory usage before refresh
                logMemoryUsage("Before refresh")
                
                // Check memory before performing fresh data fetch
                val memoryUsagePercent = getCurrentMemoryUsagePercent()
                android.util.Log.i("EventPreviewViewModel_Memory", 
                    "Memory check: ${memoryUsagePercent.toInt()}% (pressure>${MEMORY_PRESSURE_THRESHOLD}%, critical>${CRITICAL_MEMORY_THRESHOLD}%)")
                
                // Proactively clean up memory if under pressure
                if (memoryUsagePercent > MEMORY_PRESSURE_THRESHOLD) {
                    android.util.Log.i("EventPreviewViewModel_Memory", "Memory pressure detected - cleaning up")
                    performMemoryCleanupIfNeeded()
                }
                
                if (memoryUsagePercent > CRITICAL_MEMORY_THRESHOLD) {
                    // Critical memory situation - fall back to cached data if available
                    android.util.Log.w("EventPreviewViewModel_Memory", 
                        "CRITICAL memory usage (${memoryUsagePercent.toInt()}%) - using cached data to avoid OOM")
                    logMemoryUsage("Critical memory fallback")
                    if (unfilteredEvents.isNotEmpty()) {
                        val filteredEvents = applyFilter(unfilteredEvents)
                        _eventsUiState.value = UiState.Success(filteredEvents)
                        _errorMessage.emit("⚠️ Using cached data due to low memory")
                        
                        // Request GC to free up memory
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(LOW_MEMORY_GC_DELAY_MS.toLong())
                            android.util.Log.d("EventPreviewViewModel_Memory", "Requesting GC due to memory pressure")
                            System.gc()
                            kotlinx.coroutines.delay(1000) // Wait for GC
                            logMemoryUsage("Post-GC")
                        }
                    } else {
                        _eventsUiState.value = UiState.Error("Low memory - please close other apps and try again")
                    }
                    return@launch
                }
                
                // Fetch fresh data from calendar provider
                android.util.Log.d("EventPreviewViewModel", "Fetching fresh calendar data from provider")
                val currentRules = ruleRepository.getAllRules().first()
                val currentAlarms = alarmRepository.getActiveAlarms().first()
                
                // Build fresh events data (same as unified refresh but called directly)
                buildEventsWithAlarms(currentRules, currentAlarms)
                
                // Update alarm system status with fresh data
                updateAlarmSystemStatus(currentAlarms)
                
                // Log memory usage after refresh
                logMemoryUsage("After refresh")
                
                android.util.Log.d("EventPreviewViewModel", "Fresh calendar data refresh completed successfully")
                _errorMessage.emit("✅ Calendar data refreshed")
                
            } catch (e: Exception) {
                android.util.Log.e("EventPreviewViewModel", "Fresh calendar data refresh failed", e)
                
                // Fall back to cached data if available
                if (unfilteredEvents.isNotEmpty()) {
                    val filteredEvents = applyFilter(unfilteredEvents)
                    _eventsUiState.value = UiState.Success(filteredEvents)
                    _errorMessage.emit("⚠️ Refresh failed, showing cached data: ${e.message}")
                } else {
                    val errorMessage = "Failed to refresh calendar data: ${e.message}"
                    _eventsUiState.value = UiState.Error(errorMessage)
                    _errorMessage.emit("❌ $errorMessage")
                }
            } finally {
                // Perform cleanup after refresh
                performMemoryCleanupIfNeeded()
            }
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
        
        // Memory check - use new memory management functions
        val memoryUsagePercent = getCurrentMemoryUsagePercent()
        if (includeAlarmScheduling) {
            if (memoryUsagePercent > CRITICAL_MEMORY_THRESHOLD) {
                android.util.Log.w("EventPreviewViewModel", "Skipping heavy refresh due to critical memory usage: ${memoryUsagePercent.toInt()}%")
                _eventsUiState.value = UiState.Error("Low memory - please close other apps")
                return
            }
        }
        
        // Proactive cleanup for all operations if memory pressure detected
        if (memoryUsagePercent > MEMORY_PRESSURE_THRESHOLD) {
            viewModelScope.launch {
                performMemoryCleanupIfNeeded()
            }
        }
        
        viewModelScope.launch {
            isRefreshing = true
            android.util.Log.d("EventPreviewViewModel", "Starting unified refresh (scheduling: $includeAlarmScheduling)")
            
            try {
                // Get data - use provided data if available, otherwise fetch
                val currentRules = rules ?: ruleRepository.getAllRules().first()
                val currentAlarms = alarms ?: alarmRepository.getActiveAlarms().first()
                
                // Schedule alarms if requested (for user-initiated refreshes)
                if (includeAlarmScheduling && currentRules.isNotEmpty()) {
                    android.util.Log.d("EventPreviewViewModel", "Performing ACTIVE refresh with alarm scheduling")
                    scheduleAlarmsForMatchingEvents(currentRules)
                    // Get updated alarm data after scheduling
                    val updatedAlarms = alarmRepository.getActiveAlarms().first()
                    buildEventsWithAlarms(currentRules, updatedAlarms)
                } else {
                    // Read-only refresh (for combine observer)
                    android.util.Log.d("EventPreviewViewModel", "Performing READ-ONLY refresh (no alarm scheduling)")
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
        
        // Store unfiltered events for filter toggling with memory management
        unfilteredEvents = if (eventsWithAlarms.size > MAX_CACHED_EVENTS) {
            // Keep only the most recent events if we have too many
            eventsWithAlarms
                .sortedByDescending { it.event.startTimeUtc }
                .take(MAX_CACHED_EVENTS)
                .also {
                    android.util.Log.i("EventPreviewViewModel_Memory", 
                        "Limited cached events: ${eventsWithAlarms.size} → ${it.size}")
                }
        } else {
            eventsWithAlarms
        }
        
        // Apply current filter and update UI
        _eventsUiState.value = UiState.Success(applyFilter(unfilteredEvents))
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
            // No unfiltered data available yet - check if we can use current data or need to refresh
            val currentUiState = _eventsUiState.value
            android.util.Log.d("EventPreviewViewModel", "updateFilter: no unfiltered data, currentState=${currentUiState.javaClass.simpleName}")
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
                
                // First, detect any request code collisions in the database
                val domainAlarms = alarms.map { dbAlarm ->
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
                
                val collisions = alarmScheduler.detectRequestCodeCollisions(domainAlarms)
                if (collisions.isNotEmpty()) {
                    android.util.Log.w("EventPreviewViewModel", "⚠️ Detected ${collisions.size} request code collisions during recovery")
                }
                
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
                                
                                // Convert database alarm to domain model
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
                                
                                val result = alarmScheduler.scheduleAlarm(domainAlarm)
                                if (result.success) {
                                    rescheduledCount++
                                    
                                    // If request code was changed during collision resolution, update database
                                    if (result.alarm != null && result.alarm.pendingIntentRequestCode != alarm.pendingIntentRequestCode) {
                                        try {
                                            alarmRepository.updateAlarmRequestCode(alarm.id, result.alarm.pendingIntentRequestCode)
                                            collisionResolvedCount++
                                            android.util.Log.i("EventPreviewViewModel", 
                                                "✓ Updated database with resolved request code for ${alarm.eventTitle}: ${alarm.pendingIntentRequestCode} -> ${result.alarm.pendingIntentRequestCode}")
                                        } catch (e: Exception) {
                                            android.util.Log.e("EventPreviewViewModel", "Failed to update request code in database for ${alarm.eventTitle}", e)
                                        }
                                    }
                                    
                                    // Successful recovery - clear attempt tracking
                                    recoveryAttempts.remove(alarm.id)
                                    lastRecoveryTimes.remove(alarm.id)
                                    recentlyRecovered.add(alarm.id)
                                    android.util.Log.d("EventPreviewViewModel", "✅ Successfully rescheduled missing alarm: ${alarm.eventTitle}")
                                } else {
                                    failures.add(
                                        AlarmSchedulingFailure(
                                            alarmId = alarm.id,
                                            eventTitle = alarm.eventTitle,
                                            failureReason = "Auto-reschedule failed: ${result.message}",
                                            failureTime = System.currentTimeMillis(),
                                            retryCount = 0
                                        )
                                    )
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