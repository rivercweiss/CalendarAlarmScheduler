package com.example.calendaralarmscheduler.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.calendaralarmscheduler.databinding.FragmentOnboardingStepBinding
import com.example.calendaralarmscheduler.utils.PermissionUtils

class OnboardingStepFragment : Fragment() {
    
    private var _binding: FragmentOnboardingStepBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var step: OnboardingStep
    private lateinit var onStepAction: (OnboardingStep) -> Unit
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingStepBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val stepName = arguments?.getString(ARG_STEP) ?: return
        step = OnboardingStep.valueOf(stepName)
        
        setupViews()
        updatePermissionStatus()
    }
    
    private fun setupViews() {
        binding.apply {
            textTitle.text = step.title
            textDescription.text = step.description
            
            if (step.hasAction && step.actionButtonText != null) {
                buttonAction.text = step.actionButtonText
                buttonAction.visibility = View.VISIBLE
                buttonAction.setOnClickListener {
                    onStepAction(step)
                }
            } else {
                buttonAction.visibility = View.GONE
            }
            
            // Setup step icon based on step type
            when (step) {
                OnboardingStep.WELCOME -> {
                    imageIcon.setImageResource(android.R.drawable.ic_dialog_info)
                }
                OnboardingStep.CALENDAR_PERMISSION -> {
                    imageIcon.setImageResource(android.R.drawable.ic_menu_my_calendar)
                }
                OnboardingStep.NOTIFICATION_PERMISSION -> {
                    imageIcon.setImageResource(android.R.drawable.ic_popup_reminder)
                }
                OnboardingStep.EXACT_ALARM_PERMISSION -> {
                    imageIcon.setImageResource(android.R.drawable.ic_lock_idle_alarm)
                }
                OnboardingStep.BATTERY_OPTIMIZATION -> {
                    imageIcon.setImageResource(android.R.drawable.ic_menu_manage)
                }
                OnboardingStep.COMPLETION -> {
                    imageIcon.setImageResource(android.R.drawable.ic_dialog_info)
                }
            }
        }
    }
    
    private fun updatePermissionStatus() {
        if (!isAdded || context == null) return
        
        val status = when (step) {
            OnboardingStep.CALENDAR_PERMISSION -> {
                if (PermissionUtils.hasCalendarPermission(requireContext())) {
                    "✅ Calendar permission granted"
                } else {
                    "❌ Calendar permission needed"
                }
            }
            OnboardingStep.NOTIFICATION_PERMISSION -> {
                if (PermissionUtils.hasNotificationPermission(requireContext())) {
                    "✅ Notification permission granted"
                } else {
                    "❌ Notification permission needed"
                }
            }
            OnboardingStep.EXACT_ALARM_PERMISSION -> {
                if (PermissionUtils.hasExactAlarmPermission(requireContext())) {
                    "✅ Exact alarm permission granted"
                } else {
                    "❌ Exact alarm permission needed"
                }
            }
            OnboardingStep.BATTERY_OPTIMIZATION -> {
                if (PermissionUtils.isBatteryOptimizationWhitelisted(requireContext())) {
                    "✅ Battery optimization disabled"
                } else {
                    "⚠️ Battery optimization enabled (recommended to disable)"
                }
            }
            else -> null
        }
        
        if (status != null) {
            binding.textPermissionStatus.text = status
            binding.textPermissionStatus.visibility = View.VISIBLE
            
            // Hide action button if permission is already granted
            val isGranted = status.startsWith("✅")
            if (isGranted && step != OnboardingStep.BATTERY_OPTIMIZATION) {
                binding.buttonAction.visibility = View.GONE
            } else {
                binding.buttonAction.visibility = View.VISIBLE
            }
        } else {
            binding.textPermissionStatus.visibility = View.GONE
        }
    }
    
    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val ARG_STEP = "step"
        
        fun newInstance(
            step: OnboardingStep, 
            onStepAction: (OnboardingStep) -> Unit
        ): OnboardingStepFragment {
            val fragment = OnboardingStepFragment()
            fragment.onStepAction = onStepAction
            val args = Bundle().apply {
                putString(ARG_STEP, step.name)
            }
            fragment.arguments = args
            return fragment
        }
    }
}