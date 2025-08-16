package com.example.calendaralarmscheduler.data

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import com.example.calendaralarmscheduler.BuildConfig
import com.example.calendaralarmscheduler.utils.Logger
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
        
        // Settings keys
        private const val KEY_REFRESH_INTERVAL_MINUTES = "refresh_interval_minutes"
        private const val KEY_ALL_DAY_DEFAULT_HOUR = "all_day_default_hour"
        private const val KEY_ALL_DAY_DEFAULT_MINUTE = "all_day_default_minute"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_BATTERY_OPTIMIZATION_COMPLETED = "battery_optimization_completed"
        private const val KEY_PREMIUM_PURCHASED = "premium_purchased"
        
        // Default values
        val DEFAULT_REFRESH_INTERVAL_MINUTES = if (BuildConfig.DEBUG) 1 else 30
        const val DEFAULT_ALL_DAY_HOUR = 20
        const val DEFAULT_ALL_DAY_MINUTE = 0
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // StateFlows for reactive UI
    private val _refreshIntervalMinutes = MutableStateFlow(getRefreshIntervalMinutes())
    val refreshIntervalMinutes: StateFlow<Int> = _refreshIntervalMinutes.asStateFlow()
    
    private val _allDayTime = MutableStateFlow(AllDayTime(getAllDayDefaultHour(), getAllDayDefaultMinute()))
    val allDayTime: StateFlow<AllDayTime> = _allDayTime.asStateFlow()
    
    private val _batteryOptimizationCompleted = MutableStateFlow(isBatteryOptimizationSetupCompleted())
    val batteryOptimizationCompleted: StateFlow<Boolean> = _batteryOptimizationCompleted.asStateFlow()
    
    private val _premiumPurchased = MutableStateFlow(isPremiumPurchased())
    val premiumPurchased: StateFlow<Boolean> = _premiumPurchased.asStateFlow()
    
    // Callback for refresh interval changes
    fun setOnRefreshIntervalChanged(callback: (Int) -> Unit) {
        onRefreshIntervalChanged = callback
    }
    
    // Refresh interval settings
    fun getRefreshIntervalMinutes(): Int = prefs.getInt(KEY_REFRESH_INTERVAL_MINUTES, DEFAULT_REFRESH_INTERVAL_MINUTES)
    
    fun setRefreshIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_REFRESH_INTERVAL_MINUTES, minutes).apply()
        _refreshIntervalMinutes.value = minutes
        onRefreshIntervalChanged?.invoke(minutes)
    }
    
    // All-day event settings
    fun getAllDayDefaultHour(): Int = prefs.getInt(KEY_ALL_DAY_DEFAULT_HOUR, DEFAULT_ALL_DAY_HOUR)
    fun getAllDayDefaultMinute(): Int = prefs.getInt(KEY_ALL_DAY_DEFAULT_MINUTE, DEFAULT_ALL_DAY_MINUTE)
    
    fun setAllDayDefaultTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_ALL_DAY_DEFAULT_HOUR, hour)
            .putInt(KEY_ALL_DAY_DEFAULT_MINUTE, minute)
            .apply()
        
        val newTime = AllDayTime(hour, minute)
        _allDayTime.value = newTime
    }
    
    // Onboarding
    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
    
    fun isFirstLaunch(): Boolean = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    fun setFirstLaunchCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
    }
    
    // Sync tracking
    fun getLastSyncTime(): Long = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
    fun setLastSyncTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply()
    }
    
    fun updateLastSyncTime() = setLastSyncTime(System.currentTimeMillis())
    fun hasEverSynced(): Boolean = getLastSyncTime() > 0
    
    // Timezone handling
    fun handleTimezoneChange() {
        // Reset last sync time to force full resync on timezone changes
        setLastSyncTime(0L)
    }
    
    // Battery optimization (simplified)
    fun isBatteryOptimizationSetupCompleted(): Boolean = 
        prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_COMPLETED, false)
    
    fun setBatteryOptimizationSetupCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_OPTIMIZATION_COMPLETED, completed).apply()
        _batteryOptimizationCompleted.value = completed
    }
    
    // Premium purchase state - gates event details in notifications
    fun isPremiumPurchased(): Boolean = prefs.getBoolean(KEY_PREMIUM_PURCHASED, false)
    
    fun setPremiumPurchased(purchased: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM_PURCHASED, purchased).apply()
        _premiumPurchased.value = purchased
        Logger.d("SettingsRepository", "Premium status updated: $purchased")
    }
    
    // Utility methods
    fun getAllDayDefaultTimeFormatted(): String = _allDayTime.value.formatTime(context)
    
    fun getRefreshIntervalDescription(): String {
        val interval = getRefreshIntervalMinutes()
        return when {
            interval < 60 -> "$interval minute${if (interval != 1) "s" else ""}"
            interval == 60 -> "1 hour"
            else -> "${interval / 60} hour${if (interval != 60) "s" else ""}"
        }
    }
    
    fun resetToDefaults() {
        prefs.edit()
            .putInt(KEY_REFRESH_INTERVAL_MINUTES, DEFAULT_REFRESH_INTERVAL_MINUTES)
            .putInt(KEY_ALL_DAY_DEFAULT_HOUR, DEFAULT_ALL_DAY_HOUR)
            .putInt(KEY_ALL_DAY_DEFAULT_MINUTE, DEFAULT_ALL_DAY_MINUTE)
            .putBoolean(KEY_BATTERY_OPTIMIZATION_COMPLETED, false)
            .putBoolean(KEY_PREMIUM_PURCHASED, false)
            .apply()
        
        _refreshIntervalMinutes.value = DEFAULT_REFRESH_INTERVAL_MINUTES
        _allDayTime.value = AllDayTime(DEFAULT_ALL_DAY_HOUR, DEFAULT_ALL_DAY_MINUTE)
        _batteryOptimizationCompleted.value = false
        _premiumPurchased.value = false
    }
}