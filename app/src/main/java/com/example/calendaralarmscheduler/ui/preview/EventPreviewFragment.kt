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
        binding.fabRefresh.setOnClickListener {
            viewModel.refreshEvents()
        }
        
        binding.switchFilterMatching.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleMatchingRulesFilter()
            viewModel.refreshEvents()
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
                binding.switchFilterMatching.isChecked = filter.showOnlyMatchingRules
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
        
        val message = if (filter.showOnlyMatchingRules) {
            "No events matching your rules in the next 2 days.\n\nTry toggling off the filter to see all upcoming events."
        } else {
            "No upcoming events found in the next 2 days.\n\nMake sure you have calendar events scheduled."
        }
        
        binding.textEmptyMessage.text = message
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
            "Schedule Alarms Now",
            "Check Alarm Status",
            "Test Alarm Scheduling", 
            "Retry Failed Alarms",
            "Clear Failures"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Alarm Management")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.scheduleAlarmsNow()
                    1 -> viewModel.checkAlarmSystemStatus()
                    2 -> viewModel.testAlarmScheduling()
                    3 -> viewModel.retryFailedAlarms()
                    4 -> viewModel.clearSchedulingFailures()
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