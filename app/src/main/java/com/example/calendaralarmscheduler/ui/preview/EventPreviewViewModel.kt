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
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import com.example.calendaralarmscheduler.receivers.AlarmReceiver
import com.example.calendaralarmscheduler.utils.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class EventWithAlarms(
    val event: CalendarEvent,
    val alarms: List<ScheduledAlarm>,
    val matchingRules: List<Rule>
)

data class EventFilter(
    val ruleId: String? = null,
    val calendarId: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val showPastEvents: Boolean = false
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
                refreshEventsWithAlarms(rules, alarms)
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
            
            // Filter by rule
            if (filter.ruleId != null) {
                val hasMatchingRule = eventWithAlarms.matchingRules.any { it.id == filter.ruleId }
                if (!hasMatchingRule) return@filter false
            }
            
            // Filter by calendar
            if (filter.calendarId != null && event.calendarId != filter.calendarId) {
                return@filter false
            }
            
            // Filter by date range
            val eventDate = event.getLocalStartTime().toLocalDate()
            if (filter.startDate != null && eventDate.isBefore(filter.startDate)) {
                return@filter false
            }
            if (filter.endDate != null && eventDate.isAfter(filter.endDate)) {
                return@filter false
            }
            
            // Filter past events
            if (!filter.showPastEvents && event.isInPast()) {
                return@filter false
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
    
    fun clearFilter() {
        updateFilter(EventFilter())
    }
    
    fun setRuleFilter(ruleId: String?) {
        updateFilter(_currentFilter.value.copy(ruleId = ruleId))
    }
    
    fun setCalendarFilter(calendarId: Long?) {
        updateFilter(_currentFilter.value.copy(calendarId = calendarId))
    }
    
    fun setDateRangeFilter(startDate: LocalDate?, endDate: LocalDate?) {
        updateFilter(_currentFilter.value.copy(startDate = startDate, endDate = endDate))
    }
    
    fun toggleShowPastEvents() {
        val current = _currentFilter.value
        updateFilter(current.copy(showPastEvents = !current.showPastEvents))
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