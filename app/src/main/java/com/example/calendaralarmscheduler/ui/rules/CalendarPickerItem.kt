package com.example.calendaralarmscheduler.ui.rules

import com.example.calendaralarmscheduler.data.CalendarRepository

data class CalendarPickerItem(
    val calendar: CalendarRepository.CalendarInfo,
    val isSelected: Boolean
)