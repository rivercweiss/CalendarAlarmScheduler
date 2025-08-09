package com.example.calendaralarmscheduler.ui.rules

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calendaralarmscheduler.R
import com.example.calendaralarmscheduler.databinding.FragmentRuleListBinding
import com.example.calendaralarmscheduler.data.database.entities.Rule

class RuleListFragment : Fragment() {
    private var _binding: FragmentRuleListBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: RuleListViewModel
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
        
        viewModel = ViewModelProvider(this)[RuleListViewModel::class.java]
        
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        ruleAdapter = RuleAdapter(
            onRuleClick = { rule ->
                // Navigate to edit rule fragment
                // val action = RuleListFragmentDirections
                //     .actionRulesToEditRule(rule.id)
                // findNavController().navigate(action)
                // TODO: Implement navigation once Safe Args is configured
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
            // val action = RuleListFragmentDirections
            //     .actionRulesToEditRule(null)
            // findNavController().navigate(action)
            // TODO: Implement navigation once Safe Args is configured
        }
    }

    private fun observeViewModel() {
        viewModel.rules.observe(viewLifecycleOwner) { rules ->
            ruleAdapter.submitList(rules)
            
            // Show empty state if no rules
            binding.emptyStateGroup.visibility = if (rules.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}