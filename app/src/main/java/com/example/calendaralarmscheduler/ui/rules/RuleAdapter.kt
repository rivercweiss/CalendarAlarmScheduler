package com.example.calendaralarmscheduler.ui.rules

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calendaralarmscheduler.databinding.ItemRuleBinding
import com.example.calendaralarmscheduler.data.database.entities.Rule

class RuleAdapter(
    private val onRuleClick: (Rule) -> Unit,
    private val onRuleToggle: (Rule, Boolean) -> Unit
) : ListAdapter<Rule, RuleAdapter.RuleViewHolder>(RuleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemRuleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return RuleViewHolder(binding, onRuleClick, onRuleToggle)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class RuleViewHolder(
        private val binding: ItemRuleBinding,
        private val onRuleClick: (Rule) -> Unit,
        private val onRuleToggle: (Rule, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(rule: Rule) {
            binding.apply {
                textRuleName.text = rule.name
                textKeywordPattern.text = rule.keywordPattern
                textLeadTime.text = "${rule.leadTimeMinutes} min"
                textCalendarCount.text = "${rule.calendarIds.size} calendar(s)"
                
                switchEnabled.isChecked = rule.enabled
                switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                    onRuleToggle(rule, isChecked)
                }
                
                root.setOnClickListener {
                    onRuleClick(rule)
                }
                
                // Show regex indicator
                textRegexIndicator.visibility = if (rule.isRegex) {
                    android.view.View.VISIBLE
                } else {
                    android.view.View.GONE
                }
            }
        }
    }

    private class RuleDiffCallback : DiffUtil.ItemCallback<Rule>() {
        override fun areItemsTheSame(oldItem: Rule, newItem: Rule): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Rule, newItem: Rule): Boolean {
            return oldItem == newItem
        }
    }
}