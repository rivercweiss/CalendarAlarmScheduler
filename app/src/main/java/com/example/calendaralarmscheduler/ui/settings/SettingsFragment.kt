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
import com.google.android.material.color.MaterialColors
import com.example.calendaralarmscheduler.ui.BaseFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.calendaralarmscheduler.BuildConfig
import com.example.calendaralarmscheduler.R
import com.example.calendaralarmscheduler.data.SettingsRepository
import com.example.calendaralarmscheduler.databinding.FragmentSettingsBinding
import com.example.calendaralarmscheduler.utils.BillingManager
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.PermissionUtils
import com.example.calendaralarmscheduler.utils.TimezoneUtils
import com.example.calendaralarmscheduler.workers.BackgroundRefreshManager
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
    
    @Inject
    lateinit var billingManager: BillingManager
    
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
        
        
        // Update permission status immediately
        updatePermissionStatus()
        
        // Check if battery optimization status changed and provide feedback
        lifecycleScope.launch {
            // Add a small delay to allow system to update permission status
            kotlinx.coroutines.delay(500)
            
            val status = PermissionUtils.getAllPermissionStatus(requireContext())
            
            Logger.d("SettingsFragment", "Battery optimization status after return: ${status.isBatteryOptimizationWhitelisted}")
            
            // Provide feedback if battery optimization was successfully disabled
            if (status.isBatteryOptimizationWhitelisted) {
                Logger.i("SettingsFragment", "✅ Battery optimization disabled successfully!")
                
                // Record successful completion
                if (::settingsRepository.isInitialized) {
                    settingsRepository.setBatteryOptimizationSetupCompleted(true)
                }
                
                val successMessage = "✅ Battery optimization disabled! Alarms will now work reliably."
                
                Toast.makeText(
                    requireContext(), 
                    successMessage, 
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Logger.w("SettingsFragment", "Battery optimization still active")
                showBatteryOptimizationHelp()
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
        setupBillingCallbacks()
        setupObservers()
        setupClickListeners()
        updatePermissionStatus()
        updateSystemInfo()
    }
    
    private fun setupBillingCallbacks() {
        billingManager.setCallbacks(
            onStateChanged = { isPremium ->
                // This will be handled by the StateFlow observer
                Logger.d("SettingsFragment", "Premium state changed: $isPremium")
            },
            onError = { errorMessage ->
                showPurchaseError(errorMessage)
            }
        )
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
        
        // Observe premium status changes
        viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.premiumPurchased.collect { isPremium ->
                updatePremiumUI(isPremium)
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
        
        // Premium features
        binding.btnPremiumPurchase.setOnClickListener {
            launchPremiumPurchase()
        }
        
        // Debug toggle for testing (only in debug builds)
        if (BuildConfig.SHOW_DEBUG_FEATURES) {
            binding.btnDebugTogglePremium.setOnClickListener {
                togglePremiumForTesting()
            }
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
        if (status.isBatteryOptimizationAvailable) {
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
        } else {
            // Battery optimization not available on this device
            updatePermissionRow(
                iconView = binding.iconBatteryOptimization,
                textView = binding.textBatteryOptimization,
                buttonView = binding.btnBatteryOptimization,
                hasPermission = true, // Show as "granted" since feature doesn't exist
                grantedText = "Not applicable on this device",
                deniedText = "", // Won't be used
                isWarning = false
            )
        }
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
            iconView.setColorFilter(MaterialColors.getColor(iconView, com.google.android.material.R.attr.colorSecondary))
            textView.text = grantedText
            buttonView.visibility = View.GONE
        } else {
            val iconRes = if (isWarning) R.drawable.ic_warning else R.drawable.ic_error
            val colorAttr = if (isWarning) com.google.android.material.R.attr.colorSecondaryContainer else com.google.android.material.R.attr.colorError
            
            iconView.setImageResource(iconRes)
            iconView.setColorFilter(MaterialColors.getColor(iconView, colorAttr))
            textView.text = deniedText
            buttonView.visibility = View.VISIBLE
        }
    }
    
    private fun updateWorkStatus(status: BackgroundRefreshManager.WorkStatus) {
        val context = requireContext()
        
        when {
            !status.isScheduled -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_error)
                binding.iconWorkStatus.setColorFilter(MaterialColors.getColor(binding.iconWorkStatus, com.google.android.material.R.attr.colorError))
                binding.textWorkStatus.text = "Background refresh is not scheduled"
            }
            status.state == "RUNNING" -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_sync)
                binding.iconWorkStatus.setColorFilter(MaterialColors.getColor(binding.iconWorkStatus, com.google.android.material.R.attr.colorPrimary))
                binding.textWorkStatus.text = "Background refresh is currently running"
            }
            status.state == "ENQUEUED" -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_check_circle)
                binding.iconWorkStatus.setColorFilter(MaterialColors.getColor(binding.iconWorkStatus, com.google.android.material.R.attr.colorSecondary))
                binding.textWorkStatus.text = "Background refresh is scheduled and ready"
            }
            status.errorMessage != null -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_warning)
                binding.iconWorkStatus.setColorFilter(MaterialColors.getColor(binding.iconWorkStatus, com.google.android.material.R.attr.colorSecondaryContainer))
                binding.textWorkStatus.text = "Background refresh had issues: ${status.errorMessage}"
            }
            else -> {
                binding.iconWorkStatus.setImageResource(R.drawable.ic_check_circle)
                binding.iconWorkStatus.setColorFilter(MaterialColors.getColor(binding.iconWorkStatus, com.google.android.material.R.attr.colorSecondary))
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
        
        if (!result.isAvailable) {
            Logger.i("SettingsFragment", "Battery optimization not available on this device")
            Toast.makeText(
                requireContext(),
                "Battery optimization is not available on this device. Alarms should work reliably.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        Logger.d("SettingsFragment", "Opening battery optimization settings")
        showBatteryOptimizationGuidance(result) {
            batteryOptimizationLaunchTime = System.currentTimeMillis()
            systemSettingsLauncher.launch(result.intent)
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
        
        // Get a fresh battery optimization intent (simplified approach)
        val fallbackResult = PermissionUtils.getBestBatteryOptimizationIntent(context)
        
        Logger.i("SettingsFragment", "Auto-trying fallback approach")
        
        AlertDialog.Builder(requireContext())
            .setTitle("Auto-Retry with Different Method")
            .setMessage("The previous method didn't work. Let's try a different approach:\n\n${fallbackResult.guidance}")
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
        AlertDialog.Builder(requireContext())
            .setTitle("Battery Optimization Settings")
            .setMessage(result.guidance)
            .setPositiveButton("Open Settings") { _, _ ->
                onProceed()
            }
            .setNeutralButton("Show Help") { _, _ ->
                showBatteryOptimizationHelp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManualBatteryOptimizationInstructions() {
        val instructions = """
            Manual Battery Optimization Steps:
            
            1. Open your device's Settings app
            2. Navigate to Battery or Power Management  
            3. Find "Battery Optimization" or "App Battery Usage"
            4. Look for "Calendar Alarm Scheduler" in the list
            5. Tap it and select "Don't optimize" or "No restrictions"
            
            This ensures alarms work reliably even when your phone is in battery saving mode.
        """.trimIndent()
        
        AlertDialog.Builder(requireContext())
            .setTitle("Manual Instructions")
            .setMessage(instructions)
            .setPositiveButton("Got it", null)
            .setNeutralButton("Open Settings") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .show()
    }
    
    
    private fun showBatteryOptimizationHelp() {
        val context = requireContext()
        
        val message = buildString {
            append("For reliable alarm delivery, this app needs to be whitelisted from battery optimization.\n\n")
            append("Steps to whitelist:\n\n")
            append("1. Tap 'Open Settings' below\n")
            append("2. Look for 'Battery' or 'Battery Optimization' settings\n")
            append("3. Find 'Calendar Alarm Scheduler' in the list\n")
            append("4. Set it to 'Don't optimize' or 'Not optimized'\n\n")
            append("This ensures your alarms will work reliably even when your phone is in battery saving mode.")
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
        val intervals = BackgroundRefreshManager.AVAILABLE_INTERVALS
        val intervalNames = intervals.map { 
            BackgroundRefreshManager(requireContext()).getIntervalDescription(it)
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
    
    /**
     * Updates premium section UI based on purchase state.
     * 
     * Premium users get exciting "PREMIUM ACTIVE" styling with Material 3 themed colors.
     * Free users get clear upgrade CTA with $2 pricing.
     * All colors use MaterialColors.getColor() for automatic theme adaptation.
     * Debug toggle shown only in debug builds for testing.
     */
    private fun updatePremiumUI(isPremium: Boolean) {
        if (isPremium) {
            // Premium Active State - Material 3 themed celebration styling
            binding.iconPremiumStatus.setImageResource(R.drawable.ic_check_circle)
            binding.iconPremiumStatus.setColorFilter(MaterialColors.getColor(binding.iconPremiumStatus, com.google.android.material.R.attr.colorSecondary))
            binding.textPremiumTitle.text = "✨ PREMIUM ACTIVE"
            binding.textPremiumTitle.setTextColor(MaterialColors.getColor(binding.textPremiumTitle, com.google.android.material.R.attr.colorSecondary))
            binding.textPremiumStatus.text = "Event details are now shown in all alarm notifications!"
            binding.btnPremiumPurchase.visibility = View.GONE
            
            // Apply Material 3 themed premium styling to the card
            val primaryColor = MaterialColors.getColor(binding.cardPremium, com.google.android.material.R.attr.colorSecondary)
            binding.cardPremium.apply {
                strokeColor = primaryColor
                strokeWidth = 6
            }
        } else {
            // Free User State - Clear Upgrade CTA
            binding.iconPremiumStatus.setImageResource(R.drawable.ic_star)
            binding.iconPremiumStatus.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary))
            binding.textPremiumTitle.text = "Event Details in Notifications"
            // Use default text color - no need to set explicitly
            binding.textPremiumStatus.text = "See actual event titles in alarm notifications instead of generic 'Calendar Event' - \$2.00"
            binding.btnPremiumPurchase.visibility = View.VISIBLE
            
            // Reset card styling
            val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
            binding.cardPremium.apply {
                strokeColor = primaryColor
                strokeWidth = 2
            }
        }
        
        // Show/hide debug toggle based on build configuration
        binding.btnDebugTogglePremium.visibility = if (BuildConfig.SHOW_DEBUG_FEATURES) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        Logger.d("SettingsFragment", "Premium UI updated: isPremium=$isPremium")
    }
    
    /**
     * Debug-only function to toggle premium state for testing.
     * Only available in debug builds - completely hidden in release builds.
     * Allows testing premium UI states without Google Play Console setup.
     */
    private fun togglePremiumForTesting() {
        val currentState = settingsRepository.isPremiumPurchased()
        val newState = !currentState
        
        Logger.i("SettingsFragment", "Debug: Toggling premium from $currentState to $newState")
        settingsRepository.setPremiumPurchased(newState)
        
        Toast.makeText(
            requireContext(), 
            "Debug: Premium ${if (newState) "enabled" else "disabled"}", 
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun launchPremiumPurchase() {
        try {
            Logger.i("SettingsFragment", "Launching premium purchase flow")
            billingManager.launchPurchaseFlow(requireActivity())
        } catch (e: Exception) {
            Logger.e("SettingsFragment", "Error launching purchase flow", e)
            Toast.makeText(requireContext(), "Unable to launch purchase. Please try again.", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showPurchaseError(message: String) {
        Toast.makeText(requireContext(), "Purchase Error: $message", Toast.LENGTH_LONG).show()
        Logger.e("SettingsFragment", "Purchase error shown to user: $message")
    }

    override fun cleanupView() {
        // Disconnect billing client to prevent memory leaks
        if (::billingManager.isInitialized) {
            billingManager.disconnect()
        }
        
        // Clear binding reference to prevent memory leaks
        _binding = null
    }
    
}