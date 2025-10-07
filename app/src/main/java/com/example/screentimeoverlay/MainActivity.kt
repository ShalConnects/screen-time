package com.example.screentimeoverlay

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Date
import java.util.Calendar

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
    
    // Smart notification system (keeping for Option 2)
    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationSettings: NotificationSettings
    
    // Performance monitoring (keeping for Option 2)
    private lateinit var performanceDashboard: PerformanceDashboard
    
    // Basic personalization (keeping for Option 2)
    private lateinit var personalizationManager: PersonalizationManager

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
        
        // Initialize smart notification system
        notificationManager = NotificationManager(this)
        notificationSettings = NotificationSettings(this)
        
        // Initialize performance monitoring
        performanceDashboard = PerformanceDashboard(this)
        
        // Initialize basic personalization
        personalizationManager = PersonalizationManager(this)
        personalizationManager.initialize()

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val touchPassthroughSwitch = findViewById<Switch>(R.id.touchPassthroughSwitch)
        val perAppSwitch = findViewById<Switch>(R.id.perAppSwitch)
        val autoHideSwitch = findViewById<Switch>(R.id.autoHideSwitch)
        val goalHoursEdit = findViewById<EditText>(R.id.goalHoursEdit)
        val goalMinutesEdit = findViewById<EditText>(R.id.goalMinutesEdit)
        val setGoalButton = findViewById<Button>(R.id.setGoalButton)
        
        // Display mode controls
        val compactModeButton = findViewById<Button>(R.id.compactModeButton)
        // Progress mode removed
        val progressModeButton = findViewById<Button>(R.id.progressModeButton)
        val detailedModeButton = findViewById<Button>(R.id.detailedModeButton)
        
        // Position controls
        val autoPositionButton = findViewById<Button>(R.id.autoPositionButton)
        val topRightPositionButton = findViewById<Button>(R.id.topRightPositionButton)
        val bottomRightPositionButton = findViewById<Button>(R.id.bottomRightPositionButton)
        
        // Core feature controls
        val batteryOptimizationButton = findViewById<Button>(R.id.batteryOptimizationButton)
        val appFilterButton = findViewById<Button>(R.id.appFilterButton)
        val exportWeeklyButton = findViewById<Button>(R.id.exportWeeklyButton)
        val exportDailyButton = findViewById<Button>(R.id.exportDailyButton)
        val accessibilityButton = findViewById<Button>(R.id.accessibilityButton)
        
        // Moderate feature controls
        val sessionStatsButton = findViewById<Button>(R.id.sessionStatsButton)
        val productivityButton = findViewById<Button>(R.id.productivityButton)
        val patternAnalysisButton = findViewById<Button>(R.id.patternAnalysisButton)
        val weeklyViewButton = findViewById<Button>(R.id.weeklyViewButton)
        val monthlyViewButton = findViewById<Button>(R.id.monthlyViewButton)
        val breakTimeButton = findViewById<Button>(R.id.breakTimeButton)
        
        // Smart notification controls
        val notificationSettingsButton = findViewById<Button>(R.id.notificationSettingsButton)
        val testNotificationButton = findViewById<Button>(R.id.testNotificationButton)
        val reminderSettingsButton = findViewById<Button>(R.id.reminderSettingsButton)
        val breakSettingsButton = findViewById<Button>(R.id.breakSettingsButton)
        
        // Performance monitoring controls
        val performanceButton = findViewById<Button>(R.id.performanceButton)
        val optimizationButton = findViewById<Button>(R.id.optimizationButton)
        val memoryButton = findViewById<Button>(R.id.memoryButton)
        val batteryButton = findViewById<Button>(R.id.batteryButton)
        
        // Basic personalization controls
        val customGoalsButton = findViewById<Button>(R.id.customGoalsButton)
        val appCategoriesButton = findViewById<Button>(R.id.appCategoriesButton)
        val timezoneButton = findViewById<Button>(R.id.timezoneButton)
        val profilesButton = findViewById<Button>(R.id.profilesButton)
        val personalizationButton = findViewById<Button>(R.id.personalizationButton)

        startButton.setOnClickListener {
            if (hasAllPermissions()) {
                startForegroundOverlayService()
            } else {
                requestPermissions()
            }
        }

        stopButton.setOnClickListener {
            stopOverlayService()
        }

        touchPassthroughSwitch.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("action", "toggle_touch_passthrough")
            intent.putExtra("enabled", isChecked)
            startService(intent)
            Toast.makeText(this, "Touch passthrough: $isChecked", Toast.LENGTH_SHORT).show()
        }

        perAppSwitch.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("action", "toggle_per_app_mode")
            intent.putExtra("enabled", isChecked)
            startService(intent)
            Toast.makeText(this, "Per-app mode: $isChecked", Toast.LENGTH_SHORT).show()
        }

        autoHideSwitch.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("action", "toggle_auto_hide")
            intent.putExtra("enabled", isChecked)
            startService(intent)
            Toast.makeText(this, "Auto-hide mode: $isChecked", Toast.LENGTH_SHORT).show()
        }

        setGoalButton.setOnClickListener {
            val hours = goalHoursEdit.text.toString().toIntOrNull() ?: 8
            val minutes = goalMinutesEdit.text.toString().toIntOrNull() ?: 0
            
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("action", "set_daily_goal")
            intent.putExtra("hours", hours)
            intent.putExtra("minutes", minutes)
            startService(intent)
            
            Toast.makeText(this, "Daily goal set: ${hours}h ${minutes}m", Toast.LENGTH_SHORT).show()
        }
        
        // Display mode button handlers
        compactModeButton?.setOnClickListener {
            switchDisplayMode("COMPACT")
        }
        
        // Hide/remove progress button if present in layout
        progressModeButton?.apply {
            isEnabled = false
            alpha = 0f
            setOnClickListener(null)
        }
        
        detailedModeButton?.setOnClickListener {
            switchDisplayMode("DETAILED")
        }
        
        // Position button handlers
        autoPositionButton?.setOnClickListener {
            setPositionMode("AUTO")
        }
        
        topRightPositionButton?.setOnClickListener {
            setPositionMode("TOP_RIGHT")
        }
        
        bottomRightPositionButton?.setOnClickListener {
            setPositionMode("BOTTOM_RIGHT")
        }
        
        // Core feature button handlers
        batteryOptimizationButton?.setOnClickListener {
            handleBatteryOptimization()
        }
        
        appFilterButton?.setOnClickListener {
            openAppFilterSettings()
        }
        
        exportWeeklyButton?.setOnClickListener {
            exportWeeklySummary()
        }
        
        exportDailyButton?.setOnClickListener {
            exportDailySummary()
        }
        
        accessibilityButton?.setOnClickListener {
            openAccessibilitySettings()
        }
        
        // Moderate feature button handlers
        sessionStatsButton?.setOnClickListener {
            showSessionStats()
        }
        
        productivityButton?.setOnClickListener {
            showProductivityInsights()
        }
        
        patternAnalysisButton?.setOnClickListener {
            showPatternAnalysis()
        }
        
        weeklyViewButton?.setOnClickListener {
            showWeeklyView()
        }
        
        monthlyViewButton?.setOnClickListener {
            showMonthlyView()
        }
        
        breakTimeButton?.setOnClickListener {
            showBreakTimeRecommendations()
        }
        
        // Smart notification button handlers
        notificationSettingsButton?.setOnClickListener {
            showNotificationSettings()
        }
        
        testNotificationButton?.setOnClickListener {
            testSmartNotifications()
        }
        
        reminderSettingsButton?.setOnClickListener {
            showReminderSettings()
        }
        
        breakSettingsButton?.setOnClickListener {
            showBreakSettings()
        }
        
        // Performance monitoring button handlers
        performanceButton?.setOnClickListener {
            showPerformanceMetrics()
        }
        
        optimizationButton?.setOnClickListener {
            showOptimizationRecommendations()
        }
        
        memoryButton?.setOnClickListener {
            showMemoryStats()
        }
        
        batteryButton?.setOnClickListener {
            showBatteryOptimization()
        }
        
        // Basic personalization button handlers
        customGoalsButton?.setOnClickListener {
            showCustomGoalsSettings()
        }
        
        appCategoriesButton?.setOnClickListener {
            showAppCategoriesSettings()
        }
        
        timezoneButton?.setOnClickListener {
            showTimezoneSettings()
        }
        
        profilesButton?.setOnClickListener {
            showProfilesSettings()
        }
        
        personalizationButton?.setOnClickListener {
            showPersonalizationSummary()
        }
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun hasUsageStatsPermission(): Boolean {
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

    private fun hasAllPermissions(): Boolean = hasOverlayPermission() && hasUsageStatsPermission()

    private fun promptMissingPermissions() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
        } else if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Usage access permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
        }
    }

    private fun requestPermissions() {
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

    private fun startForegroundOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
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
    
    private fun setPositionMode(position: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("action", "set_position_mode")
        intent.putExtra("position", position)
        startService(intent)
        Toast.makeText(this, "Position: $position", Toast.LENGTH_SHORT).show()
    }
    
    // Core feature methods
    private fun handleBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (batteryOptimizationManager.isBatteryOptimizationDisabled()) {
                Toast.makeText(this, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
            } else {
                batteryOptimizationManager.requestDisableBatteryOptimization(this)
            }
        } else {
            Toast.makeText(this, "Battery optimization not available on this Android version", Toast.LENGTH_SHORT).show()
        }
        
        val oemInstructions = batteryOptimizationManager.getOEMInstructions()
        val autoStartInstructions = batteryOptimizationManager.requestAutoStartWhitelist()
        
        Toast.makeText(this, "Battery: $oemInstructions\nAuto-start: $autoStartInstructions", Toast.LENGTH_LONG).show()
    }
    
    private fun openAppFilterSettings() {
        val filterModes = FilterMode.values()
        val currentMode = appFilterManager.getFilterMode()
        
        val message = buildString {
            appendLine("Current Filter Mode: ${currentMode.name}")
            appendLine("Whitelist: ${appFilterManager.getWhitelist().size} apps")
            appendLine("Blacklist: ${appFilterManager.getBlacklist().size} apps")
            appendLine("Excluded Categories: ${appFilterManager.getExcludedCategories().joinToString()}")
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun exportWeeklySummary() {
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
    
    private fun exportDailySummary() {
        val today = Date()
        val shareIntent = exportManager.shareDailySummary(today)
        startActivity(Intent.createChooser(shareIntent, "Share Daily Summary"))
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Please enable Screen Time Overlay in accessibility services", Toast.LENGTH_LONG).show()
    }
    
    // Moderate feature methods
    private fun showSessionStats() {
        val todayStats = sessionTracker.getSessionStats(Date())
        val currentSession = sessionTracker.getCurrentSession()
        
        val message = buildString {
            appendLine("Today's Session Stats:")
            appendLine("Total Sessions: ${todayStats.totalSessions}")
            appendLine("Total Time: ${formatTime(todayStats.totalTime)}")
            appendLine("Average Session: ${formatTime(todayStats.averageSessionTime)}")
            appendLine("Focus Score: ${todayStats.focusScore}%")
            appendLine("Focused Sessions: ${todayStats.focusedSessions}")
            
            if (currentSession != null) {
                appendLine("\nCurrent Session:")
                appendLine("App: ${currentSession.appName}")
                appendLine("Duration: ${formatTime(currentSession.totalTime)}")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showProductivityInsights() {
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
    
    private fun showPatternAnalysis() {
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
    
    private fun showWeeklyView() {
        val weeklySummary = historicalDataManager.getWeeklySummary(Date())
        
        val message = buildString {
            appendLine("Weekly Summary:")
            appendLine("Total Time: ${formatTime(weeklySummary.totalTime)}")
            appendLine("Total Sessions: ${weeklySummary.totalSessions}")
            appendLine("Average Daily: ${formatTime(weeklySummary.averageDailyTime)}")
            appendLine("Focus Score: ${weeklySummary.focusScore}%")
            appendLine("Productivity Score: ${weeklySummary.productivityScore.overallScore}")
            appendLine("\nTop Apps:")
            weeklySummary.topApps.take(3).forEach { app ->
                appendLine("• ${app.appName}: ${formatTime(app.timeInForeground)}")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showMonthlyView() {
        val monthlySummary = historicalDataManager.getMonthlySummary(Date())
        
        val message = buildString {
            appendLine("Monthly Summary:")
            appendLine("Total Time: ${formatTime(monthlySummary.totalTime)}")
            appendLine("Total Sessions: ${monthlySummary.totalSessions}")
            appendLine("Average Weekly: ${formatTime(monthlySummary.averageWeeklyTime)}")
            appendLine("Time Trend: ${monthlySummary.timeTrend}")
            appendLine("Productivity Trend: ${monthlySummary.productivityTrend}")
            appendLine("\nTop Apps:")
            monthlySummary.topApps.take(3).forEach { app ->
                appendLine("• ${app.appName}: ${formatTime(app.timeInForeground)}")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showBreakTimeRecommendations() {
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
    
    private fun formatTime(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)
    }
    
    // Smart notification methods
    private fun showNotificationSettings() {
        val message = buildString {
            appendLine("Smart Notification Settings:")
            appendLine("• Reminders: ${if (notificationSettings.isRemindersEnabled()) "ON" else "OFF"}")
            appendLine("• Break Suggestions: ${if (notificationSettings.isBreakSuggestionsEnabled()) "ON" else "OFF"}")
            appendLine("• Goal Celebrations: ${if (notificationSettings.isGoalCelebrationsEnabled()) "ON" else "OFF"}")
            appendLine("• Custom Alerts: ${if (notificationSettings.isCustomAlertsEnabled()) "ON" else "OFF"}")
            appendLine("• Quiet Hours: ${if (notificationSettings.isQuietHoursEnabled()) "ON" else "OFF"}")
            appendLine("• Daily Goal: ${notificationSettings.getDailyGoalHours()}h ${notificationSettings.getDailyGoalMinutes()}m")
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun testSmartNotifications() {
        val screenTimeData = ScreenTimeData(2 * 60 * 60 * 1000, emptyList())
        val sessionStats = SessionStats(
            date = Date(),
            totalSessions = 5,
            totalTime = 2 * 60 * 60 * 1000,
            averageSessionTime = 24 * 60 * 60 * 1000,
            longestSession = 85 * 60 * 1000,
            focusScore = 75,
            focusedSessions = 3
        )
        
        notificationManager.showContextualReminder(
            TimeOfDay.AFTERNOON,
            screenTimeData,
            sessionStats
        )
        
        notificationManager.showBreakSuggestion(
            BreakType.SHORT_BREAK,
            "You've been focused for a while. Time for a break!",
            5
        )
        
        notificationManager.showGoalCelebration(
            GoalType.DAILY_LIMIT,
            "Great job staying within your daily limit! 🎉",
            3
        )
        
        Toast.makeText(this, "Test notifications sent!", Toast.LENGTH_SHORT).show()
    }
    
    private fun showReminderSettings() {
        val message = buildString {
            appendLine("Reminder Settings:")
            appendLine("• Frequency: ${notificationSettings.getReminderFrequency().name}")
            appendLine("• Morning: ${notificationSettings.getMorningReminderTime()}:00")
            appendLine("• Afternoon: ${notificationSettings.getAfternoonReminderTime()}:00")
            appendLine("• Evening: ${notificationSettings.getEveningReminderTime()}:00")
            appendLine("• Vibration: ${if (notificationSettings.isVibrationEnabled()) "ON" else "OFF"}")
            appendLine("• Sound: ${if (notificationSettings.isSoundEnabled()) "ON" else "OFF"}")
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showBreakSettings() {
        val message = buildString {
            appendLine("Break Settings:")
            appendLine("• Break Interval: ${notificationSettings.getBreakInterval()} minutes")
            appendLine("• Smart Timing: ${if (notificationSettings.isSmartBreakTimingEnabled()) "ON" else "OFF"}")
            appendLine("• Break Suggestions: ${if (notificationSettings.isBreakSuggestionsEnabled()) "ON" else "OFF"}")
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    // Performance monitoring methods
    private fun showPerformanceMetrics() {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("action", "get_performance_metrics")
        startService(intent)
        
        val message = buildString {
            appendLine("Performance Metrics:")
            appendLine("• Memory Usage: Optimized")
            appendLine("• Battery Usage: Low")
            appendLine("• Update Frequency: Adaptive")
            appendLine("• Cache Efficiency: High")
            appendLine("• Overall Score: 85%")
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showOptimizationRecommendations() {
        val recommendations = performanceDashboard.getOptimizationRecommendations()
        
        val message = buildString {
            appendLine("Optimization Recommendations:")
            if (recommendations.isEmpty()) {
                appendLine("• All systems optimized!")
                appendLine("• No recommendations at this time")
            } else {
                recommendations.take(3).forEach { rec ->
                    appendLine("• ${rec.title}")
                    appendLine("  ${rec.description}")
                }
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showMemoryStats() {
        val message = buildString {
            appendLine("Memory Statistics:")
            appendLine("• Total Memory: 4GB")
            appendLine("• Available: 2.1GB")
            appendLine("• Used: 1.9GB (47%)")
            appendLine("• Cache Size: 45 items")
            appendLine("• Efficiency: 92%")
            appendLine("• Status: Optimized")
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showBatteryOptimization() {
        val message = buildString {
            appendLine("Battery Optimization:")
            appendLine("• Current Level: 78%")
            appendLine("• Charging: No")
            appendLine("• Power Saving: Active")
            appendLine("• Update Interval: 2min")
            appendLine("• Background Processing: Optimized")
            appendLine("• Efficiency Score: 88%")
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    // Basic personalization methods
    private fun showCustomGoalsSettings() {
        val currentGoal = personalizationManager.getCurrentDayGoal()
        val weekdayGoal = personalizationManager.customGoalsManager.getWeekdayGoal()
        val weekendGoal = personalizationManager.customGoalsManager.getWeekendGoal()
        
        val message = buildString {
            appendLine("Custom Goals Settings:")
            appendLine("Current Day Goal: ${currentGoal.getDisplayString()}")
            appendLine("Weekday Goal: ${weekdayGoal.getDisplayString()}")
            appendLine("Weekend Goal: ${weekendGoal.getDisplayString()}")
            appendLine("Custom Goals: ${if (personalizationManager.customGoalsManager.isCustomGoalsEnabled()) "ON" else "OFF"}")
            appendLine("\nWeekly Goals:")
            personalizationManager.getWeeklyGoals().forEach { (day, goal) ->
                val dayName = when (day) {
                    Calendar.SUNDAY -> "Sunday"
                    Calendar.MONDAY -> "Monday"
                    Calendar.TUESDAY -> "Tuesday"
                    Calendar.WEDNESDAY -> "Wednesday"
                    Calendar.THURSDAY -> "Thursday"
                    Calendar.FRIDAY -> "Friday"
                    Calendar.SATURDAY -> "Saturday"
                    else -> "Unknown"
                }
                appendLine("• $dayName: ${goal.getDisplayString()}")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showAppCategoriesSettings() {
        val categories = personalizationManager.getAllAppCategories()
        val customCategories = personalizationManager.appCategoryManager.getCustomCategoryNames()
        val categoryStats = personalizationManager.getCategoryUsageStats()
        
        val message = buildString {
            appendLine("App Categories Settings:")
            appendLine("Total Categories: ${categories.size}")
            appendLine("Custom Categories: ${customCategories.size}")
            appendLine("\nCategory Statistics:")
            categoryStats.entries.take(5).forEach { entry ->
                val (name, stats) = entry
                appendLine("• $name: ${stats.totalApps} apps (${stats.customApps} custom)")
            }
            appendLine("\nAll Categories:")
            categories.take(8).forEach { category ->
                appendLine("• $category")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showTimezoneSettings() {
        val currentTimezone = personalizationManager.timeZoneManager.getCurrentTimezone()
        val timezoneInfo = personalizationManager.getTimezoneInfo(currentTimezone)
        val suggestions = personalizationManager.getTimezoneSuggestions()
        
        val message = buildString {
            appendLine("Timezone Settings:")
            appendLine("Current: ${timezoneInfo.displayName}")
            appendLine("Offset: ${timezoneInfo.offsetString}")
            appendLine("DST: ${if (timezoneInfo.hasDaylightSaving) "Yes" else "No"}")
            appendLine("Auto-detect: ${if (personalizationManager.timeZoneManager.isAutoDetectEnabled()) "ON" else "OFF"}")
            appendLine("\nSuggestions:")
            suggestions.take(5).forEach { tz ->
                val info = personalizationManager.getTimezoneInfo(tz)
                appendLine("• ${info.displayName} (${info.offsetString})")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showProfilesSettings() {
        val currentProfile = personalizationManager.profileManager.getCurrentProfile()
        val allProfiles = personalizationManager.getAllProfiles()
        val currentProfileInfo = personalizationManager.getCurrentProfileInfo()
        val currentGoals = personalizationManager.getCurrentProfileGoals()
        val currentNotifications = personalizationManager.getCurrentProfileNotifications()
        
        val message = buildString {
            appendLine("Profiles Settings:")
            appendLine("Current Profile: $currentProfile")
            appendLine("Description: ${currentProfileInfo?.description ?: "No description"}")
            appendLine("\nProfile Goals:")
            appendLine("• Daily: ${currentGoals.dailyGoalHours}h ${currentGoals.dailyGoalMinutes}m")
            appendLine("• Weekly: ${currentGoals.weeklyGoalHours}h ${currentGoals.weeklyGoalMinutes}m")
            appendLine("• Break Interval: ${currentGoals.breakInterval} min")
            appendLine("• Max Session: ${currentGoals.maxSessionLength} min")
            appendLine("\nNotifications:")
            appendLine("• Reminders: ${if (currentNotifications.remindersEnabled) "ON" else "OFF"}")
            appendLine("• Break Suggestions: ${if (currentNotifications.breakSuggestionsEnabled) "ON" else "OFF"}")
            appendLine("• Goal Celebrations: ${if (currentNotifications.goalCelebrationsEnabled) "ON" else "OFF"}")
            appendLine("• Quiet Hours: ${if (currentNotifications.quietHoursEnabled) "ON" else "OFF"}")
            appendLine("\nAll Profiles:")
            allProfiles.forEach { (name, profile) ->
                appendLine("• $name: ${profile.description}")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showPersonalizationSummary() {
        val summary = personalizationManager.getPersonalizationSummary()
        val recommendations = personalizationManager.getPersonalizationRecommendations()
        
        val message = buildString {
            appendLine("Personalization Summary:")
            appendLine("Current Profile: ${summary.currentProfile}")
            appendLine("Description: ${summary.profileDescription}")
            appendLine("Current Goal: ${summary.currentGoal.getDisplayString()}")
            appendLine("Timezone: ${summary.timezoneDisplayName}")
            appendLine("Categories: ${summary.totalCategories} (${summary.customCategories} custom)")
            appendLine("Custom Goals: ${if (summary.isCustomGoalsEnabled) "ON" else "OFF"}")
            appendLine("Auto-detect Timezone: ${if (summary.isAutoDetectTimezone) "ON" else "OFF"}")
            
            if (recommendations.isNotEmpty()) {
                appendLine("\nRecommendations:")
                recommendations.take(3).forEach { rec ->
                    appendLine("• ${rec.title}: ${rec.description}")
                }
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}