package com.example.calendaralarmscheduler.ui.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calendaralarmscheduler.R
import com.example.calendaralarmscheduler.databinding.FragmentRuleListBinding
import com.example.calendaralarmscheduler.data.database.entities.Rule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RuleListFragment : Fragment() {
    private var _binding: FragmentRuleListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: RuleListViewModel by viewModels()
    private lateinit var ruleAdapter: RuleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRuleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        ruleAdapter = RuleAdapter(
            onRuleClick = { rule ->
                // Navigate to edit rule fragment
                val action = RuleListFragmentDirections
                    .actionRulesToEditRule(rule.id)
                findNavController().navigate(action)
            },
            onRuleToggle = { rule, isEnabled ->
                viewModel.updateRuleEnabled(rule, isEnabled)
            }
        )
        
        binding.recyclerViewRules.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ruleAdapter
        }
    }

    private fun setupFab() {
        binding.fabAddRule.setOnClickListener {
            // Navigate to add new rule
            val action = RuleListFragmentDirections
                .actionRulesToEditRule(null)
            findNavController().navigate(action)
        }
    }

    private fun observeViewModel() {
        // Observe UI state for rules data
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                when (uiState) {
                    is UiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.emptyStateGroup.visibility = View.GONE
                    }
                    is UiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        ruleAdapter.submitList(uiState.data)
                        
                        // Show empty state if no rules
                        binding.emptyStateGroup.visibility = if (uiState.data.isEmpty()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                    is UiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.emptyStateGroup.visibility = View.GONE
                        Toast.makeText(requireContext(), "Error loading rules: ${uiState.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        // Observe operation state for rule operations (update/delete)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.operationState.collect { operationState ->
                when (operationState) {
                    is UiState.Loading -> {
                        // Could show a different loading indicator for operations
                        // For now, we'll rely on the operation completing quickly
                    }
                    is UiState.Success -> {
                        // Operation completed successfully - no UI action needed
                    }
                    is UiState.Error -> {
                        // Error handling is already done via statusMessage
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}