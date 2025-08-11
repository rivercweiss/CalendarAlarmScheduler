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
import com.example.calendaralarmscheduler.utils.Logger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
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
        
        // No initial refresh needed - the ViewModel's combine() observer 
        // automatically loads data when the ViewModel is created
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
            // Fetch fresh calendar data from system when user presses refresh
            viewModel.refreshEvents()
        }
        
        binding.switchFilterMatching.setOnCheckedChangeListener { _, isChecked ->
            viewModel.toggleMatchingRulesFilter()
            // No need to call refreshEvents() - toggleMatchingRulesFilter() already updates the UI
        }
        
        // Long press on refresh FAB for test alarm
        binding.fabRefresh.setOnLongClickListener {
            showAlarmMonitoringMenu()
            true
        }
    }
    
    private fun observeViewModel() {
        // Observe events UI state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.eventsUiState.collect { uiState ->
                when (uiState) {
                    is UiState.Loading -> {
                        binding.layoutLoading.visibility = View.VISIBLE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.recyclerEvents.visibility = View.GONE
                    }
                    is UiState.Success -> {
                        binding.layoutLoading.visibility = View.GONE
                        adapter.submitList(uiState.data)
                        updateEmptyState(uiState.data)
                    }
                    is UiState.Error -> {
                        binding.layoutLoading.visibility = View.GONE
                        binding.layoutEmpty.visibility = View.GONE
                        binding.recyclerEvents.visibility = View.GONE
                        Toast.makeText(requireContext(), "Error: ${uiState.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        // Observe error message events SharedFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.collect { errorMessage ->
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
            }
        }
        
        // Observe filter state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentFilter.collect { filter ->
                // Avoid UI updates during memory pressure - only update if state actually changed
                if (binding.switchFilterMatching.isChecked != filter.showOnlyMatchingRules) {
                    try {
                        binding.switchFilterMatching.isChecked = filter.showOnlyMatchingRules
                    } catch (e: OutOfMemoryError) {
                        // Skip UI update if memory is exhausted - the switch state is not critical
                        android.util.Log.w("EventPreviewFragment", "Skipping switch update due to memory pressure", e)
                    }
                }
            }
        }
        
        // Observe alarm system status
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.alarmSystemStatus.collect { status ->
                updateAlarmSystemStatus(status)
            }
        }
        
        // Observe scheduling failures
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.schedulingFailures.collect { failures ->
                if (failures.isNotEmpty()) {
                    showSchedulingFailures(failures)
                }
            }
        }
    }
    
    private fun updateEmptyState(events: List<EventWithAlarms>) {
        val isEmpty = events.isEmpty()
        
        binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerEvents.visibility = if (isEmpty) View.GONE else View.VISIBLE
        
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
    
    // Cooldown mechanism to prevent infinite loops in alarm recovery
    private var lastAlarmRecoveryAttempt = 0L
    private var consecutiveRecoveryAttempts = 0
    private val RECOVERY_COOLDOWN_MS = 60000L // 60 seconds (increased from 30s)
    private val MAX_CONSECUTIVE_ATTEMPTS = 2 // Reduced from 3 to 2
    
    private fun updateAlarmSystemStatus(status: AlarmSystemStatus) {
        // Automatically detect and repair missing alarms with safeguards against infinite loops
        if (status.systemAlarms < status.activeAlarms) {
            val missedCount = status.activeAlarms - status.systemAlarms
            val currentTime = System.currentTimeMillis()
            
            // Circuit breaker: stop after too many consecutive attempts
            if (consecutiveRecoveryAttempts >= MAX_CONSECUTIVE_ATTEMPTS) {
                Logger.w("EventPreviewFragment", 
                    "Skipping alarm recovery - reached maximum consecutive attempts ($consecutiveRecoveryAttempts). " +
                    "Manual intervention may be required.")
                return
            }
            
            // Cooldown with exponential backoff: prevent rapid successive recovery attempts
            val currentCooldown = RECOVERY_COOLDOWN_MS * (1 shl consecutiveRecoveryAttempts) // Exponential backoff
            if (currentTime - lastAlarmRecoveryAttempt < currentCooldown) {
                val remainingCooldown = (currentCooldown - (currentTime - lastAlarmRecoveryAttempt)) / 1000
                Logger.d("EventPreviewFragment", 
                    "Skipping alarm recovery - exponential cooldown active for $remainingCooldown more seconds (attempt ${consecutiveRecoveryAttempts + 1})")
                return
            }
            
            Logger.w("EventPreviewFragment", 
                "Detected $missedCount missing alarm(s) - attempting automatic recovery (attempt ${consecutiveRecoveryAttempts + 1}/$MAX_CONSECUTIVE_ATTEMPTS)")
            
            lastAlarmRecoveryAttempt = currentTime
            consecutiveRecoveryAttempts++
            
            // Automatically attempt to reschedule missing alarms
            viewModel.rescheduleAllMissingAlarms()
        } else {
            // Reset recovery attempts counter when system is healthy
            if (consecutiveRecoveryAttempts > 0) {
                Logger.d("EventPreviewFragment", "Alarm system healthy - resetting recovery attempt counter")
                consecutiveRecoveryAttempts = 0
            }
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