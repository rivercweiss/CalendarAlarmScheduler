package com.example.calendaralarmscheduler.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.workers.WorkerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val workerManager: WorkerManager,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {
    
    private val _refreshIntervalDescription = MutableStateFlow("")
    val refreshIntervalDescription: StateFlow<String> = _refreshIntervalDescription.asStateFlow()
    
    private val _allDayTimeDescription = MutableStateFlow("")
    val allDayTimeDescription: StateFlow<String> = _allDayTimeDescription.asStateFlow()
    
    private val _workStatus = MutableStateFlow(WorkerManager.WorkStatus(isScheduled = false, state = "UNKNOWN"))
    val workStatus: StateFlow<WorkerManager.WorkStatus> = _workStatus.asStateFlow()
    
    private val _lastSyncDescription = MutableStateFlow("")
    val lastSyncDescription: StateFlow<String> = _lastSyncDescription.asStateFlow()
    
    private val _duplicateHandlingModeDescription = MutableStateFlow("")
    val duplicateHandlingModeDescription: StateFlow<String> = _duplicateHandlingModeDescription.asStateFlow()
    
    init {
        Logger.d("SettingsViewModel", "SettingsViewModel initialized")
        setupObservers()
        updateWorkStatus()
        updateLastSyncDescription()
    }
    
    private fun setupObservers() {
        viewModelScope.launch {
            // Observe refresh interval changes
            Logger.d("SettingsViewModel", "Starting refresh interval StateFlow collection")
            settingsRepository.refreshIntervalMinutes.collect { intervalMinutes ->
                val oldDescription = _refreshIntervalDescription.value
                val newDescription = "Every $intervalMinutes minute${if (intervalMinutes != 1) "s" else ""}"
                _refreshIntervalDescription.value = newDescription
                Logger.i("SettingsViewModel", "Refresh interval description updated: '$oldDescription' -> '$newDescription' (from $intervalMinutes minutes)")
            }
        }
        
        viewModelScope.launch {
            // Observe all-day time changes using the new atomic StateFlow
            Logger.d("SettingsViewModel", "Starting atomic all-day time StateFlow collection")
            settingsRepository.allDayTime.collect { allDayTime ->
                val oldDescription = _allDayTimeDescription.value
                val newDescription = settingsRepository.getAllDayDefaultTimeFormatted()
                _allDayTimeDescription.value = newDescription
                Logger.i("SettingsViewModel", "All-day time description updated atomically: '$oldDescription' -> '$newDescription' (from ${allDayTime.hour}:${allDayTime.minute})")
            }
        }
        
        viewModelScope.launch {
            // Observe duplicate handling mode changes
            Logger.d("SettingsViewModel", "Starting duplicate handling mode StateFlow collection")
            settingsRepository.duplicateHandlingMode.collect { mode ->
                val oldDescription = _duplicateHandlingModeDescription.value
                val newDescription = mode.displayName
                _duplicateHandlingModeDescription.value = newDescription
                Logger.i("SettingsViewModel", "Duplicate handling mode description updated: '$oldDescription' -> '$newDescription'")
            }
        }
    }
    
    fun getCurrentRefreshInterval(): Int {
        return settingsRepository.getRefreshIntervalMinutes()
    }
    
    fun setRefreshInterval(minutes: Int) {
        Logger.i("SettingsViewModel", "Setting refresh interval to: $minutes minutes")
        settingsRepository.setRefreshIntervalMinutes(minutes)
        
        // Reschedule the background worker with new interval
        viewModelScope.launch {
            try {
                workerManager.reschedulePeriodicRefresh(minutes)
                updateWorkStatus()
                Logger.i("SettingsViewModel", "Background worker rescheduled with interval: $minutes minutes")
            } catch (e: Exception) {
                Logger.e("SettingsViewModel", "Failed to reschedule background worker", e)
            }
        }
    }
    
    fun getCurrentAllDayHour(): Int {
        return settingsRepository.getAllDayDefaultHour()
    }
    
    fun getCurrentAllDayMinute(): Int {
        return settingsRepository.getAllDayDefaultMinute()
    }
    
    fun setAllDayDefaultTime(hour: Int, minute: Int) {
        Logger.i("SettingsViewModel", "Setting all-day default time to: $hour:$minute")
        settingsRepository.setAllDayDefaultTime(hour, minute)
    }
    
    fun getCurrentDuplicateHandlingMode(): com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode {
        return settingsRepository.duplicateHandlingMode.value
    }
    
    fun setDuplicateHandlingMode(mode: com.example.calendaralarmscheduler.domain.models.DuplicateHandlingMode) {
        Logger.i("SettingsViewModel", "Setting duplicate handling mode to: ${mode.displayName}")
        settingsRepository.setDuplicateHandlingMode(mode)
    }
    
    fun resetSettings() {
        Logger.i("SettingsViewModel", "Resetting all settings to defaults")
        settingsRepository.resetToDefaults()
        
        // Reschedule background worker with default interval
        viewModelScope.launch {
            try {
                workerManager.reschedulePeriodicRefresh(WorkerManager.DEFAULT_INTERVAL_MINUTES)
                updateWorkStatus()
                Logger.i("SettingsViewModel", "Background worker rescheduled with default interval")
            } catch (e: Exception) {
                Logger.e("SettingsViewModel", "Failed to reschedule background worker after reset", e)
            }
        }
    }
    
    fun scheduleTestAlarm(callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                Logger.i("SettingsViewModel", "Scheduling test alarm")
                
                // Create a test alarm 10 seconds from now
                val currentTime = System.currentTimeMillis()
                val testAlarmTime = currentTime + 10_000 // 10 seconds
                
                val testAlarm = ScheduledAlarm(
                    id = "test_alarm_${UUID.randomUUID()}",
                    eventId = "test_event",
                    ruleId = "test_rule",
                    eventTitle = "Calendar Alarm Test",
                    eventStartTimeUtc = testAlarmTime + 60_000, // Event "starts" 1 minute after alarm
                    alarmTimeUtc = testAlarmTime,
                    scheduledAt = currentTime,
                    userDismissed = false,
                    pendingIntentRequestCode = (testAlarmTime % Int.MAX_VALUE).toInt(),
                    lastEventModified = currentTime
                )
                
                val result = alarmScheduler.scheduleAlarm(testAlarm)
                
                if (result.success) {
                    Logger.i("SettingsViewModel", "Test alarm scheduled successfully for: ${Date(testAlarmTime)}")
                } else {
                    Logger.e("SettingsViewModel", "Failed to schedule test alarm: ${result.message}")
                }
                
                callback(result.success)
                
            } catch (e: Exception) {
                Logger.e("SettingsViewModel", "Exception while scheduling test alarm", e)
                callback(false)
            }
        }
    }
    
    private fun updateWorkStatus() {
        viewModelScope.launch {
            try {
                val status = workerManager.getWorkStatus()
                _workStatus.value = status
                Logger.d("SettingsViewModel", "Work status updated: ${status.state}, scheduled: ${status.isScheduled}")
            } catch (e: Exception) {
                Logger.e("SettingsViewModel", "Error getting work status", e)
                _workStatus.value = WorkerManager.WorkStatus(
                    isScheduled = false, 
                    state = "ERROR", 
                    errorMessage = e.message
                )
            }
        }
    }
    
    private fun updateLastSyncDescription() {
        viewModelScope.launch {
            val lastSyncTime = settingsRepository.getLastSyncTime()
            val description = if (lastSyncTime > 0) {
                val now = System.currentTimeMillis()
                val timeDiffMinutes = (now - lastSyncTime) / (60 * 1000)
                
                when {
                    timeDiffMinutes < 1 -> "Last sync: Just now"
                    timeDiffMinutes == 1L -> "Last sync: 1 minute ago"
                    timeDiffMinutes < 60 -> "Last sync: $timeDiffMinutes minutes ago"
                    else -> {
                        val hours = timeDiffMinutes / 60
                        if (hours == 1L) "Last sync: 1 hour ago" else "Last sync: $hours hours ago"
                    }
                }
            } else {
                "Last sync: Never"
            }
            
            _lastSyncDescription.value = description
            Logger.d("SettingsViewModel", "Last sync description updated: $description")
        }
    }
    
    /**
     * Force refresh the work status (useful when returning from settings)
     */
    fun refreshWorkStatus() {
        updateWorkStatus()
    }
    
    /**
     * Force refresh the last sync description
     */
    fun refreshLastSyncDescription() {
        updateLastSyncDescription()
    }
    
    /**
     * Defensive refresh - force update all UI from repository state
     * Use this when returning from background or if UI seems out of sync
     */
    fun refreshAllSettings() {
        Logger.i("SettingsViewModel", "Performing defensive refresh of all settings")
        settingsRepository.refreshAllStateFlows()
        updateWorkStatus()
        updateLastSyncDescription()
    }
    
    /**
     * Force refresh just the settings displays (not work status or sync time)
     */
    fun refreshSettingsDisplays() {
        Logger.i("SettingsViewModel", "Performing defensive refresh of settings displays only")
        settingsRepository.refreshAllStateFlows()
    }
    
    override fun onCleared() {
        super.onCleared()
        Logger.d("SettingsViewModel", "SettingsViewModel cleared")
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val workerManager: WorkerManager,
    private val alarmScheduler: AlarmScheduler
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsRepository, workerManager, alarmScheduler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}