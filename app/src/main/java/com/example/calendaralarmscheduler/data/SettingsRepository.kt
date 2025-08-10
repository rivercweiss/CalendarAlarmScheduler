package com.example.calendaralarmscheduler.data

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.workers.WorkerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Locale

data class AllDayTime(
    val hour: Int,
    val minute: Int
) {
    fun formatTime(context: Context): String {
        val is24Hour = DateFormat.is24HourFormat(context)
        
        return if (is24Hour) {
            String.format(Locale.ROOT, "%02d:%02d", hour, minute)
        } else {
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
            }
            val format = SimpleDateFormat("h:mm a", Locale.getDefault())
            format.format(calendar.time)
        }
    }
}

class SettingsRepository(
    private val context: Context,
    private var onRefreshIntervalChanged: ((Int) -> Unit)? = null
) {
    
    companion object {
        private const val PREFS_NAME = "app_settings"
        
        // Settings version for migration handling
        private const val KEY_SETTINGS_VERSION = "settings_version"
        private const val CURRENT_SETTINGS_VERSION = 1
        
        // Settings keys
        private const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"
        private const val KEY_ALL_DAY_DEFAULT_HOUR = "all_day_default_hour"
        private const val KEY_ALL_DAY_DEFAULT_MINUTE = "all_day_default_minute"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        
        // Battery optimization tracking
        private const val KEY_BATTERY_OPTIMIZATION_SETUP_COMPLETED = "battery_optimization_setup_completed"
        private const val KEY_BATTERY_OPTIMIZATION_METHOD_USED = "battery_optimization_method_used"
        private const val KEY_BATTERY_OPTIMIZATION_ATTEMPTS = "battery_optimization_attempts"
        private const val KEY_BATTERY_OPTIMIZATION_LAST_ATTEMPT = "battery_optimization_last_attempt"
        private const val KEY_BATTERY_OPTIMIZATION_REMINDER_COUNT = "battery_optimization_reminder_count"
        private const val KEY_BATTERY_OPTIMIZATION_USER_SKIPPED = "battery_optimization_user_skipped"
        private const val KEY_DEVICE_BATTERY_MANAGEMENT_TYPE = "device_battery_management_type"
        
        // Default values
        private const val DEFAULT_REFRESH_INTERVAL = WorkerManager.DEFAULT_INTERVAL_MINUTES
        private const val DEFAULT_ALL_DAY_HOUR = 20 // 8:00 PM
        private const val DEFAULT_ALL_DAY_MINUTE = 0
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // State flows for reactive updates
    private val _refreshIntervalMinutes = MutableStateFlow(getRefreshIntervalMinutes())
    val refreshIntervalMinutes: StateFlow<Int> = _refreshIntervalMinutes.asStateFlow()
    
    // Atomic all-day time StateFlow - single source of truth
    private val _allDayTime = MutableStateFlow(AllDayTime(getAllDayDefaultHour(), getAllDayDefaultMinute()))
    val allDayTime: StateFlow<AllDayTime> = _allDayTime.asStateFlow()
    
    // Legacy individual flows for backward compatibility - updated when allDayTime changes
    private val _allDayDefaultHour = MutableStateFlow(_allDayTime.value.hour)
    val allDayDefaultHour: StateFlow<Int> = _allDayDefaultHour.asStateFlow()
    
    private val _allDayDefaultMinute = MutableStateFlow(_allDayTime.value.minute)
    val allDayDefaultMinute: StateFlow<Int> = _allDayDefaultMinute.asStateFlow()
    
    
    private val _batteryOptimizationCompleted = MutableStateFlow(isBatteryOptimizationSetupCompleted())
    val batteryOptimizationCompleted: StateFlow<Boolean> = _batteryOptimizationCompleted.asStateFlow()
    
    init {
        Logger.i("SettingsRepository", "SettingsRepository initialized")
        
        // Handle settings migration if needed
        handleSettingsMigration()
        
        Logger.i("SettingsRepository", "Initial StateFlow values - refresh: ${_refreshIntervalMinutes.value}min, all-day: ${_allDayTime.value.hour}:${_allDayTime.value.minute}")
    }
    
    /**
     * Set the callback for refresh interval changes (used by Hilt DI)
     */
    fun setOnRefreshIntervalChanged(callback: (Int) -> Unit) {
        onRefreshIntervalChanged = callback
        Logger.i("SettingsRepository", "Refresh interval change callback set")
    }
    
    // Refresh interval settings
    fun getRefreshIntervalMinutes(): Int {
        return prefs.getInt(KEY_REFRESH_INTERVAL_MINUTES, DEFAULT_REFRESH_INTERVAL)
    }
    
    fun setRefreshIntervalMinutes(minutes: Int) {
        val oldValue = _refreshIntervalMinutes.value
        val validMinutes = if (WorkerManager.AVAILABLE_INTERVALS.contains(minutes)) {
            minutes
        } else {
            Logger.w("SettingsRepository", "Invalid refresh interval: $minutes. Using default: $DEFAULT_REFRESH_INTERVAL")
            DEFAULT_REFRESH_INTERVAL
        }
        
        Logger.i("SettingsRepository", "Setting refresh interval: $oldValue -> $validMinutes")
        
        // Update SharedPreferences
        prefs.edit()
            .putInt(KEY_REFRESH_INTERVAL_MINUTES, validMinutes)
            .apply()
        
        // Immediately update StateFlow (synchronous, no race conditions)
        _refreshIntervalMinutes.value = validMinutes
        Logger.i("SettingsRepository", "StateFlow updated synchronously: $oldValue -> $validMinutes")
        
        // Notify about the change to trigger worker rescheduling
        onRefreshIntervalChanged?.invoke(validMinutes)
    }
    
    // All-day event default time settings
    fun getAllDayDefaultHour(): Int {
        return prefs.getInt(KEY_ALL_DAY_DEFAULT_HOUR, DEFAULT_ALL_DAY_HOUR)
    }
    
    fun setAllDayDefaultHour(hour: Int) {
        val validHour = hour.coerceIn(0, 23)
        prefs.edit()
            .putInt(KEY_ALL_DAY_DEFAULT_HOUR, validHour)
            .apply()
        
        Logger.i("SettingsRepository", "All-day default hour set to: $validHour")
    }
    
    fun getAllDayDefaultMinute(): Int {
        return prefs.getInt(KEY_ALL_DAY_DEFAULT_MINUTE, DEFAULT_ALL_DAY_MINUTE)
    }
    
    fun setAllDayDefaultMinute(minute: Int) {
        val validMinute = minute.coerceIn(0, 59)
        prefs.edit()
            .putInt(KEY_ALL_DAY_DEFAULT_MINUTE, validMinute)
            .apply()
        
        Logger.i("SettingsRepository", "All-day default minute set to: $validMinute")
    }
    
    fun setAllDayDefaultTime(hour: Int, minute: Int) {
        val oldTime = _allDayTime.value
        val validHour = hour.coerceIn(0, 23)
        val validMinute = minute.coerceIn(0, 59)
        val newTime = AllDayTime(validHour, validMinute)
        
        Logger.i("SettingsRepository", "Setting all-day time: ${oldTime.hour}:${oldTime.minute} -> ${newTime.hour}:${newTime.minute}")
        
        // Update SharedPreferences
        prefs.edit()
            .putInt(KEY_ALL_DAY_DEFAULT_HOUR, validHour)
            .putInt(KEY_ALL_DAY_DEFAULT_MINUTE, validMinute)
            .apply()
        
        // Atomic StateFlow update - single emission, no race conditions
        _allDayTime.value = newTime
        
        // Update legacy individual StateFlows for backward compatibility
        _allDayDefaultHour.value = validHour
        _allDayDefaultMinute.value = validMinute
        
        Logger.i("SettingsRepository", "StateFlows updated atomically: ${oldTime.hour}:${oldTime.minute} -> ${newTime.hour}:${newTime.minute}")
    }
    
    // Onboarding settings
    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }
    
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            .apply()
        
        Logger.i("SettingsRepository", "Onboarding completed set to: $completed")
    }
    
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    fun setFirstLaunchCompleted() {
        prefs.edit()
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
        
        Logger.i("SettingsRepository", "First launch completed")
    }
    
    // Last sync time tracking for event change detection
    fun getLastSyncTime(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    }
    
    fun setLastSyncTime(timestamp: Long) {
        prefs.edit()
            .putLong(KEY_LAST_SYNC_TIME, timestamp)
            .apply()
        
        Logger.d("SettingsRepository", "Last sync time updated to: $timestamp")
    }
    
    fun updateLastSyncTime() {
        setLastSyncTime(System.currentTimeMillis())
    }
    
    fun hasEverSynced(): Boolean {
        return getLastSyncTime() > 0
    }
    
    // Handle timezone changes by resetting last sync time
    fun handleTimezoneChange() {
        Logger.i("SettingsRepository", "Timezone change detected, resetting last sync time")
        // Reset to 0 to force full rescan after timezone change
        setLastSyncTime(0L)
    }
    
    // Utility methods
    fun getAllDayDefaultTimeFormatted(): String {
        return _allDayTime.value.formatTime(context)
    }
    
    fun getAllDayDefaultTimeFormatted24Hour(): String {
        val time = _allDayTime.value
        return String.format(Locale.ROOT, "%02d:%02d", time.hour, time.minute)
    }
    
    fun getRefreshIntervalDescription(): String {
        val workerManager = WorkerManager(context)
        return workerManager.getIntervalDescription(getRefreshIntervalMinutes())
    }
    
    
    fun resetToDefaults() {
        Logger.i("SettingsRepository", "Resetting all settings to defaults")
        
        prefs.edit()
            .putInt(KEY_REFRESH_INTERVAL_MINUTES, DEFAULT_REFRESH_INTERVAL)
            .putInt(KEY_ALL_DAY_DEFAULT_HOUR, DEFAULT_ALL_DAY_HOUR)
            .putInt(KEY_ALL_DAY_DEFAULT_MINUTE, DEFAULT_ALL_DAY_MINUTE)
            .apply()
        
        Logger.i("SettingsRepository", "Settings reset completed")
    }
    
    fun getAllSettings(): Map<String, Any> {
        return mapOf(
            "refreshIntervalMinutes" to getRefreshIntervalMinutes(),
            "allDayDefaultHour" to getAllDayDefaultHour(),
            "allDayDefaultMinute" to getAllDayDefaultMinute(),
            "allDayDefaultTimeFormatted" to getAllDayDefaultTimeFormatted(),
            "onboardingCompleted" to isOnboardingCompleted(),
            "firstLaunch" to isFirstLaunch(),
            "refreshIntervalDescription" to getRefreshIntervalDescription(),
            "lastSyncTime" to getLastSyncTime(),
            "hasEverSynced" to hasEverSynced(),
        )
    }
    
    fun dumpSettings() {
        val settings = getAllSettings()
        Logger.i("SettingsRepository", "Current settings:")
        settings.forEach { (key, value) ->
            Logger.i("SettingsRepository", "  $key: $value")
        }
    }
    
    /**
     * Handle settings migration between versions
     */
    private fun handleSettingsMigration() {
        val currentVersion = prefs.getInt(KEY_SETTINGS_VERSION, 0)
        
        if (currentVersion < CURRENT_SETTINGS_VERSION) {
            Logger.i("SettingsRepository", "Migrating settings from version $currentVersion to $CURRENT_SETTINGS_VERSION")
            
            try {
                when (currentVersion) {
                    0 -> {
                        // Migration from version 0 (initial install or pre-versioned settings)
                        migrateFromVersion0()
                    }
                    // Add future migration cases here as needed
                    // 1 -> migrateFromVersion1()
                    // etc.
                }
                
                // Update to current version
                prefs.edit()
                    .putInt(KEY_SETTINGS_VERSION, CURRENT_SETTINGS_VERSION)
                    .apply()
                    
                Logger.i("SettingsRepository", "Settings migration completed successfully")
                
            } catch (e: Exception) {
                Logger.e("SettingsRepository", "Settings migration failed", e)
                // If migration fails, we could reset to defaults as a fallback
                // resetToDefaults()
            }
        } else {
            Logger.d("SettingsRepository", "Settings are up to date (version $currentVersion)")
        }
    }
    
    /**
     * Migration from version 0 (initial version)
     * This handles any settings that need to be initialized or converted
     */
    private fun migrateFromVersion0() {
        Logger.d("SettingsRepository", "Performing migration from version 0")
        
        // Version 0 to 1 migration tasks:
        // - Ensure all settings have valid default values
        // - Clean up any invalid data from previous versions
        
        val editor = prefs.edit()
        
        // Validate refresh interval
        val currentInterval = prefs.getInt(KEY_REFRESH_INTERVAL_MINUTES, -1)
        if (currentInterval == -1 || !WorkerManager.AVAILABLE_INTERVALS.contains(currentInterval)) {
            editor.putInt(KEY_REFRESH_INTERVAL_MINUTES, DEFAULT_REFRESH_INTERVAL)
            Logger.d("SettingsRepository", "Reset refresh interval to default: $DEFAULT_REFRESH_INTERVAL")
        }
        
        // Validate all-day time settings
        val currentHour = prefs.getInt(KEY_ALL_DAY_DEFAULT_HOUR, -1)
        val currentMinute = prefs.getInt(KEY_ALL_DAY_DEFAULT_MINUTE, -1)
        if (currentHour < 0 || currentHour > 23) {
            editor.putInt(KEY_ALL_DAY_DEFAULT_HOUR, DEFAULT_ALL_DAY_HOUR)
            Logger.d("SettingsRepository", "Reset all-day hour to default: $DEFAULT_ALL_DAY_HOUR")
        }
        if (currentMinute < 0 || currentMinute > 59) {
            editor.putInt(KEY_ALL_DAY_DEFAULT_MINUTE, DEFAULT_ALL_DAY_MINUTE)
            Logger.d("SettingsRepository", "Reset all-day minute to default: $DEFAULT_ALL_DAY_MINUTE")
        }
        
        // Apply any changes
        editor.apply()
        
        Logger.d("SettingsRepository", "Version 0 migration completed")
    }
    
    /**
     * Validate all current settings and fix any invalid values
     */
    fun validateAndFixSettings(): Boolean {
        var hadIssues = false
        val editor = prefs.edit()
        
        // Validate refresh interval
        val interval = getRefreshIntervalMinutes()
        if (!WorkerManager.AVAILABLE_INTERVALS.contains(interval)) {
            editor.putInt(KEY_REFRESH_INTERVAL_MINUTES, DEFAULT_REFRESH_INTERVAL)
            hadIssues = true
            Logger.w("SettingsRepository", "Fixed invalid refresh interval: $interval -> $DEFAULT_REFRESH_INTERVAL")
        }
        
        // Validate all-day time
        val hour = getAllDayDefaultHour()
        val minute = getAllDayDefaultMinute()
        if (hour < 0 || hour > 23) {
            editor.putInt(KEY_ALL_DAY_DEFAULT_HOUR, DEFAULT_ALL_DAY_HOUR)
            hadIssues = true
            Logger.w("SettingsRepository", "Fixed invalid all-day hour: $hour -> $DEFAULT_ALL_DAY_HOUR")
        }
        if (minute < 0 || minute > 59) {
            editor.putInt(KEY_ALL_DAY_DEFAULT_MINUTE, DEFAULT_ALL_DAY_MINUTE)
            hadIssues = true
            Logger.w("SettingsRepository", "Fixed invalid all-day minute: $minute -> $DEFAULT_ALL_DAY_MINUTE")
        }
        
        if (hadIssues) {
            editor.apply()
            Logger.i("SettingsRepository", "Settings validation completed with fixes applied")
        } else {
            Logger.d("SettingsRepository", "Settings validation passed - no issues found")
        }
        
        return hadIssues
    }
    
    /**
     * Get current settings version
     */
    fun getSettingsVersion(): Int {
        return prefs.getInt(KEY_SETTINGS_VERSION, 0)
    }
    
    /**
     * Clear all settings (for debugging/testing)
     */
    fun clearAllSettings() {
        Logger.w("SettingsRepository", "Clearing all settings (this will reset the app to defaults)")
        prefs.edit().clear().apply()
    }
    
    // Defensive refresh methods to ensure UI consistency
    
    /**
     * Force refresh all StateFlows from current SharedPreferences values.
     * Use this as a fallback if UI doesn't update properly.
     */
    fun refreshAllStateFlows() {
        Logger.i("SettingsRepository", "Forcing refresh of all StateFlows from SharedPreferences")
        
        val oldRefresh = _refreshIntervalMinutes.value
        val newRefresh = getRefreshIntervalMinutes()
        if (oldRefresh != newRefresh) {
            _refreshIntervalMinutes.value = newRefresh
            Logger.i("SettingsRepository", "Defensive refresh - refresh interval: $oldRefresh -> $newRefresh")
        }
        
        val oldTime = _allDayTime.value
        val newTime = AllDayTime(getAllDayDefaultHour(), getAllDayDefaultMinute())
        if (oldTime != newTime) {
            _allDayTime.value = newTime
            _allDayDefaultHour.value = newTime.hour
            _allDayDefaultMinute.value = newTime.minute
            Logger.i("SettingsRepository", "Defensive refresh - all-day time: ${oldTime.hour}:${oldTime.minute} -> ${newTime.hour}:${newTime.minute}")
        }
        
        val oldBattery = _batteryOptimizationCompleted.value
        val newBattery = isBatteryOptimizationSetupCompleted()
        if (oldBattery != newBattery) {
            _batteryOptimizationCompleted.value = newBattery
            Logger.i("SettingsRepository", "Defensive refresh - battery optimization: $oldBattery -> $newBattery")
        }
        
        Logger.d("SettingsRepository", "StateFlow refresh completed")
    }
    
    /**
     * Force refresh only refresh interval StateFlow
     */
    fun refreshRefreshIntervalStateFlow() {
        val oldValue = _refreshIntervalMinutes.value
        val newValue = getRefreshIntervalMinutes()
        if (oldValue != newValue) {
            _refreshIntervalMinutes.value = newValue
            Logger.i("SettingsRepository", "Defensive refresh - refresh interval only: $oldValue -> $newValue")
        }
    }
    
    /**
     * Force refresh only all-day time StateFlows
     */
    fun refreshAllDayTimeStateFlows() {
        val oldTime = _allDayTime.value
        val newTime = AllDayTime(getAllDayDefaultHour(), getAllDayDefaultMinute())
        if (oldTime != newTime) {
            _allDayTime.value = newTime
            _allDayDefaultHour.value = newTime.hour
            _allDayDefaultMinute.value = newTime.minute
            Logger.i("SettingsRepository", "Defensive refresh - all-day time only: ${oldTime.hour}:${oldTime.minute} -> ${newTime.hour}:${newTime.minute}")
        }
    }
    
    // Battery Optimization Settings
    
    /**
     * Check if battery optimization setup has been completed successfully
     */
    fun isBatteryOptimizationSetupCompleted(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_SETUP_COMPLETED, false)
    }
    
    /**
     * Mark battery optimization setup as completed
     */
    fun setBatteryOptimizationSetupCompleted(completed: Boolean, method: String? = null) {
        val editor = prefs.edit()
            .putBoolean(KEY_BATTERY_OPTIMIZATION_SETUP_COMPLETED, completed)
        
        if (method != null) {
            editor.putString(KEY_BATTERY_OPTIMIZATION_METHOD_USED, method)
        }
        
        if (completed) {
            // Reset reminder count when successfully completed
            editor.putInt(KEY_BATTERY_OPTIMIZATION_REMINDER_COUNT, 0)
            editor.putBoolean(KEY_BATTERY_OPTIMIZATION_USER_SKIPPED, false)
        }
        
        editor.apply()
        
        Logger.i("SettingsRepository", "Battery optimization setup completed: $completed${if (method != null) " using method: $method" else ""}")
    }
    
    /**
     * Get the method that was successfully used to complete battery optimization
     */
    fun getBatteryOptimizationMethodUsed(): String? {
        return prefs.getString(KEY_BATTERY_OPTIMIZATION_METHOD_USED, null)
    }
    
    /**
     * Record a battery optimization setup attempt
     */
    fun recordBatteryOptimizationAttempt(method: String) {
        val currentAttempts = getBatteryOptimizationAttempts()
        prefs.edit()
            .putInt(KEY_BATTERY_OPTIMIZATION_ATTEMPTS, currentAttempts + 1)
            .putLong(KEY_BATTERY_OPTIMIZATION_LAST_ATTEMPT, System.currentTimeMillis())
            .apply()
        
        Logger.d("SettingsRepository", "Recorded battery optimization attempt #${currentAttempts + 1} using method: $method")
    }
    
    /**
     * Get the number of battery optimization setup attempts
     */
    fun getBatteryOptimizationAttempts(): Int {
        return prefs.getInt(KEY_BATTERY_OPTIMIZATION_ATTEMPTS, 0)
    }
    
    /**
     * Get the timestamp of the last battery optimization attempt
     */
    fun getLastBatteryOptimizationAttempt(): Long {
        return prefs.getLong(KEY_BATTERY_OPTIMIZATION_LAST_ATTEMPT, 0L)
    }
    
    /**
     * Check if we should show a battery optimization reminder
     */
    fun shouldShowBatteryOptimizationReminder(): Boolean {
        if (isBatteryOptimizationSetupCompleted() || getUserSkippedBatteryOptimization()) {
            return false
        }
        
        val reminderCount = getBatteryOptimizationReminderCount()
        val lastAttempt = getLastBatteryOptimizationAttempt()
        val currentTime = System.currentTimeMillis()
        
        // Show reminder if:
        // 1. Never attempted, OR
        // 2. Last attempt was more than 1 hour ago and we've shown fewer than 3 reminders
        return (lastAttempt == 0L) || 
               (currentTime - lastAttempt > 3600000 && reminderCount < 3) // 1 hour = 3600000 ms
    }
    
    /**
     * Record that a battery optimization reminder was shown
     */
    fun recordBatteryOptimizationReminderShown() {
        val currentCount = getBatteryOptimizationReminderCount()
        prefs.edit()
            .putInt(KEY_BATTERY_OPTIMIZATION_REMINDER_COUNT, currentCount + 1)
            .apply()
        
        Logger.d("SettingsRepository", "Battery optimization reminder shown (count: ${currentCount + 1})")
    }
    
    /**
     * Get the number of battery optimization reminders shown
     */
    fun getBatteryOptimizationReminderCount(): Int {
        return prefs.getInt(KEY_BATTERY_OPTIMIZATION_REMINDER_COUNT, 0)
    }
    
    /**
     * Mark that user has skipped battery optimization setup
     */
    fun setUserSkippedBatteryOptimization(skipped: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BATTERY_OPTIMIZATION_USER_SKIPPED, skipped)
            .apply()
        
        Logger.i("SettingsRepository", "User skipped battery optimization: $skipped")
    }
    
    /**
     * Check if user has skipped battery optimization setup
     */
    fun getUserSkippedBatteryOptimization(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_USER_SKIPPED, false)
    }
    
    /**
     * Store the detected device battery management type
     */
    fun setDeviceBatteryManagementType(type: String) {
        prefs.edit()
            .putString(KEY_DEVICE_BATTERY_MANAGEMENT_TYPE, type)
            .apply()
        
        Logger.d("SettingsRepository", "Device battery management type set to: $type")
    }
    
    /**
     * Get the stored device battery management type
     */
    fun getDeviceBatteryManagementType(): String? {
        return prefs.getString(KEY_DEVICE_BATTERY_MANAGEMENT_TYPE, null)
    }
    
    /**
     * Reset battery optimization tracking (for testing or after major updates)
     */
    fun resetBatteryOptimizationTracking() {
        prefs.edit()
            .putBoolean(KEY_BATTERY_OPTIMIZATION_SETUP_COMPLETED, false)
            .remove(KEY_BATTERY_OPTIMIZATION_METHOD_USED)
            .putInt(KEY_BATTERY_OPTIMIZATION_ATTEMPTS, 0)
            .putLong(KEY_BATTERY_OPTIMIZATION_LAST_ATTEMPT, 0L)
            .putInt(KEY_BATTERY_OPTIMIZATION_REMINDER_COUNT, 0)
            .putBoolean(KEY_BATTERY_OPTIMIZATION_USER_SKIPPED, false)
            .remove(KEY_DEVICE_BATTERY_MANAGEMENT_TYPE)
            .apply()
        
        Logger.i("SettingsRepository", "Battery optimization tracking reset")
    }
    
    /**
     * Get battery optimization summary for debugging
     */
    fun getBatteryOptimizationSummary(): Map<String, Any> {
        return mapOf(
            "setupCompleted" to isBatteryOptimizationSetupCompleted(),
            "methodUsed" to (getBatteryOptimizationMethodUsed() ?: "none"),
            "attempts" to getBatteryOptimizationAttempts(),
            "lastAttempt" to getLastBatteryOptimizationAttempt(),
            "reminderCount" to getBatteryOptimizationReminderCount(),
            "userSkipped" to getUserSkippedBatteryOptimization(),
            "deviceType" to (getDeviceBatteryManagementType() ?: "unknown"),
            "shouldShowReminder" to shouldShowBatteryOptimizationReminder()
        )
    }
}