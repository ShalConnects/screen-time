package com.example.screentimeoverlay

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.Fragment

class ExportFragment : Fragment(R.layout.fragment_export) {

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val exportDailyButton = view.findViewById<Button>(R.id.exportDailyButton)
        val exportWeeklyButton = view.findViewById<Button>(R.id.exportWeeklyButton)

        // Get reference to MainActivity to access its methods
        val mainActivity = requireActivity() as MainActivity

        exportDailyButton.setOnClickListener {
            mainActivity.exportDailySummary()
        }

        exportWeeklyButton.setOnClickListener {
            mainActivity.exportWeeklySummary()
        }
    }
}
