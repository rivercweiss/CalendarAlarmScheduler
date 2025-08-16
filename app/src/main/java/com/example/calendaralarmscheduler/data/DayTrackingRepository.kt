package com.example.calendaralarmscheduler.data

import android.content.Context
import android.content.SharedPreferences
import com.example.calendaralarmscheduler.utils.Logger
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tracks which rules have triggered alarms today for "first event of day only" functionality.
 * Uses local date boundaries to determine what constitutes a "day".
 * 
 * Implementation Details:
 * - Uses SharedPreferences for simple, reliable persistence across app restarts
 * - Automatically detects day boundary crossings and resets tracking state
 * - Timezone-aware: recalculates day boundaries when timezone changes
 * - Thread-safe: all operations are synchronous and use Android main thread
 * - Minimal overhead: only stores rule IDs that have triggered, not full state
 */
class DayTrackingRepository(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "day_tracking"
        private const val KEY_CURRENT_DATE = "current_date"
        private const val KEY_TRIGGERED_RULES_PREFIX = "triggered_rule_"
        private const val DATE_FORMAT = "yyyy-MM-dd"
        private const val LOG_TAG = "DayTrackingRepository"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT)
    
    /**
     * Get current local date as string
     */
    private fun getCurrentLocalDateString(): String {
        return LocalDate.now(ZoneId.systemDefault()).format(dateFormatter)
    }
    
    /**
     * Check if we've moved to a new day and reset tracking if so
     */
    private fun checkAndResetForNewDay() {
        val currentDate = getCurrentLocalDateString()
        val storedDate = prefs.getString(KEY_CURRENT_DATE, null)
        
        if (storedDate != currentDate) {
            Logger.d(LOG_TAG, "Day boundary crossed from '$storedDate' to '$currentDate', resetting tracking")
            resetDayTracking(currentDate)
        }
    }
    
    /**
     * Reset all day tracking for a new day
     */
    private fun resetDayTracking(newDate: String) {
        val editor = prefs.edit()
        
        // Clear all triggered rule entries
        val allEntries = prefs.all
        for (key in allEntries.keys) {
            if (key.startsWith(KEY_TRIGGERED_RULES_PREFIX)) {
                editor.remove(key)
            }
        }
        
        // Set new current date
        editor.putString(KEY_CURRENT_DATE, newDate)
        editor.apply()
        
        Logger.i(LOG_TAG, "Day tracking reset for date: $newDate")
    }
    
    /**
     * Mark a rule as having triggered an alarm today
     */
    fun markRuleTriggeredToday(ruleId: String) {
        checkAndResetForNewDay()
        
        val key = KEY_TRIGGERED_RULES_PREFIX + ruleId
        prefs.edit().putBoolean(key, true).apply()
        
        Logger.d(LOG_TAG, "Marked rule '$ruleId' as triggered today")
    }
    
    /**
     * Check if a rule has already triggered an alarm today
     */
    fun hasRuleTriggeredToday(ruleId: String): Boolean {
        checkAndResetForNewDay()
        
        val key = KEY_TRIGGERED_RULES_PREFIX + ruleId
        val hasTriggered = prefs.getBoolean(key, false)
        
        Logger.v(LOG_TAG, "Rule '$ruleId' triggered today: $hasTriggered")
        return hasTriggered
    }
    
    /**
     * Get all rules that have triggered today
     */
    fun getRulesTriggeredToday(): Set<String> {
        checkAndResetForNewDay()
        
        val triggeredRules = mutableSetOf<String>()
        val allEntries = prefs.all
        
        for ((key, value) in allEntries) {
            if (key.startsWith(KEY_TRIGGERED_RULES_PREFIX) && value == true) {
                val ruleId = key.removePrefix(KEY_TRIGGERED_RULES_PREFIX)
                triggeredRules.add(ruleId)
            }
        }
        
        Logger.v(LOG_TAG, "Rules triggered today: ${triggeredRules.size}")
        return triggeredRules
    }
    
    /**
     * Force reset of day tracking (for testing or timezone changes)
     */
    fun forceReset() {
        Logger.i(LOG_TAG, "Force resetting day tracking")
        resetDayTracking(getCurrentLocalDateString())
    }
    
    /**
     * Handle timezone change by forcing a reset
     */
    fun handleTimezoneChange() {
        Logger.i(LOG_TAG, "Handling timezone change, resetting day tracking")
        forceReset()
    }
    
    /**
     * Get current tracking status for debugging
     */
    fun getDebugInfo(): Map<String, Any> {
        checkAndResetForNewDay()
        
        return mapOf(
            "currentDate" to getCurrentLocalDateString(),
            "storedDate" to (prefs.getString(KEY_CURRENT_DATE, "none") ?: "none"),
            "triggeredRulesCount" to getRulesTriggeredToday().size,
            "triggeredRules" to getRulesTriggeredToday().toList()
        )
    }
}