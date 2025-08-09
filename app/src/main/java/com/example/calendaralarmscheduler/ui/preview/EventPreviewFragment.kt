package com.example.calendaralarmscheduler.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.calendaralarmscheduler.databinding.FragmentEventPreviewBinding

class EventPreviewFragment : Fragment() {
    private var _binding: FragmentEventPreviewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // TODO: Implement event preview functionality in future step
        binding.textPlaceholder.text = "Event Preview - Coming Soon\n\nThis screen will show:\n• Upcoming calendar events\n• Matching rules\n• Scheduled alarms"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}