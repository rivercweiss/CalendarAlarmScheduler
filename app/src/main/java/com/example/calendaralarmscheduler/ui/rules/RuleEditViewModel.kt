package com.example.calendaralarmscheduler.ui.rules

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.AlarmRepository
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.AppDatabase
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.AlarmScheduler
import com.example.calendaralarmscheduler.domain.AlarmSchedulingService
import com.example.calendaralarmscheduler.domain.RuleAlarmManager
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.utils.PermissionUtils
import kotlinx.coroutines.launch
import java.util.UUID

class RuleEditViewModel(application: Application) : AndroidViewModel(application) {
    
    private val ruleRepository: RuleRepository
    private val calendarRepository: CalendarRepository
    private val ruleAlarmManager: RuleAlarmManager
    
    private val _rule = MutableLiveData<Rule?>()
    val rule: LiveData<Rule?> = _rule
    
    private val _selectedCalendars = MutableLiveData<List<CalendarRepository.CalendarInfo>>()
    val selectedCalendars: LiveData<List<CalendarRepository.CalendarInfo>> = _selectedCalendars
    
    private val _leadTimeMinutes = MutableLiveData<Int>()
    val leadTimeMinutes: LiveData<Int> = _leadTimeMinutes
    
    private val _availableCalendars = MutableLiveData<List<CalendarRepository.CalendarInfo>>()
    val availableCalendars: LiveData<List<CalendarRepository.CalendarInfo>> = _availableCalendars
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _saveResult = MutableLiveData<SaveResult>()
    val saveResult: LiveData<SaveResult> = _saveResult
    
    private val _statusMessage = MutableLiveData<String?>()
    val statusMessage: LiveData<String?> = _statusMessage
    
    private var currentRuleId: String? = null
    private var pendingCalendarIds: List<Long>? = null // For storing calendar IDs while calendars are loading
    
    init {
        val database = AppDatabase.getInstance(application)
        ruleRepository = RuleRepository(database.ruleDao())
        calendarRepository = CalendarRepository(application)
        
        // Initialize dependencies for RuleAlarmManager
        val alarmRepository = AlarmRepository(database.alarmDao())
        val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmScheduler = AlarmScheduler(application, alarmManager)
        val alarmSchedulingService = AlarmSchedulingService(alarmRepository, alarmScheduler)
        
        // Create and inject RuleAlarmManager
        ruleAlarmManager = RuleAlarmManager(
            ruleRepository,
            alarmRepository,
            alarmScheduler,
            calendarRepository,
            alarmSchedulingService
        )
        ruleRepository.setRuleAlarmManager(ruleAlarmManager)
        
        // Set default values
        _selectedCalendars.value = emptyList()
        _leadTimeMinutes.value = 30 // Default 30 minutes
        _isLoading.value = false
        
        loadAvailableCalendars()
    }
    
    fun loadRule(ruleId: String) {
        currentRuleId = ruleId
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val rule = ruleRepository.getRuleById(ruleId)
                _rule.value = rule
                
                if (rule != null) {
                    _leadTimeMinutes.value = rule.leadTimeMinutes
                    
                    // Store calendar IDs for later application
                    pendingCalendarIds = rule.calendarIds
                    
                    // Try to apply selection if calendars are already loaded
                    applyPendingCalendarSelection()
                }
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error loading rule", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun loadAvailableCalendars() {
        viewModelScope.launch {
            try {
                val calendars = calendarRepository.getAvailableCalendars()
                _availableCalendars.value = calendars
                
                // Apply pending calendar selection (for existing rule edits)
                applyPendingCalendarSelection()
                
                // For new rules, select all calendars by default
                if (currentRuleId == null && _selectedCalendars.value.isNullOrEmpty() && calendars.isNotEmpty()) {
                    _selectedCalendars.value = calendars
                }
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error loading calendars", e)
                _availableCalendars.value = emptyList()
            }
        }
    }
    
    private fun applyPendingCalendarSelection() {
        val pendingIds = pendingCalendarIds
        if (pendingIds != null && !pendingIds.isEmpty()) {
            val allCalendars = _availableCalendars.value
            if (!allCalendars.isNullOrEmpty()) {
                val selectedCalendarInfos = allCalendars.filter { it.id in pendingIds }
                _selectedCalendars.value = selectedCalendarInfos
                pendingCalendarIds = null // Clear after applying
            }
        }
    }
    
