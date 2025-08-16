package com.example.calendaralarmscheduler.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.PermissionUtils
import com.example.calendaralarmscheduler.workers.BackgroundRefreshManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backgroundRefreshManager: BackgroundRefreshManager,
    private val alarmScheduler: AlarmScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _refreshIntervalDescription = MutableStateFlow("")
    val refreshIntervalDescription: StateFlow<String> = _refreshIntervalDescription.asStateFlow()
    
    private val _allDayTimeDescription = MutableStateFlow("")
    val allDayTimeDescription: StateFlow<String> = _allDayTimeDescription.asStateFlow()
    
    private val _workStatus = MutableStateFlow(BackgroundRefreshManager.WorkStatus(isScheduled = false, state = "UNKNOWN"))
    val workStatus: StateFlow<BackgroundRefreshManager.WorkStatus> = _workStatus.asStateFlow()
    
    private val _lastSyncDescription = MutableStateFlow("")
    val lastSyncDescription: StateFlow<String> = _lastSyncDescription.asStateFlow()
    
    
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
                backgroundRefreshManager.reschedulePeriodicRefresh(minutes)
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
    
    
    fun resetSettings() {
        Logger.i("SettingsViewModel", "Resetting all settings to defaults")
        settingsRepository.resetToDefaults()
        
        // Reschedule background worker with default interval
        viewModelScope.launch {
            try {
                backgroundRefreshManager.reschedulePeriodicRefresh(BackgroundRefreshManager.DEFAULT_INTERVAL_MINUTES)
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
                Logger.i("SettingsViewModel", "=== STARTING TEST ALARM SCHEDULING ===")
                
                // Comprehensive permission validation
                Logger.i("SettingsViewModel", "Validating all permissions...")
                val permissionStatus = PermissionUtils.getAllPermissionStatus(context)
                
                Logger.d("SettingsViewModel", "Calendar permission: ${permissionStatus.hasCalendarPermission}")
                Logger.d("SettingsViewModel", "Notification permission: ${permissionStatus.hasNotificationPermission}")
                Logger.d("SettingsViewModel", "Exact alarm permission: ${permissionStatus.hasExactAlarmPermission}")
                Logger.d("SettingsViewModel", "Battery optimization whitelisted: ${permissionStatus.isBatteryOptimizationWhitelisted}")
                
                if (!permissionStatus.hasCalendarPermission) {
                    Logger.e("SettingsViewModel", "❌ Calendar permission not granted")
                    callback(false)
                    return@launch
                }
                
                if (!permissionStatus.hasNotificationPermission) {
                    Logger.e("SettingsViewModel", "❌ Notification permission not granted")
                    callback(false)
                    return@launch
                }
                
                if (!permissionStatus.hasExactAlarmPermission) {
                    Logger.e("SettingsViewModel", "❌ Exact alarm permission not granted")
                    callback(false)
                    return@launch
                }
                
                // Additional AlarmManager capability check
                val canSchedule = alarmScheduler.canScheduleExactAlarms()
                Logger.d("SettingsViewModel", "AlarmManager can schedule exact alarms: $canSchedule")
                
                if (!canSchedule) {
                    Logger.e("SettingsViewModel", "❌ AlarmManager reports cannot schedule exact alarms")
                    callback(false)
                    return@launch
                }
                
                Logger.i("SettingsViewModel", "✅ All permission checks passed")
                
                // Create a test alarm 10 seconds from now
                val currentTime = System.currentTimeMillis()
                val testAlarmTime = currentTime + 10_000 // 10 seconds
                
                Logger.i("SettingsViewModel", "Creating test alarm for: ${Date(testAlarmTime)}")
                
                // Create a simple test alarm
                val testAlarm = ScheduledAlarm(
                    eventId = "test_alarm",
                    ruleId = "test_rule",
                    eventTitle = "Calendar Alarm Test",
                    eventStartTimeUtc = testAlarmTime + 60000, // 1 minute after alarm
                    alarmTimeUtc = testAlarmTime,
                    pendingIntentRequestCode = "test_alarm".hashCode(),
                    lastEventModified = System.currentTimeMillis()
                )
                val success = alarmScheduler.scheduleAlarm(testAlarm)
                
                if (success) {
                    Logger.i("SettingsViewModel", "✅ Test alarm scheduled successfully!")
                } else {
                    Logger.e("SettingsViewModel", "❌ Failed to schedule test alarm")
                }
                
                callback(success)
                
            } catch (e: Exception) {
                Logger.e("SettingsViewModel", "❌ Exception while scheduling test alarm", e)
                Logger.e("SettingsViewModel", "Stack trace: ${e.stackTraceToString()}")
                callback(false)
            } finally {
                Logger.i("SettingsViewModel", "=== TEST ALARM SCHEDULING COMPLETE ===")
            }
        }
    }
    
    private fun updateWorkStatus() {
        viewModelScope.launch {
            try {
                val status = backgroundRefreshManager.getWorkStatus()
                _workStatus.value = status
                Logger.d("SettingsViewModel", "Work status updated: ${status.state}, scheduled: ${status.isScheduled}")
            } catch (e: Exception) {
                Logger.e("SettingsViewModel", "Error getting work status", e)
                _workStatus.value = BackgroundRefreshManager.WorkStatus(
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
        updateWorkStatus()
        updateLastSyncDescription()
    }
    
    /**
     * Force refresh just the settings displays (not work status or sync time)
     */
    fun refreshSettingsDisplays() {
        Logger.i("SettingsViewModel", "Performing defensive refresh of settings displays only")
    }
    
    override fun onCleared() {
        super.onCleared()
        Logger.d("SettingsViewModel", "SettingsViewModel cleared")
    }
}