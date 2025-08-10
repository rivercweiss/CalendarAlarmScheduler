package com.example.calendaralarmscheduler.ui.alarm

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.example.calendaralarmscheduler.CalendarAlarmApplication
import com.example.calendaralarmscheduler.R
import com.example.calendaralarmscheduler.receivers.AlarmReceiver
import com.example.calendaralarmscheduler.utils.AlarmNotificationManager
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.TimezoneUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.*

@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val dateFormatter = SimpleDateFormat("EEEE, MMMM dd 'at' h:mm a", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    
    // UI Components
    private lateinit var currentTimeTextView: TextView
    private lateinit var eventTitleTextView: TextView
    private lateinit var eventTimeTextView: TextView
    private lateinit var ruleTextView: TextView
    private lateinit var snoozeButton: Button
    private lateinit var dismissButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Logger.i("AlarmActivity_onCreate", "Alarm activity starting")
        
        // Make this activity work over lock screen and turn on screen
        setupFullScreenAlarm()
        
        // Extract alarm data from intent
        val alarmId = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_ID) ?: ""
        val eventTitle = intent.getStringExtra(AlarmReceiver.EXTRA_EVENT_TITLE) ?: "Unknown Event"
        val eventStartTime = intent.getLongExtra(AlarmReceiver.EXTRA_EVENT_START_TIME, 0L)
        val ruleId = intent.getStringExtra(AlarmReceiver.EXTRA_RULE_ID) ?: "unknown"
        val launchedFromNotification = intent.getBooleanExtra("LAUNCHED_FROM_NOTIFICATION", false)
        
        Logger.i("AlarmActivity_onCreate", "Displaying alarm for event: $eventTitle")
        
        if (launchedFromNotification) {
            Logger.i("AlarmActivity_onCreate", "🔔 Alarm launched from full-screen intent notification")
            // Dismiss the notification since we're now showing the full activity
            dismissAlarmNotification(alarmId)
        }
        
        // Set layout and initialize UI components
        setContentView(R.layout.activity_alarm)
        initializeViews()
        
        // Update UI with alarm data
        updateAlarmDisplay(eventTitle, eventStartTime, ruleId)
        
        // Set up button listeners
        setupButtonListeners(alarmId)
        
        // Set up back button handling - prevent dismissing alarm with back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Prevent back button from dismissing alarm
                // User must use dismiss or snooze buttons
                Logger.d("AlarmActivity_onBackPressed", "Back button pressed - ignoring")
            }
        })
        
        // Start alarm sound and vibration
        startAlarmNotification()
        
        // Update current time display
        updateCurrentTime()
    }
    
    private fun setupFullScreenAlarm() {
        // Always keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // For Android 10 (API 29) and above - use modern methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // For older versions - use deprecated flags
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        // Request to dismiss keyguard for newer versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
    
    private fun initializeViews() {
        currentTimeTextView = findViewById(R.id.currentTimeTextView)
        eventTitleTextView = findViewById(R.id.eventTitleTextView)
        eventTimeTextView = findViewById(R.id.eventTimeTextView)
        ruleTextView = findViewById(R.id.ruleTextView)
        snoozeButton = findViewById(R.id.snoozeButton)
        dismissButton = findViewById(R.id.dismissButton)
    }
    
    private fun updateAlarmDisplay(eventTitle: String, eventStartTime: Long, ruleId: String) {
        eventTitleTextView.text = eventTitle
        
        val eventTime = if (eventStartTime > 0) {
            // Use timezone-aware formatting with timezone indicator
            TimezoneUtils.formatTimeWithTimezone(eventStartTime, ZoneId.systemDefault(), true)
        } else {
            "Unknown time"
        }
        eventTimeTextView.text = eventTime
        ruleTextView.text = "Rule: $ruleId"
    }
    
    private fun updateCurrentTime() {
        // Use timezone-aware formatting for current time with timezone indicator
        val currentTime = TimezoneUtils.formatTimeWithTimezone(System.currentTimeMillis(), ZoneId.systemDefault(), true)
        currentTimeTextView.text = currentTime
    }
    
    private fun setupButtonListeners(alarmId: String) {
        snoozeButton.setOnClickListener {
            snoozeAlarm(alarmId)
        }
        
        dismissButton.setOnClickListener {
            dismissAlarm(alarmId)
        }
    }
    
    private fun startAlarmNotification() {
        try {
            // Start vibration
            startVibration()
            
            // Start alarm sound
            startAlarmSound()
            
        } catch (e: Exception) {
            Logger.e("AlarmActivity_startNotification", "Error starting alarm notification", e)
        }
    }
    
    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            // Create vibration pattern: vibrate for 1000ms, pause 500ms, repeat
            val pattern = longArrayOf(0, 1000, 500)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(pattern, 0) // 0 = repeat indefinitely
                vibrator?.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0) // 0 = repeat indefinitely
            }
            
            Logger.d("AlarmActivity_startVibration", "Vibration started")
        } catch (e: Exception) {
            Logger.e("AlarmActivity_startVibration", "Error starting vibration", e)
        }
    }
    
    private fun startAlarmSound() {
        try {
            mediaPlayer = MediaPlayer().apply {
                // Use alarm stream to bypass DND/silent mode
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                
                // Use system default alarm sound
                val alarmUri = android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI
                setDataSource(this@AlarmActivity, alarmUri)
                
                isLooping = true
                prepare()
                start()
            }
            
            // Ensure volume is at maximum for alarm stream
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            
            Logger.d("AlarmActivity_startSound", "Alarm sound started")
        } catch (e: Exception) {
            Logger.e("AlarmActivity_startSound", "Error starting alarm sound", e)
            // Fallback to system notification sound if alarm sound fails
            startFallbackSound()
        }
    }
    
    private fun startFallbackSound() {
        try {
            mediaPlayer = MediaPlayer().apply {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setAudioAttributes(audioAttributes)
                
                val notificationUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                setDataSource(this@AlarmActivity, notificationUri)
                
                isLooping = true
                prepare()
                start()
            }
            Logger.d("AlarmActivity_startFallbackSound", "Fallback sound started")
        } catch (e: Exception) {
            Logger.e("AlarmActivity_startFallbackSound", "Error starting fallback sound", e)
        }
    }
    
    private fun stopAlarmNotification() {
        try {
            // Stop sound
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            
            // Stop vibration
            vibrator?.cancel()
            vibrator = null
            
            Logger.d("AlarmActivity_stopNotification", "Alarm notification stopped")
        } catch (e: Exception) {
            Logger.e("AlarmActivity_stopNotification", "Error stopping alarm notification", e)
        }
    }
    
    private fun dismissAlarm(alarmId: String) {
        Logger.i("AlarmActivity_dismissAlarm", "User dismissed alarm: $alarmId")
        
        lifecycleScope.launch {
            try {
                // Mark alarm as dismissed in database
                val app = application as CalendarAlarmApplication
                val alarmRepository = app.alarmRepository
                alarmRepository.markAlarmDismissed(alarmId)
                
                Logger.i("AlarmActivity_dismissAlarm", "Alarm marked as dismissed in database")
            } catch (e: Exception) {
                Logger.e("AlarmActivity_dismissAlarm", "Error marking alarm as dismissed", e)
            }
        }
        
        finishAlarm()
    }
    
    private fun snoozeAlarm(alarmId: String) {
        Logger.i("AlarmActivity_snoozeAlarm", "User snoozed alarm: $alarmId")
        
        lifecycleScope.launch {
            try {
                // Schedule alarm for 5 minutes from now
                val app = application as CalendarAlarmApplication
                val alarmScheduler = app.alarmScheduler
                
                val snoozeTime = System.currentTimeMillis() + (5 * 60 * 1000) // 5 minutes
                
                // Create a new alarm for snooze
                alarmScheduler.scheduleSnoozeAlarm(alarmId, snoozeTime)
                
                Logger.i("AlarmActivity_snoozeAlarm", "Snooze alarm scheduled for 5 minutes from now")
            } catch (e: Exception) {
                Logger.e("AlarmActivity_snoozeAlarm", "Error scheduling snooze alarm", e)
            }
        }
        
        finishAlarm()
    }
    
    private fun finishAlarm() {
        stopAlarmNotification()
        finish()
    }
    
    /**
     * Dismisses the alarm notification when the activity is launched
     */
    private fun dismissAlarmNotification(alarmId: String) {
        try {
            val alarmNotificationManager = AlarmNotificationManager(this)
            alarmNotificationManager.dismissAlarmNotification(alarmId)
            Logger.d("AlarmActivity_dismissNotification", "Dismissed notification for alarm: $alarmId")
        } catch (e: Exception) {
            Logger.w("AlarmActivity_dismissNotification", "Error dismissing notification", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAlarmNotification()
    }
    
}