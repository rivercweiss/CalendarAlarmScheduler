package com.example.calendaralarmscheduler.utils

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Simplified permission utilities focused on core functionality.
 * Handles calendar, notification, exact alarm, and battery optimization permissions.
 */
object PermissionUtils {

    /**
     * Check if we have calendar read permission
     */
    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if we have exact alarm scheduling permission (Android 12+)
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Check if we have notification permission (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if battery optimization feature is available on this device
     * Some devices (especially emulators) don't support battery optimization
     */
    fun isBatteryOptimizationFeatureAvailable(context: Context): Boolean {
        // Try direct whitelist intent first
        val directIntent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
        }
        
        if (canResolveIntent(context, directIntent)) {
            return true
        }
        
        // Try battery optimization settings list
        val settingsIntent = Intent().apply {
            action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        }
        
        if (canResolveIntent(context, settingsIntent)) {
            return true
        }
        
        // If neither intent resolves, battery optimization is not available
        return false
    }

    /**
     * Check if app is whitelisted from battery optimization
     * Only call this if isBatteryOptimizationFeatureAvailable() returns true
     */
    fun isBatteryOptimizationWhitelisted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Check all critical permissions at once
     */
    fun getAllPermissionStatus(context: Context): PermissionStatus {
        val batteryOptimizationAvailable = isBatteryOptimizationFeatureAvailable(context)
        val batteryOptimizationWhitelisted = if (batteryOptimizationAvailable) {
            isBatteryOptimizationWhitelisted(context)
        } else {
            true // Consider it "whitelisted" if feature doesn't exist
        }
        
        return PermissionStatus(
            hasCalendarPermission = hasCalendarPermission(context),
            hasNotificationPermission = hasNotificationPermission(context),
            hasExactAlarmPermission = hasExactAlarmPermission(context),
            isBatteryOptimizationAvailable = batteryOptimizationAvailable,
            isBatteryOptimizationWhitelisted = batteryOptimizationWhitelisted
        )
    }

    /**
     * Request calendar permission
     */
    fun requestCalendarPermission(launcher: ActivityResultLauncher<String>) {
        launcher.launch(Manifest.permission.READ_CALENDAR)
    }

    /**
     * Request notification permission (Android 13+)
     */
    fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Request multiple permissions
     */
    fun requestMultiplePermissions(
        launcher: ActivityResultLauncher<Array<String>>,
        permissions: Array<String>
    ) {
        launcher.launch(permissions)
    }

    /**
     * Get intent to open exact alarm settings (Android 12+)
     */
    fun getExactAlarmSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }

    /**
     * Get intent to open notification settings
     */
    fun getNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            getAppSettingsIntent(context)
        }
    }

    /**
     * Get battery optimization intent with availability detection
     */
    fun getBestBatteryOptimizationIntent(context: Context): BatteryOptimizationResult {
        if (!isBatteryOptimizationFeatureAvailable(context)) {
            return BatteryOptimizationResult(
                intent = getAppSettingsIntent(context),
                guidance = "Battery optimization is not available on this device",
                isAvailable = false
            )
        }
        
        // Try direct whitelist intent first
        val directIntent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
        }

        if (canResolveIntent(context, directIntent)) {
            return BatteryOptimizationResult(
                intent = directIntent,
                guidance = "Tap 'Allow' to whitelist this app from battery optimization",
                isAvailable = true
            )
        }

        // Try battery optimization settings list
        val settingsIntent = Intent().apply {
            action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        }

        if (canResolveIntent(context, settingsIntent)) {
            return BatteryOptimizationResult(
                intent = settingsIntent,
                guidance = "Find 'Calendar Alarm Scheduler' and set to 'Don't optimize'",
                isAvailable = true
            )
        }

        // Fallback to app settings
        return BatteryOptimizationResult(
            intent = getAppSettingsIntent(context),
            guidance = "Look for Battery settings and disable optimization for this app",
            isAvailable = true
        )
    }

    /**
     * Get intent to open app settings
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Check if permission rationale should be shown
     */
    fun shouldShowCalendarPermissionRationale(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.READ_CALENDAR
        )
    }

    /**
     * Check if notification permission rationale should be shown (Android 13+)
     */
    fun shouldShowNotificationPermissionRationale(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            false
        }
    }

    /**
     * Get user-friendly permission status messages
     */
    fun getPermissionStatusMessage(context: Context): List<PermissionMessage> {
        val messages = mutableListOf<PermissionMessage>()
        val status = getAllPermissionStatus(context)

        if (!status.hasCalendarPermission) {
            messages.add(
                PermissionMessage(
                    title = "Calendar Access Required",
                    message = "The app needs access to read your calendar events to schedule alarms.",
                    actionText = "Grant Permission",
                    isError = true
                )
            )
        }

        if (!status.hasNotificationPermission) {
            messages.add(
                PermissionMessage(
                    title = "Notification Permission Required",
                    message = "Permission to show notifications including alarm notifications is required.",
                    actionText = "Grant Permission", 
                    isError = true
                )
            )
        }

        if (!status.hasExactAlarmPermission) {
            messages.add(
                PermissionMessage(
                    title = "Exact Alarm Permission Required",
                    message = "Permission to schedule exact alarms ensures your alarms fire precisely on time.",
                    actionText = "Open Settings",
                    isError = true
                )
            )
        }

        if (status.isBatteryOptimizationNeeded()) {
            messages.add(
                PermissionMessage(
                    title = "Battery Optimization",
                    message = "For reliable background operation, please whitelist this app from battery optimization.",
                    actionText = "Whitelist App",
                    isError = false
                )
            )
        }

        if (messages.isEmpty()) {
            messages.add(
                PermissionMessage(
                    title = "All Permissions Granted",
                    message = "The app has all necessary permissions to schedule reliable alarms.",
                    actionText = "",
                    isError = false
                )
            )
        }

        return messages
    }

    /**
     * Check if all critical permissions are granted
     */
    fun hasAllCriticalPermissions(context: Context): Boolean {
        val status = getAllPermissionStatus(context)
        return status.hasCalendarPermission && status.hasNotificationPermission && status.hasExactAlarmPermission
    }

    /**
     * Get list of missing permissions that can be requested
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        if (!hasCalendarPermission(context)) {
            missingPermissions.add(Manifest.permission.READ_CALENDAR)
        }
        
        if (!hasNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        return missingPermissions
    }

    /**
     * Check if an intent can be resolved
     */
    private fun canResolveIntent(context: Context, intent: Intent): Boolean {
        return try {
            val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
            resolveInfos.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // Data classes
    data class PermissionStatus(
        val hasCalendarPermission: Boolean,
        val hasNotificationPermission: Boolean,
        val hasExactAlarmPermission: Boolean,
        val isBatteryOptimizationAvailable: Boolean,
        val isBatteryOptimizationWhitelisted: Boolean
    ) {
        fun areAllGranted(): Boolean {
            return hasCalendarPermission && hasNotificationPermission && hasExactAlarmPermission
        }
        
        fun areAllOptimal(): Boolean {
            return hasCalendarPermission && hasNotificationPermission && hasExactAlarmPermission && 
                   (!isBatteryOptimizationAvailable || isBatteryOptimizationWhitelisted)
        }
        
        fun isBatteryOptimizationNeeded(): Boolean {
            return isBatteryOptimizationAvailable && !isBatteryOptimizationWhitelisted
        }
    }

    data class PermissionMessage(
        val title: String,
        val message: String,
        val actionText: String,
        val isError: Boolean
    )

    data class BatteryOptimizationResult(
        val intent: Intent,
        val guidance: String,
        val isAvailable: Boolean = true
    )
}