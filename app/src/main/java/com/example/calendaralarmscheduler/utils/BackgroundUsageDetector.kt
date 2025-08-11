package com.example.calendaralarmscheduler.utils

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Method

/**
 * Comprehensive detector for background app usage permissions across Android versions.
 * Handles traditional battery optimization, modern background usage controls, and OEM customizations.
 */
object BackgroundUsageDetector {

    // Session-based cache for expensive background usage detection results
    private var cachedResult: BackgroundUsageStatus? = null
    private var isSessionCacheValid = false
    
    /**
     * Check if cached result is still valid for this app session
     */
    private fun isCacheValid(): Boolean {
        return cachedResult != null && isSessionCacheValid
    }
    
    /**
     * Invalidate session cache (call when app goes to background or on fresh start)
     */
    fun invalidateSessionCache() {
        isSessionCacheValid = false
        Logger.d("BackgroundUsageDetector", "Session cache invalidated - will refresh on next call")
    }

    /**
     * Result of background usage detection
     */
    data class BackgroundUsageStatus(
        val isBackgroundUsageAllowed: Boolean,
        val detectionMethod: DetectionMethod,
        val apiLevel: Int,
        val details: Map<String, Any>
    )

    enum class DetectionMethod {
        LEGACY_BATTERY_OPTIMIZATION,    // Android 6-8: PowerManager.isIgnoringBatteryOptimizations
        MODERN_BACKGROUND_USAGE,        // Android 9+: Background app refresh settings
        APP_STANDBY_BUCKET,            // Android 9+: App standby buckets (ACTIVE, WORKING_SET, etc.)
        APP_OPS_BACKGROUND_CHECK,      // Android 6+: AppOps background check
        BACKGROUND_RESTRICTION,         // Android 7+: Background restrictions
        OEM_SPECIFIC,                   // OEM-specific detection
        FALLBACK                       // When other methods fail
    }

    /**
     * Comprehensive check for background usage permissions
     */
    fun isBackgroundUsageAllowed(context: Context): BackgroundUsageStatus {
        val apiLevel = Build.VERSION.SDK_INT
        Logger.d("BackgroundUsageDetector", "Checking background usage for API level $apiLevel")
        
        // Try different detection methods based on Android version
        val detectionResults = mutableListOf<BackgroundUsageStatus>()
        
        // Method 1: Legacy battery optimization (Android 6+)
        if (apiLevel >= Build.VERSION_CODES.M) {
            detectionResults.add(checkLegacyBatteryOptimization(context))
        }
        
        // Method 2: App standby buckets (Android 9+)
        if (apiLevel >= Build.VERSION_CODES.P) {
            // Suppress NewApi warning - we already check API level and method is @RequiresApi annotated
            @Suppress("NewApi")
            detectionResults.add(checkAppStandbyBucket(context))
        }
        
        // Method 3: Background restrictions (Android 7+)
        if (apiLevel >= Build.VERSION_CODES.N) {
            detectionResults.add(checkBackgroundRestrictions(context))
        }
        
        // Method 4: AppOps background check (Android 6+)
        if (apiLevel >= Build.VERSION_CODES.M) {
            detectionResults.add(checkAppOpsBackground(context))
        }
        
        // Method 5: OEM-specific checks
        detectionResults.add(checkOEMSpecific(context))
        
        // Analyze results and return the most appropriate one
        return analyzeDetectionResults(detectionResults, context)
    }
    
    /**
     * Check legacy battery optimization (Android 6+)
     */
    private fun checkLegacyBatteryOptimization(context: Context): BackgroundUsageStatus {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
        
        Logger.d("BackgroundUsageDetector", "Legacy battery optimization - ignored: $isIgnored")
        
        return BackgroundUsageStatus(
            isBackgroundUsageAllowed = isIgnored,
            detectionMethod = DetectionMethod.LEGACY_BATTERY_OPTIMIZATION,
            apiLevel = Build.VERSION.SDK_INT,
            details = mapOf<String, Any>(
                "isIgnoringBatteryOptimizations" to isIgnored,
                "method" to "PowerManager.isIgnoringBatteryOptimizations"
            )
        )
    }
    
