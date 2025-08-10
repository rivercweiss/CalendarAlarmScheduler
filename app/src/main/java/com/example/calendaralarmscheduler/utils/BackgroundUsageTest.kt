package com.example.calendaralarmscheduler.utils

import android.content.Context

/**
 * Test utility to demonstrate and validate background usage detection
 */
object BackgroundUsageTest {
    
    /**
     * Run comprehensive background usage detection test
     */
    fun runDetectionTest(context: Context): String {
        val result = BackgroundUsageDetector.getDetailedBackgroundUsageStatus(context)
        
        return buildString {
            appendLine("=== Background Usage Detection Test ===")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("Android API: ${android.os.Build.VERSION.SDK_INT}")
            appendLine("Package: ${context.packageName}")
            appendLine()
            appendLine("Detection Result:")
            appendLine("  Background Usage Allowed: ${result.isBackgroundUsageAllowed}")
            appendLine("  Detection Method: ${result.detectionMethod}")
            appendLine("  API Level: ${result.apiLevel}")
            appendLine()
            appendLine("Details:")
            result.details.forEach { (key, value) ->
                appendLine("  $key: $value")
            }
            appendLine()
            appendLine("Interpretation:")
            when (result.detectionMethod) {
                BackgroundUsageDetector.DetectionMethod.LEGACY_BATTERY_OPTIMIZATION -> {
                    appendLine("  Using traditional PowerManager.isIgnoringBatteryOptimizations()")
                    appendLine("  This works on Android 6+ but may not detect modern controls")
                }
                BackgroundUsageDetector.DetectionMethod.APP_STANDBY_BUCKET -> {
                    val bucket = result.details["bucketName"] as? String ?: "Unknown"
                    appendLine("  Using modern App Standby Buckets (Android 9+)")
                    appendLine("  Current bucket: $bucket")
                    appendLine("  ACTIVE/WORKING_SET = allowed, FREQUENT/RARE/RESTRICTED = limited")
                }
                BackgroundUsageDetector.DetectionMethod.BACKGROUND_RESTRICTION -> {
                    appendLine("  Using ActivityManager.isBackgroundRestricted (Android 9+)")
                    appendLine("  Direct check for background restrictions")
                }
                BackgroundUsageDetector.DetectionMethod.APP_OPS_BACKGROUND_CHECK -> {
                    appendLine("  Using AppOps background permissions check")
                    appendLine("  Checks system-level app operation permissions")
                }
                BackgroundUsageDetector.DetectionMethod.OEM_SPECIFIC -> {
                    val oem = result.details["oem"] as? String ?: "Unknown"
                    appendLine("  Using OEM-specific detection for $oem")
                    appendLine("  Conservative approach for custom power management")
                }
                else -> {
                    appendLine("  Using fallback detection method")
                }
            }
            appendLine()
            appendLine("Recommendation:")
            if (result.isBackgroundUsageAllowed) {
                appendLine("  ✅ Background usage is properly configured")
                appendLine("  The app should be able to run background tasks reliably")
            } else {
                appendLine("  ⚠️ Background usage may be restricted")
                appendLine("  Users should configure background permissions for reliable alarms")
                when (result.detectionMethod) {
                    BackgroundUsageDetector.DetectionMethod.APP_STANDBY_BUCKET -> {
                        appendLine("  → Use the app more frequently to improve standby bucket")
                        appendLine("  → Check 'Allow background usage' in app settings")
                    }
                    BackgroundUsageDetector.DetectionMethod.MODERN_BACKGROUND_USAGE -> {
                        appendLine("  → Enable 'Allow background usage' or 'Background app refresh'")
                    }
                    BackgroundUsageDetector.DetectionMethod.OEM_SPECIFIC -> {
                        val oem = result.details["oem"] as? String ?: "Unknown OEM"
                        when (oem) {
                            "samsung" -> appendLine("  → Check Samsung Device Care battery settings")
                            "xiaomi" -> appendLine("  → Configure MIUI autostart and battery settings")
                            "huawei" -> appendLine("  → Enable in EMUI protected apps")
                            else -> appendLine("  → Check $oem power management settings")
                        }
                    }
                    else -> {
                        appendLine("  → Disable battery optimization in system settings")
                    }
                }
            }
            appendLine("==========================================")
        }
    }
    
    /**
     * Compare legacy vs modern detection methods
     */
    fun compareDetectionMethods(context: Context): String {
        // Get modern detection
        val modernResult = BackgroundUsageDetector.getDetailedBackgroundUsageStatus(context)
        
        // Get legacy detection
        val legacyResult = try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
        
        return buildString {
            appendLine("=== Detection Method Comparison ===")
            appendLine("Legacy Method (PowerManager):")
            appendLine("  Result: $legacyResult")
            appendLine("  Method: PowerManager.isIgnoringBatteryOptimizations()")
            appendLine()
            appendLine("Modern Method (Comprehensive):")
            appendLine("  Result: ${modernResult.isBackgroundUsageAllowed}")
            appendLine("  Method: ${modernResult.detectionMethod}")
            appendLine("  Details: ${modernResult.details}")
            appendLine()
            appendLine("Analysis:")
            when {
                legacyResult == modernResult.isBackgroundUsageAllowed -> {
                    appendLine("  ✅ Both methods agree: $legacyResult")
                    appendLine("  Detection is consistent across methods")
                }
                legacyResult && !modernResult.isBackgroundUsageAllowed -> {
                    appendLine("  ⚠️ Legacy says allowed ($legacyResult) but modern detects restrictions")
                    appendLine("  Modern method detected: ${modernResult.detectionMethod}")
                    appendLine("  This shows why comprehensive detection is important!")
                }
                !legacyResult && modernResult.isBackgroundUsageAllowed -> {
                    appendLine("  ⚠️ Legacy says restricted but modern detects allowance")
                    appendLine("  Modern method: ${modernResult.detectionMethod}")
                    appendLine("  Device likely uses modern background controls")
                }
                else -> {
                    appendLine("  Both methods detect restrictions")
                    appendLine("  User needs to configure background permissions")
                }
            }
            appendLine("=====================================")
        }
    }
}