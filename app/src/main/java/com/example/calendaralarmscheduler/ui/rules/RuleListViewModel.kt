package com.example.calendaralarmscheduler.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.RuleRepository
import com.example.calendaralarmscheduler.data.database.AppDatabase
import com.example.calendaralarmscheduler.data.database.entities.Rule
import kotlinx.coroutines.launch

class RuleListViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: RuleRepository
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    init {
        val database = AppDatabase.getInstance(application)
        repository = RuleRepository(database.ruleDao())
        
        _isLoading.value = false
    }
    
    val rules = repository.getAllRules().asLiveData()
    
    fun updateRuleEnabled(rule: Rule, isEnabled: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val updatedRule = rule.copy(enabled = isEnabled)
                repository.updateRule(updatedRule)
            } catch (e: Exception) {
                // Handle error - could show a snackbar or toast
                android.util.Log.e("RuleListViewModel", "Error updating rule", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteRule(rule)
            } catch (e: Exception) {
                android.util.Log.e("RuleListViewModel", "Error deleting rule", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}