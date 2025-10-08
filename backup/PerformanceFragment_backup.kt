package com.example.screentimeoverlay

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.Fragment

class PerformanceFragment : Fragment(R.layout.fragment_performance) {

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val performanceButton = view.findViewById<Button>(R.id.performanceButton)
        val optimizationButton = view.findViewById<Button>(R.id.optimizationButton)
        val memoryButton = view.findViewById<Button>(R.id.memoryButton)
        val batteryButton = view.findViewById<Button>(R.id.batteryButton)

        // Get reference to MainActivity to access its methods
        val mainActivity = requireActivity() as MainActivity

        performanceButton.setOnClickListener {
            mainActivity.showPerformanceMetrics()
        }

        optimizationButton.setOnClickListener {
            mainActivity.showOptimizationRecommendations()
        }

        memoryButton.setOnClickListener {
            mainActivity.showMemoryStats()
        }

        batteryButton.setOnClickListener {
            mainActivity.showBatteryOptimization()
        }
    }
}
