package com.example.calendaralarmscheduler.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.CalendarRepository
import kotlinx.coroutines.launch

class CalendarPickerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val calendarRepository = CalendarRepository(application)
    
    private val _availableCalendars = MutableLiveData<List<CalendarPickerItem>>()
    val availableCalendars: LiveData<List<CalendarPickerItem>> = _availableCalendars
    
    private val _selectedCalendars = MutableLiveData<List<CalendarRepository.CalendarInfo>>()
    val selectedCalendars: LiveData<List<CalendarRepository.CalendarInfo>> = _selectedCalendars
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val selectedCalendarIds = mutableSetOf<Long>()
    
    init {
        _selectedCalendars.value = emptyList()
        loadCalendars()
    }
    
    private fun loadCalendars() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val calendars = calendarRepository.getAvailableCalendars()
                updateCalendarList(calendars)
            } catch (e: Exception) {
                android.util.Log.e("CalendarPickerViewModel", "Error loading calendars", e)
                _availableCalendars.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun setInitialSelection(calendarIds: List<Long>) {
        selectedCalendarIds.clear()
        selectedCalendarIds.addAll(calendarIds)
        
        // Update the available calendars list to reflect selection
        val currentCalendars = _availableCalendars.value
        if (!currentCalendars.isNullOrEmpty()) {
            updateCalendarList(currentCalendars.map { it.calendar })
        }
        
        updateSelectedCalendars()
    }
    
    fun toggleCalendar(calendar: CalendarRepository.CalendarInfo, isSelected: Boolean) {
        if (isSelected) {
            selectedCalendarIds.add(calendar.id)
        } else {
            selectedCalendarIds.remove(calendar.id)
        }
        
        // Update the list to reflect the new selection state
        val currentList = _availableCalendars.value ?: emptyList()
        val updatedList = currentList.map { item ->
            if (item.calendar.id == calendar.id) {
                item.copy(isSelected = isSelected)
            } else {
                item
            }
        }
        _availableCalendars.value = updatedList
        
        updateSelectedCalendars()
    }
    
    private fun updateCalendarList(calendars: List<CalendarRepository.CalendarInfo>) {
        val items = calendars.map { calendar ->
            CalendarPickerItem(
                calendar = calendar,
                isSelected = calendar.id in selectedCalendarIds
            )
        }
        _availableCalendars.value = items
    }
    
    private fun updateSelectedCalendars() {
        val currentList = _availableCalendars.value ?: emptyList()
        val selected = currentList.filter { it.isSelected }.map { it.calendar }
        _selectedCalendars.value = selected
    }
}