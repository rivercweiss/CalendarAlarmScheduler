package com.example.calendaralarmscheduler.ui.rules

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.calendaralarmscheduler.databinding.ItemLeadTimeBinding

class LeadTimeAdapter(
    private val options: List<LeadTimeOption>,
    private val onOptionSelected: (Int) -> Unit
) : RecyclerView.Adapter<LeadTimeAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLeadTimeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onOptionSelected)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(options[position])
    }
    
    override fun getItemCount(): Int = options.size
    
    class ViewHolder(
        private val binding: ItemLeadTimeBinding,
        private val onOptionSelected: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(option: LeadTimeOption) {
            binding.textLeadTime.text = option.displayText
            
            binding.root.setOnClickListener {
                onOptionSelected(option.minutes)
            }
        }
    }
}