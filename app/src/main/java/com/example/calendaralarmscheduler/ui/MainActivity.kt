package com.example.calendaralarmscheduler.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.calendaralarmscheduler.R
import com.example.calendaralarmscheduler.databinding.ActivityMainBinding
import com.example.calendaralarmscheduler.ui.onboarding.PermissionOnboardingActivity
import com.example.calendaralarmscheduler.utils.PermissionUtils
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.CrashHandler

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val crashHandler by lazy { CrashHandler(this) }

    // Permission launcher for calendar permission
    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Logger.logPermission("READ_CALENDAR", isGranted, "User response from permission dialog")
        if (isGranted) {
            // Permission granted, continue with normal flow
            Logger.i("MainActivity", "Calendar permission granted, proceeding normally")
            onPermissionGranted()
        } else {
            // Permission denied, show onboarding
            Logger.w("MainActivity", "Calendar permission denied, launching onboarding")
            launchOnboarding()
        }
    }

    // Multiple permissions launcher (for future use)
    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Logger.i("MainActivity", "Multiple permissions result: $permissions")
        val allGranted = permissions.values.all { it }
        permissions.forEach { (permission, granted) ->
            Logger.logPermission(permission, granted, "Multiple permissions result")
        }
        
        if (allGranted) {
            Logger.i("MainActivity", "All multiple permissions granted")
            onPermissionGranted()
        } else {
            Logger.w("MainActivity", "Some permissions denied, launching onboarding")
            launchOnboarding()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val startTime = System.currentTimeMillis()
        Logger.logLifecycle("MainActivity", "onCreate", "Starting MainActivity initialization")
        
        try {
            super.onCreate(savedInstanceState)
            Logger.d("MainActivity", "super.onCreate() completed successfully")
            
            // Initialize view binding with error handling
            Logger.d("MainActivity", "Inflating ActivityMainBinding")
            binding = ActivityMainBinding.inflate(layoutInflater)
            Logger.d("MainActivity", "ActivityMainBinding inflated successfully")
            
            Logger.d("MainActivity", "Setting content view")
            setContentView(binding.root)
            Logger.d("MainActivity", "Content view set successfully")
            
            Logger.dumpContext("MainActivity", this)
            
            // Setup navigation with error handling
            Logger.d("MainActivity", "Setting up navigation")
            setupNavigation()
            Logger.d("MainActivity", "Navigation setup completed")
            
            // Check permissions
            Logger.d("MainActivity", "Checking permissions and proceeding")
            checkPermissionsAndProceed()
            
            val createTime = System.currentTimeMillis() - startTime
            Logger.logPerformance("MainActivity", "onCreate()", createTime)
            Logger.logLifecycle("MainActivity", "onCreate", "Completed successfully in ${createTime}ms")
            
        } catch (e: Exception) {
            val createTime = System.currentTimeMillis() - startTime
            Logger.crash("MainActivity", "FATAL: onCreate() failed after ${createTime}ms", e)
            crashHandler.logCurrentAppState("MainActivity")
            crashHandler.logNonFatalException("MainActivity", "onCreate failed", e)
            throw e
        }
    }

    private fun setupNavigation() {
        try {
            Logger.d("MainActivity", "Finding NavHostFragment")
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            
            if (navHostFragment == null) {
                Logger.e("MainActivity", "NavHostFragment not found! Fragment may be missing from layout")
                throw IllegalStateException("NavHostFragment not found in R.id.nav_host_fragment")
            }
            
            Logger.d("MainActivity", "NavHostFragment found, getting NavController")
            val navController = navHostFragment.navController
            Logger.d("MainActivity", "NavController obtained: ${navController}")
            
            // Setup bottom navigation with nav controller
            Logger.d("MainActivity", "Setting up bottom navigation with NavController")
            binding.bottomNavigation.setupWithNavController(navController)
            Logger.d("MainActivity", "Bottom navigation setup completed")
            
        } catch (e: Exception) {
            Logger.e("MainActivity", "Navigation setup failed", e)
            crashHandler.logNonFatalException("MainActivity", "Navigation setup failed", e)
            throw e
        }
    }

    private fun checkPermissionsAndProceed() {
        try {
            Logger.d("MainActivity", "Checking critical permissions status")
            val hasCriticalPermissions = PermissionUtils.hasAllCriticalPermissions(this)
            Logger.i("MainActivity", "Critical permissions status: $hasCriticalPermissions")
            
            if (!hasCriticalPermissions) {
                // Show onboarding if critical permissions are missing
                Logger.w("MainActivity", "Critical permissions missing, launching onboarding")
                launchOnboarding()
            } else {
                // All permissions granted, proceed normally
                Logger.i("MainActivity", "All critical permissions granted, proceeding normally")
                onPermissionGranted()
            }
            
        } catch (e: Exception) {
            Logger.e("MainActivity", "Permission check failed", e)
            crashHandler.logNonFatalException("MainActivity", "Permission check failed", e)
            // Default to showing onboarding on error
            launchOnboarding()
        }
    }

    private fun onPermissionGranted() {
        Logger.i("MainActivity", "onPermissionGranted called - app can function normally")
        // Permissions are granted, app can function normally
        // Navigation is already set up, no additional action needed
    }

    private fun launchOnboarding() {
        try {
            Logger.i("MainActivity", "Launching PermissionOnboardingActivity")
            val intent = Intent(this, PermissionOnboardingActivity::class.java)
            startActivity(intent)
            Logger.i("MainActivity", "PermissionOnboardingActivity launched successfully")
            // Don't finish this activity - user can return after granting permissions
        } catch (e: Exception) {
            Logger.e("MainActivity", "Failed to launch onboarding", e)
            crashHandler.logNonFatalException("MainActivity", "Failed to launch onboarding", e)
        }
    }

    override fun onResume() {
        Logger.logLifecycle("MainActivity", "onResume", "Checking permissions on resume")
        super.onResume()
        
        try {
            // Check permissions again when returning from other activities
            // This handles the case where user grants permissions in system settings
            val hasCriticalPermissions = PermissionUtils.hasAllCriticalPermissions(this)
            Logger.i("MainActivity", "onResume permission check: $hasCriticalPermissions")
            
            if (!hasCriticalPermissions) {
                Logger.w("MainActivity", "Still missing permissions on resume, showing onboarding")
                launchOnboarding()
            } else {
                Logger.i("MainActivity", "All permissions available on resume")
            }
        } catch (e: Exception) {
            Logger.e("MainActivity", "onResume permission check failed", e)
            crashHandler.logNonFatalException("MainActivity", "onResume permission check failed", e)
        }
    }

    override fun onPause() {
        Logger.logLifecycle("MainActivity", "onPause", "Activity pausing")
        super.onPause()
    }

    override fun onStop() {
        Logger.logLifecycle("MainActivity", "onStop", "Activity stopping")
        super.onStop()
    }

    override fun onDestroy() {
        Logger.logLifecycle("MainActivity", "onDestroy", "Activity destroying")
        super.onDestroy()
    }

    /**
     * Method to request calendar permission (can be called from fragments)
     */
    fun requestCalendarPermission() {
        try {
            Logger.logUserAction("RequestCalendarPermission", "Called from fragment")
            if (PermissionUtils.shouldShowCalendarPermissionRationale(this)) {
                // Show rationale and then request permission
                Logger.d("MainActivity", "Showing permission rationale before request")
                PermissionUtils.requestCalendarPermission(calendarPermissionLauncher)
            } else {
                // Directly request permission
                Logger.d("MainActivity", "Directly requesting calendar permission")
                PermissionUtils.requestCalendarPermission(calendarPermissionLauncher)
            }
        } catch (e: Exception) {
            Logger.e("MainActivity", "Failed to request calendar permission", e)
            crashHandler.logNonFatalException("MainActivity", "Calendar permission request failed", e)
        }
    }

    /**
     * Method to request multiple permissions (for future use)
     */
    fun requestMultiplePermissions(permissions: Array<String>) {
        try {
            Logger.logUserAction("RequestMultiplePermissions", "Permissions: ${permissions.joinToString()}")
            Logger.d("MainActivity", "Requesting multiple permissions: ${permissions.joinToString()}")
            PermissionUtils.requestMultiplePermissions(multiplePermissionLauncher, permissions)
        } catch (e: Exception) {
            Logger.e("MainActivity", "Failed to request multiple permissions", e)
            crashHandler.logNonFatalException("MainActivity", "Multiple permissions request failed", e)
        }
    }

    /**
     * Get permission status for display in fragments
     */
    fun getPermissionStatus(): PermissionUtils.PermissionStatus {
        return try {
            val status = PermissionUtils.getAllPermissionStatus(this)
            Logger.d("MainActivity", "Permission status requested: $status")
            status
        } catch (e: Exception) {
            Logger.e("MainActivity", "Failed to get permission status", e)
            crashHandler.logNonFatalException("MainActivity", "Permission status check failed", e)
            PermissionUtils.PermissionStatus(
                hasCalendarPermission = false,
                hasNotificationPermission = false,
                hasExactAlarmPermission = false,
                hasFullScreenIntentPermission = false,
                isBatteryOptimizationWhitelisted = false
            )
        }
    }
}