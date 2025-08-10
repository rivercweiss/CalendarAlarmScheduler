package com.example.calendaralarmscheduler.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.entities.Rule
import com.example.calendaralarmscheduler.domain.RuleAlarmManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RuleListViewModel @Inject constructor(
    private val repository: RuleRepository,
    private val ruleAlarmManager: RuleAlarmManager
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _statusMessage = MutableSharedFlow<String>()
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()
    
    val rules: StateFlow<List<Rule>> = repository.getAllRules()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        // Set up RuleAlarmManager in the repository
        repository.setRuleAlarmManager(ruleAlarmManager)
    }
    
    fun updateRuleEnabled(rule: Rule, isEnabled: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val result = repository.updateRuleEnabledWithAlarmManagement(rule, isEnabled)
                
                if (result.success) {
                    _statusMessage.emit("✅ ${result.message}")
                    android.util.Log.i("RuleListViewModel", "Rule '${rule.name}' ${if (isEnabled) "enabled" else "disabled"}: ${result.message}")
                } else {
                    _statusMessage.emit("⚠️ ${result.message}")
                    android.util.Log.e("RuleListViewModel", "Failed to update rule '${rule.name}': ${result.message}")
                }
            } catch (e: Exception) {
                _statusMessage.emit("❌ Error updating rule: ${e.message}")
                android.util.Log.e("RuleListViewModel", "Error updating rule '${rule.name}'", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val result = repository.deleteRuleWithAlarmCleanup(rule)
                
                if (result.success) {
                    _statusMessage.emit("✅ ${result.message}")
                    android.util.Log.i("RuleListViewModel", "Rule '${rule.name}' deleted: ${result.message}")
                } else {
                    _statusMessage.emit("⚠️ ${result.message}")
                    android.util.Log.e("RuleListViewModel", "Failed to delete rule '${rule.name}': ${result.message}")
                }
            } catch (e: Exception) {
                _statusMessage.emit("❌ Error deleting rule: ${e.message}")
                android.util.Log.e("RuleListViewModel", "Error deleting rule '${rule.name}'", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}