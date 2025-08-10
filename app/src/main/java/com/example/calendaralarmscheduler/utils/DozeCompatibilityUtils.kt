package com.example.calendaralarmscheduler.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.calendaralarmscheduler.workers.WorkerManager

object DozeCompatibilityUtils {
    
    /**
     * Check if device is currently in Doze mode (API 23+)
     */
    fun isDeviceInDozeMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isDeviceIdleMode
        } else {
            false // Doze mode doesn't exist on pre-M devices
        }
    }
    
    /**
     * Check if device supports Doze mode
     */
    fun doesDeviceSupportDozeMode(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
    
    /**
     * Check if app is whitelisted from Doze mode restrictions
     */
    fun isWhitelistedFromDoze(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // No Doze restrictions on pre-M devices
        }
    }
    
    /**
     * Get Doze mode compatibility status for the app
     */
    fun getDozeCompatibilityStatus(context: Context): DozeCompatibilityStatus {
        val supportsDoze = doesDeviceSupportDozeMode()
        val isInDozeMode = if (supportsDoze) isDeviceInDozeMode(context) else false
        val isWhitelisted = if (supportsDoze) isWhitelistedFromDoze(context) else true
        val workerManager = WorkerManager(context)
        val isOptimizationIgnored = workerManager.isBatteryOptimizationIgnored()
        val backgroundUsageStatus = BackgroundUsageDetector.getDetailedBackgroundUsageStatus(context)
        val batteryManagementType = detectBatteryManagementType()
        val deviceInfo = getDeviceInfo()
        
        return DozeCompatibilityStatus(
            deviceSupportsDoze = supportsDoze,
            isCurrentlyInDozeMode = isInDozeMode,
            isAppWhitelisted = isWhitelisted,
            isBatteryOptimizationIgnored = isOptimizationIgnored,
            apiLevel = Build.VERSION.SDK_INT,
            batteryManagementType = batteryManagementType,
            deviceInfo = deviceInfo,
            backgroundUsageStatus = backgroundUsageStatus
        )
    }
    
    /**
     * Test if work can execute during Doze mode
     */
    fun testDozeCompatibility(context: Context): DozeTestResult {
        val status = getDozeCompatibilityStatus(context)
        
        val issues = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        if (!status.deviceSupportsDoze) {
            // Pre-Android 6.0 - no Doze restrictions
            return DozeTestResult(
                isCompatible = true,
                severity = DozeTestResult.Severity.NONE,
                message = "Device does not support Doze mode (Android ${status.apiLevel}). No restrictions on background work.",
                issues = emptyList(),
                recommendations = emptyList()
            )
        }
        
        // Android 6.0+ - check for Doze restrictions
        if (!status.isAppWhitelisted) {
            issues.add("App is not whitelisted from battery optimization")
            recommendations.add("Request user to whitelist app from battery optimization")
        }
        
        if (status.isCurrentlyInDozeMode && !status.isAppWhitelisted) {
            issues.add("Device is currently in Doze mode and app is not whitelisted")
            recommendations.add("Background work may be severely restricted")
        }
        
        // Determine compatibility level
        val isCompatible = status.isAppWhitelisted || !status.isCurrentlyInDozeMode
        val severity = when {
            status.isAppWhitelisted -> DozeTestResult.Severity.NONE
            status.isCurrentlyInDozeMode -> DozeTestResult.Severity.HIGH
            else -> DozeTestResult.Severity.MEDIUM
        }
        
        val message = when (severity) {
            DozeTestResult.Severity.NONE -> "App is optimally configured for Doze mode compatibility"
            DozeTestResult.Severity.MEDIUM -> "App may experience delays in background work during Doze mode"
            DozeTestResult.Severity.HIGH -> "App background work is severely restricted in current Doze state"
        }
        
        return DozeTestResult(
            isCompatible = isCompatible,
            severity = severity,
            message = message,
            issues = issues,
            recommendations = recommendations
        )
    }
    
    /**
     * Get intent to whitelist app from battery optimization
     */
    fun getWhitelistIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }
    }
    
    /**
     * Get intent to open battery optimization settings
     */
    fun getBatteryOptimizationSettingsIntent(): Intent {
        return Intent().apply {
            action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        }
    }
    
    /**
     * Log current Doze status for debugging
     */
    fun logDozeStatus(context: Context) {
        val status = getDozeCompatibilityStatus(context)
        val testResult = testDozeCompatibility(context)
        
        Logger.i("DozeCompatibility", "=== Doze Mode Compatibility Status ===")
        Logger.i("DozeCompatibility", "Device API Level: ${status.apiLevel}")
        Logger.i("DozeCompatibility", "Supports Doze Mode: ${status.deviceSupportsDoze}")
        Logger.i("DozeCompatibility", "Currently in Doze Mode: ${status.isCurrentlyInDozeMode}")
        Logger.i("DozeCompatibility", "App Whitelisted: ${status.isAppWhitelisted}")
        Logger.i("DozeCompatibility", "Battery Optimization Ignored: ${status.isBatteryOptimizationIgnored}")
        Logger.i("DozeCompatibility", "Background Usage Method: ${status.backgroundUsageStatus.detectionMethod}")
        Logger.i("DozeCompatibility", "Background Usage Allowed: ${status.backgroundUsageStatus.isBackgroundUsageAllowed}")
        Logger.i("DozeCompatibility", "Compatibility Test Result: ${testResult.message}")
        Logger.i("DozeCompatibility", "Severity: ${testResult.severity}")
        
        if (testResult.issues.isNotEmpty()) {
            Logger.w("DozeCompatibility", "Issues found:")
            testResult.issues.forEach { issue ->
                Logger.w("DozeCompatibility", "  - $issue")
            }
        }
        
        if (testResult.recommendations.isNotEmpty()) {
            Logger.i("DozeCompatibility", "Recommendations:")
            testResult.recommendations.forEach { recommendation ->
                Logger.i("DozeCompatibility", "  - $recommendation")
            }
        }
        
        Logger.i("DozeCompatibility", "=====================================")
    }
    
    /**
     * Monitor Doze mode changes (requires broadcast receiver registration)
     */
    fun createDozeMonitoringIntent(): Intent {
        return Intent().apply {
            action = PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED
        }
    }
    
    data class DozeCompatibilityStatus(
        val deviceSupportsDoze: Boolean,
        val isCurrentlyInDozeMode: Boolean,
        val isAppWhitelisted: Boolean,
        val isBatteryOptimizationIgnored: Boolean,
        val apiLevel: Int,
        val batteryManagementType: BatteryManagementType,
        val deviceInfo: DeviceInfo,
        val backgroundUsageStatus: BackgroundUsageDetector.BackgroundUsageStatus
    )
    
    data class DozeTestResult(
        val isCompatible: Boolean,
        val severity: Severity,
        val message: String,
        val issues: List<String>,
        val recommendations: List<String>
    ) {
        enum class Severity {
            NONE,    // No issues - optimal configuration
            MEDIUM,  // May experience delays but will work
            HIGH     // Severely restricted - critical issues
        }
    }
    
    /**
     * Types of battery management systems found on different devices
     */
    enum class BatteryManagementType {
        LEGACY_OPTIMIZATION,     // Traditional battery optimization (Android 6.0-8.0)
        MODERN_BACKGROUND_USAGE, // "Allow background usage" slider (Android 9+)
        OEM_CUSTOM,             // OEM-specific battery management (Samsung, Xiaomi, etc.)
        GRANULAR_PERMISSIONS,   // Fine-grained background permissions (Android 11+)
        ADAPTIVE_BATTERY,       // AI-based adaptive battery (Android 9+ Pixel)
        UNKNOWN                 // Could not determine type
    }
    
    /**
     * Device information for battery management detection
     */
    data class DeviceInfo(
        val manufacturer: String,
        val model: String,
        val brand: String,
        val sdkVersion: Int,
        val hasCustomUI: Boolean,
        val customUIVersion: String?
    )
    
    /**
     * Detect the type of battery management system on this device
     */
    fun detectBatteryManagementType(): BatteryManagementType {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val sdkVersion = Build.VERSION.SDK_INT
        
        return when {
            // Android 11+ with granular background permissions
            sdkVersion >= Build.VERSION_CODES.R -> {
                when {
                    manufacturer.contains("google") -> BatteryManagementType.ADAPTIVE_BATTERY
                    isOEMWithCustomBatteryManagement(manufacturer) -> BatteryManagementType.OEM_CUSTOM
                    else -> BatteryManagementType.GRANULAR_PERMISSIONS
                }
            }
            // Android 9-10 with background usage controls
            sdkVersion >= Build.VERSION_CODES.P -> {
                when {
                    manufacturer.contains("google") -> BatteryManagementType.ADAPTIVE_BATTERY
                    isOEMWithCustomBatteryManagement(manufacturer) -> BatteryManagementType.OEM_CUSTOM
                    else -> BatteryManagementType.MODERN_BACKGROUND_USAGE
                }
            }
            // Android 6.0-8.1 with traditional battery optimization
            sdkVersion >= Build.VERSION_CODES.M -> {
                if (isOEMWithCustomBatteryManagement(manufacturer)) {
                    BatteryManagementType.OEM_CUSTOM
                } else {
                    BatteryManagementType.LEGACY_OPTIMIZATION
                }
            }
            // Pre-Android 6.0 - no battery restrictions
            else -> BatteryManagementType.UNKNOWN
        }
    }
    
    /**
     * Check if the manufacturer has custom battery management
     */
    private fun isOEMWithCustomBatteryManagement(manufacturer: String): Boolean {
        return manufacturer.contains("samsung") ||
               manufacturer.contains("xiaomi") ||
               manufacturer.contains("huawei") ||
               manufacturer.contains("honor") ||
               manufacturer.contains("oneplus") ||
               manufacturer.contains("oppo") ||
               manufacturer.contains("vivo") ||
               manufacturer.contains("realme") ||
               manufacturer.contains("asus") ||
               manufacturer.contains("sony")
    }
    
    /**
     * Get detailed device information for battery management
     */
    fun getDeviceInfo(): DeviceInfo {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val brand = Build.BRAND
        val sdkVersion = Build.VERSION.SDK_INT
        
        val hasCustomUI = isOEMWithCustomBatteryManagement(manufacturer.lowercase())
        val customUIVersion = detectCustomUIVersion(manufacturer.lowercase())
        
        return DeviceInfo(
            manufacturer = manufacturer,
            model = model,
            brand = brand,
            sdkVersion = sdkVersion,
            hasCustomUI = hasCustomUI,
            customUIVersion = customUIVersion
        )
    }
    
    /**
     * Detect custom UI version for known OEMs
     */
    private fun detectCustomUIVersion(manufacturer: String): String? {
        return when {
            manufacturer.contains("samsung") -> {
                try {
                    // Try to detect One UI version through system properties
                    System.getProperty("ro.build.version.oneui") ?: "One UI (Unknown)"
                } catch (e: Exception) {
                    "One UI (Unknown)"
                }
            }
            manufacturer.contains("xiaomi") -> {
                try {
                    // Try to detect MIUI version
                    System.getProperty("ro.miui.ui.version.name") ?: "MIUI (Unknown)"
                } catch (e: Exception) {
                    "MIUI (Unknown)"
                }
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                try {
                    // Try to detect EMUI version
                    System.getProperty("ro.build.version.emui") ?: "EMUI/Magic UI (Unknown)"
                } catch (e: Exception) {
                    "EMUI/Magic UI (Unknown)"
                }
            }
            manufacturer.contains("oneplus") -> {
                try {
                    // Try to detect OxygenOS version
                    System.getProperty("ro.oxygen.version") ?: "OxygenOS (Unknown)"
                } catch (e: Exception) {
                    "OxygenOS (Unknown)"
                }
            }
            else -> null
        }
    }
    
    /**
     * Check if device supports modern background app management
     */
    fun supportsModernBackgroundManagement(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }
    
    /**
     * Check if device likely uses "Allow background usage" instead of battery optimization
     */
    fun usesBackgroundUsageSlider(): Boolean {
        val batteryType = detectBatteryManagementType()
        return batteryType == BatteryManagementType.MODERN_BACKGROUND_USAGE ||
               batteryType == BatteryManagementType.GRANULAR_PERMISSIONS ||
               batteryType == BatteryManagementType.ADAPTIVE_BATTERY
    }
    
    /**
     * Get battery management recommendations based on device type
     */
    fun getBatteryManagementRecommendations(context: Context): List<String> {
        val batteryType = detectBatteryManagementType()
        val deviceInfo = getDeviceInfo()
        val recommendations = mutableListOf<String>()
        
        when (batteryType) {
            BatteryManagementType.MODERN_BACKGROUND_USAGE -> {
                recommendations.addAll(listOf(
                    "This device uses 'Allow background usage' controls",
                    "Look for a slider or toggle instead of 'Don't optimize'",
                    "Set background usage to 'Allowed' or 'Unrestricted'"
                ))
            }
            BatteryManagementType.GRANULAR_PERMISSIONS -> {
                recommendations.addAll(listOf(
                    "This device has fine-grained background permissions",
                    "Enable 'Background app refresh' for reliable alarms",
                    "Allow 'Display over other apps' for unmissable alarms"
                ))
            }
            BatteryManagementType.ADAPTIVE_BATTERY -> {
                recommendations.addAll(listOf(
                    "This device uses Adaptive Battery (AI-powered)",
                    "Allow the app to 'Use battery in background'",
                    "The system will learn this app needs background access"
                ))
            }
            BatteryManagementType.OEM_CUSTOM -> {
                when (deviceInfo.manufacturer.lowercase()) {
                    "samsung" -> recommendations.addAll(getSamsungBatteryRecommendations())
                    "xiaomi" -> recommendations.addAll(getXiaomiBatteryRecommendations())
                    "huawei", "honor" -> recommendations.addAll(getHuaweiBatteryRecommendations())
                    "oneplus" -> recommendations.addAll(getOnePlusBatteryRecommendations())
                    else -> recommendations.addAll(getGenericOEMRecommendations())
                }
            }
            BatteryManagementType.LEGACY_OPTIMIZATION -> {
                recommendations.addAll(listOf(
                    "This device uses traditional battery optimization",
                    "Select 'Don't optimize' or 'Not optimized'",
                    "This ensures reliable alarm delivery"
                ))
            }
            BatteryManagementType.UNKNOWN -> {
                recommendations.addAll(listOf(
                    "Could not detect battery management type",
                    "Look for battery optimization, background usage, or app permissions",
                    "Allow unrestricted background activity for this app"
                ))
            }
        }
        
        return recommendations
    }
    
    private fun getSamsungBatteryRecommendations(): List<String> {
        return listOf(
            "Samsung Device Care manages battery usage",
            "Go to Device Care → Battery → Background app limits",
            "Remove this app from 'Sleeping apps' or 'Deep sleeping apps'",
            "Add to 'Never sleeping apps' for best reliability"
        )
    }
    
    private fun getXiaomiBatteryRecommendations(): List<String> {
        return listOf(
            "MIUI has aggressive battery management",
            "Settings → Apps → Manage apps → Calendar Alarm Scheduler",
            "Battery saver → No restrictions",
            "Also enable Autostart and allow background activity"
        )
    }
    
    private fun getHuaweiBatteryRecommendations(): List<String> {
        return listOf(
            "EMUI/Magic UI has strict power management",
            "Battery → Launch → Manage manually (enable all 3 toggles)",
            "App launch: Auto-launch, Secondary launch, Run in background",
            "Also whitelist in Protected apps if available"
        )
    }
    
    private fun getOnePlusBatteryRecommendations(): List<String> {
        return listOf(
            "OxygenOS battery optimization can block alarms",
            "Settings → Battery → Battery optimization",
            "Don't optimize → All apps → Calendar Alarm Scheduler → Don't optimize",
            "Also check Advanced optimization settings"
        )
    }
    
    private fun getGenericOEMRecommendations(): List<String> {
        return listOf(
            "This device has custom battery management",
            "Look for app-specific battery settings",
            "Disable battery optimization or enable background activity",
            "Check for autostart or background app permissions"
        )
    }
}