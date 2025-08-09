package com.example.calendaralarmscheduler.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.AppDatabase
import com.example.calendaralarmscheduler.domain.RuleMatcher
import com.example.calendaralarmscheduler.data.database.entities.Rule
import kotlinx.coroutines.launch
import java.util.UUID

class RuleEditViewModel(application: Application) : AndroidViewModel(application) {
    
    private val ruleRepository: RuleRepository
    private val calendarRepository: CalendarRepository
    
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
    
    private var currentRuleId: String? = null
    
    init {
        val database = AppDatabase.getInstance(application)
        ruleRepository = RuleRepository(database.ruleDao())
        calendarRepository = CalendarRepository(application)
        
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
                    
                    // Load selected calendars
                    val allCalendars = _availableCalendars.value ?: emptyList()
                    val selectedCalendarInfos = allCalendars.filter { it.id in rule.calendarIds }
                    _selectedCalendars.value = selectedCalendarInfos
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
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error loading calendars", e)
                _availableCalendars.value = emptyList()
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
                    ruleRepository.updateRule(rule)
                } else {
                    ruleRepository.insertRule(rule)
                }
                
                _saveResult.value = SaveResult.Success
                
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error saving rule", e)
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
            try {
                ruleRepository.deleteRule(ruleToDelete)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("RuleEditViewModel", "Error deleting rule", e)
                _saveResult.value = SaveResult.Error(e.message ?: "Unknown error")
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