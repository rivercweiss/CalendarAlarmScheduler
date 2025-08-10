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
import com.example.calendaralarmscheduler.ui.alarm.AlarmActivity
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.PermissionUtils

class AlarmNotificationManager(private val context: Context) {
    
    companion object {
        private const val ALARM_CHANNEL_ID = "calendar_alarms"
        private const val ALARM_CHANNEL_NAME = "Calendar Alarms"
        private const val ALARM_CHANNEL_DESCRIPTION = "Notifications for calendar alarm alerts"
        private const val ALARM_NOTIFICATION_ID_BASE = 3000
        
        // Notification importance levels
        private const val ALARM_IMPORTANCE = NotificationManager.IMPORTANCE_HIGH
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                ALARM_CHANNEL_NAME,
                ALARM_IMPORTANCE
            ).apply {
                description = ALARM_CHANNEL_DESCRIPTION
                
                // Configure for alarms - bypass DND and show over lockscreen
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setShowBadge(true)
                
                // Use default alarm sound
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                
                setSound(alarmSound, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                
                // Enable vibration
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250, 250, 250)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(alarmChannel)
            
            Logger.d("AlarmNotificationManager", "Created alarm notification channel")
        }
    }
    
    /**
     * Shows a full-screen intent notification for an alarm that bypasses Android's
     * Background Activity Launch restrictions
     */
    fun showAlarmNotification(
        alarmId: String,
        eventTitle: String,
        eventStartTime: Long,
        ruleId: String? = null,
        isTestAlarm: Boolean = false
    ) {
        try {
            Logger.i("AlarmNotificationManager", "=== SHOWING ALARM NOTIFICATION ===")
            Logger.i("AlarmNotificationManager", "Alarm ID: '$alarmId'")
            Logger.i("AlarmNotificationManager", "Event Title: '$eventTitle'")
            Logger.i("AlarmNotificationManager", "Is Test: $isTestAlarm")
            
            // Check if full-screen intent permission is granted
            val hasFullScreenPermission = PermissionUtils.hasFullScreenIntentPermission(context)
            Logger.i("AlarmNotificationManager", "Full-screen intent permission granted: $hasFullScreenPermission")
            
            // Create intent for AlarmActivity
            val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                
                // Pass all alarm data
                putExtra("ALARM_ID", alarmId)
                putExtra("EVENT_TITLE", eventTitle)
                putExtra("EVENT_START_TIME", eventStartTime)
                ruleId?.let { putExtra("RULE_ID", it) }
                putExtra("IS_TEST_ALARM", isTestAlarm)
                putExtra("LAUNCHED_FROM_NOTIFICATION", true)
            }
            
            // Create PendingIntent for full-screen launch
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                generateNotificationId(alarmId),
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create content intent (when notification is tapped)
            val contentIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("ALARM_ID", alarmId)
                putExtra("EVENT_TITLE", eventTitle)
                putExtra("EVENT_START_TIME", eventStartTime)
                ruleId?.let { putExtra("RULE_ID", it) }
                putExtra("IS_TEST_ALARM", isTestAlarm)
                putExtra("LAUNCHED_FROM_NOTIFICATION", true)
            }
            
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                generateNotificationId(alarmId) + 1,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Create dismiss action
            val dismissIntent = createDismissIntent(alarmId)
            val dismissPendingIntent = PendingIntent.getBroadcast(
                context,
                generateNotificationId(alarmId) + 2,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // Build the notification
            val notificationBuilder = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(if (isTestAlarm) "📅 Test Alarm" else "📅 Calendar Alarm")
                .setContentText(eventTitle)
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText(if (isTestAlarm) 
                        "Test alarm for: $eventTitle" 
                    else 
                        "Calendar event alarm: $eventTitle"
                    ))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                
                // Content intent for when notification is tapped
                .setContentIntent(contentPendingIntent)
            
            // Conditionally set full-screen intent based on permission
            if (hasFullScreenPermission) {
                Logger.i("AlarmNotificationManager", "✅ Using full-screen intent (permission granted)")
                notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
            } else {
                Logger.w("AlarmNotificationManager", "⚠️ Full-screen intent permission denied, using high-priority notification fallback")
                // Make notification more prominent when full-screen intent is not available
                notificationBuilder
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            }
            
            val notification = notificationBuilder
                // Add dismiss action
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Dismiss",
                    dismissPendingIntent
                )
                
                // Sound and vibration (handled by channel on Android O+)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .setVibrate(longArrayOf(0, 250, 250, 250, 250, 250))
                
                .build()
            
            // Show the notification
            val notificationId = generateNotificationId(alarmId)
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, notification)
            }
            
            Logger.i("AlarmNotificationManager", "✅ Alarm notification shown with ID: $notificationId")
            if (hasFullScreenPermission) {
                Logger.i("AlarmNotificationManager", "📱 Full-screen intent will bypass BAL restrictions")
            } else {
                Logger.i("AlarmNotificationManager", "🔔 Using high-priority notification fallback")
            }
            Logger.i("AlarmNotificationManager", "=== NOTIFICATION DISPLAY COMPLETE ===")
            
        } catch (e: SecurityException) {
            Logger.e("AlarmNotificationManager", "❌ Cannot show notification - permission denied", e)
            
            // Fallback: try direct activity launch for older Android versions
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Logger.i("AlarmNotificationManager", "Attempting fallback direct activity launch for older Android")
                fallbackToDirectLaunch(alarmId, eventTitle, eventStartTime, ruleId, isTestAlarm)
            }
        } catch (e: Exception) {
            Logger.e("AlarmNotificationManager", "❌ Error showing alarm notification", e)
        }
    }
    
    /**
     * Fallback method for older Android versions or when notifications fail
     */
    private fun fallbackToDirectLaunch(
        alarmId: String,
        eventTitle: String,
        eventStartTime: Long,
        ruleId: String?,
        isTestAlarm: Boolean
    ) {
        try {
            Logger.w("AlarmNotificationManager", "Using fallback direct activity launch")
            
            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("ALARM_ID", alarmId)
                putExtra("EVENT_TITLE", eventTitle)
                putExtra("EVENT_START_TIME", eventStartTime)
                ruleId?.let { putExtra("RULE_ID", it) }
                putExtra("IS_TEST_ALARM", isTestAlarm)
                putExtra("LAUNCHED_FROM_FALLBACK", true)
            }
            
            context.startActivity(alarmIntent)
            Logger.i("AlarmNotificationManager", "✅ Fallback activity launch successful")
            
        } catch (e: Exception) {
            Logger.e("AlarmNotificationManager", "❌ Fallback activity launch also failed", e)
        }
    }
    
    /**
     * Creates a dismiss intent for the alarm notification
     */
    private fun createDismissIntent(alarmId: String): Intent {
        return Intent("com.example.calendaralarmscheduler.DISMISS_ALARM").apply {
            setPackage(context.packageName)
            putExtra("ALARM_ID", alarmId)
        }
    }
    
    /**
     * Generates a unique notification ID for each alarm
     */
    private fun generateNotificationId(alarmId: String): Int {
        return ALARM_NOTIFICATION_ID_BASE + Math.abs(alarmId.hashCode() % 1000)
    }
    
    /**
     * Dismisses an alarm notification
     */
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
    
    /**
     * Dismisses all alarm notifications
     */
    fun dismissAllAlarmNotifications() {
        try {
            with(NotificationManagerCompat.from(context)) {
                // Cancel notifications in our ID range
                for (i in 0..999) {
                    cancel(ALARM_NOTIFICATION_ID_BASE + i)
                }
            }
            Logger.d("AlarmNotificationManager", "Dismissed all alarm notifications")
        } catch (e: Exception) {
            Logger.w("AlarmNotificationManager", "Error dismissing all alarm notifications", e)
        }
    }
    
    /**
     * Checks if notification permission is granted
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true // Pre-Android 13 doesn't require runtime notification permission
        }
    }
}