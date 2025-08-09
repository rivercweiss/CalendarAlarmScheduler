package com.example.calendaralarmscheduler.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(
    private val activity: FragmentActivity,
    private val onStepAction: (OnboardingStep) -> Unit
) : FragmentStateAdapter(activity) {

    private val steps = listOf(
        OnboardingStep.WELCOME,
        OnboardingStep.CALENDAR_PERMISSION,
        OnboardingStep.EXACT_ALARM_PERMISSION,
        OnboardingStep.BATTERY_OPTIMIZATION,
        OnboardingStep.COMPLETION
    )

    override fun getItemCount(): Int = steps.size

    override fun createFragment(position: Int): Fragment {
        return OnboardingStepFragment.newInstance(steps[position], onStepAction)
    }
    
    fun refreshCurrentStep() {
        notifyDataSetChanged()
    }
}

enum class OnboardingStep(
    val title: String,
    val description: String,
    val actionButtonText: String?,
    val hasAction: Boolean = false
) {
    WELCOME(
        title = "Welcome to Calendar Alarm Scheduler",
        description = "This app creates reliable alarms before your calendar events, ensuring you never miss important meetings or appointments.\n\nTo work properly, the app needs a few permissions.",
        actionButtonText = null
    ),
    
    CALENDAR_PERMISSION(
        title = "Calendar Access",
        description = "The app needs to read your calendar events to know when to schedule alarms.\n\nThis permission is essential for the app to function.",
        actionButtonText = "Grant Calendar Permission",
        hasAction = true
    ),
    
    EXACT_ALARM_PERMISSION(
        title = "Exact Alarm Scheduling",
        description = "Android 12+ requires special permission to schedule exact alarms.\n\nThis ensures your alarms fire precisely on time, not delayed by system optimization.",
        actionButtonText = "Open Settings",
        hasAction = true
    ),
    
    BATTERY_OPTIMIZATION(
        title = "Battery Optimization",
        description = "To ensure reliable background operation, please whitelist this app from battery optimization.\n\nThis prevents Android from limiting alarm scheduling when the app isn't actively used.",
        actionButtonText = "Whitelist App",
        hasAction = true
    ),
    
    COMPLETION(
        title = "All Set!",
        description = "Great! You've granted all necessary permissions.\n\nThe app can now schedule reliable alarms for your calendar events. You can start by creating your first rule.",
        actionButtonText = null
    )
}