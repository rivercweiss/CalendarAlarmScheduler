package com.example.calendaralarmscheduler.ui.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.launch
import com.example.calendaralarmscheduler.R
import com.example.calendaralarmscheduler.databinding.FragmentRuleEditBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RuleEditFragment : Fragment(), MenuProvider {
    private var _binding: FragmentRuleEditBinding? = null
    private val binding get() = _binding!!
    
    private val args: RuleEditFragmentArgs by navArgs()
    private val viewModel: RuleEditViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRuleEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup menu
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        
        setupViews()
        observeViewModel()
        
        // Load rule if editing existing one
        args.ruleId?.let { ruleId ->
            viewModel.loadRule(ruleId)
        }
    }

    private fun setupViews() {
        binding.apply {
            // Calendar selection button
            buttonSelectCalendars.setOnClickListener {
                showCalendarPickerDialog()
            }
            
            // Lead time button
            buttonSelectLeadTime.setOnClickListener {
                showLeadTimePickerDialog()
            }
            
            // Save button (also available in menu)
            buttonSave.setOnClickListener {
                saveRule()
            }
            
            // Initialize button texts with current ViewModel values
            val leadTimeMinutes = viewModel.leadTimeMinutes.value
            buttonSelectLeadTime.text = formatLeadTime(leadTimeMinutes)
            
            val selectedCalendars = viewModel.selectedCalendars.value
            val count = selectedCalendars.size
            buttonSelectCalendars.text = if (count == 0) {
                getString(R.string.select_calendars)
            } else {
                "$count calendar(s) selected"
            }
        }
    }

    private fun observeViewModel() {
        // Observe rule StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rule.collect { rule ->
                if (rule != null) {
                    binding.apply {
                        editTextRuleName.setText(rule.name)
                        editTextKeywordPattern.setText(rule.keywordPattern)
                        switchEnabled.isChecked = rule.enabled
                        switchFirstEventOnly.isChecked = rule.firstEventOfDayOnly
                    }
                }
            }
        }
        
        // Observe selected calendars StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedCalendars.collect { calendars ->
                val count = calendars.size
                binding.buttonSelectCalendars.text = if (count == 0) {
                    getString(R.string.select_calendars)
                } else {
                    "$count calendar(s) selected"
                }
            }
        }
        
        // Observe lead time StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.leadTimeMinutes.collect { minutes ->
                binding.buttonSelectLeadTime.text = formatLeadTime(minutes)
            }
        }
        
        // Observe loading state StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.buttonSave.isEnabled = !isLoading
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
        
        // Observe save result events SharedFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saveResult.collect { result ->
                when (result) {
                    is RuleEditViewModel.SaveResult.Success -> {
                        Toast.makeText(requireContext(), "Rule saved", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    is RuleEditViewModel.SaveResult.Error -> {
                        Toast.makeText(requireContext(), "Error: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        // Observe status message events SharedFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statusMessage.collect { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCalendarPickerDialog() {
        val dialog = CalendarPickerDialog.newInstance(viewModel.selectedCalendars.value)
        dialog.setOnCalendarsSelectedListener { selectedCalendars ->
            viewModel.setSelectedCalendars(selectedCalendars)
        }
        dialog.show(parentFragmentManager, "calendar_picker")
    }

    private fun showLeadTimePickerDialog() {
        val dialog = LeadTimePickerDialog.newInstance(viewModel.leadTimeMinutes.value ?: 30)
        dialog.setOnLeadTimeSelectedListener { minutes ->
            viewModel.setLeadTime(minutes)
        }
        dialog.show(parentFragmentManager, "lead_time_picker")
    }

    private fun saveRule() {
        val name = binding.editTextRuleName.text.toString().trim()
        val pattern = binding.editTextKeywordPattern.text.toString().trim()
        val enabled = binding.switchEnabled.isChecked
        val firstEventOfDayOnly = binding.switchFirstEventOnly.isChecked
        
        if (name.isEmpty()) {
            binding.editTextRuleName.error = "Rule name is required"
            return
        }
        
        if (pattern.isEmpty()) {
            binding.editTextKeywordPattern.error = "Keyword pattern is required"
            return
        }
        
        viewModel.saveRule(name, pattern, enabled, firstEventOfDayOnly)
    }

    private fun formatLeadTime(minutes: Int): String {
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 1440 -> "${minutes / 60}h ${minutes % 60}min"
            else -> "${minutes / 1440}d ${(minutes % 1440) / 60}h"
        }.replace(" 0min", "").replace(" 0h", "")
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.rule_edit_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_delete -> {
                deleteRule()
                true
            }
            else -> false
        }
    }

    private fun deleteRule() {
        viewModel.deleteRule {
            Toast.makeText(requireContext(), "Rule deleted", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}