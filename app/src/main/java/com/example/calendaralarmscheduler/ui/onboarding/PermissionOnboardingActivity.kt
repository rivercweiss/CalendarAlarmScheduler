package com.example.calendaralarmscheduler.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.calendaralarmscheduler.databinding.ActivityPermissionOnboardingBinding
import com.example.calendaralarmscheduler.utils.PermissionUtils
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PermissionOnboardingActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityPermissionOnboardingBinding
    private lateinit var adapter: OnboardingPagerAdapter
    
    // Permission launcher for single permissions (calendar, notification)
    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkAllPermissionsAndProceed()
        } else {
            // Stay on current page, user can try again
            showPermissionDeniedMessage()
        }
    }
    
    // Multiple permissions launcher
    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkAllPermissionsAndProceed()
        } else {
            showPermissionDeniedMessage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViewPager()
        setupButtons()
        checkInitialPermissions()
    }
    
    private fun setupViewPager() {
        adapter = OnboardingPagerAdapter(this) { step ->
            handleStepAction(step)
        }
        
        binding.viewPager.adapter = adapter
        binding.viewPager.isUserInputEnabled = false // Disable swipe
        
        // Setup tab dots
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ ->
            // Empty implementation - just creates dots
        }.attach()
        
        // Listen for page changes
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateButtons(position)
            }
        })
    }
    
    private fun setupButtons() {
        binding.buttonBack.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem > 0) {
                binding.viewPager.currentItem = currentItem - 1
            }
        }
        
        binding.buttonNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            val totalItems = adapter.itemCount
            
            if (currentItem < totalItems - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                // Last page - finish onboarding
                finishOnboarding()
            }
        }
        
        binding.buttonSkip.setOnClickListener {
            finishOnboarding()
        }
    }
    
    private fun checkInitialPermissions() {
        // Check if all permissions are already granted
        if (PermissionUtils.hasAllCriticalPermissions(this)) {
            // Jump to completion page
            binding.viewPager.currentItem = adapter.itemCount - 1
        }
    }
    
    private fun updateButtons(position: Int) {
        val totalItems = adapter.itemCount
        
        binding.buttonBack.visibility = if (position == 0) {
            android.view.View.INVISIBLE
        } else {
            android.view.View.VISIBLE
        }
        
        binding.buttonNext.text = if (position == totalItems - 1) {
            "Get Started"
        } else {
            "Next"
        }
        
        // Show skip button only on first few pages
        binding.buttonSkip.visibility = if (position < totalItems - 2) {
            android.view.View.VISIBLE
        } else {
            android.view.View.INVISIBLE
        }
    }
    
    private fun handleStepAction(step: OnboardingStep) {
        when (step) {
            OnboardingStep.CALENDAR_PERMISSION -> {
                requestCalendarPermission()
            }
            OnboardingStep.NOTIFICATION_PERMISSION -> {
                requestNotificationPermission()
            }
            OnboardingStep.EXACT_ALARM_PERMISSION -> {
                openExactAlarmSettings()
            }
            OnboardingStep.BATTERY_OPTIMIZATION -> {
                openBatteryOptimizationSettings()
            }
            else -> {
                // No action needed for welcome and completion steps
            }
        }
    }
    
    private fun requestCalendarPermission() {
        if (PermissionUtils.shouldShowCalendarPermissionRationale(this)) {
            // Show explanation first, then request
            PermissionUtils.requestCalendarPermission(singlePermissionLauncher)
        } else {
            PermissionUtils.requestCalendarPermission(singlePermissionLauncher)
        }
    }
    
    private fun requestNotificationPermission() {
        if (PermissionUtils.shouldShowNotificationPermissionRationale(this)) {
            // Show explanation first, then request
            PermissionUtils.requestNotificationPermission(singlePermissionLauncher)
        } else {
            PermissionUtils.requestNotificationPermission(singlePermissionLauncher)
        }
    }
    
    private fun openExactAlarmSettings() {
        val intent = PermissionUtils.getExactAlarmSettingsIntent(this)
        if (intent != null) {
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback to app settings
                startActivity(PermissionUtils.getAppSettingsIntent(this))
            }
        }
    }
    
    private fun openBatteryOptimizationSettings() {
        try {
            val result = PermissionUtils.getBestBatteryOptimizationIntent(this)
            startActivity(result.intent)
        } catch (e: Exception) {
            // Fallback to app settings
            startActivity(PermissionUtils.getAppSettingsIntent(this))
        }
    }
    
    private fun checkAllPermissionsAndProceed() {
        if (PermissionUtils.hasAllCriticalPermissions(this)) {
            // All critical permissions granted, but let user manually proceed to completion
            // Don't auto-advance to avoid confusion - let user click Next when ready
            adapter.refreshCurrentStep()
        } else {
            // Some permissions still missing, refresh current page
            adapter.refreshCurrentStep()
        }
    }
    
    private fun showPermissionDeniedMessage() {
        // Could show a snackbar or dialog explaining why permissions are needed
        // For now, just refresh the current step
        adapter.refreshCurrentStep()
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
            finish() // Still finish even on error
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh permission status when returning from settings
        // Don't auto-advance - let user control the flow
        adapter.refreshCurrentStep()
    }
}