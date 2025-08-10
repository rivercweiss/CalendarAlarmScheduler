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
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            // No payloads, do full bind
            onBindViewHolder(holder, position)
        } else {
            // Handle partial updates based on payloads
            val item = getItem(position)
            for (payload in payloads) {
                when (payload) {
                    PAYLOAD_SELECTION_CHANGED -> {
                        holder.updateSelectionOnly(item.isSelected)
                    }
                }
            }
        }
    }
    
    class ViewHolder(
        private val binding: ItemCalendarPickerBinding,
        private val onCalendarToggle: (CalendarRepository.CalendarInfo, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var isBinding = false
        
        private var currentItem: CalendarPickerItem? = null
        
        fun bind(item: CalendarPickerItem) {
            isBinding = true
            currentItem = item
            
            binding.apply {
                textCalendarName.text = item.calendar.displayName
                textAccountName.text = item.calendar.accountName
                
                // Set calendar color indicator
                viewColorIndicator.setBackgroundColor(item.calendar.color)
                
                // Clear listener before setting state to prevent triggering during bind
                checkboxSelected.setOnCheckedChangeListener(null)
                checkboxSelected.isChecked = item.isSelected
                
                // Set listener after state is properly initialized
                checkboxSelected.setOnCheckedChangeListener { _, isChecked ->
                    // Ignore listener calls during binding
                    if (!isBinding) {
                        onCalendarToggle(item.calendar, isChecked)
                    }
                }
                
                // Improve click handling - make entire row clickable with better UX
                root.setOnClickListener {
                    if (!isBinding) {
                        checkboxSelected.isChecked = !checkboxSelected.isChecked
                    }
                }
            }
            
            isBinding = false
        }
        
        fun updateSelectionOnly(isSelected: Boolean) {
            isBinding = true
            
            // Update only the checkbox state without affecting other UI elements
            binding.checkboxSelected.setOnCheckedChangeListener(null)
            binding.checkboxSelected.isChecked = isSelected
            binding.checkboxSelected.setOnCheckedChangeListener { _, isChecked ->
                if (!isBinding) {
                    currentItem?.let { item ->
                        onCalendarToggle(item.calendar, isChecked)
                    }
                }
            }
            
            isBinding = false
        }
    }
    
    private class CalendarPickerDiffCallback : DiffUtil.ItemCallback<CalendarPickerItem>() {
        override fun areItemsTheSame(oldItem: CalendarPickerItem, newItem: CalendarPickerItem): Boolean {
            return oldItem.calendar.id == newItem.calendar.id
        }
        
        override fun areContentsTheSame(oldItem: CalendarPickerItem, newItem: CalendarPickerItem): Boolean {
            // Check all relevant properties for content changes
            return oldItem.calendar.id == newItem.calendar.id &&
                   oldItem.calendar.displayName == newItem.calendar.displayName &&
                   oldItem.calendar.accountName == newItem.calendar.accountName &&
                   oldItem.calendar.color == newItem.calendar.color &&
                   oldItem.isSelected == newItem.isSelected
        }
        
        override fun getChangePayload(oldItem: CalendarPickerItem, newItem: CalendarPickerItem): Any? {
            // Return a payload for selection changes to enable efficient partial updates
            return if (oldItem.isSelected != newItem.isSelected) {
                PAYLOAD_SELECTION_CHANGED
            } else {
                null
            }
        }
    }
    
    companion object {
        private const val PAYLOAD_SELECTION_CHANGED = "selection_changed"
    }
}