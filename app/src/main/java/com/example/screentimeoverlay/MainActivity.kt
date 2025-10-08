package com.example.screentimeoverlay

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import java.util.Date
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val requestOverlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasAllPermissions()) startForegroundOverlayService() else promptMissingPermissions()
        }

    private val requestUsageAccess =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasAllPermissions()) startForegroundOverlayService() else promptMissingPermissions()
        }

    // Core feature managers (keeping for Option 2)
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var appFilterManager: AppFilterManager
    private lateinit var exportManager: ExportManager
    
    // Moderate feature managers (keeping for Option 2)
    private lateinit var sessionTracker: SessionTracker
    private lateinit var productivityScorer: ProductivityScorer
    private lateinit var usagePatternAnalyzer: UsagePatternAnalyzer
    private lateinit var historicalDataManager: HistoricalDataManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize core managers
        batteryOptimizationManager = BatteryOptimizationManager(this)
        appFilterManager = AppFilterManager(this)
        exportManager = ExportManager(this)
        
        // Initialize moderate feature managers
        sessionTracker = SessionTracker(this)
        productivityScorer = ProductivityScorer(this)
        usagePatternAnalyzer = UsagePatternAnalyzer(this)
        historicalDataManager = HistoricalDataManager(this)

        // Setup ViewPager2 and TabLayout
        setupViewPager()
    }

    private fun setupViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.setIcon(R.drawable.ic_monitor)
                }
                1 -> {
                    tab.setIcon(R.drawable.ic_analytics)
                }
                else -> {
                    tab.setIcon(R.drawable.ic_monitor)
                }
            }
        }.attach()
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode == AppOpsManager.MODE_ALLOWED) return true

        return try {
            val usage = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 60_000
            val list = usage.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end)
            list != null && list.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    fun hasAllPermissions(): Boolean = hasOverlayPermission() && hasUsageStatsPermission()

    private fun promptMissingPermissions() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
        } else if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Usage access permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
        }
    }

    fun requestPermissions() {
        if (!hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            requestOverlayPermission.launch(intent)
        } else if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            requestUsageAccess.launch(intent)
            Toast.makeText(this, "Please enable usage access for this app", Toast.LENGTH_LONG).show()
        }
    }

    fun startForegroundOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
    }

    fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun switchDisplayMode(mode: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("action", "switch_display_mode")
        intent.putExtra("mode", mode)
        startService(intent)
        Toast.makeText(this, "Display mode: $mode", Toast.LENGTH_SHORT).show()
    }
    
    fun setPositionMode(position: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("action", "set_position_mode")
        intent.putExtra("position", position)
        startService(intent)
        Toast.makeText(this, "Position: $position", Toast.LENGTH_SHORT).show()
    }
    
    // Core feature methods
    fun handleBatteryOptimization() {
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_battery_settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val batteryStatusText = dialog.findViewById<android.widget.TextView>(R.id.batteryStatusText)
        val autoStartStatusText = dialog.findViewById<android.widget.TextView>(R.id.autoStartStatusText)
        val instructionsText = dialog.findViewById<android.widget.TextView>(R.id.instructionsText)
        val openBatterySettingsButton = dialog.findViewById<android.widget.Button>(R.id.openBatterySettingsButton)
        val openAutoStartButton = dialog.findViewById<android.widget.Button>(R.id.openAutoStartButton)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        // Check battery optimization status
        val isBatteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            batteryOptimizationManager.isBatteryOptimizationDisabled()
        } else {
            true // Not available on older versions
        }
        
        batteryStatusText.text = if (isBatteryOptimized) "✓ Disabled" else "✗ Enabled"
        batteryStatusText.setTextColor(if (isBatteryOptimized) getColor(android.R.color.holo_green_light) else getColor(android.R.color.holo_red_light))
        
        // Check auto-start status (simplified)
        autoStartStatusText.text = "Check Settings"
        autoStartStatusText.setTextColor(getColor(R.color.text_tertiary))
        
        // Get instructions
        val oemInstructions = batteryOptimizationManager.getOEMInstructions()
        val autoStartInstructions = batteryOptimizationManager.requestAutoStartWhitelist()
        
        instructionsText.text = buildString {
            appendLine("Battery Optimization:")
            appendLine("$oemInstructions")
            appendLine("\nAuto-Start Settings:")
            appendLine("$autoStartInstructions")
        }
        
        // Button listeners
        openBatterySettingsButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!isBatteryOptimized) {
                    batteryOptimizationManager.requestDisableBatteryOptimization(this)
                } else {
                    Toast.makeText(this, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Battery optimization not available on this Android version", Toast.LENGTH_SHORT).show()
            }
        }
        
        openAutoStartButton.setOnClickListener {
            // Open auto-start settings (device-specific)
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "Look for 'Auto-start' or 'Background activity' settings", Toast.LENGTH_LONG).show()
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    fun openAppFilterSettings() {
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_app_filters)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val filterModeText = dialog.findViewById<android.widget.TextView>(R.id.filterModeText)
        val totalAppsText = dialog.findViewById<android.widget.TextView>(R.id.totalAppsText)
        val whitelistCountText = dialog.findViewById<android.widget.TextView>(R.id.whitelistCountText)
        val blacklistCountText = dialog.findViewById<android.widget.TextView>(R.id.blacklistCountText)
        val excludedCategoriesText = dialog.findViewById<android.widget.TextView>(R.id.excludedCategoriesText)
        val configureFiltersButton = dialog.findViewById<android.widget.Button>(R.id.configureFiltersButton)
        val resetFiltersButton = dialog.findViewById<android.widget.Button>(R.id.resetFiltersButton)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        // Get current filter data
        val currentMode = appFilterManager.getFilterMode()
        val whitelist = appFilterManager.getWhitelist()
        val blacklist = appFilterManager.getBlacklist()
        val excludedCategories = appFilterManager.getExcludedCategories()
        
        // Populate data
        filterModeText.text = currentMode.name
        totalAppsText.text = (whitelist.size + blacklist.size).toString()
        whitelistCountText.text = "${whitelist.size} apps"
        blacklistCountText.text = "${blacklist.size} apps"
        excludedCategoriesText.text = if (excludedCategories.isNotEmpty()) {
            excludedCategories.joinToString(", ")
        } else {
            "None"
        }
        
        // Button listeners
        configureFiltersButton.setOnClickListener {
            Toast.makeText(this, "Filter configuration coming soon!", Toast.LENGTH_SHORT).show()
            // TODO: Implement filter configuration
        }
        
        resetFiltersButton.setOnClickListener {
            // Reset filters to default
            appFilterManager.resetFilters()
            Toast.makeText(this, "Filters reset to default", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    fun exportWeeklySummary() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val weekStart = calendar.time
        val shareIntent = exportManager.shareWeeklySummary(weekStart)
        startActivity(Intent.createChooser(shareIntent, "Share Weekly Summary"))
    }
    
    fun exportDailySummary() {
        val today = Date()
        val shareIntent = exportManager.shareDailySummary(today)
        startActivity(Intent.createChooser(shareIntent, "Share Daily Summary"))
    }
    
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Please enable Screen Time Overlay in accessibility services", Toast.LENGTH_LONG).show()
    }
    
    // Moderate feature methods
    fun showSessionStats() {
        val todayStats = sessionTracker.getSessionStats(Date())
        val currentSession = sessionTracker.getCurrentSession()
        val todaySessions = sessionTracker.getSessionsForDate(Date())
        
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_session_stats)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val totalSessionsText = dialog.findViewById<android.widget.TextView>(R.id.totalSessionsText)
        val totalTimeText = dialog.findViewById<android.widget.TextView>(R.id.totalTimeText)
        val averageSessionText = dialog.findViewById<android.widget.TextView>(R.id.averageSessionText)
        val focusScoreText = dialog.findViewById<android.widget.TextView>(R.id.focusScoreText)
        val focusedSessionsText = dialog.findViewById<android.widget.TextView>(R.id.focusedSessionsText)
        val longestSessionText = dialog.findViewById<android.widget.TextView>(R.id.longestSessionText)
        val currentSessionContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.currentSessionContainer)
        val currentAppText = dialog.findViewById<android.widget.TextView>(R.id.currentAppText)
        val currentDurationText = dialog.findViewById<android.widget.TextView>(R.id.currentDurationText)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        // Populate data with real session statistics
        totalSessionsText.text = todayStats.totalSessions.toString()
        totalTimeText.text = formatTime(todayStats.totalTime)
        averageSessionText.text = formatTime(todayStats.averageSessionTime)
        focusScoreText.text = "${todayStats.focusScore}%"
        focusedSessionsText.text = todayStats.focusedSessions.toString()
        longestSessionText.text = formatTime(todayStats.longestSession)
        
        // Show current session if available
        if (currentSession != null && currentSession.isActive) {
            currentSessionContainer.visibility = android.view.View.VISIBLE
            currentAppText.text = currentSession.appName
            // Calculate current session duration including active time
            val currentDuration = currentSession.totalTime + (System.currentTimeMillis() - currentSession.lastActivityTime)
            currentDurationText.text = formatTime(currentDuration)
        } else {
            currentSessionContainer.visibility = android.view.View.GONE
        }
        
        // Add session insights based on real data
        addSessionInsights(dialog, todaySessions, todayStats)
        
        // Close button listener
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    private fun addSessionInsights(dialog: android.app.Dialog, sessions: List<AppSession>, stats: SessionStats) {
        // Find the container for additional insights
        val insightsContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.insightsContainer)
        if (insightsContainer != null) {
            insightsContainer.removeAllViews()
            
            // Get enhanced session insights
            val insights = sessionTracker.getSessionInsights(Date())
            
            if (sessions.isNotEmpty()) {
                // Add session timeline visualization
                addSessionTimeline(insightsContainer, sessions)
                
                val insightsText = android.widget.TextView(this).apply {
                    text = buildString {
                        appendLine("Session Insights:")
                        appendLine("• Pattern: ${insights.sessionPattern}")
                        if (insights.firstSessionTime != null) {
                            appendLine("• First session: ${formatTime(insights.firstSessionTime)}")
                        }
                        if (insights.lastSessionTime != null) {
                            appendLine("• Last session: ${formatTime(insights.lastSessionTime)}")
                        }
                        if (insights.averageBreakTime > 0) {
                            appendLine("• Average break: ${formatTime(insights.averageBreakTime)}")
                        }
                        if (insights.mostUsedApp != null) {
                            appendLine("• Most used app: ${insights.mostUsedApp}")
                        }
                    }
                    textSize = 12f
                    setTextColor(getColor(R.color.text_tertiary))
                    setPadding(0, 8, 0, 8)
                }
                insightsContainer.addView(insightsText)
            } else {
                val noDataText = android.widget.TextView(this).apply {
                    text = "No session data available for today.\nStart using apps to see session analytics."
                    textSize = 12f
                    setTextColor(getColor(R.color.text_tertiary))
                    setPadding(0, 8, 0, 8)
                    gravity = android.view.Gravity.CENTER
                }
                insightsContainer.addView(noDataText)
            }
        }
    }
    
    private fun addSessionTimeline(container: android.widget.LinearLayout, sessions: List<AppSession>) {
        val timelineTitle = android.widget.TextView(this).apply {
            text = "Session Timeline"
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }
        container.addView(timelineTitle)
        
        // Create a simple timeline visualization
        val timelineContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }
        
        val sortedSessions = sessions.sortedBy { it.startTime }
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        // Create timeline bars for each session
        sortedSessions.forEachIndexed { index, session ->
            val sessionBar = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }
            
            // Session time label
            val timeLabel = android.widget.TextView(this).apply {
                text = formatTime(session.startTime)
                textSize = 10f
                setTextColor(getColor(R.color.text_tertiary))
                layoutParams = android.widget.LinearLayout.LayoutParams(80, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            
            // Session duration bar
            val sessionDuration = session.totalTime
            val barWidth = (sessionDuration * 200 / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(20) // Scale to fit
            
            val sessionBarView = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(barWidth, 20)
                setBackgroundColor(if (sessionDuration >= TimeUnit.MINUTES.toMillis(15)) {
                    getColor(android.R.color.holo_green_light) // Focused session
                } else {
                    getColor(android.R.color.holo_orange_light) // Short session
                })
            }
            
            // Session info
            val sessionInfo = android.widget.TextView(this).apply {
                text = "${session.appName} (${formatTime(sessionDuration)})"
                textSize = 10f
                setTextColor(getColor(R.color.text_tertiary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(8, 0, 0, 0)
            }
            
            sessionBar.addView(timeLabel)
            sessionBar.addView(sessionBarView)
            sessionBar.addView(sessionInfo)
            timelineContainer.addView(sessionBar)
        }
        
        container.addView(timelineContainer)
    }
    
    fun showProductivityInsights() {
        val insights = productivityScorer.getProductivityInsights(
            DateRange(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time,
                Date()
            ),
            emptyList()
        )
        
        val message = buildString {
            appendLine("Productivity Insights:")
            appendLine("Productivity Ratio: ${insights.productivityRatio}%")
            appendLine("Distraction Ratio: ${insights.distractionRatio}%")
            appendLine("\nTop Distracting Apps:")
            insights.topDistractingApps.take(3).forEach { app ->
                appendLine("• ${app.appName}: ${formatTime(app.timeSpent)}")
            }
            appendLine("\nRecommendations:")
            insights.recommendations.take(2).forEach { rec ->
                appendLine("• $rec")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    fun showPatternAnalysis() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekStart = calendar.time
        val weekEnd = Date()
        
        val analysis = usagePatternAnalyzer.analyzeUsagePatterns(DateRange(weekStart, weekEnd))
        
        val message = buildString {
            appendLine("Usage Pattern Analysis:")
            appendLine("Average Daily Usage: ${formatTime(analysis.averageDailyUsage)}")
            appendLine("Peak Usage Hour: ${analysis.peakUsageHour}:00")
            appendLine("Focus Score: ${analysis.focusScore}%")
            appendLine("Consistency Score: ${analysis.consistencyScore}%")
            appendLine("\nRecommendations:")
            analysis.recommendations.take(3).forEach { rec ->
                appendLine("• $rec")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    fun showWeeklyView() {
        val weeklySummary = historicalDataManager.getWeeklySummary(Date())
        
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_weekly_view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val totalTimeText = dialog.findViewById<android.widget.TextView>(R.id.totalTimeText)
        val totalSessionsText = dialog.findViewById<android.widget.TextView>(R.id.totalSessionsText)
        val averageDailyText = dialog.findViewById<android.widget.TextView>(R.id.averageDailyText)
        val focusScoreText = dialog.findViewById<android.widget.TextView>(R.id.focusScoreText)
        val productivityScoreText = dialog.findViewById<android.widget.TextView>(R.id.productivityScoreText)
        val mostActiveDayText = dialog.findViewById<android.widget.TextView>(R.id.mostActiveDayText)
        val topAppsContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.topAppsContainer)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        // Populate data
        totalTimeText.text = formatTime(weeklySummary.totalTime)
        totalSessionsText.text = weeklySummary.totalSessions.toString()
        averageDailyText.text = formatTime(weeklySummary.averageDailyTime)
        focusScoreText.text = "${weeklySummary.focusScore}%"
        productivityScoreText.text = "${weeklySummary.productivityScore.overallScore}%"
        
        // Format most active day
        val calendar = Calendar.getInstance()
        calendar.time = weeklySummary.mostActiveDay
        val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        mostActiveDayText.text = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        
        // Add top apps dynamically
        topAppsContainer.removeAllViews()
        weeklySummary.topApps.take(3).forEach { app ->
            val appView = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 8, 0, 8)
            }
            
            val appNameText = android.widget.TextView(this).apply {
                text = app.appName
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val appTimeText = android.widget.TextView(this).apply {
                text = formatTime(app.timeInForeground)
                textSize = 12f
                setTextColor(getColor(R.color.text_tertiary))
                gravity = android.view.Gravity.END
            }
            
            appView.addView(appNameText)
            appView.addView(appTimeText)
            topAppsContainer.addView(appView)
        }
        
        // Close button listener
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    fun showMonthlyView() {
        val monthlySummary = historicalDataManager.getMonthlySummary(Date())
        
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_monthly_view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val totalTimeText = dialog.findViewById<android.widget.TextView>(R.id.totalTimeText)
        val totalSessionsText = dialog.findViewById<android.widget.TextView>(R.id.totalSessionsText)
        val averageWeeklyText = dialog.findViewById<android.widget.TextView>(R.id.averageWeeklyText)
        val weeksTrackedText = dialog.findViewById<android.widget.TextView>(R.id.weeksTrackedText)
        val timeTrendText = dialog.findViewById<android.widget.TextView>(R.id.timeTrendText)
        val productivityTrendText = dialog.findViewById<android.widget.TextView>(R.id.productivityTrendText)
        val topAppsContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.topAppsContainer)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        // Populate data
        totalTimeText.text = formatTime(monthlySummary.totalTime)
        totalSessionsText.text = monthlySummary.totalSessions.toString()
        averageWeeklyText.text = formatTime(monthlySummary.averageWeeklyTime)
        weeksTrackedText.text = monthlySummary.weeklySummaries.size.toString()
        
        // Format trends with better display
        val timeTrendDisplay = when (monthlySummary.timeTrend) {
            com.example.screentimeoverlay.TrendDirection.INCREASING -> "↗ Increasing"
            com.example.screentimeoverlay.TrendDirection.DECREASING -> "↘ Decreasing"
            com.example.screentimeoverlay.TrendDirection.IMPROVING -> "↗ Improving"
            com.example.screentimeoverlay.TrendDirection.DECLINING -> "↘ Declining"
            com.example.screentimeoverlay.TrendDirection.STABLE -> "→ Stable"
        }
        timeTrendText.text = timeTrendDisplay
        
        val productivityTrendDisplay = when (monthlySummary.productivityTrend) {
            com.example.screentimeoverlay.TrendDirection.INCREASING -> "↗ Improving"
            com.example.screentimeoverlay.TrendDirection.DECREASING -> "↘ Declining"
            com.example.screentimeoverlay.TrendDirection.IMPROVING -> "↗ Improving"
            com.example.screentimeoverlay.TrendDirection.DECLINING -> "↘ Declining"
            com.example.screentimeoverlay.TrendDirection.STABLE -> "→ Stable"
        }
        productivityTrendText.text = productivityTrendDisplay
        
        // Add top apps dynamically
        topAppsContainer.removeAllViews()
        monthlySummary.topApps.take(3).forEach { app ->
            val appView = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 8, 0, 8)
            }
            
            val appNameText = android.widget.TextView(this).apply {
                text = app.appName
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val appTimeText = android.widget.TextView(this).apply {
                text = formatTime(app.timeInForeground)
                textSize = 12f
                setTextColor(getColor(R.color.text_tertiary))
                gravity = android.view.Gravity.END
            }
            
            appView.addView(appNameText)
            appView.addView(appTimeText)
            topAppsContainer.addView(appView)
        }
        
        // Close button listener
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    fun showBreakTimeRecommendations() {
        val recommendations = usagePatternAnalyzer.getOptimalBreakTimes()
        
        val message = buildString {
            appendLine("Break Time Recommendations:")
            recommendations.forEach { rec ->
                appendLine("• ${rec.time} (${rec.duration})")
                appendLine("  ${rec.reason}")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    fun formatTime(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)
    }
    
    
    
}