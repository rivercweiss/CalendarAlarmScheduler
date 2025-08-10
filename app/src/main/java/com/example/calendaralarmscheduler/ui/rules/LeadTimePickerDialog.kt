package com.example.calendaralarmscheduler.ui.rules

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.example.calendaralarmscheduler.databinding.DialogLeadTimePickerBinding

class LeadTimePickerDialog : DialogFragment() {
    
    private var _binding: DialogLeadTimePickerBinding? = null
    private val binding get() = _binding!!
    
    private var onLeadTimeSelectedListener: ((Int) -> Unit)? = null
    private var currentLeadTimeMinutes: Int = 30
    
    // Time units
    private enum class TimeUnit(val displayName: String, val minutesMultiplier: Int) {
        MINUTES("Minutes", 1),
        HOURS("Hours", 60),
        DAYS("Days", 1440)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLeadTimePickerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        currentLeadTimeMinutes = arguments?.getInt(ARG_CURRENT_LEAD_TIME, 30) ?: 30
        
        setupSpinner()
        setupInputField()
        setupButtons()
        initializeWithCurrentValue()
    }
    
    private fun setupSpinner() {
        val units = TimeUnit.values().map { it.displayName }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, units)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerUnit.adapter = adapter
        
        binding.spinnerUnit.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePreview()
                validateInput()
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }
    
    private fun setupInputField() {
        binding.editTextValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePreview()
                validateInput()
            }
        })
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonOk.setOnClickListener {
            val totalMinutes = getCurrentInputInMinutes()
            if (totalMinutes != null && isValidInput(totalMinutes)) {
                onLeadTimeSelectedListener?.invoke(totalMinutes)
                dismiss()
            }
        }
    }
    
    private fun initializeWithCurrentValue() {
        val (value, unit) = convertMinutesToBestUnit(currentLeadTimeMinutes)
        
        // Set spinner to the appropriate unit
        val unitIndex = TimeUnit.values().indexOf(unit)
        binding.spinnerUnit.setSelection(unitIndex)
        
        // Set input value
        binding.editTextValue.setText(value.toString())
        
        updatePreview()
        validateInput()
    }
    
    private fun convertMinutesToBestUnit(minutes: Int): Pair<Int, TimeUnit> {
        return when {
            minutes >= 1440 && minutes % 1440 == 0 -> {
                // Display in days if evenly divisible
                Pair(minutes / 1440, TimeUnit.DAYS)
            }
            minutes >= 60 && minutes % 60 == 0 -> {
                // Display in hours if evenly divisible  
                Pair(minutes / 60, TimeUnit.HOURS)
            }
            else -> {
                // Display in minutes
                Pair(minutes, TimeUnit.MINUTES)
            }
        }
    }
    
    private fun getCurrentInputInMinutes(): Int? {
        val valueText = binding.editTextValue.text.toString().trim()
        if (valueText.isEmpty()) return null
        
        val value = valueText.toIntOrNull() ?: return null
        val selectedUnit = TimeUnit.values()[binding.spinnerUnit.selectedItemPosition]
        
        return value * selectedUnit.minutesMultiplier
    }
    
    private fun updatePreview() {
        val totalMinutes = getCurrentInputInMinutes()
        if (totalMinutes != null) {
            binding.textPreview.text = formatDuration(totalMinutes)
            binding.textPreview.visibility = View.VISIBLE
        } else {
            binding.textPreview.visibility = View.GONE
        }
    }
    
    private fun validateInput(): Boolean {
        val totalMinutes = getCurrentInputInMinutes()
        val errorMessage = when {
            totalMinutes == null -> {
                if (binding.editTextValue.text.toString().trim().isEmpty()) {
                    "Please enter a value"
                } else {
                    "Please enter a valid number"
                }
            }
            totalMinutes <= 0 -> "Must be greater than 0"
            totalMinutes > 2880 -> "Maximum is 2 days (2880 minutes)"
            else -> null
        }
        
        val isValid = errorMessage == null && totalMinutes != null && isValidInput(totalMinutes)
        
        if (errorMessage != null) {
            binding.textError.text = errorMessage
            binding.textError.visibility = View.VISIBLE
        } else {
            binding.textError.visibility = View.GONE
        }
        
        binding.buttonOk.isEnabled = isValid
        return isValid
    }
    
    private fun isValidInput(totalMinutes: Int): Boolean {
        return totalMinutes in 1..2880
    }
    
    private fun formatDuration(minutes: Int): String {
        return when {
            minutes < 60 -> "Total: $minutes minute${if (minutes != 1) "s" else ""}"
            minutes < 1440 -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes == 0) {
                    "Total: $hours hour${if (hours != 1) "s" else ""}"
                } else {
                    "Total: ${hours}h ${remainingMinutes}min"
                }
            }
            else -> {
                val days = minutes / 1440
                val remainingHours = (minutes % 1440) / 60
                if (remainingHours == 0) {
                    "Total: $days day${if (days != 1) "s" else ""}"
                } else {
                    "Total: ${days}d ${remainingHours}h"
                }
            }
        }
    }
    
    fun setOnLeadTimeSelectedListener(listener: (Int) -> Unit) {
        onLeadTimeSelectedListener = listener
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val ARG_CURRENT_LEAD_TIME = "current_lead_time"
        
        fun newInstance(currentLeadTimeMinutes: Int): LeadTimePickerDialog {
            val dialog = LeadTimePickerDialog()
            val args = Bundle().apply {
                putInt(ARG_CURRENT_LEAD_TIME, currentLeadTimeMinutes)
            }
            dialog.arguments = args
            return dialog
        }
    }
}