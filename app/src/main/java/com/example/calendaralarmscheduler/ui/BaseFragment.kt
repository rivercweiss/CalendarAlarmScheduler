package com.example.calendaralarmscheduler.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.calendaralarmscheduler.utils.Logger
import com.example.calendaralarmscheduler.utils.CrashHandler

/**
 * Base fragment with comprehensive logging for all lifecycle events
 */
abstract class BaseFragment : Fragment() {
    
    protected val crashHandler = CrashHandler()
    
    protected abstract val fragmentName: String
    
    override fun onAttach(context: Context) {
        Logger.logLifecycle(fragmentName, "onAttach", "Fragment attaching to context")
        Logger.dumpContext(fragmentName, context)
        try {
            super.onAttach(context)
            Logger.d(fragmentName, "onAttach completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onAttach failed", e)
            crashHandler.logNonFatalException(fragmentName, "onAttach failed", e)
            throw e
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.logLifecycle(fragmentName, "onCreate", "Fragment creating")
        Logger.d(fragmentName, "savedInstanceState: ${savedInstanceState != null}")
        
        try {
            super.onCreate(savedInstanceState)
            Logger.d(fragmentName, "onCreate completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onCreate failed", e)
            crashHandler.logNonFatalException(fragmentName, "onCreate failed", e)
            throw e
        }
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Logger.logLifecycle(fragmentName, "onCreateView", "Creating view")
        val startTime = System.currentTimeMillis()
        
        try {
            val view = createView(inflater, container, savedInstanceState)
            val createTime = System.currentTimeMillis() - startTime
            Logger.logPerformance(fragmentName, "onCreateView", createTime)
            Logger.d(fragmentName, "View created successfully in ${createTime}ms")
            return view
        } catch (e: Exception) {
            val createTime = System.currentTimeMillis() - startTime
            Logger.e(fragmentName, "onCreateView failed after ${createTime}ms", e)
            crashHandler.logNonFatalException(fragmentName, "onCreateView failed", e)
            throw e
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Logger.logLifecycle(fragmentName, "onViewCreated", "View created, setting up")
        val startTime = System.currentTimeMillis()
        
        try {
            super.onViewCreated(view, savedInstanceState)
            setupView(view, savedInstanceState)
            val setupTime = System.currentTimeMillis() - startTime
            Logger.logPerformance(fragmentName, "onViewCreated + setup", setupTime)
            Logger.d(fragmentName, "View setup completed successfully in ${setupTime}ms")
        } catch (e: Exception) {
            val setupTime = System.currentTimeMillis() - startTime
            Logger.e(fragmentName, "onViewCreated/setup failed after ${setupTime}ms", e)
            crashHandler.logNonFatalException(fragmentName, "onViewCreated/setup failed", e)
            throw e
        }
    }
    
    override fun onStart() {
        Logger.logLifecycle(fragmentName, "onStart", "Fragment starting")
        try {
            super.onStart()
            Logger.d(fragmentName, "onStart completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onStart failed", e)
            crashHandler.logNonFatalException(fragmentName, "onStart failed", e)
            throw e
        }
    }
    
    override fun onResume() {
        Logger.logLifecycle(fragmentName, "onResume", "Fragment resuming")
        try {
            super.onResume()
            onFragmentResumed()
            Logger.d(fragmentName, "onResume completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onResume failed", e)
            crashHandler.logNonFatalException(fragmentName, "onResume failed", e)
            throw e
        }
    }
    
    override fun onPause() {
        Logger.logLifecycle(fragmentName, "onPause", "Fragment pausing")
        try {
            onFragmentPaused()
            super.onPause()
            Logger.d(fragmentName, "onPause completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onPause failed", e)
            crashHandler.logNonFatalException(fragmentName, "onPause failed", e)
        }
    }
    
    override fun onStop() {
        Logger.logLifecycle(fragmentName, "onStop", "Fragment stopping")
        try {
            super.onStop()
            Logger.d(fragmentName, "onStop completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onStop failed", e)
            crashHandler.logNonFatalException(fragmentName, "onStop failed", e)
        }
    }
    
    override fun onDestroyView() {
        Logger.logLifecycle(fragmentName, "onDestroyView", "Destroying view")
        try {
            cleanupView()
            super.onDestroyView()
            Logger.d(fragmentName, "onDestroyView completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onDestroyView failed", e)
            crashHandler.logNonFatalException(fragmentName, "onDestroyView failed", e)
        }
    }
    
    override fun onDestroy() {
        Logger.logLifecycle(fragmentName, "onDestroy", "Fragment destroying")
        try {
            super.onDestroy()
            Logger.d(fragmentName, "onDestroy completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onDestroy failed", e)
            crashHandler.logNonFatalException(fragmentName, "onDestroy failed", e)
        }
    }
    
    override fun onDetach() {
        Logger.logLifecycle(fragmentName, "onDetach", "Fragment detaching")
        try {
            super.onDetach()
            Logger.d(fragmentName, "onDetach completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "onDetach failed", e)
            crashHandler.logNonFatalException(fragmentName, "onDetach failed", e)
        }
    }
    
    // Abstract methods for subclasses to implement
    protected abstract fun createView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    
    protected abstract fun setupView(view: View, savedInstanceState: Bundle?)
    
    // Optional lifecycle hooks for subclasses
    protected open fun onFragmentResumed() {
        // Override in subclasses if needed
    }
    
    protected open fun onFragmentPaused() {
        // Override in subclasses if needed
    }
    
    protected open fun cleanupView() {
        // Override in subclasses if needed
    }
    
    // Utility methods for logging
    protected fun logUserAction(action: String, details: String = "") {
        Logger.logUserAction("${fragmentName}_$action", details)
    }
    
    protected fun logNavigation(destination: String, action: String = "") {
        Logger.logNavigation(fragmentName, destination, action)
    }
    
    protected fun safeExecute(operation: String, action: () -> Unit) {
        try {
            Logger.d(fragmentName, "Executing: $operation")
            action()
            Logger.d(fragmentName, "$operation completed successfully")
        } catch (e: Exception) {
            Logger.e(fragmentName, "$operation failed", e)
            crashHandler.logNonFatalException(fragmentName, "$operation failed", e)
            throw e
        }
    }
}