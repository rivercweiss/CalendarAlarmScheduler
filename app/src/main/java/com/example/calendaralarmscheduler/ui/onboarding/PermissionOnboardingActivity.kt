package com.example.calendaralarmscheduler.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.calendaralarmscheduler.databinding.ActivityPermissionOnboardingBinding
import com.example.calendaralarmscheduler.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionOnboardingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPermissionOnboardingBinding
    
    // Permission launcher for single permissions (calendar, notification)
    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { 
        updateAllPermissionStatus()
    }
    
    // Multiple permissions launcher
    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { 
        updateAllPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupButtons()
        updateAllPermissionStatus()
    }
    
    private fun setupButtons() {
        // Calendar permission button
        binding.buttonCalendar.setOnClickListener {
            requestCalendarPermission()
        }
        
        // Notification permission button
        binding.buttonNotification.setOnClickListener {
            requestNotificationPermission()
        }
        
        // Exact alarm permission button
        binding.buttonExactAlarm.setOnClickListener {
            openExactAlarmSettings()
        }
        
        // Battery optimization button
        binding.buttonBattery.setOnClickListener {
            openBatteryOptimizationSettings()
        }
        
        // Continue button
        binding.buttonContinue.setOnClickListener {
            finishOnboarding()
        }
    }
    
    private fun updateAllPermissionStatus() {
        val status = PermissionUtils.getAllPermissionStatus(this)
        
        // Update calendar permission status
        updateCalendarPermissionStatus(status.hasCalendarPermission)
        
        // Update notification permission status
        updateNotificationPermissionStatus(status.hasNotificationPermission)
        
        // Update exact alarm permission status
        updateExactAlarmPermissionStatus(status.hasExactAlarmPermission)
        
        // Update battery optimization status
        updateBatteryOptimizationStatus(status)
        
        // Update continue button
        val allCriticalGranted = status.hasCalendarPermission && 
                                status.hasNotificationPermission && 
                                status.hasExactAlarmPermission
        binding.buttonContinue.isEnabled = allCriticalGranted
    }
    
    private fun updateCalendarPermissionStatus(granted: Boolean) {
        if (granted) {
            binding.textCalendarStatus.text = "✅ Permission granted"
            binding.buttonCalendar.visibility = View.GONE
        } else {
            binding.textCalendarStatus.text = "❌ Permission required"
            binding.buttonCalendar.visibility = View.VISIBLE
        }
    }
    
    private fun updateNotificationPermissionStatus(granted: Boolean) {
        if (granted) {
            binding.textNotificationStatus.text = "✅ Permission granted"
            binding.buttonNotification.visibility = View.GONE
        } else {
            binding.textNotificationStatus.text = "❌ Permission required"
            binding.buttonNotification.visibility = View.VISIBLE
        }
    }
    
    private fun updateExactAlarmPermissionStatus(granted: Boolean) {
        if (granted) {
            binding.textExactAlarmStatus.text = "✅ Permission granted"
            binding.buttonExactAlarm.visibility = View.GONE
        } else {
            binding.textExactAlarmStatus.text = "❌ Permission required"
            binding.buttonExactAlarm.visibility = View.VISIBLE
        }
    }
    
    private fun updateBatteryOptimizationStatus(status: PermissionUtils.PermissionStatus) {
        if (!status.isBatteryOptimizationAvailable) {
            binding.textBatteryStatus.text = "✅ Not applicable on this device"
            binding.buttonBattery.visibility = View.GONE
        } else if (status.isBatteryOptimizationWhitelisted) {
            binding.textBatteryStatus.text = "✅ App whitelisted from optimization"
            binding.buttonBattery.visibility = View.GONE
        } else {
            binding.textBatteryStatus.text = "⚠️ Recommended to whitelist app"
            binding.buttonBattery.visibility = View.VISIBLE
        }
    }
    
    private fun requestCalendarPermission() {
        PermissionUtils.requestCalendarPermission(singlePermissionLauncher)
    }
    
    private fun requestNotificationPermission() {
        PermissionUtils.requestNotificationPermission(singlePermissionLauncher)
    }
    
    private fun openExactAlarmSettings() {
        val intent = PermissionUtils.getExactAlarmSettingsIntent(this)
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(PermissionUtils.getAppSettingsIntent(this))
            }
        }
    }
    
    private fun openBatteryOptimizationSettings() {
        try {
            val result = PermissionUtils.getBestBatteryOptimizationIntent(this)
            startActivity(result.intent)
        } catch (e: Exception) {
            startActivity(PermissionUtils.getAppSettingsIntent(this))
        }
    }
    
    private fun finishOnboarding() {
        try {
            // Mark onboarding as completed
            val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
            
            // Log completion for debugging
            val hasCriticalPermissions = PermissionUtils.hasAllCriticalPermissions(this)
            android.util.Log.i("PermissionOnboarding", "Onboarding completed - Critical permissions: $hasCriticalPermissions")
            
            // Close onboarding and return to main activity
            setResult(RESULT_OK)
            finish()
        } catch (e: Exception) {
            android.util.Log.e("PermissionOnboarding", "Error finishing onboarding", e)
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh permission status when returning from settings
        updateAllPermissionStatus()
    }
}