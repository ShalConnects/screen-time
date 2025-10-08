package com.example.screentimeoverlay

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionStatsButton = view.findViewById<Button>(R.id.sessionStatsButton)
        val weeklyViewButton = view.findViewById<Button>(R.id.weeklyViewButton)
        val monthlyViewButton = view.findViewById<Button>(R.id.monthlyViewButton)
        
        // Core feature buttons
        val batteryOptimizationButton = view.findViewById<Button>(R.id.batteryOptimizationButton)
        val accessibilityButton = view.findViewById<Button>(R.id.accessibilityButton)
        val appFilterButton = view.findViewById<Button>(R.id.appFilterButton)

        // Get reference to MainActivity to access its methods
        val mainActivity = requireActivity() as MainActivity

        sessionStatsButton.setOnClickListener {
            mainActivity.showSessionStats()
        }

        weeklyViewButton.setOnClickListener {
            mainActivity.showWeeklyView()
        }

        monthlyViewButton.setOnClickListener {
            mainActivity.showMonthlyView()
        }
        
        // Core feature button listeners
        batteryOptimizationButton.setOnClickListener {
            mainActivity.handleBatteryOptimization()
        }

        accessibilityButton.setOnClickListener {
            mainActivity.openAccessibilitySettings()
        }

        appFilterButton.setOnClickListener {
            mainActivity.openAppFilterSettings()
        }
    }
}
