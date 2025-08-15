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
    
    fun showError(title: String, message: String, openSettings: Boolean = true) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        
        if (openSettings) {
            val settingsIntent = Intent().apply {
                setClassName(context, "com.example.calendaralarmscheduler.ui.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_fragment", "settings")
            }
            
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            builder.addAction(
                android.R.drawable.ic_menu_preferences,
                "Open Settings",
                pendingIntent
            )
        }
        
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(ERROR_NOTIFICATION_ID, builder.build())
            }
            Logger.i("ErrorNotificationManager", "Showed error notification: $title")
        } catch (e: SecurityException) {
            Logger.w("ErrorNotificationManager", "Cannot show notification - permission denied", e)
        }
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