package com.example.screentimeoverlay

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.Date
import java.util.concurrent.TimeUnit

class AnalyticsFragment : Fragment(R.layout.fragment_analytics) {

    private var refreshHandler: Handler? = null
    private var refreshRunnable: Runnable? = null
    private val REFRESH_INTERVAL_MS = 5000L // Refresh every 5 seconds

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionStatsButton = view.findViewById<Button>(R.id.sessionStatsButton)
        val weeklyViewButton = view.findViewById<Button>(R.id.weeklyViewButton)
        val monthlyViewButton = view.findViewById<Button>(R.id.monthlyViewButton)
        
        // Core feature buttons
        val batteryOptimizationButton = view.findViewById<Button>(R.id.batteryOptimizationButton)
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

        appFilterButton.setOnClickListener {
            mainActivity.openAppFilterSettings()
        }

        // Settings actions moved here
        val restartOnboardingButton = view.findViewById<Button>(com.example.screentimeoverlay.R.id.restartOnboardingButton)
        val privacyPolicyButton = view.findViewById<Button>(com.example.screentimeoverlay.R.id.privacyPolicyButton)

        restartOnboardingButton.setOnClickListener {
            mainActivity.restartOnboarding()
        }

        privacyPolicyButton.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
            intent.data = android.net.Uri.parse("https://screen-time-pmdx.vercel.app/privacy-policy.html")
            startActivity(intent)
        }

        // Populate Daily Summary banner from MetricsStore (3 lines + optional witty copy)
        updateDailySummary(view)

        // Setup info button
        val infoButton = view.findViewById<ImageButton>(R.id.dailySummaryInfoButton)
        infoButton?.setOnClickListener {
            showDailySummaryInfo()
        }

        // Setup quick enable button (shown when metrics disabled)
        val enableDailySummaryButton = view.findViewById<Button>(R.id.enableDailySummaryButton)
        enableDailySummaryButton?.setOnClickListener {
            // Direct path to enable - show disclosure if needed, then open settings
            mainActivity.openAccessibilitySettings()
        }

        // Update quick stats cards
        updateQuickStats(view)
        
        // Update goal progress
        updateGoalProgress(view)
        
        // Setup and populate charts
        setupCharts(view)
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning to fragment
        view?.let { 
            updateDailySummary(it)
            updateQuickStats(it)
            updateGoalProgress(it)
            updateCharts(it)
        }
        
        // Setup periodic refresh
        refreshHandler = Handler(Looper.getMainLooper())
        refreshRunnable = Runnable {
            view?.let { 
                updateDailySummary(it)
                updateQuickStats(it)
                updateGoalProgress(it)
                // Charts update less frequently to save performance
            }
            refreshHandler?.postDelayed(refreshRunnable!!, REFRESH_INTERVAL_MS)
        }
        refreshHandler?.postDelayed(refreshRunnable!!, REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic refresh when fragment is paused
        refreshRunnable?.let { refreshHandler?.removeCallbacks(it) }
        refreshHandler = null
        refreshRunnable = null
    }

    private fun updateDailySummary(view: android.view.View) {
        val titleView = view.findViewById<android.widget.TextView>(R.id.dailySummaryTitle)
        val textView = view.findViewById<android.widget.TextView>(R.id.dailySummaryText)
        val enableButton = view.findViewById<Button>(R.id.enableDailySummaryButton)
        
        if (titleView != null && textView != null) {
            val isServiceEnabled = isAccessibilityServiceEnabled()
            val store = MetricsStore(requireContext())
            val metrics = store.getTodayMetrics()
            val inAppToggleEnabled = requireContext()
                .getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
                .getBoolean("metrics_accessibility_enabled", false)

            val isFullyEnabled = isServiceEnabled && inAppToggleEnabled
            val hasData = metrics.taps > 0 || metrics.words > 0 || metrics.scrollPx > 0

            // Show enable button if not fully enabled
            if (!isFullyEnabled) {
                enableButton?.visibility = android.view.View.VISIBLE
                // Show encouraging message instead of technical instructions
                if (!hasData) {
                    textView.text = "Track your taps, scrolls, and words typed! Tap the button below to enable."
                }
            } else {
                enableButton?.visibility = android.view.View.GONE
            }

            // If not enabled and no data, show encouraging message and return early
            if (!isFullyEnabled && !hasData) {
                return
            }

            val dpi = requireContext().resources.displayMetrics.densityDpi.toFloat()
            val inches = if (dpi > 0f) metrics.scrollPx / dpi else 0f
            val meters = (inches * 0.0254f).coerceAtLeast(0f)
            val metersInt = meters.toInt().coerceAtLeast(0)
            val metersStr = java.text.NumberFormat.getIntegerInstance(java.util.Locale.getDefault()).format(metersInt)
            val wordsStr = java.text.NumberFormat.getIntegerInstance().format(metrics.words)
            val tapsStr = java.text.NumberFormat.getIntegerInstance().format(metrics.taps)

            val calendar = java.util.Calendar.getInstance()
            val dayIndex = calendar.get(java.util.Calendar.DAY_OF_YEAR)

            // Fixed jokes without conflicting numbers
            val doomLines = listOf(
                "You scrolled so far, NASA lost signal.",
                "You scrolled past happiness like it owed you money.",
                "You doom‑scrolled enough to make your thumb stronger than your willpower."
            )
            val typingLines = listOf(
                "You wrote a novel. Sadly, it's all in your drafts.",
                "You typed enough to start a revolution, then deleted it to avoid drama.",
                "You typed so much, Grammarly left the chat."
            )
            val tappingLines = listOf(
                "You tapped like your screen owed you rent.",
                "You tapped enough to make Beethoven proud.",
                "You tapped harder than your ex hitting 'ignore.'"
            )

            val doomQuip = doomLines[dayIndex % doomLines.size]
            val typingQuip = typingLines[dayIndex % typingLines.size]
            val tappingQuip = tappingLines[dayIndex % tappingLines.size]

            // Create SpannableString for visual hierarchy
            val spannable = android.text.SpannableStringBuilder()
            @ColorInt val accentColor: Int = ContextCompat.getColor(requireContext(), R.color.accent_primary)
            @ColorInt val secondaryColor: Int = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            @ColorInt val tertiaryColor: Int = ContextCompat.getColor(requireContext(), R.color.text_tertiary)
            
            // Scrolled line (show meters instead of km)
            spannable.append("Scrolled: ")
            spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val scrollStart = spannable.length
            spannable.append("$metersStr m")
            spannable.setSpan(android.text.style.ForegroundColorSpan(accentColor), scrollStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.RelativeSizeSpan(1.15f), scrollStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), scrollStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (meters >= 500f) {
                spannable.append("\n")
                val commentStart = spannable.length
                spannable.append(doomQuip)
                spannable.setSpan(android.text.style.ForegroundColorSpan(tertiaryColor), commentStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.RelativeSizeSpan(0.85f), commentStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            spannable.append("\n\n")
            
            // Typed line
            val typedLabelStart = spannable.length
            spannable.append("Typed: ")
            spannable.setSpan(android.text.style.ForegroundColorSpan(secondaryColor), typedLabelStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), typedLabelStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val typedValueStart = spannable.length
            spannable.append("$wordsStr words")
            spannable.setSpan(android.text.style.ForegroundColorSpan(accentColor), typedValueStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.RelativeSizeSpan(1.15f), typedValueStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), typedValueStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (metrics.words >= 1000) {
                spannable.append("\n")
                val commentStart = spannable.length
                spannable.append(typingQuip)
                spannable.setSpan(android.text.style.ForegroundColorSpan(tertiaryColor), commentStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.RelativeSizeSpan(0.85f), commentStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            spannable.append("\n\n")
            
            // Tapped line
            val tappedLabelStart = spannable.length
            spannable.append("Tapped: ")
            spannable.setSpan(android.text.style.ForegroundColorSpan(secondaryColor), tappedLabelStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), tappedLabelStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val tappedValueStart = spannable.length
            spannable.append("$tapsStr taps")
            spannable.setSpan(android.text.style.ForegroundColorSpan(accentColor), tappedValueStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.RelativeSizeSpan(1.15f), tappedValueStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), tappedValueStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (metrics.taps >= 1000) {
                spannable.append("\n")
                val commentStart = spannable.length
                spannable.append(tappingQuip)
                spannable.setSpan(android.text.style.ForegroundColorSpan(tertiaryColor), commentStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(android.text.style.RelativeSizeSpan(0.85f), commentStart, spannable.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            textView.text = spannable
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = requireContext().getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val serviceName = MetricsAccessibilityService::class.java.name
        val packageName = requireContext().packageName
        
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name == serviceName
        }
    }

    private fun showDailySummaryInfo() {
        val isServiceEnabled = isAccessibilityServiceEnabled()
        val prefs = requireContext().getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val inAppToggleEnabled = prefs.getBoolean("metrics_accessibility_enabled", false)
        
        val message = buildString {
            appendLine("Daily Summary tracks:")
            appendLine("• Taps (screen touches)")
            appendLine("• Scroll distance (pixels scrolled)")
            appendLine("• Words typed")
            appendLine()
            if (!isServiceEnabled) {
                appendLine("⚠️ Accessibility service not enabled")
                appendLine("Go to Settings > Accessibility to enable 'Screen Time Overlay metrics service'")
                appendLine()
            } else if (!inAppToggleEnabled) {
                appendLine("⚠️ In-app toggle is off")
                appendLine("Tap 'Open Settings' to enable the metrics toggle")
                appendLine()
            } else {
                appendLine("✓ Service is enabled and collecting data")
            }
            appendLine("Data is collected locally and resets daily at midnight.")
        }
        
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Daily Summary Info")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                (requireActivity() as? MainActivity)?.openAccessibilitySettings()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updateQuickStats(view: android.view.View) {
        try {
            val mainActivity = requireActivity() as? MainActivity ?: return
            val todayStats = mainActivity.getSessionTracker().getSessionStats(Date())
            // Get total time using same method as overlay to ensure consistency
            val todayTotalTime = mainActivity.getTodayTotalUsageFromUsageStats()
            
            // Update Total Time card
            val totalTimeValue = view.findViewById<android.widget.TextView>(R.id.totalTimeValue)
            totalTimeValue?.text = formatTimeCompact(todayTotalTime)
            
            // Update Sessions card
            val sessionsValue = view.findViewById<android.widget.TextView>(R.id.sessionsValue)
            sessionsValue?.text = todayStats.totalSessions.toString()
            
            // Update Average Session card
            val avgSessionValue = view.findViewById<android.widget.TextView>(R.id.avgSessionValue)
            val avgSessionTime = if (todayStats.totalSessions > 0) {
                todayStats.averageSessionTime
            } else {
                0L
            }
            avgSessionValue?.text = formatTimeCompact(avgSessionTime)
            
            // Update Focus Score card
            val focusScoreValue = view.findViewById<android.widget.TextView>(R.id.focusScoreValue)
            focusScoreValue?.text = "${todayStats.focusScore}%"
        } catch (e: Exception) {
            // Silently handle errors - stats will update on next refresh
        }
    }

    private fun updateGoalProgress(view: android.view.View) {
        try {
            val goalProgressContainer = view.findViewById<LinearLayout>(R.id.goalProgressContainer)
            val goalProgressBar = view.findViewById<android.widget.ProgressBar>(R.id.goalProgressBar)
            val goalProgressText = view.findViewById<android.widget.TextView>(R.id.goalProgressText)
            val goalProgressLabel = view.findViewById<android.widget.TextView>(R.id.goalProgressLabel)
            
            if (goalProgressContainer == null || goalProgressBar == null || 
                goalProgressText == null || goalProgressLabel == null) {
                return
            }
            
            val mainActivity = requireActivity() as? MainActivity ?: return
            val todayTotalTime = mainActivity.getTodayTotalUsageFromUsageStats()
            
            // Get daily goal from preferences
            val overlayPrefs = requireContext().getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
            val goalHours = overlayPrefs.getInt("daily_goal_hours", 0)
            val goalMinutes = overlayPrefs.getInt("daily_goal_minutes", 0)
            val goalTotalMs = (goalHours * 60 + goalMinutes) * 60 * 1000L
            
            if (goalTotalMs > 0) {
                goalProgressContainer.visibility = View.VISIBLE
                
                val progressPercent = ((todayTotalTime.toFloat() / goalTotalMs.toFloat()) * 100).toInt().coerceIn(0, 100)
                goalProgressBar.progress = progressPercent
                
                goalProgressText.text = "$progressPercent%"
                goalProgressLabel.text = "Daily Goal (${goalHours}h ${goalMinutes}m)"
            } else {
                goalProgressContainer.visibility = View.GONE
            }
        } catch (e: Exception) {
            // Silently handle errors - goal progress will update on next refresh
        }
    }

    private fun formatTimeCompact(timeMs: Long): String {
        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(timeMs)
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(timeMs) % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }

    private fun setupCharts(view: android.view.View) {
        setupWeeklyChart(view)
    }

    private fun setupWeeklyChart(view: android.view.View) {
        val chart = view.findViewById<BarChart>(R.id.weeklyUsageChart) ?: return
        
        try {
            val mainActivity = requireActivity() as? MainActivity ?: return
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            
            val weekStart = calendar.time
            val weeklySummary = mainActivity.getHistoricalDataManager().getWeeklySummary(weekStart)
            
            val entries = mutableListOf<BarEntry>()
            val dayLabels = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            
            weeklySummary.dailySummaries.forEachIndexed { index, dailySummary ->
                val hours = TimeUnit.MILLISECONDS.toHours(dailySummary.totalTime).toFloat()
                entries.add(BarEntry(index.toFloat(), hours))
            }
            
            val dataSet = BarDataSet(entries, "Hours")
            @ColorInt val accentColor = ContextCompat.getColor(requireContext(), R.color.accent_primary)
            dataSet.color = accentColor
            dataSet.valueTextColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
            dataSet.valueTextSize = 10f
            
            val barData = BarData(dataSet)
            barData.barWidth = 0.6f
            
            chart.data = barData
            chart.description.isEnabled = false
            chart.legend.isEnabled = false
            chart.setFitBars(true)
            
            val xAxis = chart.xAxis
            xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            xAxis.textSize = 10f
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt().coerceIn(0, dayLabels.size - 1)
                    return dayLabels[index]
                }
            }
            
            val leftAxis = chart.axisLeft
            leftAxis.setDrawGridLines(true)
            leftAxis.textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
            leftAxis.textSize = 10f
            leftAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}h"
                }
            }
            
            chart.axisRight.isEnabled = false
            chart.invalidate()
        } catch (e: Exception) {
            // Silently handle errors - chart will remain empty
        }
    }


    private fun updateCharts(view: android.view.View) {
        // Charts update less frequently, only refresh on resume
        setupCharts(view)
    }
}
