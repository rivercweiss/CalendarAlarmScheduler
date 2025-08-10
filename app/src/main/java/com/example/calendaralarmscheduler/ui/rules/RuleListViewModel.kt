package com.example.calendaralarmscheduler.ui.rules

import android.app.Application
import android.app.AlarmManager
import android.content.Context
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
import com.example.calendaralarmscheduler.domain.RuleAlarmManager
import kotlinx.coroutines.launch

class RuleListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: RuleRepository
    private val ruleAlarmManager: RuleAlarmManager
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _statusMessage = MutableLiveData<String?>()
    val statusMessage: LiveData<String?> = _statusMessage
    
    init {
        val database = AppDatabase.getInstance(application)
        repository = RuleRepository(database.ruleDao())
        
        // Initialize dependencies for RuleAlarmManager
        val alarmRepository = AlarmRepository(database.alarmDao())
        val calendarRepository = CalendarRepository(application)
        val alarmManager = application.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmScheduler = AlarmScheduler(application, alarmManager)
        val alarmSchedulingService = AlarmSchedulingService(alarmRepository, alarmScheduler)
        
        // Create and inject RuleAlarmManager
        ruleAlarmManager = RuleAlarmManager(
            repository,
            alarmRepository,
            alarmScheduler,
            calendarRepository,
            alarmSchedulingService
        )
        repository.setRuleAlarmManager(ruleAlarmManager)
        
        _isLoading.value = false
    }
    
    val rules = repository.getAllRules().asLiveData()
    
    fun updateRuleEnabled(rule: Rule, isEnabled: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = null
            
            try {
                val result = repository.updateRuleEnabledWithAlarmManagement(rule, isEnabled)
                
                if (result.success) {
                    _statusMessage.value = "✅ ${result.message}"
                    android.util.Log.i("RuleListViewModel", "Rule '${rule.name}' ${if (isEnabled) "enabled" else "disabled"}: ${result.message}")
                } else {
                    _statusMessage.value = "⚠️ ${result.message}"
                    android.util.Log.e("RuleListViewModel", "Failed to update rule '${rule.name}': ${result.message}")
                }
            } catch (e: Exception) {
                _statusMessage.value = "❌ Error updating rule: ${e.message}"
                android.util.Log.e("RuleListViewModel", "Error updating rule '${rule.name}'", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = null
            
            try {
                val result = repository.deleteRuleWithAlarmCleanup(rule)
                
                if (result.success) {
                    _statusMessage.value = "✅ ${result.message}"
                    android.util.Log.i("RuleListViewModel", "Rule '${rule.name}' deleted: ${result.message}")
                } else {
                    _statusMessage.value = "⚠️ ${result.message}"
                    android.util.Log.e("RuleListViewModel", "Failed to delete rule '${rule.name}': ${result.message}")
                }
            } catch (e: Exception) {
                _statusMessage.value = "❌ Error deleting rule: ${e.message}"
                android.util.Log.e("RuleListViewModel", "Error deleting rule '${rule.name}'", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}