package com.example.calendaralarmscheduler.ui.rules

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calendaralarmscheduler.databinding.DialogLeadTimePickerBinding

class LeadTimePickerDialog : DialogFragment() {
    
    private var _binding: DialogLeadTimePickerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: LeadTimeAdapter
    private var onLeadTimeSelectedListener: ((Int) -> Unit)? = null
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLeadTimePickerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val currentSelection = arguments?.getInt(ARG_CURRENT_LEAD_TIME, 30) ?: 30
        
        setupRecyclerView(currentSelection)
        setupButtons()
    }
    
    private fun setupRecyclerView(currentSelection: Int) {
        val leadTimeOptions = getLeadTimeOptions()
        
        adapter = LeadTimeAdapter(leadTimeOptions) { selectedMinutes ->
            onLeadTimeSelectedListener?.invoke(selectedMinutes)
            dismiss()
        }
        
        binding.recyclerViewLeadTimes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@LeadTimePickerDialog.adapter
        }
        
        // Scroll to current selection
        val currentIndex = leadTimeOptions.indexOfFirst { it.minutes == currentSelection }
        if (currentIndex >= 0) {
            binding.recyclerViewLeadTimes.scrollToPosition(currentIndex)
        }
    }
    
    private fun setupButtons() {
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }
    
    private fun getLeadTimeOptions(): List<LeadTimeOption> {
        return listOf(
            // Minutes
            LeadTimeOption(1, "1 minute"),
            LeadTimeOption(5, "5 minutes"),
            LeadTimeOption(10, "10 minutes"),
            LeadTimeOption(15, "15 minutes"),
            LeadTimeOption(30, "30 minutes"),
            LeadTimeOption(45, "45 minutes"),
            
            // Hours
            LeadTimeOption(60, "1 hour"),
            LeadTimeOption(90, "1.5 hours"),
            LeadTimeOption(120, "2 hours"),
            LeadTimeOption(180, "3 hours"),
            LeadTimeOption(240, "4 hours"),
            LeadTimeOption(360, "6 hours"),
            LeadTimeOption(480, "8 hours"),
            LeadTimeOption(720, "12 hours"),
            
            // Days
            LeadTimeOption(1440, "1 day"),
            LeadTimeOption(2880, "2 days"),
            LeadTimeOption(4320, "3 days"),
            LeadTimeOption(10080, "1 week")
        )
    }
    
    fun setOnLeadTimeSelectedListener(listener: (Int) -> Unit) {
        onLeadTimeSelectedListener = listener
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    companion object {
        private const val ARG_CURRENT_LEAD_TIME = "current_lead_time"
        
        fun newInstance(currentLeadTimeMinutes: Int): LeadTimePickerDialog {
            val dialog = LeadTimePickerDialog()
            val args = Bundle().apply {
                putInt(ARG_CURRENT_LEAD_TIME, currentLeadTimeMinutes)
            }
            dialog.arguments = args
            return dialog
        }
    }
}

data class LeadTimeOption(
    val minutes: Int,
    val displayText: String
)