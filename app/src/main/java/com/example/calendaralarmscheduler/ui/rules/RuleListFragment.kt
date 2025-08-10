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
        // Observe rules StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rules.collect { rules ->
                ruleAdapter.submitList(rules)
                
                // Show empty state if no rules
                binding.emptyStateGroup.visibility = if (rules.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
        
        // Observe loading state StateFlow
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) {
                    View.VISIBLE
                } else {
                    View.GONE
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