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
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: android.view.View
    private lateinit var timeTextView: TextView
    private lateinit var dateTextView: TextView
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var goalTextView: TextView
    private lateinit var mainContainer: LinearLayout
    private lateinit var expandedContainer: LinearLayout
    private lateinit var topAppsContainer: LinearLayout
    private lateinit var closeButton: ImageButton
    private lateinit var expandButton: ImageButton
    private lateinit var pauseButton: ImageButton
    private lateinit var settingsButton: ImageButton
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
    
    // Advanced features
    private lateinit var appFilterManager: AppFilterManager
    private var currentAppFromAccessibility: String? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Screen time overlay is active"))

        // Initialize advanced features
        appFilterManager = AppFilterManager(this)
        smartPositioningManager = SmartPositioningManager(this)
        
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
                updateScreenTime()
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
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

        val screenTimeData = getScreenTimeData()
        val newText = if (showPerApp && screenTimeData.currentApp != null) {
            val currentAppUsage = screenTimeData.topApps.find { it.packageName == screenTimeData.currentApp }
            currentAppUsage?.getFormattedTime() ?: screenTimeData.getFormattedTime()
        } else {
            screenTimeData.getFormattedTime()
        }

        if (timeTextView.text != newText) {
            animateTextChange(newText)
        }
        
        // Update progress bar
        updateProgressBar(screenTimeData.totalTime)
        
        // Update goal text if needed
        updateGoalText()

        // Check daily goal and show nudge if needed
        checkDailyGoal(screenTimeData.totalTime)
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
        return try {
            val appInfo = appPackageManager.getApplicationInfo(packageName, 0)
            appPackageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
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
        // Expand/Collapse functionality
        expandButton?.setOnClickListener {
            when (currentDisplayMode) {
                DisplayMode.COMPACT -> switchDisplayMode(DisplayMode.PROGRESS)
                DisplayMode.PROGRESS -> switchDisplayMode(DisplayMode.DETAILED)
                DisplayMode.DETAILED -> toggleExpandedView()
                DisplayMode.EXPANDED -> toggleExpandedView()
            }
        }
        
        // Pause button
        pauseButton?.setOnClickListener {
            pauseForMinutes(5)
        }
        
        // Settings button (for future implementation)
        settingsButton?.setOnClickListener {
            // TODO: Open settings or show quick settings menu
            Toast.makeText(this, "Settings coming soon!", Toast.LENGTH_SHORT).show()
        }
        
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
        goalTextView.text = "Goal: ${dailyGoal.maxHours}h ${dailyGoal.maxMinutes}m"
    }
    
    private fun updateProgressBar(totalTime: Long) {
        val goalMinutes = dailyGoal.getTotalMinutes()
        val progress = if (goalMinutes > 0) {
            val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalTime)
            ((totalMinutes.toFloat() / goalMinutes.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        
        progressBar.progress = progress
        
        // Change progress bar color based on goal status
        if (progress >= 100) {
            progressBar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.progress_bar_fill)?.apply {
                setColorFilter(0xFFFF6B6B.toInt(), PorterDuff.Mode.SRC_IN)
            }
        } else if (progress >= 80) {
            progressBar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.progress_bar_fill)?.apply {
                setColorFilter(0xFFFFA500.toInt(), PorterDuff.Mode.SRC_IN)
            }
        } else {
            progressBar.progressDrawable = ContextCompat.getDrawable(this, R.drawable.progress_bar_fill)
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
                timeTextView = overlayView.findViewById(R.id.timeTextCompact)
                closeButton = overlayView.findViewById(R.id.closeButtonCompact)
                expandButton = overlayView.findViewById(R.id.expandButtonCompact)
            }
            DisplayMode.PROGRESS -> {
                timeTextView = overlayView.findViewById(R.id.timeTextProgress)
                progressBar = overlayView.findViewById(R.id.progressBarMain)
                goalTextView = overlayView.findViewById(R.id.goalTextProgress)
                closeButton = overlayView.findViewById(R.id.closeButtonProgress)
                expandButton = overlayView.findViewById(R.id.modeButton)
            }
            DisplayMode.DETAILED, DisplayMode.EXPANDED -> {
                timeTextView = overlayView.findViewById(R.id.timeText)
                dateTextView = overlayView.findViewById(R.id.dateText)
                progressBar = overlayView.findViewById(R.id.progressBar)
                goalTextView = overlayView.findViewById(R.id.goalText)
                mainContainer = overlayView.findViewById(R.id.mainContainer)
                expandedContainer = overlayView.findViewById(R.id.expandedContainer)
                topAppsContainer = overlayView.findViewById(R.id.topAppsContainer)
                closeButton = overlayView.findViewById(R.id.closeButton)
                expandButton = overlayView.findViewById(R.id.expandButton)
                pauseButton = overlayView.findViewById(R.id.pauseButton)
                settingsButton = overlayView.findViewById(R.id.settingsButton)
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        pauseHandler.removeCallbacksAndMessages(null)
        
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
                    "PROGRESS" -> DisplayMode.PROGRESS
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


