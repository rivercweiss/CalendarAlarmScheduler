package com.example.calendaralarmscheduler.ui.rules

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calendaralarmscheduler.databinding.DialogCalendarPickerBinding
import com.example.calendaralarmscheduler.data.CalendarRepository

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
        
        viewModel = ViewModelProvider(this)[CalendarPickerViewModel::class.java]
        
        setupRecyclerView()
        setupButtons()
        observeViewModel()
        
        // Load initial selection
        val selectedIds = arguments?.getLongArray(ARG_SELECTED_CALENDAR_IDS)?.toList() ?: emptyList()
        viewModel.setInitialSelection(selectedIds)
    }
    
    private fun setupRecyclerView() {
        adapter = CalendarPickerAdapter { calendar, isSelected ->
            viewModel.toggleCalendar(calendar, isSelected)
        }
        
        binding.recyclerViewCalendars.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@CalendarPickerDialog.adapter
        }
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
        
        binding.buttonSelect.setOnClickListener {
            val selectedCalendars = viewModel.selectedCalendars.value ?: emptyList()
            onCalendarsSelectedListener?.invoke(selectedCalendars)
            dismiss()
        }
    }
    
    private fun observeViewModel() {
        viewModel.availableCalendars.observe(viewLifecycleOwner) { calendars ->
            adapter.submitList(calendars)
            
            binding.emptyStateText.visibility = if (calendars.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        
        viewModel.selectedCalendars.observe(viewLifecycleOwner) { selectedCalendars ->
            val count = selectedCalendars.size
            binding.buttonSelect.text = if (count == 0) {
                "Select"
            } else {
                "Select ($count)"
            }
            binding.buttonSelect.isEnabled = count > 0
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
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