    /**
     * Check app standby bucket (Android 9+)
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun checkAppStandbyBucket(context: Context): BackgroundUsageStatus {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val bucket = usageStatsManager.getAppStandbyBucket()
            
            // ACTIVE (5) and WORKING_SET (10) buckets allow background usage
            // FREQUENT (20), RARE (30), RESTRICTED (40) have limitations
            val isAllowed = when (bucket) {
                5 -> true   // STANDBY_BUCKET_ACTIVE - app is currently in use
                10 -> true  // STANDBY_BUCKET_WORKING_SET - app is used regularly
                20 -> false // STANDBY_BUCKET_FREQUENT - app is used frequently but not currently
                30 -> false // STANDBY_BUCKET_RARE - app is used rarely
                40 -> false // STANDBY_BUCKET_RESTRICTED - app is restricted due to abuse
                45 -> false // STANDBY_BUCKET_NEVER - app has never been used
                else -> false // Unknown bucket, assume restricted
            }
            
            Logger.d("BackgroundUsageDetector", "App standby bucket: $bucket, allowed: $isAllowed")
            
            BackgroundUsageStatus(
                isBackgroundUsageAllowed = isAllowed,
                detectionMethod = DetectionMethod.APP_STANDBY_BUCKET,
                apiLevel = Build.VERSION.SDK_INT,
                details = mapOf<String, Any>(
                    "standbyBucket" to bucket,
                    "bucketName" to getBucketName(bucket),
                    "method" to "UsageStatsManager.getAppStandbyBucket"
                )
            )
        } catch (e: Exception) {
            Logger.e("BackgroundUsageDetector", "Failed to check app standby bucket", e)
            BackgroundUsageStatus(
                isBackgroundUsageAllowed = false,
                detectionMethod = DetectionMethod.FALLBACK,
                apiLevel = Build.VERSION.SDK_INT,
                details = mapOf<String, Any>("error" to (e.message ?: "Unknown error"))
            )
        }
    }
    
    /**
     * Check background restrictions (Android 7+)
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private fun checkBackgroundRestrictions(context: Context): BackgroundUsageStatus {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val isBackgroundRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activityManager.isBackgroundRestricted
            } else {
                false // Not available on Android 7, assume not restricted
            }
            
            Logger.d("BackgroundUsageDetector", "Background restricted: $isBackgroundRestricted")
            
            BackgroundUsageStatus(
                isBackgroundUsageAllowed = !isBackgroundRestricted,
                detectionMethod = DetectionMethod.BACKGROUND_RESTRICTION,
                apiLevel = Build.VERSION.SDK_INT,
                details = mapOf(
                    "isBackgroundRestricted" to isBackgroundRestricted,
                    "method" to "ActivityManager.isBackgroundRestricted"
                )
            )
        } catch (e: Exception) {
            Logger.e("BackgroundUsageDetector", "Failed to check background restrictions", e)
            BackgroundUsageStatus(
                isBackgroundUsageAllowed = true, // Assume allowed if check fails
                detectionMethod = DetectionMethod.FALLBACK,
                apiLevel = Build.VERSION.SDK_INT,
                details = mapOf<String, Any>("error" to (e.message ?: "Unknown error"))
            )
        }
    }
    
    /**
     * Check AppOps background check (Android 6+)
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkAppOpsBackground(context: Context): BackgroundUsageStatus {
        return try {
            val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val packageName = context.packageName
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            
            // Check SYSTEM_ALERT_WINDOW (used for background app detection)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, uid, packageName
                )
            }
            
            val isAllowed = mode == AppOpsManager.MODE_ALLOWED
            
            Logger.d("BackgroundUsageDetector", "AppOps background check - mode: $mode, allowed: $isAllowed")
            
            BackgroundUsageStatus(
                isBackgroundUsageAllowed = isAllowed,
                detectionMethod = DetectionMethod.APP_OPS_BACKGROUND_CHECK,
                apiLevel = Build.VERSION.SDK_INT,
                details = mapOf(
                    "appOpsMode" to mode,
                    "modeName" to getAppOpsModeName(mode),
                    "method" to "AppOpsManager.checkOpNoThrow"
                )
            )
        } catch (e: Exception) {
            Logger.e("BackgroundUsageDetector", "Failed to check AppOps", e)
            BackgroundUsageStatus(
                isBackgroundUsageAllowed = true, // Assume allowed if check fails
                detectionMethod = DetectionMethod.FALLBACK,
                apiLevel = Build.VERSION.SDK_INT,
                details = mapOf<String, Any>("error" to (e.message ?: "Unknown error"))
            )
        }
    }
    
    /**
     * Check OEM-specific background usage controls
     */
    private fun checkOEMSpecific(context: Context): BackgroundUsageStatus {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("samsung") -> checkSamsungBackgroundUsage(context)
            manufacturer.contains("xiaomi") -> checkXiaomiBackgroundUsage(context)
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> checkHuaweiBackgroundUsage(context)
            manufacturer.contains("oppo") -> checkOppoBackgroundUsage(context)
            manufacturer.contains("vivo") -> checkVivoBackgroundUsage(context)
            else -> BackgroundUsageStatus(
                isBackgroundUsageAllowed = true, // Assume allowed for unknown OEMs
                detectionMethod = DetectionMethod.OEM_SPECIFIC,
                apiLevel = Build.VERSION.SDK_INT,
                details = mapOf(
                    "manufacturer" to manufacturer,
                    "method" to "generic_oem_check"
                )
            )
        }
    }
    
    /**
     * Samsung-specific background usage check
     */
    private fun checkSamsungBackgroundUsage(context: Context): BackgroundUsageStatus {
        // Samsung uses Device Care and Sleeping Apps
        // This would require checking Samsung's internal APIs which aren't publicly available
        // For now, fall back to legacy detection but log that this is Samsung
        
        Logger.d("BackgroundUsageDetector", "Samsung device detected - using fallback detection")
        
        return BackgroundUsageStatus(
            isBackgroundUsageAllowed = true, // Conservative assumption for Samsung
            detectionMethod = DetectionMethod.OEM_SPECIFIC,
            apiLevel = Build.VERSION.SDK_INT,
            details = mapOf(
                "oem" to "samsung",
                "method" to "samsung_fallback",
                "note" to "Samsung Device Care settings may override this detection"
            )
        )
    }
    
    /**
     * Xiaomi-specific background usage check
     */
    private fun checkXiaomiBackgroundUsage(context: Context): BackgroundUsageStatus {
        // Xiaomi MIUI has aggressive power management
        // Check if we can access MIUI-specific settings
        
        Logger.d("BackgroundUsageDetector", "Xiaomi device detected - checking MIUI settings")
        
        return BackgroundUsageStatus(
            isBackgroundUsageAllowed = false, // Conservative assumption for MIUI
            detectionMethod = DetectionMethod.OEM_SPECIFIC,
            apiLevel = Build.VERSION.SDK_INT,
            details = mapOf(
                "oem" to "xiaomi",
                "method" to "miui_conservative",
                "note" to "MIUI power management may restrict background usage"
            )
        )
    }
    
    /**
     * Huawei-specific background usage check
     */
    private fun checkHuaweiBackgroundUsage(context: Context): BackgroundUsageStatus {
        Logger.d("BackgroundUsageDetector", "Huawei/Honor device detected")
        
        return BackgroundUsageStatus(
            isBackgroundUsageAllowed = false, // Conservative assumption for EMUI
            detectionMethod = DetectionMethod.OEM_SPECIFIC,
            apiLevel = Build.VERSION.SDK_INT,
            details = mapOf(
                "oem" to "huawei",
                "method" to "emui_conservative",
                "note" to "EMUI protected apps settings may be required"
            )
        )
    }
    
    /**
     * Oppo-specific background usage check
     */
    private fun checkOppoBackgroundUsage(context: Context): BackgroundUsageStatus {
        Logger.d("BackgroundUsageDetector", "Oppo device detected")
        
        return BackgroundUsageStatus(
            isBackgroundUsageAllowed = false, // Conservative assumption for ColorOS
            detectionMethod = DetectionMethod.OEM_SPECIFIC,
            apiLevel = Build.VERSION.SDK_INT,
            details = mapOf(
                "oem" to "oppo",
                "method" to "coloros_conservative",
                "note" to "ColorOS power management may restrict background usage"
            )
        )
    }
    
    /**
     * Vivo-specific background usage check
     */
    private fun checkVivoBackgroundUsage(context: Context): BackgroundUsageStatus {
        Logger.d("BackgroundUsageDetector", "Vivo device detected")
        
        return BackgroundUsageStatus(
            isBackgroundUsageAllowed = false, // Conservative assumption for FunTouch OS
            detectionMethod = DetectionMethod.OEM_SPECIFIC,
            apiLevel = Build.VERSION.SDK_INT,
            details = mapOf(
                "oem" to "vivo",
                "method" to "funtouchos_conservative",
                "note" to "FunTouch OS may restrict background apps"
            )
        )
    }
    
    /**
     * Analyze multiple detection results and return the most appropriate one
     */
    private fun analyzeDetectionResults(
        results: List<BackgroundUsageStatus>, 
        context: Context
    ): BackgroundUsageStatus {
        if (results.isEmpty()) {
            return BackgroundUsageStatus(
                isBackgroundUsageAllowed = false,
                detectionMethod = DetectionMethod.FALLBACK,
                apiLevel = Build.VERSION.SDK_INT,
                details = mapOf("error" to "No detection methods available")
            )
        }
        
        Logger.d("BackgroundUsageDetector", "Analyzing ${results.size} detection results:")
        results.forEach { result ->
            Logger.d("BackgroundUsageDetector", "  ${result.detectionMethod}: ${result.isBackgroundUsageAllowed}")
        }
        
        // Priority order for detection methods (most reliable first)
        val priorityOrder = listOf(
            DetectionMethod.APP_STANDBY_BUCKET,        // Most accurate for modern Android
            DetectionMethod.BACKGROUND_RESTRICTION,    // Direct background restriction check
            DetectionMethod.MODERN_BACKGROUND_USAGE,   // Modern background usage controls
            DetectionMethod.LEGACY_BATTERY_OPTIMIZATION, // Traditional method
            DetectionMethod.APP_OPS_BACKGROUND_CHECK,  // AppOps check
            DetectionMethod.OEM_SPECIFIC,              // OEM-specific detection
            DetectionMethod.FALLBACK                   // Fallback method
        )
        
        // Find the highest priority result
        for (method in priorityOrder) {
            val result = results.find { it.detectionMethod == method }
            if (result != null) {
                Logger.i("BackgroundUsageDetector", "Selected detection method: ${result.detectionMethod}, allowed: ${result.isBackgroundUsageAllowed}")
                return result
            }
        }
        
        // If no prioritized method found, use the first result
        val selectedResult = results.first()
        Logger.i("BackgroundUsageDetector", "Using first available result: ${selectedResult.detectionMethod}, allowed: ${selectedResult.isBackgroundUsageAllowed}")
        return selectedResult
    }
    
    /**
     * Get human-readable bucket name
     */
    private fun getBucketName(bucket: Int): String = when (bucket) {
        5 -> "ACTIVE"
        10 -> "WORKING_SET"
        20 -> "FREQUENT"
        30 -> "RARE"
        40 -> "RESTRICTED"
        45 -> "NEVER"
        else -> "UNKNOWN($bucket)"
    }
    
    /**
     * Get human-readable AppOps mode name
     */
    private fun getAppOpsModeName(mode: Int): String = when (mode) {
        AppOpsManager.MODE_ALLOWED -> "ALLOWED"
        AppOpsManager.MODE_IGNORED -> "IGNORED"
        AppOpsManager.MODE_ERRORED -> "ERRORED"
        AppOpsManager.MODE_DEFAULT -> "DEFAULT"
        else -> "UNKNOWN($mode)"
    }
    
    /**
     * Get cached background usage status (synchronous) - returns immediately if cached
     * Falls back to basic detection if no session cache available
     */
    fun getDetailedBackgroundUsageStatus(context: Context): BackgroundUsageStatus {
        // Return cached result if available - no blocking operations
        if (isCacheValid()) {
            Logger.d("BackgroundUsageDetector", "Returning cached background usage result from session")
            return cachedResult!!
        }
        
        // If no cache available, provide a basic synchronous fallback
        Logger.w("BackgroundUsageDetector", "No session cache available, using basic fallback detection")
        val basicResult = BackgroundUsageStatus(
            isBackgroundUsageAllowed = true, // Conservative assumption
            detectionMethod = DetectionMethod.FALLBACK,
            apiLevel = Build.VERSION.SDK_INT,
            details = mapOf("note" to "Fallback result - use initializeBackgroundUsageCache() for full detection")
        )
        
        return basicResult
    }
    
    /**
     * Initialize background usage cache with full detection (async, should be called once per session)
     * Runs expensive system calls on background thread to prevent ANR
     */
    suspend fun initializeBackgroundUsageCache(context: Context): BackgroundUsageStatus {
        // Skip if already cached for this session
        if (isCacheValid()) {
            Logger.d("BackgroundUsageDetector", "Session cache already initialized")
            return cachedResult!!
        }
        
        // Run expensive system calls on background thread
        return withContext(Dispatchers.IO) {
            Logger.i("BackgroundUsageDetector", "=== Initializing Background Usage Session Cache (Background Thread) ===")
            Logger.i("BackgroundUsageDetector", "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            Logger.i("BackgroundUsageDetector", "Android API: ${Build.VERSION.SDK_INT}")
            Logger.i("BackgroundUsageDetector", "Package: ${context.packageName}")
            
            val result = isBackgroundUsageAllowed(context)
            
            Logger.i("BackgroundUsageDetector", "Final Result:")
            Logger.i("BackgroundUsageDetector", "  Background Usage Allowed: ${result.isBackgroundUsageAllowed}")
            Logger.i("BackgroundUsageDetector", "  Detection Method: ${result.detectionMethod}")
            Logger.i("BackgroundUsageDetector", "  Details: ${result.details}")
            Logger.i("BackgroundUsageDetector", "===========================================")
            
            // Cache the result for the entire app session to prevent repeated expensive calls
            cachedResult = result
            isSessionCacheValid = true
            Logger.d("BackgroundUsageDetector", "Background usage session cache initialized")
            
            result
        }
    }
    
    /**
     * Clear the cache to force fresh detection (useful for testing or when user changes settings)
     */
    fun clearCache() {
        cachedResult = null
        isSessionCacheValid = false
        Logger.d("BackgroundUsageDetector", "Cleared background usage detection cache")
    }
}