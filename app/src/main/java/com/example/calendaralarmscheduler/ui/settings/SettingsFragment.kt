package com.example.calendaralarmscheduler.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.calendaralarmscheduler.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // TODO: Implement settings functionality in future step
        binding.textPlaceholder.text = "Settings - Coming Soon\n\nThis screen will include:\n• Background refresh interval\n• All-day event default time\n• Permission status dashboard\n• App preferences"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}