    fun setSelectedCalendars(calendars: List<CalendarRepository.CalendarInfo>) {
        _selectedCalendars.value = calendars
    }
    
    fun setLeadTime(minutes: Int) {
        _leadTimeMinutes.value = minutes
    }
    
    fun saveRule(name: String, pattern: String, enabled: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = null
            
            try {
                // Check if all critical permissions are granted
                val permissionStatus = PermissionUtils.getAllPermissionStatus(getApplication())
                if (!permissionStatus.areAllGranted()) {
                    val missingPermissions = mutableListOf<String>()
                    if (!permissionStatus.hasCalendarPermission) missingPermissions.add("Calendar access")
                    if (!permissionStatus.hasNotificationPermission) missingPermissions.add("Notification permission")
                    if (!permissionStatus.hasExactAlarmPermission) missingPermissions.add("Exact alarm permission")
                    
                    _saveResult.value = SaveResult.Error("Missing required permissions: ${missingPermissions.joinToString(", ")}. Please grant all permissions before creating rules.")
                    return@launch
                }
                
                val selectedCalendarIds = _selectedCalendars.value?.map { it.id } ?: emptyList()
                val leadTime = _leadTimeMinutes.value ?: 30
                
                if (selectedCalendarIds.isEmpty()) {
                    _saveResult.value = SaveResult.Error("Please select at least one calendar")
                    return@launch
                }
                
                // Auto-detect if pattern is regex
                val isRegex = RuleMatcher.isRegex(pattern)
                
                val rule = if (currentRuleId != null) {
                    // Update existing rule
                    val existingRule = _rule.value
                    if (existingRule != null) {
                        existingRule.copy(
                            name = name,
                            keywordPattern = pattern,
                            isRegex = isRegex,
                            calendarIds = selectedCalendarIds,
                            leadTimeMinutes = leadTime,
                            enabled = enabled
                        )
                    } else {
                        _saveResult.value = SaveResult.Error("Rule not found")
                        return@launch
                    }
                } else {
                    // Create new rule
                    Rule(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        keywordPattern = pattern,
                        isRegex = isRegex,
                        calendarIds = selectedCalendarIds,
                        leadTimeMinutes = leadTime,
                        enabled = enabled,
                        createdAt = System.currentTimeMillis()
                    )
                }
                
                if (currentRuleId != null) {
                    // Update existing rule with proper alarm management
                    val existingRule = _rule.value!!
                    val result = ruleAlarmManager.updateRuleWithAlarmManagement(existingRule, rule)
                    
                    if (result.success) {
                        _statusMessage.value = "✅ ${result.message}"
                        _saveResult.value = SaveResult.Success
                        android.util.Log.i("RuleEditViewModel", "Rule '${rule.name}' updated: ${result.message}")
                    } else {
                        _statusMessage.value = "⚠️ ${result.message}"
                        _saveResult.value = SaveResult.Error(result.message)
                        android.util.Log.e("RuleEditViewModel", "Failed to update rule '${rule.name}': ${result.message}")
                    }
                } else {
                    // Create new rule (no alarm management needed yet)
                    ruleRepository.insertRule(rule)
                    _statusMessage.value = "✅ Rule created successfully"
                    _saveResult.value = SaveResult.Success
                }
                
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error saving rule", e)
                _statusMessage.value = "❌ Error saving rule: ${e.message}"
                _saveResult.value = SaveResult.Error(e.message ?: "Unknown error")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteRule(onSuccess: () -> Unit) {
        val ruleToDelete = _rule.value
        if (ruleToDelete == null) {
            onSuccess()
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = null
            
            try {
                val result = ruleAlarmManager.deleteRuleWithAlarmCleanup(ruleToDelete)
                
                if (result.success) {
                    _statusMessage.value = "✅ ${result.message}"
                    android.util.Log.i("RuleEditViewModel", "Rule '${ruleToDelete.name}' deleted: ${result.message}")
                    onSuccess()
                } else {
                    _statusMessage.value = "⚠️ ${result.message}"
                    _saveResult.value = SaveResult.Error(result.message)
                    android.util.Log.e("RuleEditViewModel", "Failed to delete rule '${ruleToDelete.name}': ${result.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error deleting rule", e)
                _statusMessage.value = "❌ Error deleting rule: ${e.message}"
                _saveResult.value = SaveResult.Error(e.message ?: "Unknown error")
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearStatusMessage() {
        _statusMessage.value = null
    }
    
    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}