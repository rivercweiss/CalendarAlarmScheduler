package com.example.calendaralarmscheduler.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.calendaralarmscheduler.utils.Logger

class AlarmNotificationManager(private val context: Context) {
    
    companion object {
        private const val ALARM_CHANNEL_ID = "calendar_alarms"
        private const val ALARM_CHANNEL_NAME = "Calendar Alarms"
        private const val ALARM_CHANNEL_DESCRIPTION = "Unmissable notifications for calendar alarm alerts"
        private const val ALARM_NOTIFICATION_ID_BASE = 3000
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                ALARM_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = ALARM_CHANNEL_DESCRIPTION
                
                // Make it unmissable - bypass DND and show over lockscreen
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
                
                // Use alarm sound
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                
                // Strong vibration pattern
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Logger.d("AlarmNotificationManager", "Created unmissable alarm notification channel")
        }
    }
    
    fun showAlarmNotification(
        alarmId: String,
        eventTitle: String,
        eventStartTime: Long,
        ruleId: String? = null,
        isTestAlarm: Boolean = false
    ) {
        try {
            Logger.i("AlarmNotificationManager", "Showing unmissable alarm notification for: $eventTitle")
            
            // Create dismiss action
            val dismissIntent = Intent("com.example.calendaralarmscheduler.DISMISS_ALARM").apply {
                setPackage(context.packageName)
                putExtra("ALARM_ID", alarmId)
            }
            
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                generateNotificationId(alarmId),
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build unmissable notification
            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(if (isTestAlarm) "📅 Test Alarm" else "📅 Calendar Alarm")
                .setContentText(eventTitle)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(if (isTestAlarm) 
                        "Test alarm for: $eventTitle" 
                    else 
                        "Calendar event: $eventTitle"
                    ))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                
                // Tapping notification dismisses it
                .setContentIntent(dismissPendingIntent)
                
                // Add dismiss action button
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss",
                    dismissPendingIntent
                )
                
                // Ensure sound and vibration work regardless of phone state
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                
                .build()
            
            // Show the notification
            val notificationId = generateNotificationId(alarmId)
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notification)
            }
            
            Logger.i("AlarmNotificationManager", "✅ Unmissable alarm notification shown with ID: $notificationId")
            
        } catch (e: Exception) {
            Logger.e("AlarmNotificationManager", "❌ Error showing alarm notification", e)
        }
    }
    
    private fun generateNotificationId(alarmId: String): Int {
        return ALARM_NOTIFICATION_ID_BASE + Math.abs(alarmId.hashCode() % 1000)
    }
    
    fun dismissAlarmNotification(alarmId: String) {
        try {
            val notificationId = generateNotificationId(alarmId)
            with(NotificationManagerCompat.from(context)) {
                cancel(notificationId)
            }
            Logger.d("AlarmNotificationManager", "Dismissed alarm notification: $notificationId")
        } catch (e: Exception) {
            Logger.w("AlarmNotificationManager", "Error dismissing alarm notification", e)
        }
    }
}