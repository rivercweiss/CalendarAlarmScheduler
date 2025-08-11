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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sealed interface representing different UI states for a data type T
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>
}

@HiltViewModel
class RuleListViewModel @Inject constructor(
    private val repository: RuleRepository,
    private val ruleAlarmManager: RuleAlarmManager
) : ViewModel() {
    
    private val _statusMessage = MutableSharedFlow<String>()
    val statusMessage: SharedFlow<String> = _statusMessage.asSharedFlow()
    
    private val _operationState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val operationState: StateFlow<UiState<Unit>> = _operationState.asStateFlow()
    
    val uiState: StateFlow<UiState<List<Rule>>> = repository.getAllRules()
        .map<List<Rule>, UiState<List<Rule>>> { ruleList ->
            UiState.Success(ruleList)
        }
        .catch { throwable ->
            emit(UiState.Error("Failed to load rules: ${throwable.message}", throwable))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = 5000,
                replayExpirationMillis = 0 // Don't replay old values after timeout to save memory
            ),
            initialValue = UiState.Loading
        )
    
    init {
        // Set up RuleAlarmManager in the repository
        repository.setRuleAlarmManager(ruleAlarmManager)
    }
    
    fun updateRuleEnabled(rule: Rule, isEnabled: Boolean) {
        viewModelScope.launch {
            _operationState.value = UiState.Loading
            
            try {
                val result = repository.updateRuleEnabledWithAlarmManagement(rule, isEnabled)
                
                if (result.success) {
                    _statusMessage.emit("✅ ${result.message}")
                    _operationState.value = UiState.Success(Unit)
                    android.util.Log.i("RuleListViewModel", "Rule '${rule.name}' ${if (isEnabled) "enabled" else "disabled"}: ${result.message}")
                } else {
                    _statusMessage.emit("⚠️ ${result.message}")
                    _operationState.value = UiState.Error(result.message)
                    android.util.Log.e("RuleListViewModel", "Failed to update rule '${rule.name}': ${result.message}")
                }
            } catch (e: Exception) {
                val errorMessage = "Error updating rule: ${e.message}"
                _statusMessage.emit("❌ $errorMessage")
                _operationState.value = UiState.Error(errorMessage, e)
                android.util.Log.e("RuleListViewModel", "Error updating rule '${rule.name}'", e)
            }
        }
    }
    
    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            _operationState.value = UiState.Loading
            
            try {
                val result = repository.deleteRuleWithAlarmCleanup(rule)
                
                if (result.success) {
                    _statusMessage.emit("✅ ${result.message}")
                    _operationState.value = UiState.Success(Unit)
                    android.util.Log.i("RuleListViewModel", "Rule '${rule.name}' deleted: ${result.message}")
                } else {
                    _statusMessage.emit("⚠️ ${result.message}")
                    _operationState.value = UiState.Error(result.message)
                    android.util.Log.e("RuleListViewModel", "Failed to delete rule '${rule.name}': ${result.message}")
                }
            } catch (e: Exception) {
                val errorMessage = "Error deleting rule: ${e.message}"
                _statusMessage.emit("❌ $errorMessage")
                _operationState.value = UiState.Error(errorMessage, e)
                android.util.Log.e("RuleListViewModel", "Error deleting rule '${rule.name}'", e)
            }
        }
    }
}