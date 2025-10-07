package com.example.screentimeoverlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.graphics.PorterDuff
import kotlin.math.abs
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: android.view.View
    private lateinit var timeTextView: TextView
    private lateinit var timeSecondsTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var goalTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var mainContainer: LinearLayout
    private lateinit var expandedContainer: LinearLayout
    private lateinit var topAppsContainer: LinearLayout
    private lateinit var closeButton: ImageButton
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    
    // Dragging state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    
    // Pause state
    private var isPaused = false
    private var pauseEndTime = 0L
    private val pauseHandler = Handler(Looper.getMainLooper())
    
    // Touch passthrough state
    private var touchPassthrough = false
    
    // Smart features
    private var showPerApp = false
    private var dailyGoal = DailyGoal(8, 0) // 8 hours default
    private var lastNudgeTime = 0L
    private val appPackageManager: PackageManager by lazy { packageManager }
    
    // UI state
    private var isExpanded = false
    private var currentDate = ""
    private var currentDisplayMode = DisplayMode.DETAILED
    private var currentPositionMode = PositionMode.AUTO
    private lateinit var smartPositioningManager: SmartPositioningManager
    
    // Auto-hide mode
    private var autoHideEnabled = false
    private var autoHideTimer: Runnable? = null
    private var lastScreenTimeText = ""
    private var isCurrentlyVisible = true
    private val autoHideDelay = 3000L // 3 seconds
    
    // Advanced features
    private lateinit var appFilterManager: AppFilterManager
    private var currentAppFromAccessibility: String? = null
    
    // New advanced features
    private lateinit var sessionTracker: SessionTracker
    private lateinit var productivityScorer: ProductivityScorer
    private lateinit var usagePatternAnalyzer: UsagePatternAnalyzer
    private lateinit var historicalDataManager: HistoricalDataManager
    
    // Smart notification system
    private lateinit var notificationManager: com.example.screentimeoverlay.NotificationManager
    private lateinit var notificationScheduler: SmartNotificationScheduler
    private lateinit var notificationSettings: NotificationSettings
    
    // Performance optimization managers
    private lateinit var performanceOptimizer: PerformanceOptimizer
    private lateinit var adaptiveUpdateManager: AdaptiveUpdateManager
    private lateinit var memoryManager: MemoryManager
    
    // Smart theming and visual enhancements
    private lateinit var smartThemingManager: SmartThemingManager

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Screen time overlay is active"))

        // Initialize advanced features
        appFilterManager = AppFilterManager(this)
        smartPositioningManager = SmartPositioningManager(this)
        
        // Initialize new advanced features
        sessionTracker = SessionTracker(this)
        productivityScorer = ProductivityScorer(this)
        usagePatternAnalyzer = UsagePatternAnalyzer(this)
        historicalDataManager = HistoricalDataManager(this)
        
        // Initialize smart notification system
        notificationManager = com.example.screentimeoverlay.NotificationManager(this)
        notificationScheduler = SmartNotificationScheduler(this)
        notificationSettings = NotificationSettings(this)
        
        // Initialize performance optimization managers
        performanceOptimizer = PerformanceOptimizer(this)
        adaptiveUpdateManager = AdaptiveUpdateManager(this)
        memoryManager = MemoryManager(this)
        
        // Initialize smart theming manager
        smartThemingManager = SmartThemingManager(this)
        
        // Initialize performance monitoring
        performanceOptimizer.initialize()
        adaptiveUpdateManager.initialize()
        memoryManager.initialize()
        
        // Register for accessibility service updates
        val filter = IntentFilter(ScreenTimeAccessibilityService.ACTION_APP_CHANGED)
        registerReceiver(accessibilityReceiver, filter)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = createOverlayView()
        
        // Initialize UI components based on current display mode
        initializeUIComponents()
        
        // Set initial date
        updateDate()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20
        params.y = 100

        setupTouchHandling()
        setupClickHandling()
        setupModernInteractions()

        try {
            windowManager.addView(overlayView, params)
        } catch (_: Throwable) {
            stopSelf()
            return
        }

        startUpdating()
    }

    private fun startUpdating() {
        handler.post(object : Runnable {
            override fun run() {
                // Check if we should skip this update for performance
                if (performanceOptimizer.shouldSkipUpdate() || adaptiveUpdateManager.shouldSkipUpdate()) {
                    // Skip this update but continue the cycle
                    handler.postDelayed(this, adaptiveUpdateManager.getOptimalUpdateInterval())
                    return
                }
                
                updateScreenTime()
                
                // Use adaptive update interval
                val optimalInterval = adaptiveUpdateManager.getOptimalUpdateInterval()
                handler.postDelayed(this, optimalInterval)
            }
        })
    }

    private fun updateScreenTime() {
        if (isPaused) {
            val remainingPause = pauseEndTime - System.currentTimeMillis()
            if (remainingPause <= 0) {
                isPaused = false
                overlayView.visibility = View.VISIBLE
                animateIn()
            } else {
                return
            }
        }

        // Record activity for adaptive updates
        adaptiveUpdateManager.recordActivity()
        
        val screenTimeData = getScreenTimeData()
        
        // Update session tracking
        if (screenTimeData.currentApp != null) {
            val appName = memoryManager.getCachedAppName(screenTimeData.currentApp)
            sessionTracker.startSession(screenTimeData.currentApp, appName)
        }
        sessionTracker.updateSession()
        
        val newText = if (showPerApp && screenTimeData.currentApp != null) {
            val currentAppUsage = screenTimeData.topApps.find { it.packageName == screenTimeData.currentApp }
            currentAppUsage?.getFormattedTime() ?: screenTimeData.getFormattedTime()
        } else {
            screenTimeData.getFormattedTime()
        }

        val hasSignificantChange = timeTextView.text != newText
        if (hasSignificantChange) {
            animateTextChange(newText)
            
            // Auto-hide logic: show overlay when screen time changes
            if (autoHideEnabled && newText != lastScreenTimeText) {
                showOverlayTemporarily()
                lastScreenTimeText = newText
            }
        }

        // Update seconds counter every tick - use current time seconds
        val currentTime = System.currentTimeMillis()
        val secs = TimeUnit.MILLISECONDS.toSeconds(currentTime) % 60
        if (::timeSecondsTextView.isInitialized) {
            timeSecondsTextView.text = String.format("%02ds", secs)
        }
        
        // Record update for adaptive analysis
        adaptiveUpdateManager.recordUpdate(hasSignificantChange)
        
        // Update progress bar with smart theming
        updateProgressBar(screenTimeData.totalTime)
        
        // Update goal text if needed
        updateGoalText()
        
        // Apply smart theming based on usage status
        applySmartTheming(screenTimeData.totalTime)

        // Check daily goal and show nudge if needed
        checkDailyGoal(screenTimeData.totalTime)
        
        // Check and trigger smart notifications
        checkSmartNotifications(screenTimeData)
        
        // Update performance metrics
        updatePerformanceMetrics()
    }

    private fun getScreenTimeData(): ScreenTimeData {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(1)

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return ScreenTimeData(0, emptyList())

        var totalTime = 0L
        val appUsages = mutableListOf<AppUsage>()

        usageStats.forEach { stats ->
            // Apply app filtering
            if (appFilterManager.shouldTrackApp(stats.packageName)) {
                totalTime += stats.totalTimeInForeground
                if (stats.totalTimeInForeground > 0) {
                    val appName = getAppName(stats.packageName)
                    appUsages.add(AppUsage(stats.packageName, appName, stats.totalTimeInForeground))
                }
            }
        }

        // Sort by usage time and get top 5
        val topApps = appUsages.sortedByDescending { it.timeInForeground }.take(5)
        
        // Get current app (prefer accessibility service, fallback to usage stats)
        val currentApp = currentAppFromAccessibility ?: usageStats.maxByOrNull { it.lastTimeUsed }?.packageName

        return ScreenTimeData(totalTime, topApps, currentApp)
    }

    private fun getAppName(packageName: String): String {
        // Use memory manager for caching
        return memoryManager.getCachedAppName(packageName)
    }

    private fun checkDailyGoal(totalTime: Long) {
        if (dailyGoal.isExceeded(totalTime)) {
            val currentTime = System.currentTimeMillis()
            // Show nudge only once every 30 minutes
            if (currentTime - lastNudgeTime > TimeUnit.MINUTES.toMillis(30)) {
                showGoalNudge()
                lastNudgeTime = currentTime
            }
        }
    }

    private fun showGoalNudge() {
        // Animate the overlay to draw attention
        overlayView.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(200)
            .withEndAction {
                overlayView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }
    
    private fun checkSmartNotifications(screenTimeData: ScreenTimeData) {
        // Skip notifications if in quiet hours
        if (notificationSettings.isInQuietHours()) return
        
        // Skip notifications if disabled
        if (!notificationSettings.isNotificationsEnabled()) return
        
        // Get current session stats
        val sessionStats = sessionTracker.getSessionStats(Date())
        val currentSession = sessionTracker.getCurrentSession()
        
        // Check contextual reminders
        notificationScheduler.checkContextualReminders(screenTimeData, sessionStats)
        
        // Check break suggestions
        notificationScheduler.checkBreakSuggestions(screenTimeData, sessionStats, currentSession)
        
        // Check goal celebrations
        notificationScheduler.checkGoalCelebrations(screenTimeData, sessionStats, dailyGoal)
        
        // Check custom alerts
        val customTriggers = notificationSettings.getCustomTriggers()
        notificationScheduler.checkCustomAlerts(screenTimeData, sessionStats, customTriggers)
    }

    private fun setupTouchHandling() {
        overlayView.setOnTouchListener { _, event ->
            if (touchPassthrough) return@setOnTouchListener false
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isDragging = true
                        params.x = initialX + deltaX
                        params.y = initialY + deltaY
                        windowManager.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        snapToEdge()
                    }
                    false
                }
                else -> false
            }
        }
    }
    
    private fun setupClickHandling() {
        timeTextView.setOnClickListener {
            if (!isDragging) {
                pauseForMinutes(5)
            }
        }
        
        closeButton.setOnClickListener {
            stopSelf()
        }
    }
    
    private fun setupModernInteractions() {
        // Update goal text
        updateGoalText()
    }
    
    private fun toggleExpandedView() {
        isExpanded = !isExpanded
        
        if (isExpanded) {
            mainContainer.visibility = View.GONE
            expandedContainer.visibility = View.VISIBLE
            updateTopAppsDisplay()
            animateExpansion()
        } else {
            expandedContainer.visibility = View.GONE
            mainContainer.visibility = View.VISIBLE
            animateCollapse()
        }
    }
    
    private fun animateExpansion() {
        expandedContainer.alpha = 0f
        expandedContainer.scaleX = 0.8f
        expandedContainer.scaleY = 0.8f
        
        expandedContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()
    }
    
    private fun animateCollapse() {
        mainContainer.alpha = 0f
        mainContainer.scaleX = 0.8f
        mainContainer.scaleY = 0.8f
        
        mainContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()
    }
    
    private fun updateDate() {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        
        currentDate = "${monthNames[month]} $day"
        dateTextView.text = currentDate
    }
    
    private fun updateGoalText() {
        if (::goalTextView.isInitialized) {
            goalTextView.text = "Goal: ${dailyGoal.maxHours}h ${dailyGoal.maxMinutes}m"
        }
    }
    
    private fun updateProgressBar(totalTime: Long) {
        // Only update progress bar for PROGRESS mode
        if (currentDisplayMode != DisplayMode.PROGRESS) {
            return
        }
        
        val goalMinutes = dailyGoal.getTotalMinutes()
        val progress = if (goalMinutes > 0) {
            val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalTime)
            ((totalMinutes.toFloat() / goalMinutes.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        
        // Animate progress bar with smooth transition
        animateProgressBar(progress)
        
        // Update smart theming based on progress
        smartThemingManager.updateUsageStatus(progress, goalMinutes, TimeUnit.MILLISECONDS.toMinutes(totalTime).toInt())
    }
    
    private fun animateProgressBar(targetProgress: Int) {
        if (::progressBar.isInitialized) {
            val currentProgress = progressBar.progress
            val animator = ValueAnimator.ofInt(currentProgress, targetProgress)
            animator.duration = 500
            animator.addUpdateListener { animation ->
                progressBar.progress = animation.animatedValue as Int
            }
            animator.start()
        }
    }
    
    private fun updateTopAppsDisplay() {
        topAppsContainer.removeAllViews()
        
        val screenTimeData = getScreenTimeData()
        val topApps = screenTimeData.topApps.take(3) // Show top 3 apps
        
        topApps.forEach { appUsage ->
            val appView = createAppUsageView(appUsage)
            topAppsContainer.addView(appView)
        }
    }
    
    private fun createAppUsageView(appUsage: AppUsage): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }
        
        val appNameText = TextView(this).apply {
            text = appUsage.appName
            textSize = 12f
            setTextColor(0xFFE0E0E0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        
        val timeText = TextView(this).apply {
            text = appUsage.getFormattedTime()
            textSize = 11f
            setTextColor(0xFFB0B0B0.toInt())
            gravity = android.view.Gravity.END
        }
        
        layout.addView(appNameText)
        layout.addView(timeText)
        
        return layout
    }
    
    private fun createOverlayView(): View {
        return when (currentDisplayMode) {
            DisplayMode.COMPACT -> LayoutInflater.from(this).inflate(R.layout.overlay_layout_compact, null)
            DisplayMode.PROGRESS -> LayoutInflater.from(this).inflate(R.layout.overlay_layout_progress, null)
            DisplayMode.DETAILED, DisplayMode.EXPANDED -> LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        }
    }
    
    private fun initializeUIComponents() {
        when (currentDisplayMode) {
            DisplayMode.COMPACT -> {
                titleTextView = overlayView.findViewById(R.id.titleTextCompact)
                timeTextView = overlayView.findViewById(R.id.timeTextCompact)
                timeSecondsTextView = overlayView.findViewById(R.id.timeSecondsCompact)
                closeButton = overlayView.findViewById(R.id.closeButtonCompact)
            }
            DisplayMode.PROGRESS -> {
                // Use progress layout elements
                timeTextView = overlayView.findViewById(R.id.timeTextProgress)
                progressBar = overlayView.findViewById(R.id.progressBarMain)
                goalTextView = overlayView.findViewById(R.id.goalTextProgress)
                closeButton = overlayView.findViewById(R.id.closeButtonProgress)
            }
            DisplayMode.DETAILED, DisplayMode.EXPANDED -> {
                titleTextView = overlayView.findViewById(R.id.titleText)
                timeTextView = overlayView.findViewById(R.id.timeText)
                timeSecondsTextView = overlayView.findViewById(R.id.timeSeconds)
                dateTextView = overlayView.findViewById(R.id.dateText)
                goalTextView = overlayView.findViewById(R.id.goalText)
                // Progress bar exists in detailed layout (hidden by default)
                progressBar = overlayView.findViewById(R.id.progressBar)
                mainContainer = overlayView.findViewById(R.id.mainContainer)
                expandedContainer = overlayView.findViewById(R.id.expandedContainer)
                topAppsContainer = overlayView.findViewById(R.id.topAppsContainer)
                closeButton = overlayView.findViewById(R.id.closeButton)
            }
        }
    }
    
    private fun switchDisplayMode(newMode: DisplayMode) {
        if (currentDisplayMode == newMode) return
        
        // Remove current overlay
        try {
            windowManager.removeView(overlayView)
        } catch (_: Throwable) {}
        
        // Create new overlay with new mode
        currentDisplayMode = newMode
        overlayView = createOverlayView()
        initializeUIComponents()
        
        // Update positioning
        updatePositioning()
        
        // Add new overlay
        try {
            windowManager.addView(overlayView, params)
        } catch (_: Throwable) {
            stopSelf()
            return
        }
        
        // Setup interactions for new mode
        setupTouchHandling()
        setupClickHandling()
        setupModernInteractions()
        
        // Start updating
        startUpdating()
    }
    
    private fun updatePositioning() {
        when (currentPositionMode) {
            PositionMode.AUTO -> {
                val optimalPosition = smartPositioningManager.getOptimalPosition(
                    overlayView.width, overlayView.height
                )
                params.x = optimalPosition.x
                params.y = optimalPosition.y
                params.gravity = optimalPosition.gravity
            }
            PositionMode.TOP_LEFT -> {
                params.gravity = Gravity.TOP or Gravity.START
                params.x = 20
                params.y = 100
            }
            PositionMode.TOP_RIGHT -> {
                params.gravity = Gravity.TOP or Gravity.END
                params.x = 20
                params.y = 100
            }
            PositionMode.BOTTOM_LEFT -> {
                params.gravity = Gravity.BOTTOM or Gravity.START
                params.x = 20
                params.y = 100
            }
            PositionMode.BOTTOM_RIGHT -> {
                params.gravity = Gravity.BOTTOM or Gravity.END
                params.x = 20
                params.y = 100
            }
            PositionMode.CENTER_LEFT -> {
                params.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                params.x = 20
                params.y = 0
            }
            PositionMode.CENTER_RIGHT -> {
                params.gravity = Gravity.CENTER_VERTICAL or Gravity.END
                params.x = 20
                params.y = 0
            }
        }
        
        try {
            windowManager.updateViewLayout(overlayView, params)
        } catch (_: Throwable) {}
    }
    
    private fun snapToEdge() {
        if (currentPositionMode == PositionMode.AUTO) {
            // Use smart positioning
            val optimalPosition = smartPositioningManager.getSnapPosition(
                params.x, params.y, overlayView.width, overlayView.height
            )
            params.x = optimalPosition.x
            params.y = optimalPosition.y
            params.gravity = optimalPosition.gravity
            windowManager.updateViewLayout(overlayView, params)
        } else {
            // Use traditional edge snapping
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            val centerX = screenWidth / 2
            val centerY = screenHeight / 2
            
            val targetX = if (params.x < centerX) 20 else screenWidth - overlayView.width - 20
            val targetY = params.y.coerceIn(100, screenHeight - overlayView.height - 100)
            
            animateToPosition(targetX, targetY)
        }
    }
    
    private fun animateToPosition(targetX: Int, targetY: Int) {
        val animatorX = ValueAnimator.ofInt(params.x, targetX)
        val animatorY = ValueAnimator.ofInt(params.y, targetY)
        
        animatorX.addUpdateListener { valueAnimator ->
            params.x = valueAnimator.animatedValue as Int
            windowManager.updateViewLayout(overlayView, params)
        }
        
        animatorY.addUpdateListener { valueAnimator ->
            params.y = valueAnimator.animatedValue as Int
            windowManager.updateViewLayout(overlayView, params)
        }
        
        animatorX.duration = 200
        animatorY.duration = 200
        
        animatorX.start()
        animatorY.start()
    }
    
    private fun pauseForMinutes(minutes: Int) {
        isPaused = true
        pauseEndTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())
        overlayView.visibility = View.GONE
        
        pauseHandler.postDelayed({
            if (isPaused) {
                isPaused = false
                overlayView.visibility = View.VISIBLE
                animateIn()
            }
        }, TimeUnit.MINUTES.toMillis(minutes.toLong()))
    }
    
    private fun animateIn() {
        overlayView.alpha = 0f
        overlayView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    private fun showOverlayTemporarily() {
        if (!isCurrentlyVisible) {
            overlayView.visibility = View.VISIBLE
            overlayView.alpha = 0f
            overlayView.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
            isCurrentlyVisible = true
        }
        
        // Cancel existing timer
        autoHideTimer?.let { handler.removeCallbacks(it) }
        
        // Start new timer
        autoHideTimer = Runnable {
            if (autoHideEnabled && isCurrentlyVisible) {
                overlayView.animate()
                    .alpha(0f)
                    .setDuration(500)
                    .withEndAction {
                        overlayView.visibility = View.GONE
                        isCurrentlyVisible = false
                    }
                    .start()
            }
        }
        
        handler.postDelayed(autoHideTimer!!, autoHideDelay)
    }
    
    private fun enableAutoHide() {
        autoHideEnabled = true
        // Start with overlay hidden
        overlayView.visibility = View.GONE
        isCurrentlyVisible = false
    }
    
    private fun disableAutoHide() {
        autoHideEnabled = false
        autoHideTimer?.let { handler.removeCallbacks(it) }
        overlayView.visibility = View.VISIBLE
        overlayView.alpha = 1f
        isCurrentlyVisible = true
    }
    
    private fun animateTextChange(newText: String) {
        timeTextView.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(100)
            .withEndAction {
                timeTextView.text = newText
                timeTextView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }
    
    private fun applySmartTheming(totalTime: Long) {
        // Analyze background and apply appropriate theme
        val optimalTheme = smartThemingManager.analyzeBackground(windowManager, overlayView)
        smartThemingManager.setThemeMode(optimalTheme)
        
        // Apply theme to all components
        when (currentDisplayMode) {
            DisplayMode.DETAILED, DisplayMode.EXPANDED -> {
                smartThemingManager.applyTheme(
                    mainContainer,
                    timeTextView,
                    dateTextView,
                    progressBar,
                    goalTextView
                )
            }
            DisplayMode.PROGRESS -> {
                // Apply to progress mode components
                val progressContainer = overlayView.findViewById<LinearLayout>(R.id.progressContainer)
                if (progressContainer != null) {
                    smartThemingManager.applyTheme(
                        progressContainer,
                        timeTextView,
                        null,
                        progressBar,
                        goalTextView
                    )
                }
            }
            DisplayMode.COMPACT -> {
                // Apply to compact mode components (no progress bar in compact layout)
                val compactContainer = overlayView.findViewById<LinearLayout>(R.id.compactContainer)
                if (compactContainer != null) {
                    smartThemingManager.applyTheme(
                        compactContainer,
                        timeTextView,
                        null,
                        null,
                        null
                    )
                }
            }
            else -> {}
        }
        
        // Add floating particles for ambient effect
        if (smartThemingManager.getCurrentUsageStatus() == SmartThemingManager.UsageStatus.GOOD) {
            smartThemingManager.addFloatingParticles(mainContainer)
        }
        
        // Add pulse effect when approaching goals
        val goalMinutes = dailyGoal.getTotalMinutes()
        val currentMinutes = TimeUnit.MILLISECONDS.toMinutes(totalTime)
        val progress = if (goalMinutes > 0) {
            ((currentMinutes.toFloat() / goalMinutes.toFloat()) * 100).toInt()
        } else 0
        
        if (progress >= 80) {
            smartThemingManager.addPulseEffect(overlayView)
        }
    }
    
    fun toggleTouchPassthrough() {
        touchPassthrough = !touchPassthrough
        params.flags = if (touchPassthrough) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        windowManager.updateViewLayout(overlayView, params)
    }

    fun togglePerAppMode() {
        showPerApp = !showPerApp
        // Force update on next cycle
        updateScreenTime()
    }

    fun setDailyGoal(hours: Int, minutes: Int) {
        dailyGoal = DailyGoal(hours, minutes, true)
    }

    fun getDailyGoal(): DailyGoal = dailyGoal

    fun getCurrentScreenTimeData(): ScreenTimeData = getScreenTimeData()
    
    // New advanced feature methods
    
    /**
     * Get current session information
     */
    fun getCurrentSession(): AppSession? = sessionTracker.getCurrentSession()
    
    /**
     * Get session statistics for today
     */
    fun getTodaySessionStats(): SessionStats {
        val today = Date()
        return sessionTracker.getSessionStats(today)
    }
    
    /**
     * Get productivity score for current usage
     */
    fun getCurrentProductivityScore(): ProductivityScore {
        val screenTimeData = getScreenTimeData()
        return productivityScorer.calculateDailyProductivityScore(screenTimeData.topApps)
    }
    
    /**
     * Get usage pattern analysis for the last week
     */
    fun getWeeklyPatternAnalysis(): UsagePatternAnalysis {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekStart = calendar.time
        val weekEnd = Date()
        
        return usagePatternAnalyzer.analyzeUsagePatterns(DateRange(weekStart, weekEnd))
    }
    
    /**
     * Get optimal break time recommendations
     */
    fun getBreakTimeRecommendations(): List<BreakTimeRecommendation> {
        return usagePatternAnalyzer.getOptimalBreakTimes()
    }
    
    /**
     * Get weekly summary
     */
    fun getWeeklySummary(): WeeklySummary {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        return historicalDataManager.getWeeklySummary(calendar.time)
    }
    
    /**
     * Get monthly summary
     */
    fun getMonthlySummary(): MonthlySummary {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        return historicalDataManager.getMonthlySummary(calendar.time)
    }
    
    /**
     * Get chart data for visualization
     */
    fun getChartData(chartType: ChartType): ChartData {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30) // Last 30 days
        val startDate = calendar.time
        val endDate = Date()
        
        return historicalDataManager.getChartData(DateRange(startDate, endDate), chartType)
    }
    
    /**
     * Set productivity score for an app
     */
    fun setAppProductivityScore(packageName: String, score: Int) {
        productivityScorer.setProductivityScore(packageName, score)
    }
    
    /**
     * Get productivity insights
     */
    fun getProductivityInsights(): ProductivityInsights {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekStart = calendar.time
        val weekEnd = Date()
        
        val sessions = sessionTracker.getSessionsForDateRange(weekStart, weekEnd)
        val appUsages = sessions.map { session ->
            AppUsage(session.packageName, session.appName, session.totalTime)
        }
        
        return productivityScorer.getProductivityInsights(DateRange(weekStart, weekEnd), appUsages)
    }

    /**
     * Update performance metrics
     */
    private fun updatePerformanceMetrics() {
        // Update battery status for adaptive updates
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val batteryLevel = batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        
        adaptiveUpdateManager.updateBatteryStatus(batteryLevel, isCharging)
        
        // Check if we need to force memory cleanup
        if (memoryManager.isHighMemoryUsage()) {
            memoryManager.forceCleanup()
        }
    }
    
    /**
     * Get current performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        return performanceOptimizer.getPerformanceMetrics()
    }
    
    /**
     * Get adaptive state information
     */
    fun getAdaptiveState(): AdaptiveState {
        return adaptiveUpdateManager.getCurrentState()
    }
    
    /**
     * Get memory statistics
     */
    fun getMemoryStats(): MemoryStats {
        return memoryManager.getMemoryStats()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        pauseHandler.removeCallbacksAndMessages(null)
        
        // Cleanup performance managers
        performanceOptimizer.cleanup()
        adaptiveUpdateManager.cleanup()
        memoryManager.cleanup()
        
        // Unregister accessibility receiver
        try {
            unregisterReceiver(accessibilityReceiver)
        } catch (_: Throwable) {
        }
        
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Throwable) {
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let { handleIntent(it) }
        return START_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        when (intent.getStringExtra("action")) {
            "toggle_touch_passthrough" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                if (enabled) {
                    toggleTouchPassthrough()
                }
            }
            "toggle_per_app_mode" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                if (enabled) {
                    togglePerAppMode()
                }
            }
            "set_daily_goal" -> {
                val hours = intent.getIntExtra("hours", 8)
                val minutes = intent.getIntExtra("minutes", 0)
                setDailyGoal(hours, minutes)
            }
            "switch_display_mode" -> {
                val modeName = intent.getStringExtra("mode")
                val mode = when (modeName) {
                    "COMPACT" -> DisplayMode.COMPACT
                    // Progress mode removed: treat as DETAILED
                    "PROGRESS" -> DisplayMode.DETAILED
                    "DETAILED" -> DisplayMode.DETAILED
                    "EXPANDED" -> DisplayMode.EXPANDED
                    else -> DisplayMode.DETAILED
                }
                switchDisplayMode(mode)
            }
            "set_position_mode" -> {
                val positionName = intent.getStringExtra("position")
                val position = when (positionName) {
                    "AUTO" -> PositionMode.AUTO
                    "TOP_LEFT" -> PositionMode.TOP_LEFT
                    "TOP_RIGHT" -> PositionMode.TOP_RIGHT
                    "BOTTOM_LEFT" -> PositionMode.BOTTOM_LEFT
                    "BOTTOM_RIGHT" -> PositionMode.BOTTOM_RIGHT
                    "CENTER_LEFT" -> PositionMode.CENTER_LEFT
                    "CENTER_RIGHT" -> PositionMode.CENTER_RIGHT
                    else -> PositionMode.AUTO
                }
                currentPositionMode = position
                updatePositioning()
            }
            "toggle_auto_hide" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                if (enabled) {
                    enableAutoHide()
                } else {
                    disableAutoHide()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Time Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Time")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }

    // Accessibility service receiver
    private val accessibilityReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ScreenTimeAccessibilityService.ACTION_APP_CHANGED) {
                val packageName = intent.getStringExtra(ScreenTimeAccessibilityService.EXTRA_PACKAGE_NAME)
                currentAppFromAccessibility = packageName
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "screen_time_overlay_channel"
        private const val NOTIFICATION_ID = 101
        private const val UPDATE_INTERVAL_MS = 60_000L
    }
}


