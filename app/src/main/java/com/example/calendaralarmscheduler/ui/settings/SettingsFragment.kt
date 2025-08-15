package com.example.calendaralarmscheduler.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.calendaralarmscheduler.ui.BaseFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.calendaralarmscheduler.R
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.databinding.FragmentSettingsBinding
import com.example.calendaralarmscheduler.utils.BackgroundUsageDetector
import com.example.calendaralarmscheduler.utils.DozeCompatibilityUtils
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.PermissionUtils
import com.example.calendaralarmscheduler.utils.TimezoneUtils
import com.example.calendaralarmscheduler.workers.WorkerManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : BaseFragment() {
    override val fragmentName = "SettingsFragment"
    
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: SettingsViewModel by viewModels()
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    // Track battery optimization attempts  
    private var batteryOptimizationLaunchTime = 0L
    private var lastBatteryOptimizationResult: PermissionUtils.BatteryOptimizationResult? = null
    
    // Permission launcher for single permissions (calendar, notification)
    private val singlePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Logger.i("SettingsFragment", "Permission granted")
            updatePermissionStatus()
            Toast.makeText(requireContext(), "Permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Logger.w("SettingsFragment", "Permission denied")
            Toast.makeText(requireContext(), "This permission is required for the app to function properly", Toast.LENGTH_LONG).show()
        }
    }
    
    // Activity launcher for system settings
    private val systemSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val returnTime = System.currentTimeMillis()
        val timeSpentInSettings = returnTime - batteryOptimizationLaunchTime
        
        Logger.d("SettingsFragment", "Returned from system settings")
        Logger.d("SettingsFragment", "Time spent in settings: ${timeSpentInSettings}ms")
        
        // Clear background usage cache since user may have changed settings
        BackgroundUsageDetector.clearCache()
        
        // Update permission status immediately
        updatePermissionStatus()
        
        // Check if battery optimization status changed and provide feedback
        lifecycleScope.launch {
            // Add a small delay to allow system to update permission status
            kotlinx.coroutines.delay(500)
            
            val status = PermissionUtils.getAllPermissionStatus(requireContext())
            val backgroundStatus = PermissionUtils.getBackgroundUsageStatus(requireContext())
            
            Logger.d("SettingsFragment", "Background usage status after return:")
            Logger.d("SettingsFragment", "  Allowed: ${backgroundStatus.isBackgroundUsageAllowed}")
            Logger.d("SettingsFragment", "  Method: ${backgroundStatus.detectionMethod}")
            Logger.d("SettingsFragment", "  Legacy check: ${status.isBatteryOptimizationWhitelisted}")
            
            // Use the comprehensive background usage status for feedback
            if (backgroundStatus.isBackgroundUsageAllowed) {
                Logger.i("SettingsFragment", "✅ Battery optimization disabled successfully!")
                
                // Record successful completion
                val methodUsed = lastBatteryOptimizationResult?.type?.name ?: "unknown"
                if (::settingsRepository.isInitialized) {
                    settingsRepository.setBatteryOptimizationSetupCompleted(true)
                }
                
                val successMessage = when (backgroundStatus.detectionMethod) {
                    BackgroundUsageDetector.DetectionMethod.APP_STANDBY_BUCKET -> 
                        "✅ App is in active standby bucket! Background usage allowed."
                    BackgroundUsageDetector.DetectionMethod.MODERN_BACKGROUND_USAGE -> 
                        "✅ Background app refresh enabled! Alarms will work reliably."
                    BackgroundUsageDetector.DetectionMethod.LEGACY_BATTERY_OPTIMIZATION -> 
                        "✅ Battery optimization disabled successfully! Alarms will now work reliably."
                    BackgroundUsageDetector.DetectionMethod.BACKGROUND_RESTRICTION -> 
                        "✅ Background restrictions removed! App can run in background."
                    else -> "✅ Background usage permissions configured! Alarms should work reliably."
                }
                
                Toast.makeText(
                    requireContext(), 
                    successMessage, 
                    Toast.LENGTH_LONG
                ).show()
            } else {
                // Check for quick return (likely failed intent)
                val isQuickReturn = timeSpentInSettings < 5000 // Less than 5 seconds
                Logger.w("SettingsFragment", "Battery optimization still active. Quick return: $isQuickReturn")
                
                if (isQuickReturn && canTryAlternativeMethod()) {
                    // Automatically try fallback for quick returns
                    Logger.i("SettingsFragment", "Quick return detected, trying auto-fallback")
                    handleQuickReturnFallback()
                } else {
                    // Show manual guidance or additional options
                    Logger.i("SettingsFragment", "Showing battery optimization help")
                    showBatteryOptimizationHelp()
                }
            }
            
            // Refresh status again to be sure
            updatePermissionStatus()
        }
    }

    override fun createView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        // SettingsRepository is now injected via Hilt
        return binding.root
    }

    override fun setupView(view: View, savedInstanceState: Bundle?) {
        setupObservers()
        setupClickListeners()
        updatePermissionStatus()
        updateSystemInfo()
    }
    
    override fun onFragmentResumed() {
        // Defensive refresh: ensure UI is up-to-date with actual settings
        viewModel.refreshAllSettings()
        
        // Update permission status when returning to fragment
        updatePermissionStatus()
    }
    
    private fun setupObservers() {
        // Observe refresh interval changes
        viewLifecycleOwner.lifecycleScope.launch {
            Logger.d("SettingsFragment", "Starting refresh interval description collection")
            viewModel.refreshIntervalDescription.collect { description ->
                val currentText = binding.textRefreshIntervalDescription.text.toString()
                Logger.i("SettingsFragment", "Refresh interval UI update: '$currentText' -> '$description'")
                binding.textRefreshIntervalDescription.text = description
                Logger.d("SettingsFragment", "UI text actually set to: '${binding.textRefreshIntervalDescription.text}'")
            }
        }
        
        // Observe all-day time changes
        viewLifecycleOwner.lifecycleScope.launch {
            Logger.d("SettingsFragment", "Starting all-day time description collection")
            viewModel.allDayTimeDescription.collect { description ->
                val currentText = binding.textAllDayTimeDescription.text.toString()
                Logger.i("SettingsFragment", "All-day time UI update: '$currentText' -> '$description'")
                binding.textAllDayTimeDescription.text = description
                Logger.d("SettingsFragment", "UI text actually set to: '${binding.textAllDayTimeDescription.text}'")
            }
        }
        
        
        // Observe work status
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.workStatus.collect { status ->
                updateWorkStatus(status)
            }
        }
        
        // Observe last sync time
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.lastSyncDescription.collect { description ->
                binding.textLastSync.text = description
            }
        }
    }
    
    private fun setupClickListeners() {
        // Permission action buttons
        binding.btnCalendarPermission.setOnClickListener {
            requestCalendarPermission()
        }
        
        binding.btnNotificationPermission.setOnClickListener {
            requestNotificationPermission()
        }
        
        binding.btnExactAlarmPermission.setOnClickListener {
            openExactAlarmSettings()
        }
        
        
        binding.btnBatteryOptimization.setOnClickListener {
            openBatteryOptimizationSettings()
        }
        
        // Settings action buttons
        binding.btnRefreshInterval.setOnClickListener {
            showRefreshIntervalPicker()
        }
        
        binding.btnAllDayTime.setOnClickListener {
            showAllDayTimePicker()
        }
        
        
        // Action buttons
        binding.btnResetSettings.setOnClickListener {
            showResetSettingsConfirmation()
        }
        
        binding.btnTestAlarm.setOnClickListener {
            testAlarm()
        }
    }
    
    private fun updatePermissionStatus() {
        val context = requireContext()
        val status = PermissionUtils.getAllPermissionStatus(context)
        
        Logger.d("SettingsFragment", "Updating permission status: $status")
        
        // Calendar permission
        updatePermissionRow(
            iconView = binding.iconCalendarPermission,
            textView = binding.textCalendarPermission,
            buttonView = binding.btnCalendarPermission,
            hasPermission = status.hasCalendarPermission,
            grantedText = "Calendar events can be read",
            deniedText = "Required to read calendar events"
        )
        
        // Notification permission
        updatePermissionRow(
            iconView = binding.iconNotificationPermission,
            textView = binding.textNotificationPermission,
            buttonView = binding.btnNotificationPermission,
            hasPermission = status.hasNotificationPermission,
            grantedText = "Notifications can be displayed",
            deniedText = "Required for alarm notifications on Android 13+"
        )
        
        // Exact alarm permission
        updatePermissionRow(
            iconView = binding.iconExactAlarmPermission,
            textView = binding.textExactAlarmPermission,
            buttonView = binding.btnExactAlarmPermission,
            hasPermission = status.hasExactAlarmPermission,
            grantedText = "Exact alarms can be scheduled",
            deniedText = "Required for precise alarm timing on Android 12+"
        )
        
        
        // Battery optimization
        val batteryOptimized = status.isBatteryOptimizationWhitelisted
        updatePermissionRow(
            iconView = binding.iconBatteryOptimization,
            textView = binding.textBatteryOptimization,
            buttonView = binding.btnBatteryOptimization,
            hasPermission = batteryOptimized,
            grantedText = "App is whitelisted from battery optimization",
            deniedText = "Recommended for reliable background operation",
            isWarning = true
        )
    }
    
    private fun updatePermissionRow(
        iconView: android.widget.ImageView,
        textView: android.widget.TextView,
        buttonView: android.widget.Button,
        hasPermission: Boolean,
        grantedText: String,
        deniedText: String,
        isWarning: Boolean = false
    ) {
        if (hasPermission) {
            iconView.setImageResource(R.drawable.ic_check_circle)
            iconView.setColorFilter(ContextCompat.getColor(requireContext(), R.color.success_green))
            textView.text = grantedText
            buttonView.visibility = View.GONE
        } else {
            val iconRes = if (isWarning) R.drawable.ic_warning else R.drawable.ic_error
            val colorRes = if (isWarning) R.color.warning_orange else R.color.error_red
            
            iconView.setImageResource(iconRes)
            iconView.setColorFilter(ContextCompat.getColor(requireContext(), colorRes))
            textView.text = deniedText
            buttonView.visibility = View.VISIBLE
        }
    }
    
    private fun updateWorkStatus(status: WorkerManager.WorkStatus) {
        val context = requireContext()
        
        when {
            !status.isScheduled -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_error)
                binding.iconWorkStatus.setColorFilter(ContextCompat.getColor(context, R.color.error_red))
                binding.textWorkStatus.text = "Background refresh is not scheduled"
            }
            status.state == "RUNNING" -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_sync)
                binding.iconWorkStatus.setColorFilter(ContextCompat.getColor(context, R.color.primary))
                binding.textWorkStatus.text = "Background refresh is currently running"
            }
            status.state == "ENQUEUED" -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_check_circle)
                binding.iconWorkStatus.setColorFilter(ContextCompat.getColor(context, R.color.success_green))
                binding.textWorkStatus.text = "Background refresh is scheduled and ready"
            }
            status.errorMessage != null -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_warning)
                binding.iconWorkStatus.setColorFilter(ContextCompat.getColor(context, R.color.warning_orange))
                binding.textWorkStatus.text = "Background refresh had issues: ${status.errorMessage}"
            }
            else -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_check_circle)
                binding.iconWorkStatus.setColorFilter(ContextCompat.getColor(context, R.color.success_green))
                binding.textWorkStatus.text = "Background refresh is running normally"
            }
        }
    }
    
    private fun updateSystemInfo() {
        // Timezone information
        val currentTimeZone = TimezoneUtils.getTimezoneDisplayName(ZoneId.systemDefault())
        val isDST = TimezoneUtils.isCurrentlyDST(ZoneId.systemDefault())
        val dstStatus = if (isDST) " (Currently DST)" else " (Standard Time)"
        binding.textTimezoneInfo.text = "$currentTimeZone$dstStatus"
    }
    
    private fun requestCalendarPermission() {
        Logger.d("SettingsFragment", "Requesting calendar permission")
        singlePermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
    }
    
    private fun requestNotificationPermission() {
        Logger.d("SettingsFragment", "Requesting notification permission")
        PermissionUtils.requestNotificationPermission(singlePermissionLauncher)
    }
    
    private fun openNotificationSettings() {
        val intent = PermissionUtils.getNotificationSettingsIntent(requireContext())
        Logger.d("SettingsFragment", "Opening notification settings")
        systemSettingsLauncher.launch(intent)
    }
    
    private fun openExactAlarmSettings() {
        val intent = PermissionUtils.getExactAlarmSettingsIntent(requireContext())
        if (intent != null) {
            Logger.d("SettingsFragment", "Opening exact alarm settings")
            systemSettingsLauncher.launch(intent)
        } else {
            Toast.makeText(requireContext(), "Exact alarm settings not available on this device", Toast.LENGTH_SHORT).show()
        }
    }
    
    
    private fun openBatteryOptimizationSettings() {
        val context = requireContext()
        Logger.d("SettingsFragment", "Opening battery optimization settings")
        
        val result = PermissionUtils.getBestBatteryOptimizationIntent(context)
        lastBatteryOptimizationResult = result
        
        when (result.type) {
            PermissionUtils.IntentType.DIRECT_WHITELIST -> {
                Logger.d("SettingsFragment", "Using direct whitelist intent")
                showBatteryOptimizationGuidance(result) {
                    batteryOptimizationLaunchTime = System.currentTimeMillis()
                    systemSettingsLauncher.launch(result.intent)
                }
            }
            PermissionUtils.IntentType.SETTINGS_LIST -> {
                Logger.d("SettingsFragment", "Using battery optimization settings list")
                showBatteryOptimizationGuidance(result) {
                    batteryOptimizationLaunchTime = System.currentTimeMillis()
                    systemSettingsLauncher.launch(result.intent)
                }
            }
            PermissionUtils.IntentType.APP_DETAILS -> {
                Logger.d("SettingsFragment", "Using app details fallback")
                showBatteryOptimizationGuidance(result) {
                    batteryOptimizationLaunchTime = System.currentTimeMillis()
                    systemSettingsLauncher.launch(result.intent)
                }
            }
            PermissionUtils.IntentType.MANUAL_GUIDANCE -> {
                Logger.d("SettingsFragment", "Showing manual guidance only")
                showManualBatteryOptimizationInstructions()
            }
        }
    }
    
    /**
     * Check if we can try an alternative battery optimization method
     */
    private fun canTryAlternativeMethod(): Boolean {
        return true // Always allow retry - simplified approach
    }
    
    private fun handleQuickReturnFallback() {
        val context = requireContext()
        Logger.i("SettingsFragment", "Handling quick return fallback")
        
        // Get the next best option based on what we haven't tried yet
        val fallbackResult = when (lastBatteryOptimizationResult?.type) {
            PermissionUtils.IntentType.DIRECT_WHITELIST -> {
                // Try settings list as fallback
                val settingsListIntent = android.content.Intent().apply {
                    action = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                }
                if (context.packageManager.queryIntentActivities(settingsListIntent, 0).isNotEmpty()) {
                    PermissionUtils.BatteryOptimizationResult(
                        intent = settingsListIntent,
                        type = PermissionUtils.IntentType.SETTINGS_LIST,
                        userGuidance = "The direct whitelist didn't work. Find 'Calendar Alarm Scheduler' in this list and tap it, then select 'Don't optimize'."
                    )
                } else {
                    // Fall back to app details
                    PermissionUtils.BatteryOptimizationResult(
                        intent = PermissionUtils.getAppSettingsIntent(context),
                        type = PermissionUtils.IntentType.APP_DETAILS,
                        userGuidance = "The direct whitelist didn't work. Go to Battery → Battery Optimization, find this app, and set it to 'Not optimized'."
                    )
                }
            }
            PermissionUtils.IntentType.SETTINGS_LIST -> {
                // Try direct whitelist as fallback
                val directIntent = android.content.Intent().apply {
                    action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
                if (context.packageManager.queryIntentActivities(directIntent, 0).isNotEmpty()) {
                    PermissionUtils.BatteryOptimizationResult(
                        intent = directIntent,
                        type = PermissionUtils.IntentType.DIRECT_WHITELIST,
                        userGuidance = "The settings list didn't work. A system dialog should appear - tap 'Allow' to whitelist this app.",
                        expectedDialog = true
                    )
                } else {
                    // Fall back to app details
                    PermissionUtils.BatteryOptimizationResult(
                        intent = PermissionUtils.getAppSettingsIntent(context),
                        type = PermissionUtils.IntentType.APP_DETAILS,
                        userGuidance = "Settings list didn't work. Go to app info and find battery settings."
                    )
                }
            }
            else -> {
                // Try a fresh approach - get the best intent again
                PermissionUtils.getBestBatteryOptimizationIntent(context)
            }
        }
        
        Logger.i("SettingsFragment", "Auto-trying fallback: ${fallbackResult.type}")
        
        AlertDialog.Builder(requireContext())
            .setTitle("Auto-Retry with Different Method")
            .setMessage("The previous method didn't work. Let's try a different approach:\n\n${fallbackResult.userGuidance}")
            .setPositiveButton("Try Now") { _, _ ->
                lastBatteryOptimizationResult = fallbackResult
                batteryOptimizationLaunchTime = System.currentTimeMillis()
                systemSettingsLauncher.launch(fallbackResult.intent)
            }
            .setNeutralButton("Manual Steps") { _, _ ->
                showManualBatteryOptimizationInstructions()
            }
            .setNegativeButton("Skip for Now") { _, _ ->
                showBatteryOptimizationSkipDialog()
            }
            .show()
    }
    
    private fun showBatteryOptimizationGuidance(result: PermissionUtils.BatteryOptimizationResult, onProceed: () -> Unit) {
        val title = when (result.type) {
            PermissionUtils.IntentType.DIRECT_WHITELIST -> "Whitelist from Battery Optimization"
            PermissionUtils.IntentType.SETTINGS_LIST -> "Battery Optimization Settings"
            PermissionUtils.IntentType.APP_DETAILS -> "App Battery Settings"
            PermissionUtils.IntentType.MANUAL_GUIDANCE -> "Manual Setup Required"
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(result.userGuidance)
            .setPositiveButton("Open Settings") { _, _ ->
                onProceed()
            }
            .setNeutralButton("Show Manual Steps") { _, _ ->
                showManualBatteryOptimizationInstructions()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showManualBatteryOptimizationInstructions() {
        val context = requireContext()
        val instructions = PermissionUtils.getBatteryOptimizationInstructions(context)
        val instructionText = instructions.joinToString("\n")
        
        AlertDialog.Builder(context)
            .setTitle("How to Whitelist App from Battery Optimization")
            .setMessage(instructionText)
            .setPositiveButton("Open Settings") { _, _ ->
                val appDetailsIntent = PermissionUtils.getAppSettingsIntent(context)
                systemSettingsLauncher.launch(appDetailsIntent)
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun showBatteryOptimizationHelp() {
        val context = requireContext()
        val deviceInfo = DozeCompatibilityUtils.getDeviceInfo()
        val backgroundStatus = PermissionUtils.getBackgroundUsageStatus(context)
        val recommendations = DozeCompatibilityUtils.getBatteryManagementRecommendations(context)
        
        val detectionInfo = when (backgroundStatus.detectionMethod) {
            BackgroundUsageDetector.DetectionMethod.APP_STANDBY_BUCKET -> {
                val bucketName = backgroundStatus.details["bucketName"] as? String ?: "Unknown"
                "App is in '$bucketName' standby bucket. For reliable background usage, the app needs to be in 'ACTIVE' or 'WORKING_SET' bucket."
            }
            BackgroundUsageDetector.DetectionMethod.MODERN_BACKGROUND_USAGE -> {
                "This device uses modern 'Allow background usage' controls. Look for background app refresh settings instead of traditional battery optimization."
            }
            BackgroundUsageDetector.DetectionMethod.BACKGROUND_RESTRICTION -> {
                "Background restrictions are active. The app needs unrestricted background access for reliable alarms."
            }
            BackgroundUsageDetector.DetectionMethod.OEM_SPECIFIC -> {
                val oem = backgroundStatus.details["oem"] as? String ?: "Unknown"
                "${oem.uppercase()} device with custom power management detected. Check manufacturer-specific battery settings."
            }
            else -> "Traditional battery optimization detected."
        }
        
        val message = buildString {
            append("Background usage is currently restricted. Here's what we detected:\n\n")
            append("ℹ️ $detectionInfo\n\n")
            append("Device-specific recommendations:\n\n")
            recommendations.forEach { recommendation ->
                append("• $recommendation\n")
            }
            append("\nDevice: ${deviceInfo.manufacturer} ${deviceInfo.model}")
            if (deviceInfo.customUIVersion != null) {
                append("\nUI: ${deviceInfo.customUIVersion}")
            }
            append("\nDetection method: ${backgroundStatus.detectionMethod}")
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Battery Optimization Help")
            .setMessage(message)
            .setPositiveButton("Try Again") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNeutralButton("Manual Instructions") { _, _ ->
                showManualBatteryOptimizationInstructions()
            }
            .setNegativeButton("Skip for Now") { _, _ ->
                showBatteryOptimizationSkipDialog()
            }
            .show()
    }
    
    private fun showBatteryOptimizationSkipDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Skip Battery Optimization?")
            .setMessage("WARNING: Without battery optimization disabled, alarms may not work reliably when your phone is in battery saver mode or doze mode.\n\nYou can always enable this later in Settings.")
            .setPositiveButton("I Understand") { _, _ ->
                // User acknowledged the risk
                Logger.i("SettingsFragment", "User chose to skip battery optimization setup")
            }
            .setNeutralButton("Remind Me Later") { _, _ ->
                // Don't mark as skipped
                Logger.i("SettingsFragment", "User chose to be reminded about battery optimization later")
            }
            .setNegativeButton("Try Again") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .show()
    }
    
    private fun showBatteryOptimizationStatusCheck() {
        AlertDialog.Builder(requireContext())
            .setTitle("Battery Optimization Still Active")
            .setMessage("The app is still being battery optimized. This may cause alarms to be delayed or missed.\n\nWould you like to:")
            .setPositiveButton("Try Again") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNeutralButton("Show Manual Steps") { _, _ ->
                showManualBatteryOptimizationInstructions()
            }
            .setNegativeButton("Skip for Now", null)
            .show()
    }
    
    private fun showRefreshIntervalPicker() {
        val intervals = WorkerManager.AVAILABLE_INTERVALS
        val intervalNames = intervals.map { 
            WorkerManager(requireContext()).getIntervalDescription(it)
        }.toTypedArray()
        
        val currentInterval = viewModel.getCurrentRefreshInterval()
        val selectedIndex = intervals.indexOf(currentInterval)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Background Refresh Interval")
            .setSingleChoiceItems(intervalNames, selectedIndex) { dialog, which ->
                val selectedInterval = intervals[which]
                Logger.i("SettingsFragment", "User selected refresh interval: $selectedInterval minutes")
                Logger.d("SettingsFragment", "Current UI text before change: '${binding.textRefreshIntervalDescription.text}'")
                viewModel.setRefreshInterval(selectedInterval)
                Logger.d("SettingsFragment", "ViewModel.setRefreshInterval() called")
                
                // Defensive: ensure UI updates immediately even if StateFlow has issues
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(100) // Small delay to allow StateFlow to propagate
                    val expectedText = "Every $selectedInterval minute${if (selectedInterval != 1) "s" else ""}"
                    if (binding.textRefreshIntervalDescription.text.toString() != expectedText) {
                        Logger.w("SettingsFragment", "UI not updated after 100ms, forcing defensive refresh")
                        viewModel.refreshSettingsDisplays()
                    }
                }
                
                dialog.dismiss()
                Toast.makeText(requireContext(), "Refresh interval updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showAllDayTimePicker() {
        val currentHour = viewModel.getCurrentAllDayHour()
        val currentMinute = viewModel.getCurrentAllDayMinute()
        val is24HourFormat = DateFormat.is24HourFormat(requireContext())
        
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                Logger.i("SettingsFragment", "User selected all-day time: $hour:$minute")
                Logger.d("SettingsFragment", "Current UI text before change: '${binding.textAllDayTimeDescription.text}'")
                val oldText = binding.textAllDayTimeDescription.text.toString()
                viewModel.setAllDayDefaultTime(hour, minute)
                Logger.d("SettingsFragment", "ViewModel.setAllDayDefaultTime() called")
                
                // Defensive: ensure UI updates immediately even if StateFlow has issues
                viewLifecycleOwner.lifecycleScope.launch {
                    kotlinx.coroutines.delay(100) // Small delay to allow StateFlow to propagate  
                    val currentText = binding.textAllDayTimeDescription.text.toString()
                    if (currentText == oldText) {
                        Logger.w("SettingsFragment", "All-day time UI not updated after 100ms, forcing defensive refresh")
                        viewModel.refreshSettingsDisplays()
                    }
                }
                
                Toast.makeText(requireContext(), "All-day event time updated", Toast.LENGTH_SHORT).show()
            },
            currentHour,
            currentMinute,
            is24HourFormat
        ).show()
    }
    
    
    private fun showResetSettingsConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Reset Settings")
            .setMessage("This will reset all settings to their default values. Background refresh will be rescheduled with default interval. Continue?")
            .setPositiveButton("Reset") { _, _ ->
                Logger.i("SettingsFragment", "Resetting settings to defaults")
                viewModel.resetSettings()
                updatePermissionStatus()
                Toast.makeText(requireContext(), "Settings reset to defaults", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun testAlarm() {
        Logger.i("SettingsFragment", "Testing alarm")
        
        // Check if we have necessary permissions first
        val status = PermissionUtils.getAllPermissionStatus(requireContext())
        if (!status.areAllGranted()) {
            Toast.makeText(requireContext(), "Please grant all required permissions before testing alarms", Toast.LENGTH_LONG).show()
            return
        }
        
        viewModel.scheduleTestAlarm { success ->
            val message = if (success) {
                "Test alarm scheduled for 10 seconds from now"
            } else {
                "Failed to schedule test alarm. Check permissions and try again."
            }
            
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun cleanupView() {
        // Clear binding reference to prevent memory leaks
        _binding = null
        
        // Clear background usage cache when view is destroyed to force fresh detection
        BackgroundUsageDetector.clearCache()
    }
    
}