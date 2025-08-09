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
            // Pre-Android 12, no special permission needed
            true
        }
    }

    /**
     * Check if the app is whitelisted from battery optimization
     */
    fun isBatteryOptimizationWhitelisted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // No battery optimization on pre-M devices
        }
    }

    /**
     * Check all critical permissions at once
     */
    fun getAllPermissionStatus(context: Context): PermissionStatus {
        return PermissionStatus(
            hasCalendarPermission = hasCalendarPermission(context),
            hasExactAlarmPermission = hasExactAlarmPermission(context),
            isBatteryOptimizationWhitelisted = isBatteryOptimizationWhitelisted(context)
        )
    }

    /**
     * Request calendar permission using the provided ActivityResultLauncher
     */
    fun requestCalendarPermission(
        launcher: ActivityResultLauncher<String>
    ) {
        launcher.launch(Manifest.permission.READ_CALENDAR)
    }

    /**
     * Request multiple permissions using the provided ActivityResultLauncher
     */
    fun requestMultiplePermissions(
        launcher: ActivityResultLauncher<Array<String>>,
        permissions: Array<String>
    ) {
        launcher.launch(permissions)
    }

    /**
     * Get an intent to open exact alarm settings (Android 12+)
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
     * Get an intent to open battery optimization settings
     */
    fun getBatteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            // Fallback to general app settings
            Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /**
     * Get an intent to open general app settings
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Check if we should show permission rationale
     */
    fun shouldShowCalendarPermissionRationale(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.READ_CALENDAR
        )
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
                    message = "The app needs access to read your calendar events to schedule alarms. This permission is essential for the app to function.",
                    actionText = "Grant Permission",
                    isError = true
                )
            )
        }

        if (!status.hasExactAlarmPermission) {
            messages.add(
                PermissionMessage(
                    title = "Exact Alarm Permission Required",
                    message = "Android 12+ requires special permission to schedule exact alarms. This ensures your alarms fire precisely on time.",
                    actionText = "Open Settings",
                    isError = true
                )
            )
        }

        if (!status.isBatteryOptimizationWhitelisted) {
            messages.add(
                PermissionMessage(
                    title = "Battery Optimization",
                    message = "For reliable background operation, please whitelist this app from battery optimization. This prevents Android from limiting alarm scheduling.",
                    actionText = "Whitelist App",
                    isError = false // Warning, not critical error
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
        return status.hasCalendarPermission && status.hasExactAlarmPermission
    }

    /**
     * Get the list of permissions that need to be requested
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        if (!hasCalendarPermission(context)) {
            missingPermissions.add(Manifest.permission.READ_CALENDAR)
        }
        
        // Note: SCHEDULE_EXACT_ALARM can't be requested via ActivityResultLauncher,
        // it requires opening system settings
        
        return missingPermissions
    }

    data class PermissionStatus(
        val hasCalendarPermission: Boolean,
        val hasExactAlarmPermission: Boolean,
        val isBatteryOptimizationWhitelisted: Boolean
    ) {
        fun areAllGranted(): Boolean {
            return hasCalendarPermission && hasExactAlarmPermission
        }
        
        fun areAllOptimal(): Boolean {
            return hasCalendarPermission && hasExactAlarmPermission && isBatteryOptimizationWhitelisted
        }
    }

    data class PermissionMessage(
        val title: String,
        val message: String,
        val actionText: String,
        val isError: Boolean
    )
}