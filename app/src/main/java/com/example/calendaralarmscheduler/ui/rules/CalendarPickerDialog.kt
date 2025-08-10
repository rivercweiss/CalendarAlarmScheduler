package com.example.calendaralarmscheduler.ui.rules

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calendaralarmscheduler.databinding.DialogCalendarPickerBinding
import com.example.calendaralarmscheduler.data.CalendarRepository
import com.example.calendaralarmscheduler.utils.Logger
import kotlinx.coroutines.launch

class CalendarPickerDialog : DialogFragment() {
    
    private var _binding: DialogCalendarPickerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: CalendarPickerAdapter
    private lateinit var viewModel: CalendarPickerViewModel
    
    private var onCalendarsSelectedListener: ((List<CalendarRepository.CalendarInfo>) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCalendarPickerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Logger.i("CalendarPickerDialog", "onViewCreated - Setting up calendar picker dialog")
        viewModel = ViewModelProvider(this)[CalendarPickerViewModel::class.java]
        Logger.d("CalendarPickerDialog", "ViewModel created successfully")
        
        setupRecyclerView()
        setupButtons()
        observeViewModel()
        
        // Load initial selection
        val selectedIds = arguments?.getLongArray(ARG_SELECTED_CALENDAR_IDS)?.toList() ?: emptyList()
        Logger.d("CalendarPickerDialog", "Setting initial selection: ${selectedIds.size} calendars")
        viewModel.setInitialSelection(selectedIds)
    }
    
    private fun setupRecyclerView() {
        Logger.d("CalendarPickerDialog", "Setting up RecyclerView")
        adapter = CalendarPickerAdapter { calendar, isSelected ->
            Logger.d("CalendarPickerDialog", "Calendar toggled: ${calendar.displayName} -> $isSelected")
            viewModel.toggleCalendar(calendar, isSelected)
        }
        
        binding.recyclerViewCalendars.apply {
            Logger.d("CalendarPickerDialog", "Configuring RecyclerView with LinearLayoutManager")
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CalendarPickerDialog.adapter
            Logger.d("CalendarPickerDialog", "RecyclerView adapter attached successfully")
        }
        Logger.i("CalendarPickerDialog", "RecyclerView setup completed")
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonSelect.setOnClickListener {
            val selectedCalendars = viewModel.selectedCalendars.value
            onCalendarsSelectedListener?.invoke(selectedCalendars)
            dismiss()
        }
        
        binding.buttonSelectAll.setOnClickListener {
            viewModel.toggleSelectAll()
        }
    }
    
    private fun observeViewModel() {
        Logger.d("CalendarPickerDialog", "Setting up ViewModel observers")
        
        // Observe available calendars StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableCalendars.collect { calendars ->
                Logger.i("CalendarPickerDialog", "Received calendar list update: ${calendars.size} calendars")
                
                if (calendars.isEmpty()) {
                    Logger.w("CalendarPickerDialog", "Calendar list is empty - showing empty state")
                    Logger.d("CalendarPickerDialog", "Setting emptyStateText visibility to VISIBLE")
                } else {
                    Logger.d("CalendarPickerDialog", "Submitting calendar list to adapter:")
                    calendars.forEachIndexed { index, item ->
                        Logger.d("CalendarPickerDialog", "  [$index] ${item.calendar.displayName} (selected: ${item.isSelected})")
                    }
                    Logger.d("CalendarPickerDialog", "Setting emptyStateText visibility to GONE")
                }
                
                Logger.d("CalendarPickerDialog", "Calling adapter.submitList() with ${calendars.size} items")
                adapter.submitList(calendars)
                Logger.d("CalendarPickerDialog", "adapter.submitList() completed")
                
                binding.emptyStateText.visibility = if (calendars.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                Logger.d("CalendarPickerDialog", "UI visibility states updated")
                
                // Update select all button text based on selection state
                val allSelected = calendars.isNotEmpty() && calendars.all { it.isSelected }
                binding.buttonSelectAll.text = if (allSelected) {
                    "Deselect All"
                } else {
                    "Select All"
                }
                Logger.d("CalendarPickerDialog", "Select all button updated: '${binding.buttonSelectAll.text}'")
            }
        }
        
        // Observe selected calendars StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedCalendars.collect { selectedCalendars ->
                val count = selectedCalendars.size
                Logger.d("CalendarPickerDialog", "Selected calendars count changed: $count")
                binding.buttonSelect.text = if (count == 0) {
                    "Select"
                } else {
                    "Select ($count)"
                }
                binding.buttonSelect.isEnabled = count > 0
                Logger.d("CalendarPickerDialog", "Button text updated: '${binding.buttonSelect.text}', enabled: ${binding.buttonSelect.isEnabled}")
            }
        }
        
        // Observe loading state StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                Logger.d("CalendarPickerDialog", "Loading state changed: $isLoading")
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                Logger.d("CalendarPickerDialog", "Progress bar visibility: ${if (isLoading) "VISIBLE" else "GONE"}")
            }
        }
    }
    
    fun setOnCalendarsSelectedListener(listener: (List<CalendarRepository.CalendarInfo>) -> Unit) {
        onCalendarsSelectedListener = listener
    }
    
    override fun onStart() {
        super.onStart()
        // Make dialog larger
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
        private const val ARG_SELECTED_CALENDAR_IDS = "selected_calendar_ids"
        
        fun newInstance(selectedCalendars: List<CalendarRepository.CalendarInfo>): CalendarPickerDialog {
            val dialog = CalendarPickerDialog()
            val args = Bundle().apply {
                putLongArray(ARG_SELECTED_CALENDAR_IDS, selectedCalendars.map { it.id }.toLongArray())
            }
            dialog.arguments = args
            return dialog
        }
    }
}