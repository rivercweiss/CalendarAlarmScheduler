package com.example.calendaralarmscheduler.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class ErrorNotificationManager(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "calendar_alarm_errors"
        private const val CHANNEL_NAME = "Calendar Alarm Errors"
        private const val CHANNEL_DESCRIPTION = "Notifications for Calendar Alarm Scheduler errors"
        private const val ERROR_NOTIFICATION_ID = 2001
        
        // Error types
        const val ERROR_CALENDAR_PROVIDER = "calendar_provider"
        const val ERROR_ALARM_SCHEDULING = "alarm_scheduling"
        const val ERROR_PERMISSION_LOST = "permission_lost"
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showPersistentError(
        errorType: String,
        title: String,
        message: String,
        actionText: String? = null,
        actionIntent: Intent? = null
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        // Add action if provided
        if (actionText != null && actionIntent != null) {
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            builder.addAction(
                android.R.drawable.ic_menu_preferences,
                actionText,
                pendingIntent
            )
        }
        
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(ERROR_NOTIFICATION_ID, builder.build())
            }
            
            Logger.i("ErrorNotificationManager", "Showed persistent error notification: $errorType - $title")
        } catch (e: SecurityException) {
            Logger.w("ErrorNotificationManager", "Cannot show notification - permission denied", e)
        }
    }
    
    fun showCalendarProviderError() {
        showPersistentError(
            errorType = ERROR_CALENDAR_PROVIDER,
            title = "Calendar Access Error",
            message = "Failed to read calendar events after multiple attempts. Check calendar permissions and try refreshing.",
            actionText = "Open Settings",
            actionIntent = Intent().apply {
                setClassName(context, "com.example.calendaralarmscheduler.ui.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_fragment", "settings")
            }
        )
    }
    
    fun showAlarmSchedulingError(eventTitle: String) {
        showPersistentError(
            errorType = ERROR_ALARM_SCHEDULING,
            title = "Alarm Scheduling Failed",
            message = "Could not schedule alarm for '$eventTitle' after multiple attempts. Check alarm permissions and battery optimization settings.",
            actionText = "Open Settings",
            actionIntent = Intent().apply {
                setClassName(context, "com.example.calendaralarmscheduler.ui.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_fragment", "settings")
            }
        )
    }
    
    fun showPermissionLostError() {
        showPersistentError(
            errorType = ERROR_PERMISSION_LOST,
            title = "Required Permission Lost",
            message = "Calendar or alarm permission was revoked. The app cannot function properly without these permissions.",
            actionText = "Grant Permissions",
            actionIntent = Intent().apply {
                setClassName(context, "com.example.calendaralarmscheduler.ui.onboarding.PermissionOnboardingActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }
    
    fun clearErrorNotifications() {
        try {
            with(NotificationManagerCompat.from(context)) {
                cancel(ERROR_NOTIFICATION_ID)
            }
            Logger.d("ErrorNotificationManager", "Cleared error notifications")
        } catch (e: Exception) {
            Logger.w("ErrorNotificationManager", "Error clearing notifications", e)
        }
    }
}