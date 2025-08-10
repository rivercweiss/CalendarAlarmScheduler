package com.example.calendaralarmscheduler.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calendaralarmscheduler.databinding.FragmentEventPreviewBinding
import com.example.calendaralarmscheduler.domain.models.ScheduledAlarm
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class EventPreviewFragment : Fragment() {
    private var _binding: FragmentEventPreviewBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: EventPreviewViewModel by viewModels()
    private lateinit var adapter: EventPreviewAdapter
    
    private var isFilterVisible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Initial refresh
        viewModel.refreshEvents()
    }
    
    private fun setupRecyclerView() {
        adapter = EventPreviewAdapter(
            onEventClick = { eventWithAlarms ->
                showEventDetails(eventWithAlarms)
            },
            onAlarmClick = { alarm ->
                showAlarmDetails(alarm)
            }
        )
        
        binding.recyclerEvents.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerEvents.adapter = adapter
    }
    
    private fun setupClickListeners() {
        binding.fabFilter.setOnClickListener {
            toggleFilterVisibility()
        }
        
        binding.fabRefresh.setOnClickListener {
            viewModel.refreshEvents()
        }
        
        binding.btnFilterRule.setOnClickListener {
            showRuleFilterDialog()
        }
        
        binding.btnFilterCalendar.setOnClickListener {
            showCalendarFilterDialog()
        }
        
        binding.switchShowPast.setOnCheckedChangeListener { _, isChecked ->
            val currentFilter = viewModel.currentFilter.value
            viewModel.updateFilter(currentFilter.copy(showPastEvents = isChecked))
        }
        
        binding.btnClearFilters.setOnClickListener {
            viewModel.clearFilter()
            binding.switchShowPast.isChecked = false
            updateFilterButtonText()
        }
        
        // Long press on refresh FAB for test alarm
        binding.fabRefresh.setOnLongClickListener {
            showAlarmMonitoringMenu()
            true
        }
    }
    
    private fun observeViewModel() {
        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Observe error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
        
        // Observe events with alarms
        lifecycleScope.launch {
            viewModel.eventsWithAlarms.collect { eventsWithAlarms ->
                adapter.submitList(eventsWithAlarms)
                updateEmptyState(eventsWithAlarms)
            }
        }
        
        // Observe filter state
        lifecycleScope.launch {
            viewModel.currentFilter.collect { filter ->
                binding.switchShowPast.isChecked = filter.showPastEvents
                updateFilterButtonText()
            }
        }
        
        // Observe alarm system status
        lifecycleScope.launch {
            viewModel.alarmSystemStatus.collect { status ->
                updateAlarmSystemStatus(status)
            }
        }
        
        // Observe scheduling failures
        lifecycleScope.launch {
            viewModel.schedulingFailures.collect { failures ->
                if (failures.isNotEmpty()) {
                    showSchedulingFailures(failures)
                }
            }
        }
    }
    
    private fun updateEmptyState(events: List<EventWithAlarms>) {
        val isEmpty = events.isEmpty()
        val isLoading = viewModel.isLoading.value ?: false
        
        binding.layoutEmpty.visibility = if (isEmpty && !isLoading) View.VISIBLE else View.GONE
        binding.recyclerEvents.visibility = if (isEmpty && !isLoading) View.GONE else View.VISIBLE
        
        // Update empty message based on filter state
        val filter = viewModel.currentFilter.value
        val hasActiveFilter = filter.ruleId != null || filter.calendarId != null
        
        val message = if (hasActiveFilter) {
            "No events match the selected filters.\n\nTry adjusting your filter criteria or clearing filters."
        } else {
            "No upcoming events found.\n\nMake sure you have calendar events and rules configured."
        }
        
        binding.textEmptyMessage.text = message
    }
    
    private fun toggleFilterVisibility() {
        isFilterVisible = !isFilterVisible
        binding.cardFilters.visibility = if (isFilterVisible) View.VISIBLE else View.GONE
        
        // Update FAB text
        binding.fabFilter.text = if (isFilterVisible) "Hide Filters" else "Filter"
    }
    
    private fun showRuleFilterDialog() {
        viewModel.rules.value?.let { rules ->
            val ruleNames = listOf("All Rules") + rules.map { it.name }
            val currentFilter = viewModel.currentFilter.value
            val selectedIndex = if (currentFilter.ruleId == null) 0 else {
                rules.indexOfFirst { it.id == currentFilter.ruleId } + 1
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filter by Rule")
                .setSingleChoiceItems(ruleNames.toTypedArray(), selectedIndex) { dialog, which ->
                    val ruleId = if (which == 0) null else rules[which - 1].id
                    viewModel.setRuleFilter(ruleId)
                    updateFilterButtonText()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun showCalendarFilterDialog() {
        lifecycleScope.launch {
            val calendars = viewModel.getCalendarsWithNames()
            if (calendars.isEmpty()) {
                Toast.makeText(requireContext(), "No calendars found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val calendarNames = listOf("All Calendars") + calendars.values.toList()
            val calendarIds = listOf(-1L) + calendars.keys.toList()
            
            val currentFilter = viewModel.currentFilter.value
            val selectedIndex = if (currentFilter.calendarId == null) 0 else {
                calendarIds.indexOfFirst { it == currentFilter.calendarId }
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filter by Calendar")
                .setSingleChoiceItems(calendarNames.toTypedArray(), selectedIndex) { dialog, which ->
                    val calendarId = if (which == 0) null else calendarIds[which]
                    viewModel.setCalendarFilter(calendarId)
                    updateFilterButtonText()
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun updateFilterButtonText() {
        val filter = viewModel.currentFilter.value
        
        // Update rule filter button
        if (filter.ruleId != null) {
            viewModel.rules.value?.find { it.id == filter.ruleId }?.let { rule ->
                binding.btnFilterRule.text = rule.name
            }
        } else {
            binding.btnFilterRule.text = "All Rules"
        }
        
        // Update calendar filter button
        if (filter.calendarId != null) {
            lifecycleScope.launch {
                val calendars = viewModel.getCalendarsWithNames()
                calendars[filter.calendarId]?.let { calendarName ->
                    binding.btnFilterCalendar.text = calendarName
                } ?: run {
                    binding.btnFilterCalendar.text = "Unknown Calendar"
                }
            }
        } else {
            binding.btnFilterCalendar.text = "All Calendars"
        }
    }
    
    private fun showEventDetails(eventWithAlarms: EventWithAlarms) {
        val event = eventWithAlarms.event
        val alarms = eventWithAlarms.alarms
        val rules = eventWithAlarms.matchingRules
        
        val message = buildString {
            append("Event: ${event.title}\n\n")
            
            val startTime = event.getLocalStartTime()
            val endTime = event.getLocalEndTime()
            append("Time: ${startTime} - ${endTime}\n")
            
            if (event.location?.isNotBlank() == true) {
                append("Location: ${event.location}\n")
            }
            
            if (rules.isNotEmpty()) {
                append("\nMatching Rules (${rules.size}):\n")
                rules.forEach { rule ->
                    append("• ${rule.name} (${rule.leadTimeMinutes} min)\n")
                }
            }
            
            if (alarms.isNotEmpty()) {
                append("\nScheduled Alarms (${alarms.size}):\n")
                alarms.forEach { alarm ->
                    val status = when {
                        alarm.userDismissed -> "Dismissed"
                        alarm.isInPast() -> "Past"
                        else -> "Active"
                    }
                    append("• ${alarm.getLocalAlarmTime()} ($status)\n")
                }
            } else {
                append("\nNo alarms scheduled for this event.\n")
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Event Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showAlarmDetails(alarm: ScheduledAlarm) {
        val message = buildString {
            append("Event: ${alarm.eventTitle}\n\n")
            append("Alarm Time: ${alarm.getLocalAlarmTime()}\n")
            append("Event Time: ${alarm.getLocalEventStartTime()}\n")
            append("Lead Time: ${alarm.getLeadTimeMinutes()} minutes\n")
            append("Status: ${if (alarm.userDismissed) "Dismissed" else if (alarm.isInPast()) "Past" else "Active"}\n")
            
            if (alarm.isInFuture() && !alarm.userDismissed) {
                append("\nTime until alarm: ${alarm.formatTimeUntilAlarm()}")
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Alarm Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun showAlarmMonitoringMenu() {
        val options = arrayOf(
            "Check Alarm Status",
            "Test Alarm Scheduling", 
            "Retry Failed Alarms",
            "Clear Failures"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Alarm Monitoring")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.checkAlarmSystemStatus()
                    1 -> viewModel.testAlarmScheduling()
                    2 -> viewModel.retryFailedAlarms()
                    3 -> viewModel.clearSchedulingFailures()
                }
            }
            .show()
    }
    
    private fun updateAlarmSystemStatus(status: AlarmSystemStatus) {
        // Could update a status indicator in the UI
        // For now, we'll just log it and show a brief message if there are issues
        if (status.systemAlarms < status.activeAlarms) {
            val missedCount = status.activeAlarms - status.systemAlarms
            Toast.makeText(
                requireContext(), 
                "Warning: $missedCount alarm(s) may not be scheduled in system", 
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun showSchedulingFailures(failures: List<AlarmSchedulingFailure>) {
        if (failures.isEmpty()) return
        
        val message = buildString {
            append("Alarm Scheduling Issues (${failures.size}):\n\n")
            failures.take(5).forEach { failure ->
                append("• ${failure.eventTitle}\n")
                append("  ${failure.failureReason}\n")
                if (failure.retryCount > 0) {
                    append("  Retried ${failure.retryCount} time(s)\n")
                }
                append("\n")
            }
            if (failures.size > 5) {
                append("... and ${failures.size - 5} more")
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Alarm Scheduling Failures")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ ->
                viewModel.retryFailedAlarms()
            }
            .setNegativeButton("Dismiss") { _, _ ->
                viewModel.clearSchedulingFailures()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}