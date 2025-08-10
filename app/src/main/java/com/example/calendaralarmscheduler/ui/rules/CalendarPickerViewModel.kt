package com.example.calendaralarmscheduler.ui.rules

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.utils.Logger
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
        Logger.i("CalendarPickerViewModel", "Initializing CalendarPickerViewModel")
        _selectedCalendars.value = emptyList()
        loadCalendars()
    }
    
    private fun loadCalendars() {
        Logger.i("CalendarPickerViewModel", "Starting to load available calendars")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Logger.d("CalendarPickerViewModel", "Calling calendarRepository.getAvailableCalendars()")
                val calendars = calendarRepository.getAvailableCalendars()
                Logger.i("CalendarPickerViewModel", "Successfully loaded ${calendars.size} calendars")
                
                if (calendars.isEmpty()) {
                    Logger.w("CalendarPickerViewModel", "No calendars found - this may indicate:")
                    Logger.w("CalendarPickerViewModel", "  1. No calendar accounts configured")
                    Logger.w("CalendarPickerViewModel", "  2. Calendar permissions not granted")
                    Logger.w("CalendarPickerViewModel", "  3. All calendars are hidden/disabled")
                } else {
                    Logger.d("CalendarPickerViewModel", "Calendar details:")
                    calendars.forEachIndexed { index, calendar ->
                        Logger.d("CalendarPickerViewModel", "  [$index] ${calendar.displayName} (${calendar.accountName})")
                    }
                }
                
                updateCalendarList(calendars)
                Logger.d("CalendarPickerViewModel", "Calendar list updated successfully")
            } catch (e: Exception) {
                Logger.e("CalendarPickerViewModel", "Error loading calendars", e)
                Logger.e("CalendarPickerViewModel", "Exception type: ${e::class.simpleName}")
                Logger.e("CalendarPickerViewModel", "Exception message: ${e.message}")
                _availableCalendars.value = emptyList()
            } finally {
                _isLoading.value = false
                Logger.d("CalendarPickerViewModel", "Calendar loading completed, isLoading set to false")
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
        // Prevent redundant updates
        val wasSelected = calendar.id in selectedCalendarIds
        if (wasSelected == isSelected) {
            Logger.d("CalendarPickerViewModel", "Toggle ignored - calendar ${calendar.displayName} already in state: $isSelected")
            return
        }
        
        if (isSelected) {
            selectedCalendarIds.add(calendar.id)
            Logger.d("CalendarPickerViewModel", "Added calendar to selection: ${calendar.displayName}")
        } else {
            selectedCalendarIds.remove(calendar.id)
            Logger.d("CalendarPickerViewModel", "Removed calendar from selection: ${calendar.displayName}")
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
        
        Logger.d("CalendarPickerViewModel", "Updating calendar list with new selection state")
        _availableCalendars.value = updatedList
        
        updateSelectedCalendars()
        Logger.d("CalendarPickerViewModel", "Toggle completed - selected count: ${selectedCalendarIds.size}")
    }
    
    private fun updateCalendarList(calendars: List<CalendarRepository.CalendarInfo>) {
        Logger.d("CalendarPickerViewModel", "Updating calendar list with ${calendars.size} calendars")
        val items = calendars.map { calendar ->
            val isSelected = calendar.id in selectedCalendarIds
            Logger.v("CalendarPickerViewModel", "Mapping calendar: ${calendar.displayName} (selected: $isSelected)")
            CalendarPickerItem(
                calendar = calendar,
                isSelected = isSelected
            )
        }
        Logger.d("CalendarPickerViewModel", "Created ${items.size} CalendarPickerItems")
        _availableCalendars.value = items
        Logger.d("CalendarPickerViewModel", "Updated _availableCalendars LiveData")
    }
    
    private fun updateSelectedCalendars() {
        val currentList = _availableCalendars.value ?: emptyList()
        val selected = currentList.filter { it.isSelected }.map { it.calendar }
        _selectedCalendars.value = selected
    }
    
    fun selectAll() {
        Logger.i("CalendarPickerViewModel", "Selecting all calendars")
        val currentList = _availableCalendars.value ?: emptyList()
        
        // Select all calendars
        val updatedList = currentList.map { item ->
            selectedCalendarIds.add(item.calendar.id)
            item.copy(isSelected = true)
        }
        
        _availableCalendars.value = updatedList
        updateSelectedCalendars()
        Logger.d("CalendarPickerViewModel", "All calendars selected - count: ${selectedCalendarIds.size}")
    }
    
    fun selectNone() {
        Logger.i("CalendarPickerViewModel", "Deselecting all calendars")
        val currentList = _availableCalendars.value ?: emptyList()
        
        // Deselect all calendars
        val updatedList = currentList.map { item ->
            selectedCalendarIds.remove(item.calendar.id)
            item.copy(isSelected = false)
        }
        
        _availableCalendars.value = updatedList
        updateSelectedCalendars()
        Logger.d("CalendarPickerViewModel", "All calendars deselected - count: ${selectedCalendarIds.size}")
    }
    
    fun toggleSelectAll() {
        val currentList = _availableCalendars.value ?: emptyList()
        val allSelected = currentList.isNotEmpty() && currentList.all { it.isSelected }
        
        if (allSelected) {
            selectNone()
        } else {
            selectAll()
        }
    }
    
    fun areAllCalendarsSelected(): Boolean {
        val currentList = _availableCalendars.value ?: emptyList()
        return currentList.isNotEmpty() && currentList.all { it.isSelected }
    }
}