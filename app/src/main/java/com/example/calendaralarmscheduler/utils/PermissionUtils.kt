package com.example.calendaralarmscheduler.utils

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    /**
     * Check if we have calendar read permission
     */
    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if we have exact alarm scheduling permission (Android 12+)
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            // Pre-Android 12, no special permission needed
            true
        }
    }

    /**
     * Check if the app has background usage permissions (replaces legacy battery optimization check)
     */
    fun isBatteryOptimizationWhitelisted(context: Context): Boolean {
        // Use comprehensive background usage detection
        val backgroundStatus = BackgroundUsageDetector.getDetailedBackgroundUsageStatus(context)
        
        Logger.d("PermissionUtils", "Background usage permission check:")
        Logger.d("PermissionUtils", "  Result: ${backgroundStatus.isBackgroundUsageAllowed}")
        Logger.d("PermissionUtils", "  Method: ${backgroundStatus.detectionMethod}")
        
        return backgroundStatus.isBackgroundUsageAllowed
    }
    
    /**
     * Get detailed background usage status for UI display
     */
    fun getBackgroundUsageStatus(context: Context): BackgroundUsageDetector.BackgroundUsageStatus {
        return BackgroundUsageDetector.getDetailedBackgroundUsageStatus(context)
    }
    
    /**
     * Legacy method - kept for compatibility but uses modern detection
     */
    @Deprecated("Use isBatteryOptimizationWhitelisted instead", ReplaceWith("isBatteryOptimizationWhitelisted(context)"))
    fun isLegacyBatteryOptimizationWhitelisted(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // No battery optimization on pre-M devices
        }
    }

    /**
     * Check all critical permissions at once
     */
    fun getAllPermissionStatus(context: Context): PermissionStatus {
        return PermissionStatus(
            hasCalendarPermission = hasCalendarPermission(context),
            hasExactAlarmPermission = hasExactAlarmPermission(context),
            isBatteryOptimizationWhitelisted = isBatteryOptimizationWhitelisted(context)
        )
    }

    /**
     * Request calendar permission using the provided ActivityResultLauncher
     */
    fun requestCalendarPermission(
        launcher: ActivityResultLauncher<String>
    ) {
        launcher.launch(Manifest.permission.READ_CALENDAR)
    }

    /**
     * Request multiple permissions using the provided ActivityResultLauncher
     */
    fun requestMultiplePermissions(
        launcher: ActivityResultLauncher<Array<String>>,
        permissions: Array<String>
    ) {
        launcher.launch(permissions)
    }

    /**
     * Get an intent to open exact alarm settings (Android 12+)
     */
    fun getExactAlarmSettingsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }

    /**
     * Result of battery optimization intent analysis
     */
    data class BatteryOptimizationResult(
        val intent: Intent,
        val type: IntentType,
        val userGuidance: String,
        val expectedDialog: Boolean = false,
        val modernInterface: Boolean = false,
        val oemSpecific: Boolean = false
    )
    
    enum class IntentType {
        DIRECT_WHITELIST,    // System dialog to whitelist app
        SETTINGS_LIST,       // Battery optimization settings list
        APP_DETAILS,         // App details page
        MANUAL_GUIDANCE      // Show manual instructions
    }

    /**
     * Get the best battery optimization intent based on device capabilities
     */
    fun getBestBatteryOptimizationIntent(context: Context): BatteryOptimizationResult {
        // Get enhanced device information
        val deviceInfo = DozeCompatibilityUtils.getDeviceInfo()
        val batteryManagementType = DozeCompatibilityUtils.detectBatteryManagementType()
        
        // Add comprehensive device logging
        Logger.i("BatteryOptimization", "=== Enhanced Device Info ===")
        Logger.i("BatteryOptimization", "Manufacturer: ${deviceInfo.manufacturer}")
        Logger.i("BatteryOptimization", "Model: ${deviceInfo.model}")  
        Logger.i("BatteryOptimization", "Brand: ${deviceInfo.brand}")
        Logger.i("BatteryOptimization", "SDK Version: ${deviceInfo.sdkVersion}")
        Logger.i("BatteryOptimization", "Has Custom UI: ${deviceInfo.hasCustomUI}")
        Logger.i("BatteryOptimization", "Custom UI Version: ${deviceInfo.customUIVersion}")
        Logger.i("BatteryOptimization", "Battery Management Type: $batteryManagementType")
        Logger.i("BatteryOptimization", "Package: ${context.packageName}")
        Logger.i("BatteryOptimization", "==============================")
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Logger.i("BatteryOptimization", "Android version < M, using manual guidance")
            return BatteryOptimizationResult(
                intent = getAppSettingsIntent(context),
                type = IntentType.MANUAL_GUIDANCE,
                userGuidance = "Battery optimization is not available on this Android version"
            )
        }
        
        // Use battery management type to determine best approach
        return when (batteryManagementType) {
            DozeCompatibilityUtils.BatteryManagementType.MODERN_BACKGROUND_USAGE -> 
                getModernBackgroundUsageIntent(context, deviceInfo)
            DozeCompatibilityUtils.BatteryManagementType.GRANULAR_PERMISSIONS -> 
                getGranularPermissionsIntent(context, deviceInfo)
            DozeCompatibilityUtils.BatteryManagementType.ADAPTIVE_BATTERY -> 
                getAdaptiveBatteryIntent(context, deviceInfo)
            DozeCompatibilityUtils.BatteryManagementType.OEM_CUSTOM -> 
                getOEMCustomIntent(context, deviceInfo)
            DozeCompatibilityUtils.BatteryManagementType.LEGACY_OPTIMIZATION -> 
                getLegacyOptimizationIntent(context)
            DozeCompatibilityUtils.BatteryManagementType.UNKNOWN -> 
                getUnknownDeviceIntent(context)
        }
        
    }
    
    /**
     * Get intent for modern background usage controls (Android 9+)
     */
    private fun getModernBackgroundUsageIntent(context: Context, deviceInfo: DozeCompatibilityUtils.DeviceInfo): BatteryOptimizationResult {
        Logger.i("BatteryOptimization", "Using modern background usage approach")
        
        // Try app-specific battery settings first
        val appBatteryIntent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.parse("package:${context.packageName}")
        }
        
        if (canResolveIntent(context, appBatteryIntent)) {
            return BatteryOptimizationResult(
                intent = appBatteryIntent,
                type = IntentType.APP_DETAILS,
                userGuidance = "Look for 'Battery' or 'Background app refresh'. Set to 'Allowed' or 'Unrestricted' instead of 'Don't optimize'.",
                modernInterface = true
            )
        }
        
        // Fallback to legacy approach
        return getLegacyOptimizationIntent(context)
    }
    
    /**
     * Get intent for granular permissions (Android 11+)
     */
    private fun getGranularPermissionsIntent(context: Context, deviceInfo: DozeCompatibilityUtils.DeviceInfo): BatteryOptimizationResult {
        Logger.i("BatteryOptimization", "Using granular permissions approach")
        
        val appDetailsIntent = getAppSettingsIntent(context)
        return BatteryOptimizationResult(
            intent = appDetailsIntent,
            type = IntentType.APP_DETAILS,
            userGuidance = "Enable 'Allow background activity', 'Remove from battery optimization', and 'Display over other apps' for reliable alarms.",
            modernInterface = true
        )
    }
    
    /**
     * Get intent for Adaptive Battery (Pixel devices)
     */
    private fun getAdaptiveBatteryIntent(context: Context, deviceInfo: DozeCompatibilityUtils.DeviceInfo): BatteryOptimizationResult {
        Logger.i("BatteryOptimization", "Using Adaptive Battery approach")
        
        val appDetailsIntent = getAppSettingsIntent(context)
        return BatteryOptimizationResult(
            intent = appDetailsIntent,
            type = IntentType.APP_DETAILS,
            userGuidance = "Allow 'Background app refresh' and 'Battery usage'. The system will learn this app needs background access.",
            modernInterface = true
        )
    }
    
    /**
     * Get intent for OEM custom battery management
     */
    private fun getOEMCustomIntent(context: Context, deviceInfo: DozeCompatibilityUtils.DeviceInfo): BatteryOptimizationResult {
        Logger.i("BatteryOptimization", "Using OEM custom approach for ${deviceInfo.manufacturer}")
        
        val manufacturer = deviceInfo.manufacturer.lowercase()
        
        // Try OEM-specific intents first, then fallback
        val oemIntent = when {
            manufacturer.contains("samsung") -> getSamsungBatteryIntent(context)
            manufacturer.contains("xiaomi") -> getXiaomiBatteryIntent(context)
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> getHuaweiBatteryIntent(context)
            manufacturer.contains("oneplus") -> getOnePlusBatteryIntent(context)
            manufacturer.contains("oppo") -> getOppoBatteryIntent(context)
            manufacturer.contains("vivo") -> getVivoBatteryIntent(context)
            manufacturer.contains("realme") -> getOppoBatteryIntent(context) // Realme uses ColorOS
            else -> null
        }
        
        if (oemIntent != null) {
            return oemIntent
        }
        
        // Fallback to app details with OEM-specific guidance
        val recommendations = DozeCompatibilityUtils.getBatteryManagementRecommendations(context)
        val guidance = recommendations.joinToString("\n")
        
        return BatteryOptimizationResult(
            intent = getAppSettingsIntent(context),
            type = IntentType.APP_DETAILS,
            userGuidance = guidance,
            oemSpecific = true
        )
    }
    
    /**
     * Get intent for legacy battery optimization
     */
    private fun getLegacyOptimizationIntent(context: Context): BatteryOptimizationResult {
        Logger.i("BatteryOptimization", "Using legacy optimization approach")
        
        // Try direct whitelist intent first
        val directWhitelistIntent = Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:${context.packageName}")
        }
        
        Logger.d("BatteryOptimization", "Testing direct whitelist intent:")
        Logger.d("BatteryOptimization", "  Action: ${directWhitelistIntent.action}")
        Logger.d("BatteryOptimization", "  Data: ${directWhitelistIntent.data}")
        
        val canResolveDirectWhitelist = canResolveIntent(context, directWhitelistIntent)
        Logger.d("BatteryOptimization", "  Can resolve: $canResolveDirectWhitelist")
        
        if (canResolveDirectWhitelist) {
            val isReliable = isDirectWhitelistReliable(context)
            Logger.d("BatteryOptimization", "  Is reliable on this device: $isReliable")
            
            if (isReliable) {
                Logger.i("BatteryOptimization", "✅ Using direct whitelist intent")
                return BatteryOptimizationResult(
                    intent = directWhitelistIntent,
                    type = IntentType.DIRECT_WHITELIST,
                    userGuidance = "A system dialog will appear. Please tap 'Allow' to whitelist this app from battery optimization.",
                    expectedDialog = true
                )
            } else {
                Logger.w("BatteryOptimization", "❌ Direct whitelist not reliable on this device, trying fallbacks")
            }
        } else {
            Logger.w("BatteryOptimization", "❌ Direct whitelist intent cannot be resolved")
        }
        
        // Try battery optimization settings list
        val settingsListIntent = Intent().apply {
            action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        }
        
        Logger.d("BatteryOptimization", "Testing settings list intent:")
        Logger.d("BatteryOptimization", "  Action: ${settingsListIntent.action}")
        
        val canResolveSettingsList = canResolveIntent(context, settingsListIntent)
        Logger.d("BatteryOptimization", "  Can resolve: $canResolveSettingsList")
        
        if (canResolveSettingsList) {
            Logger.i("BatteryOptimization", "✅ Using settings list intent")
            return BatteryOptimizationResult(
                intent = settingsListIntent,
                type = IntentType.SETTINGS_LIST,
                userGuidance = "Find 'Calendar Alarm Scheduler' in the list and tap it, then select 'Don't optimize' or 'Allow'."
            )
        } else {
            Logger.w("BatteryOptimization", "❌ Settings list intent cannot be resolved")
        }
        
        // Fallback to app details
        val appDetailsIntent = getAppSettingsIntent(context)
        Logger.d("BatteryOptimization", "Using app details fallback:")
        Logger.d("BatteryOptimization", "  Action: ${appDetailsIntent.action}")
        Logger.d("BatteryOptimization", "  Data: ${appDetailsIntent.data}")
        Logger.i("BatteryOptimization", "⚠️ Using app details fallback")
        
        return BatteryOptimizationResult(
            intent = appDetailsIntent,
            type = IntentType.APP_DETAILS,
            userGuidance = "Go to Battery → Battery Optimization (or similar), find this app, and set it to 'Not optimized' or 'Don't optimize'."
        )
    }
    
    /**
     * Get intent for unknown devices
     */
    private fun getUnknownDeviceIntent(context: Context): BatteryOptimizationResult {
        Logger.w("BatteryOptimization", "Unknown device type, using comprehensive fallback")
        
        return BatteryOptimizationResult(
            intent = getAppSettingsIntent(context),
            type = IntentType.APP_DETAILS,
            userGuidance = "Look for Battery settings, Background usage, or App permissions. Allow unrestricted background activity for reliable alarms."
        )
    }
    
    /**
     * Check if direct whitelist intent is reliable on this device
     */
    private fun isDirectWhitelistReliable(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val sdkVersion = Build.VERSION.SDK_INT
        
        // Some OEMs are known to have issues with direct whitelist intent
        return when {
            // Xiaomi MIUI sometimes doesn't show the dialog properly
            manufacturer.contains("xiaomi") -> sdkVersion >= Build.VERSION_CODES.P
            // Samsung generally works well
            manufacturer.contains("samsung") -> true
            // OnePlus works well on newer versions
            manufacturer.contains("oneplus") -> sdkVersion >= Build.VERSION_CODES.O
            // Huawei/Honor has restrictions
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> false
            // Default to true for other manufacturers
            else -> true
        }
    }

    /**
     * Get an intent to request battery optimization whitelist (legacy)
     */
    @Deprecated("Use getBestBatteryOptimizationIntent instead")
    fun getBatteryOptimizationWhitelistIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
            }.takeIf { canResolveIntent(context, it) }
        } else {
            null
        }
    }
    
    /**
     * Get an intent to open battery optimization settings list (legacy)
     */
    @Deprecated("Use getBestBatteryOptimizationIntent instead")
    fun getBatteryOptimizationSettingsIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent().apply {
                action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            }
        } else {
            null
        }
    }

    /**
     * Get an intent to open battery optimization settings (legacy with fallbacks)
     */
    @Deprecated("Use getBestBatteryOptimizationIntent instead")
    fun getBatteryOptimizationIntent(context: Context): Intent {
        return getBestBatteryOptimizationIntent(context).intent
    }
    
    /**
     * Check if an intent can be resolved by the system
     */
    private fun canResolveIntent(context: Context, intent: Intent): Boolean {
        return try {
            val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
            Logger.d("BatteryOptimization", "    Query result: ${resolveInfos.size} activities found")
            
            if (resolveInfos.isNotEmpty()) {
                Logger.d("BatteryOptimization", "    Primary activity: ${resolveInfos[0].activityInfo.name}")
                Logger.d("BatteryOptimization", "    Package: ${resolveInfos[0].activityInfo.packageName}")
            }
            
            resolveInfos.isNotEmpty()
        } catch (e: Exception) {
            Logger.e("BatteryOptimization", "Error resolving intent: ${intent.action}", e)
            false
        }
    }
    
    /**
     * Get detailed instructions for manual battery optimization whitelist
     */
    fun getBatteryOptimizationInstructions(context: Context): List<String> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val instructions = mutableListOf<String>()
        
        when {
            manufacturer.contains("samsung") -> {
                instructions.addAll(listOf(
                    "1. Tap Open Settings below",
                    "2. Look for 'Battery usage' or 'Battery'",
                    "3. Tap 'More battery settings'",
                    "4. Tap 'Optimize battery usage'",
                    "5. Tap 'Apps not optimized' → 'All apps'",
                    "6. Find 'Calendar Alarm Scheduler'",
                    "7. Toggle it OFF (so it shows 'Not optimizing')"
                ))
            }
            manufacturer.contains("xiaomi") -> {
                instructions.addAll(listOf(
                    "1. Tap Open Settings below",
                    "2. Look for 'Battery & performance' or 'Battery'",
                    "3. Tap 'Battery optimization'",
                    "4. Find 'Calendar Alarm Scheduler'",
                    "5. Tap it and select 'Don't optimize'",
                    "6. Also check Settings → Apps → Manage apps → Calendar Alarm Scheduler → Battery saver → No restrictions"
                ))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                instructions.addAll(listOf(
                    "1. Tap Open Settings below",
                    "2. Go to Battery → Battery optimization",
                    "3. Find 'Calendar Alarm Scheduler'",
                    "4. Tap it and select 'Don't allow'",
                    "5. Also check Settings → Apps → Calendar Alarm Scheduler → Battery → Launch → Manage manually (enable all 3 toggles)"
                ))
            }
            manufacturer.contains("oneplus") -> {
                instructions.addAll(listOf(
                    "1. Tap Open Settings below",
                    "2. Go to Battery → Battery optimization",
                    "3. Tap 'Not optimized' → 'All apps'",
                    "4. Find 'Calendar Alarm Scheduler'",
                    "5. Tap it and select 'Don't optimize'"
                ))
            }
            else -> {
                instructions.addAll(listOf(
                    "1. Tap Open Settings below",
                    "2. Look for 'Battery' or 'Battery optimization'",
                    "3. Find the battery optimization or app battery settings",
                    "4. Look for 'Calendar Alarm Scheduler'",
                    "5. Set it to 'Not optimized', 'Don't optimize', or 'No restrictions'"
                ))
            }
        }
        
        instructions.add("")
        instructions.add("This ensures alarms work reliably even when your phone is in battery saving mode.")
        
        return instructions
    }

    /**
     * Get Samsung-specific battery intent
     */
    private fun getSamsungBatteryIntent(context: Context): BatteryOptimizationResult? {
        val intentsToTry = listOf(
            // Samsung Device Care (One UI 2.0+)
            Intent().apply {
                action = "com.samsung.android.lool.accu.launchActivity"
                putExtra("packageName", context.packageName)
            },
            // Samsung Smart Manager (older devices)
            Intent().apply {
                action = "com.samsung.android.sm.ACTION_BATTERY"
                putExtra("package_name", context.packageName)
            },
            // Samsung App Power Management
            Intent().apply {
                setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            },
            // Device Care Battery
            Intent().apply {
                setClassName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.usage.CheckableAppListActivity"
                )
            }
        )
        
        for (intent in intentsToTry) {
            if (canResolveIntent(context, intent)) {
                return BatteryOptimizationResult(
                    intent = intent,
                    type = IntentType.APP_DETAILS,
                    userGuidance = "Samsung Device: Go to Battery → Background app limits or App power management. Remove from 'Sleeping apps' and add to 'Never sleeping apps'.",
                    oemSpecific = true
                )
            }
        }
        
        return null
    }
    
    /**
     * Get Xiaomi-specific battery intent
     */
    private fun getXiaomiBatteryIntent(context: Context): BatteryOptimizationResult? {
        val intentsToTry = listOf(
            // MIUI Security app - App permissions
            Intent().apply {
                action = "miui.intent.action.APP_PERM_EDITOR"
                putExtra("extra_pkgname", context.packageName)
            },
            // MIUI Power settings
            Intent().apply {
                action = "miui.intent.action.POWER_HIDE_MODE_APP_LIST"
                putExtra("package_name", context.packageName)
            },
            // MIUI Autostart management
            Intent().apply {
                action = "miui.intent.action.OP_AUTO_START"
                putExtra("packageName", context.packageName)
            },
            // MIUI Battery and Performance
            Intent().apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            },
            // MIUI Power settings (newer versions)
            Intent().apply {
                setClassName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", getAppName(context))
            }
        )
        
        for (intent in intentsToTry) {
            if (canResolveIntent(context, intent)) {
                return BatteryOptimizationResult(
                    intent = intent,
                    type = IntentType.APP_DETAILS,
                    userGuidance = "MIUI Device: Enable 'Autostart', 'Background activity', and set Battery saver to 'No restrictions'. Also check Power settings.",
                    oemSpecific = true
                )
            }
        }
        
        return null
    }
    
    /**
     * Get Huawei-specific battery intent
     */
    private fun getHuaweiBatteryIntent(context: Context): BatteryOptimizationResult? {
        val intentsToTry = listOf(
            // Huawei Protected Apps
            Intent().apply {
                action = "huawei.intent.action.HSM_PROTECTED_APPS"
            },
            // Huawei Power Manager
            Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            },
            // Huawei Power Genie (older devices)
            Intent().apply {
                setClassName(
                    "com.huawei.powergenie",
                    "com.huawei.powergenie.ui.HwPowerGenieMainActivity"
                )
            },
            // Huawei Battery optimization
            Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"
                )
            },
            // EMUI Phone Manager
            Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.MainActivity"
                )
            }
        )
        
        for (intent in intentsToTry) {
            if (canResolveIntent(context, intent)) {
                return BatteryOptimizationResult(
                    intent = intent,
                    type = IntentType.SETTINGS_LIST,
                    userGuidance = "Huawei/Honor Device: Enable in Protected apps, Power Manager, or Launch settings. Enable Auto-launch, Secondary launch, and Run in background.",
                    oemSpecific = true
                )
            }
        }
        
        return null
    }
    
    /**
     * Get OnePlus-specific battery intent
     */
    private fun getOnePlusBatteryIntent(context: Context): BatteryOptimizationResult? {
        val intentsToTry = listOf(
            // OnePlus Battery optimization (OxygenOS 11+)
            Intent().apply {
                setClassName(
                    "com.oneplus.battery",
                    "com.oneplus.battery.PowerConsumptionActivity"
                )
            },
            // OnePlus Advanced optimization
            Intent().apply {
                setClassName(
                    "com.android.settings",
                    "com.android.settings.Settings\$HighPowerApplicationsActivity"
                )
            },
            // OnePlus Game Space battery
            Intent().apply {
                setClassName(
                    "com.oneplus.gamespace",
                    "com.oneplus.gamespace.GameSpaceSettingsActivity"
                )
            }
        )
        
        for (intent in intentsToTry) {
            if (canResolveIntent(context, intent)) {
                return BatteryOptimizationResult(
                    intent = intent,
                    type = IntentType.APP_DETAILS,
                    userGuidance = "OnePlus Device: Disable battery optimization and advanced optimization. Also check background restrictions.",
                    oemSpecific = true
                )
            }
        }
        
        return null // Fall back to standard approach
    }
    
    /**
     * Get app name for display in OEM settings
     */
    private fun getAppName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
            context.packageManager.getApplicationLabel(packageInfo).toString()
        } catch (e: Exception) {
            "Calendar Alarm Scheduler"
        }
    }
    
    /**
     * Get Oppo-specific battery intent
     */
    private fun getOppoBatteryIntent(context: Context): BatteryOptimizationResult? {
        val intentsToTry = listOf(
            // ColorOS Power Manager
            Intent().apply {
                setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity"
                )
            },
            // Oppo Battery optimization
            Intent().apply {
                setClassName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"
                )
            },
            // ColorOS Phone Manager
            Intent().apply {
                setClassName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.main.MainActivity"
                )
            }
        )
        
        for (intent in intentsToTry) {
            if (canResolveIntent(context, intent)) {
                return BatteryOptimizationResult(
                    intent = intent,
                    type = IntentType.SETTINGS_LIST,
                    userGuidance = "Oppo Device: Enable auto-launch and disable battery optimization. Check Power Manager settings.",
                    oemSpecific = true
                )
            }
        }
        
        return null
    }
    
    /**
     * Get Vivo-specific battery intent
     */
    private fun getVivoBatteryIntent(context: Context): BatteryOptimizationResult? {
        val intentsToTry = listOf(
            // Vivo Phone Manager
            Intent().apply {
                setClassName(
                    "com.iqoo.powersaving",
                    "com.iqoo.powersaving.PowerSavingManagerActivity"
                )
            },
            // Vivo Battery optimization
            Intent().apply {
                setClassName(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                )
            }
        )
        
        for (intent in intentsToTry) {
            if (canResolveIntent(context, intent)) {
                return BatteryOptimizationResult(
                    intent = intent,
                    type = IntentType.SETTINGS_LIST,
                    userGuidance = "Vivo Device: Enable background running and disable power saving restrictions.",
                    oemSpecific = true
                )
            }
        }
        
        return null
    }
    
    /**
     * Get an intent to open general app settings
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /**
     * Check if we should show permission rationale
     */
    fun shouldShowCalendarPermissionRationale(activity: androidx.fragment.app.FragmentActivity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.READ_CALENDAR
        )
    }

    /**
     * Get user-friendly permission status messages
     */
    fun getPermissionStatusMessage(context: Context): List<PermissionMessage> {
        val messages = mutableListOf<PermissionMessage>()
        val status = getAllPermissionStatus(context)

        if (!status.hasCalendarPermission) {
            messages.add(
                PermissionMessage(
                    title = "Calendar Access Required",
                    message = "The app needs access to read your calendar events to schedule alarms. This permission is essential for the app to function.",
                    actionText = "Grant Permission",
                    isError = true
                )
            )
        }

        if (!status.hasExactAlarmPermission) {
            messages.add(
                PermissionMessage(
                    title = "Exact Alarm Permission Required",
                    message = "Android 12+ requires special permission to schedule exact alarms. This ensures your alarms fire precisely on time.",
                    actionText = "Open Settings",
                    isError = true
                )
            )
        }

        if (!status.isBatteryOptimizationWhitelisted) {
            messages.add(
                PermissionMessage(
                    title = "Battery Optimization",
                    message = "For reliable background operation, please whitelist this app from battery optimization. This prevents Android from limiting alarm scheduling.",
                    actionText = "Whitelist App",
                    isError = false // Warning, not critical error
                )
            )
        }

        if (messages.isEmpty()) {
            messages.add(
                PermissionMessage(
                    title = "All Permissions Granted",
                    message = "The app has all necessary permissions to schedule reliable alarms.",
                    actionText = "",
                    isError = false
                )
            )
        }

        return messages
    }

    /**
     * Check if all critical permissions are granted
     */
    fun hasAllCriticalPermissions(context: Context): Boolean {
        val status = getAllPermissionStatus(context)
        return status.hasCalendarPermission && status.hasExactAlarmPermission
    }

    /**
     * Get the list of permissions that need to be requested
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missingPermissions = mutableListOf<String>()
        
        if (!hasCalendarPermission(context)) {
            missingPermissions.add(Manifest.permission.READ_CALENDAR)
        }
        
        // Note: SCHEDULE_EXACT_ALARM can't be requested via ActivityResultLauncher,
        // it requires opening system settings
        
        return missingPermissions
    }

    data class PermissionStatus(
        val hasCalendarPermission: Boolean,
        val hasExactAlarmPermission: Boolean,
        val isBatteryOptimizationWhitelisted: Boolean
    ) {
        fun areAllGranted(): Boolean {
            return hasCalendarPermission && hasExactAlarmPermission
        }
        
        fun areAllOptimal(): Boolean {
            return hasCalendarPermission && hasExactAlarmPermission && isBatteryOptimizationWhitelisted
        }
    }

    data class PermissionMessage(
        val title: String,
        val message: String,
        val actionText: String,
        val isError: Boolean
    )
}