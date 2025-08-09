package com.example.calendaralarmscheduler.ui.rules

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calendaralarmscheduler.databinding.ItemCalendarPickerBinding
import com.example.calendaralarmscheduler.data.CalendarRepository

class CalendarPickerAdapter(
    private val onCalendarToggle: (CalendarRepository.CalendarInfo, Boolean) -> Unit
) : ListAdapter<CalendarPickerItem, CalendarPickerAdapter.ViewHolder>(CalendarPickerDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCalendarPickerBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onCalendarToggle)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(
        private val binding: ItemCalendarPickerBinding,
        private val onCalendarToggle: (CalendarRepository.CalendarInfo, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: CalendarPickerItem) {
            binding.apply {
                textCalendarName.text = item.calendar.displayName
                textAccountName.text = item.calendar.accountName
                
                // Set calendar color indicator
                viewColorIndicator.setBackgroundColor(item.calendar.color)
                
                checkboxSelected.isChecked = item.isSelected
                checkboxSelected.setOnCheckedChangeListener { _, isChecked ->
                    onCalendarToggle(item.calendar, isChecked)
                }
                
                root.setOnClickListener {
                    checkboxSelected.isChecked = !checkboxSelected.isChecked
                }
            }
        }
    }
    
    private class CalendarPickerDiffCallback : DiffUtil.ItemCallback<CalendarPickerItem>() {
        override fun areItemsTheSame(oldItem: CalendarPickerItem, newItem: CalendarPickerItem): Boolean {
            return oldItem.calendar.id == newItem.calendar.id
        }
        
        override fun areContentsTheSame(oldItem: CalendarPickerItem, newItem: CalendarPickerItem): Boolean {
            return oldItem == newItem
        }
    }
}

data class CalendarPickerItem(
    val calendar: CalendarRepository.CalendarInfo,
    val isSelected: Boolean
)