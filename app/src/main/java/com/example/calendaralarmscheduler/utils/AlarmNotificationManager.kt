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
import kotlin.math.abs
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.ui.AlarmActivity
import com.example.calendaralarmscheduler.utils.Logger

/**
 * Creates unmissable alarm notifications that bypass DND and silent mode.
 * 
 * Premium Feature Gating:
 * - Free users: Show "Calendar Event" with upgrade message
 * - Premium users: Show actual event titles and descriptions
 * - Test alarms: Always show full details for testing
 */
class AlarmNotificationManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    
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
    
    @Suppress("UNUSED_PARAMETER")
    fun showAlarmNotification(
        alarmId: String,
        eventTitle: String,
        eventStartTime: Long,
        ruleId: String? = null,
        isTestAlarm: Boolean = false
    ) {
        try {
            Logger.i("AlarmNotificationManager", "Showing unmissable alarm notification for: $eventTitle")
            
            // Premium Feature: Gate event details behind purchase
            // Free users see generic "Calendar Event", premium users see actual event title
            val isPremium = settingsRepository.isPremiumPurchased()
            val displayTitle = if (isPremium || isTestAlarm) eventTitle else "Calendar Event"
            
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
            
            // Create full-screen alarm intent for lock screen display
            val alarmActivityIntent = Intent(context, AlarmActivity::class.java).apply {
                putExtra(AlarmActivity.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmActivity.EXTRA_EVENT_TITLE, eventTitle)
                putExtra(AlarmActivity.EXTRA_IS_TEST_ALARM, isTestAlarm)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                generateNotificationId(alarmId) + 1, // Offset to avoid ID collision
                alarmActivityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build unmissable notification
            val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(if (isTestAlarm) "📅 Test Alarm" else "📅 Calendar Alarm")
                .setContentText(displayTitle)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(when {
                        isTestAlarm -> "Test alarm for: $eventTitle"
                        isPremium -> "Calendar event: $eventTitle"
                        else -> "Calendar event alarm. Upgrade to see event details."
                    }))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                
                // Tapping notification dismisses it
                .setContentIntent(dismissPendingIntent)
                
                // Add dismiss action button
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss",
                    dismissPendingIntent
                )
                .build()
            
            // Show the notification with permission check
            val notificationId = generateNotificationId(alarmId)
            val notificationManager = NotificationManagerCompat.from(context)
            
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(notificationId, notification)
            } else {
                Logger.w("AlarmNotificationManager", "❌ Notifications are disabled for this app - alarm may not be visible")
            }
            
            Logger.i("AlarmNotificationManager", "✅ Unmissable alarm notification shown with ID: $notificationId")
            
        } catch (e: Exception) {
            Logger.e("AlarmNotificationManager", "❌ Error showing alarm notification", e)
        }
    }
    
    private fun generateNotificationId(alarmId: String): Int {
        return ALARM_NOTIFICATION_ID_BASE + abs(alarmId.hashCode() % 1000)
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