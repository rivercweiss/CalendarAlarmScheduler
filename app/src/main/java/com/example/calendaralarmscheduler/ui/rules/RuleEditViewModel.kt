package com.example.calendaralarmscheduler.ui.rules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.RuleAlarmManager
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.utils.PermissionUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RuleEditViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val calendarRepository: CalendarRepository,
    private val ruleAlarmManager: RuleAlarmManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    // STATE data - persistent UI state
    private val _rule = MutableStateFlow<Rule?>(null)
    val rule: StateFlow<Rule?> = _rule.asStateFlow()
    
    private val _selectedCalendars = MutableStateFlow<List<CalendarRepository.CalendarInfo>>(emptyList())
    val selectedCalendars: StateFlow<List<CalendarRepository.CalendarInfo>> = _selectedCalendars.asStateFlow()
    
    private val _leadTimeMinutes = MutableStateFlow(30) // Default 30 minutes
    val leadTimeMinutes: StateFlow<Int> = _leadTimeMinutes.asStateFlow()
    
    private val _availableCalendars = MutableStateFlow<List<CalendarRepository.CalendarInfo>>(emptyList())
    val availableCalendars: StateFlow<List<CalendarRepository.CalendarInfo>> = _availableCalendars.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // EVENT data - one-time events
    private val _saveResult = MutableSharedFlow<SaveResult>()
    val saveResult: SharedFlow<SaveResult> = _saveResult.asSharedFlow()
    
    private val _statusMessage = MutableSharedFlow<String>()
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()
    
    private var currentRuleId: String? = null
    private var pendingCalendarIds: List<Long>? = null // For storing calendar IDs while calendars are loading
    
    init {
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
            
            try {
                // Check if all critical permissions are granted
                val permissionStatus = PermissionUtils.getAllPermissionStatus(context)
                if (!permissionStatus.areAllGranted()) {
                    val missingPermissions = mutableListOf<String>()
                    if (!permissionStatus.hasCalendarPermission) missingPermissions.add("Calendar access")
                    if (!permissionStatus.hasNotificationPermission) missingPermissions.add("Notification permission")
                    if (!permissionStatus.hasExactAlarmPermission) missingPermissions.add("Exact alarm permission")
                    
                    _saveResult.emit(SaveResult.Error("Missing required permissions: ${missingPermissions.joinToString(", ")}. Please grant all permissions before creating rules."))
                    return@launch
                }
                
                val selectedCalendarIds = _selectedCalendars.value.map { it.id }
                val leadTime = _leadTimeMinutes.value
                
                if (selectedCalendarIds.isEmpty()) {
                    _saveResult.emit(SaveResult.Error("Please select at least one calendar"))
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
                        _saveResult.emit(SaveResult.Error("Rule not found"))
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
                        _statusMessage.emit("✅ ${result.message}")
                        _saveResult.emit(SaveResult.Success)
                        android.util.Log.i("RuleEditViewModel", "Rule '${rule.name}' updated: ${result.message}")
                    } else {
                        _statusMessage.emit("⚠️ ${result.message}")
                        _saveResult.emit(SaveResult.Error(result.message))
                        android.util.Log.e("RuleEditViewModel", "Failed to update rule '${rule.name}': ${result.message}")
                    }
                } else {
                    // Create new rule with immediate alarm scheduling
                    ruleRepository.insertRule(rule)
                    
                    if (rule.enabled) {
                        // Schedule alarms for the newly created enabled rule
                        val result = ruleAlarmManager.updateRuleEnabled(rule, true)
                        
                        if (result.success) {
                            _statusMessage.emit("✅ ${result.message}")
                            _saveResult.emit(SaveResult.Success)
                            android.util.Log.i("RuleEditViewModel", "Rule '${rule.name}' created with immediate alarm scheduling: ${result.message}")
                        } else {
                            _statusMessage.emit("⚠️ Rule created but ${result.message}")
                            _saveResult.emit(SaveResult.Error("Rule created but ${result.message}"))
                            android.util.Log.e("RuleEditViewModel", "Rule '${rule.name}' created but alarm scheduling failed: ${result.message}")
                        }
                    } else {
                        // Rule created but disabled - no alarm scheduling needed
                        _statusMessage.emit("✅ Rule created successfully (disabled)")
                        _saveResult.emit(SaveResult.Success)
                        android.util.Log.i("RuleEditViewModel", "Rule '${rule.name}' created in disabled state")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error saving rule", e)
                _statusMessage.emit("❌ Error saving rule: ${e.message}")
                _saveResult.emit(SaveResult.Error(e.message ?: "Unknown error"))
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
            
            try {
                val result = ruleAlarmManager.deleteRuleWithAlarmCleanup(ruleToDelete)
                
                if (result.success) {
                    _statusMessage.emit("✅ ${result.message}")
                    android.util.Log.i("RuleEditViewModel", "Rule '${ruleToDelete.name}' deleted: ${result.message}")
                    onSuccess()
                } else {
                    _statusMessage.emit("⚠️ ${result.message}")
                    _saveResult.emit(SaveResult.Error(result.message))
                    android.util.Log.e("RuleEditViewModel", "Failed to delete rule '${ruleToDelete.name}': ${result.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error deleting rule", e)
                _statusMessage.emit("❌ Error deleting rule: ${e.message}")
                _saveResult.emit(SaveResult.Error(e.message ?: "Unknown error"))
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    sealed class SaveResult {
        object Success : SaveResult()
        data class Error(val message: String) : SaveResult()
    }
}