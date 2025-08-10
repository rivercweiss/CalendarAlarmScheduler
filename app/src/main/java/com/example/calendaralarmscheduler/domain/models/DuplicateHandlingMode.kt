package com.example.calendaralarmscheduler.domain.models

enum class DuplicateHandlingMode(val value: String, val displayName: String, val description: String) {
    ALLOW_MULTIPLE("ALLOW_MULTIPLE", "Allow Multiple Alarms", "Create separate alarms for each rule that matches an event"),
    EARLIEST_ONLY("EARLIEST_ONLY", "Earliest Alarm Only", "Only create the alarm with the earliest time for each event"),
    LATEST_ONLY("LATEST_ONLY", "Latest Alarm Only", "Only create the alarm with the latest time for each event"),
    SHORTEST_LEAD_TIME("SHORTEST_LEAD_TIME", "Shortest Lead Time", "Use the rule with the shortest lead time for each event"),
    LONGEST_LEAD_TIME("LONGEST_LEAD_TIME", "Longest Lead Time", "Use the rule with the longest lead time for each event");
    
    companion object {
        fun fromValue(value: String): DuplicateHandlingMode {
            return values().firstOrNull { it.value == value } ?: ALLOW_MULTIPLE
        }
        
        fun getDefaultMode(): DuplicateHandlingMode = ALLOW_MULTIPLE
    }
}