package com.example.calendaralarmscheduler.ui.preview

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.AppDatabase
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.domain.AlarmSchedulingService
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import com.example.calendaralarmscheduler.receivers.AlarmReceiver
import com.example.calendaralarmscheduler.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

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

class EventPreviewViewModel(application: Application) : AndroidViewModel(application) {
    
    private val calendarRepository = CalendarRepository(application)
    private val ruleRepository: RuleRepository
    private val alarmRepository: AlarmRepository
    private val alarmScheduler: AlarmScheduler
    private val alarmManager: AlarmManager
    private val ruleMatcher = RuleMatcher()
    private val alarmSchedulingService: AlarmSchedulingService
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage
    
    private val _currentFilter = MutableStateFlow(EventFilter())
    val currentFilter: StateFlow<EventFilter> = _currentFilter.asStateFlow()
    
    private val _eventsWithAlarms = MutableStateFlow<List<EventWithAlarms>>(emptyList())
    val eventsWithAlarms: StateFlow<List<EventWithAlarms>> = _eventsWithAlarms.asStateFlow()
    
    private val _alarmSystemStatus = MutableStateFlow(AlarmSystemStatus(0, 0, 0, 0, emptyList()))
    val alarmSystemStatus: StateFlow<AlarmSystemStatus> = _alarmSystemStatus.asStateFlow()
    
    private val _schedulingFailures = MutableStateFlow<List<AlarmSchedulingFailure>>(emptyList())
    val schedulingFailures: StateFlow<List<AlarmSchedulingFailure>> = _schedulingFailures.asStateFlow()
    
    init {
        val database = AppDatabase.getInstance(application)
        ruleRepository = RuleRepository(database.ruleDao())
        alarmRepository = AlarmRepository(database.alarmDao())
        alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmScheduler = AlarmScheduler(application, alarmManager)
        alarmSchedulingService = AlarmSchedulingService(alarmRepository, alarmScheduler)
        
        _isLoading.value = false
        
        // Observe rules and alarms and refresh when they change
        viewModelScope.launch {
            combine(
                ruleRepository.getAllRules(),
                alarmRepository.getActiveAlarms()
            ) { rules, alarms ->
                refreshEventsWithAlarms(rules, alarms)
                updateAlarmSystemStatus(alarms)
            }
        }
    }
    
    val rules = ruleRepository.getAllRules().asLiveData()
    
    fun refreshEvents() {
        if (!PermissionUtils.hasCalendarPermission(getApplication())) {
            _errorMessage.value = "Calendar permission is required to view events"
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            try {
                val rules = ruleRepository.getAllRulesSync()
                val alarms = alarmRepository.getActiveAlarmsSync()
                
                // First schedule any missing alarms before refreshing the UI
                scheduleAlarmsForMatchingEvents(rules)
                
                // Then refresh the UI with updated alarm data
                val updatedAlarms = alarmRepository.getActiveAlarmsSync()
                refreshEventsWithAlarms(rules, updatedAlarms)
            } catch (e: Exception) {
                _errorMessage.value = "Error loading events: ${e.message}"
                android.util.Log.e("EventPreviewViewModel", "Error refreshing events", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private suspend fun refreshEventsWithAlarms(rules: List<Rule>, alarms: List<com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm>) {
        try {
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
                        // Convert database entity to domain model
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
            
            _eventsWithAlarms.value = applyFilter(eventsWithAlarms)
        } catch (e: Exception) {
            _errorMessage.value = "Error processing events: ${e.message}"
            android.util.Log.e("EventPreviewViewModel", "Error in refreshEventsWithAlarms", e)
        }
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
        // Re-apply filter to existing events
        val currentEvents = _eventsWithAlarms.value
        if (currentEvents.isNotEmpty()) {
            _eventsWithAlarms.value = applyFilter(currentEvents)
        }
    }
    
    fun toggleMatchingRulesFilter() {
        val current = _currentFilter.value
        updateFilter(current.copy(showOnlyMatchingRules = !current.showOnlyMatchingRules))
    }
    
    fun clearError() {
        _errorMessage.value = null
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
                    _errorMessage.value = "✅ $message"
                } else if (result.skippedCount > 0) {
                    android.util.Log.d("EventPreviewViewModel", "All ${result.skippedCount} matching alarms already exist")
                }
            } else {
                _errorMessage.value = "⚠️ ${result.message}"
            }
            
        } catch (e: Exception) {
            android.util.Log.e("EventPreviewViewModel", "Error scheduling alarms for matching events", e)
            _errorMessage.value = "Error scheduling alarms: ${e.message}"
        }
    }
    
    /**
     * Manually trigger alarm scheduling - exposed for UI use
     */
    fun scheduleAlarmsNow() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val rules = ruleRepository.getAllRulesSync()
                scheduleAlarmsForMatchingEvents(rules)
                
                // Refresh the UI to show newly scheduled alarms
                refreshEvents()
            } catch (e: Exception) {
                _errorMessage.value = "Error scheduling alarms: ${e.message}"
                android.util.Log.e("EventPreviewViewModel", "Error in scheduleAlarmsNow", e)
            } finally {
                _isLoading.value = false
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
            val intent = Intent(getApplication(), AlarmReceiver::class.java).apply {
                putExtra("ALARM_ID", alarm.id)
                putExtra("EVENT_TITLE", alarm.eventTitle)
                putExtra("RULE_ID", alarm.ruleId)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                getApplication(),
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
                _errorMessage.value = "Error checking alarm system status: ${e.message}"
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
                    _errorMessage.value = "Test alarm scheduled for 5 minutes from now"
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
                    _errorMessage.value = "Failed to schedule test alarm"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error testing alarm scheduling: ${e.message}"
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
                _errorMessage.value = "Retried ${failures.size - retriedFailures.size} failed alarms"
            }
        }
    }
}