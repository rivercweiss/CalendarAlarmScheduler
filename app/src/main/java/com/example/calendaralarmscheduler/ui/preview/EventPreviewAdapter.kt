package com.example.calendaralarmscheduler.ui.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calendaralarmscheduler.R
import com.google.android.material.color.MaterialColors
import com.example.calendaralarmscheduler.databinding.ItemEventPreviewBinding
import com.example.calendaralarmscheduler.domain.models.CalendarEvent
import com.example.calendaralarmscheduler.data.database.entities.ScheduledAlarm
import com.example.calendaralarmscheduler.utils.TimezoneUtils
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class EventPreviewAdapter(
    private val onEventClick: (EventWithAlarms) -> Unit = {},
    private val onAlarmClick: (ScheduledAlarm) -> Unit = {}
) : ListAdapter<EventWithAlarms, EventPreviewAdapter.ViewHolder>(EventDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemEventPreviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemEventPreviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(eventWithAlarms: EventWithAlarms) {
            val event = eventWithAlarms.event
            val alarms = eventWithAlarms.alarms
            val rules = eventWithAlarms.matchingRules

            // Event basic info
            binding.textEventTitle.text = event.title
            
            // Event time with timezone
            val startTime = event.getLocalStartTime()
            val endTime = event.getLocalEndTime()
            
            if (event.isAllDay) {
                binding.textEventTime.text = if (event.isMultiDay()) {
                    "${startTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))} - ${endTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}"
                } else {
                    "All Day - ${startTime.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}"
                }
            } else {
                val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                binding.textEventTime.text = "${startTime.format(dateFormatter)} - ${endTime.format(dateFormatter)}"
            }

            // Timezone info
            val timezoneName = TimezoneUtils.getTimezoneDisplayName(startTime.zone)
            binding.textTimezone.text = timezoneName

            // Event details
            if (event.location?.isNotBlank() == true) {
                binding.textEventLocation.text = event.location
                binding.textEventLocation.visibility = android.view.View.VISIBLE
            } else {
                binding.textEventLocation.visibility = android.view.View.GONE
            }

            // Matching rules count
            if (rules.isNotEmpty()) {
                binding.textMatchingRules.text = itemView.context.getString(
                    R.string.matching_rules_count,
                    rules.size
                )
                binding.textMatchingRules.visibility = android.view.View.VISIBLE
                
                // Show rule names (first few)
                val ruleNames = rules.take(3).joinToString(", ") { it.name }
                val extraCount = rules.size - 3
                binding.textRuleNames.text = if (extraCount > 0) {
                    "$ruleNames (+$extraCount more)"
                } else {
                    ruleNames
                }
                binding.textRuleNames.visibility = android.view.View.VISIBLE
            } else {
                binding.textMatchingRules.visibility = android.view.View.GONE
                binding.textRuleNames.visibility = android.view.View.GONE
            }

            // Scheduled alarms
            when {
                alarms.isEmpty() -> {
                    binding.textAlarmStatus.text = "No alarms scheduled"
                    binding.textAlarmStatus.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant))
                    binding.textAlarmDetails.visibility = android.view.View.GONE
                }
                alarms.all { it.userDismissed } -> {
                    binding.textAlarmStatus.text = "${alarms.size} alarm(s) dismissed"
                    binding.textAlarmStatus.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorError))
                    binding.textAlarmDetails.visibility = android.view.View.GONE
                }
                else -> {
                    val activeAlarms = alarms.filter { !it.userDismissed && it.isInFuture() }
                    val pastAlarms = alarms.filter { it.isInPast() }
                    
                    when {
                        activeAlarms.isNotEmpty() -> {
                            binding.textAlarmStatus.text = "${activeAlarms.size} active alarm(s)"
                            binding.textAlarmStatus.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorPrimary))
                            
                            // Show next alarm time
                            val nextAlarm = activeAlarms.minByOrNull { it.alarmTimeUtc }
                            nextAlarm?.let { alarm ->
                                val alarmTime = alarm.getLocalAlarmTime()
                                val timeUntil = alarm.formatTimeUntilAlarm()
                                binding.textAlarmDetails.text = "Next: ${alarmTime.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT))} ($timeUntil)"
                                binding.textAlarmDetails.visibility = android.view.View.VISIBLE
                            }
                        }
                        pastAlarms.isNotEmpty() -> {
                            binding.textAlarmStatus.text = "${pastAlarms.size} past alarm(s)"
                            binding.textAlarmStatus.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant))
                            binding.textAlarmDetails.visibility = android.view.View.GONE
                        }
                        else -> {
                            binding.textAlarmStatus.text = "No active alarms"
                            binding.textAlarmStatus.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant))
                            binding.textAlarmDetails.visibility = android.view.View.GONE
                        }
                    }
                }
            }

            // Event status (past/future)
            if (event.isInPast()) {
                binding.cardEvent.alpha = 0.7f
                binding.textEventTitle.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurfaceVariant))
            } else {
                binding.cardEvent.alpha = 1.0f
                binding.textEventTitle.setTextColor(MaterialColors.getColor(itemView, com.google.android.material.R.attr.colorOnSurface))
            }

            // Click listeners
            binding.root.setOnClickListener {
                onEventClick(eventWithAlarms)
            }
            
            binding.textAlarmStatus.setOnClickListener {
                if (alarms.isNotEmpty()) {
                    alarms.firstOrNull()?.let { onAlarmClick(it) }
                }
            }
        }
    }

    class EventDiffCallback : DiffUtil.ItemCallback<EventWithAlarms>() {
        override fun areItemsTheSame(oldItem: EventWithAlarms, newItem: EventWithAlarms): Boolean {
            return oldItem.event.id == newItem.event.id
        }

        override fun areContentsTheSame(oldItem: EventWithAlarms, newItem: EventWithAlarms): Boolean {
            return oldItem.event == newItem.event && 
                   oldItem.alarms == newItem.alarms && 
                   oldItem.matchingRules == newItem.matchingRules
        }
    }
}