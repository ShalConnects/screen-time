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
import android.content.BroadcastReceiver
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.provider.Settings
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
import com.example.screentimeoverlay.BuildConfig
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
    private lateinit var progressInfoTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var reminderTextView: TextView
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
    private var isTrackingPaused = false // Manual pause/resume tracking
    private val pauseHandler = Handler(Looper.getMainLooper())
    
    // Touch passthrough state
    private var touchPassthrough = false
    
    // Smart features
    // Per-App Mode - HIDDEN (backed up in backup/per_app_mode_logic_backup.kt)
    // private var showPerApp = false
    private var dailyGoal = DailyGoal(8, 0) // 8 hours default - will be loaded from preferences
    private var lastNudgeTime = 0L
    private val appPackageManager: PackageManager by lazy { packageManager }
    
    // SharedPreferences for loading saved settings
    private lateinit var preferences: SharedPreferences
    
    // UI state
    private var isExpanded = false
    private var currentDate = ""
    private var currentDisplayMode = DisplayMode.COMPACT
    private var currentPositionMode = PositionMode.AUTO
    private lateinit var smartPositioningManager: SmartPositioningManager
    
    // Auto-hide mode
    private var autoHideEnabled = false
    private var autoHideTimer: Runnable? = null
    private var reminderDismissRunnable: Runnable? = null
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
    
    // Limit portion reminder system
    private lateinit var limitPortionManager: LimitPortionManager
    private lateinit var reminderToneManager: ReminderToneManager
    private val shownReminders = mutableSetOf<Int>() // Track which portions have shown reminders

    // UI accumulator to prevent minute rollback due to UsageStats lag
    private var uiBaseTotalMs: Long = 0L
    private var uiBaseTsMs: Long = 0L
    private var lastUsageStatsTotalMs: Long = 0L
    
    // Track current day for midnight reset detection
    private var currentDayKey: String = ""

    // Minute tick receiver to ensure updates at each minute boundary
    private val minuteTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                updateScreenTime()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        // CRITICAL: Check overlay permission BEFORE doing anything else
        // On MIUI and other devices, missing permission causes SecurityException
        if (!Settings.canDrawOverlays(this)) {
            android.util.Log.e("OverlayService", "Overlay permission not granted, stopping service")
            stopSelf()
            return
        }
        
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Screen time overlay is active"))

        // Initialize SharedPreferences
        preferences = getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
        
        // Load tracking pause state
        isTrackingPaused = preferences.getBoolean("tracking_paused", false)
        
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
        
        // Initialize limit portion reminder system
        limitPortionManager = LimitPortionManager()
        reminderToneManager = ReminderToneManager(this)
        
        // Load saved daily goal and reminder settings AFTER all managers are initialized
        loadSavedSettings()
        
        // Initialize performance monitoring
        performanceOptimizer.initialize()
        adaptiveUpdateManager.initialize()
        memoryManager.initialize()
        
        // Note: Accessibility service was removed - using UsageStatsManager instead

        // Get WindowManager with null check for MIUI compatibility
        windowManager = try {
            getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Failed to get WindowManager", e)
            stopSelf()
            return
        } ?: run {
            android.util.Log.e("OverlayService", "WindowManager is null")
            stopSelf()
            return
        }
        
        overlayView = try {
            createOverlayView()
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Failed to create overlay view", e)
            stopSelf()
            return
        }
        
        // Initialize UI components based on current display mode
        try {
            initializeUIComponents()
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Failed to initialize UI components", e)
            stopSelf()
            return
        }
        
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

        // Add overlay view with proper error handling
        try {
            windowManager.addView(overlayView, params)
        } catch (e: SecurityException) {
            android.util.Log.e("OverlayService", "SecurityException: Overlay permission not granted", e)
            stopSelf()
            return
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Failed to add overlay view", e)
            stopSelf()
            return
        }

        // Apply saved touch passthrough setting after view is created
        val savedTouchPassthrough = preferences.getBoolean("touch_passthrough", false)
        if (savedTouchPassthrough != touchPassthrough) {
            setTouchPassthrough(savedTouchPassthrough)
        }

        // Apply saved opacity setting after view is created
        // Note: overlayView is guaranteed to be initialized here since it's created above
        try {
            if (::overlayView.isInitialized && ::preferences.isInitialized) {
                val savedOpacity = preferences.getInt("overlay_opacity", 80)
                if (savedOpacity != 80) {
                    val alpha = savedOpacity / 100f
                    overlayView.alpha = alpha
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error applying opacity", e)
            // Don't crash - opacity is not critical
        }

        // Initial update to populate real data
        updateScreenTime()
        
        startUpdating()

        // Register minute tick receiver for consistent minute rollovers
        try {
            registerReceiver(minuteTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        } catch (_: Throwable) {}
    }

    private fun startUpdating() {
        // Start the main screen time update loop
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
        
        // Start the seconds counter update loop (updates every second)
        handler.post(object : Runnable {
            override fun run() {
                updateSecondsCounter()
                handler.postDelayed(this, 1000) // Update every second
            }
        })
    }

    private fun updateScreenTime() {
        try {
            // Check manual tracking pause first
            if (isTrackingPaused) {
                return
            }
            
            if (isPaused) {
                val remainingPause = pauseEndTime - System.currentTimeMillis()
                if (remainingPause <= 0) {
                    isPaused = false
                    if (::overlayView.isInitialized) {
                        overlayView.visibility = View.VISIBLE
                        animateIn()
                    }
                } else {
                    return
                }
            }

            // Ensure overlayView is initialized before proceeding
            if (!::overlayView.isInitialized) {
                return
            }

            // Ensure all managers are initialized
            if (!::adaptiveUpdateManager.isInitialized || !::sessionTracker.isInitialized || 
                !::appFilterManager.isInitialized || !::memoryManager.isInitialized) {
                return
            }

            // Record activity for adaptive updates
            adaptiveUpdateManager.recordActivity()
            
            val screenTimeData = getScreenTimeData()
            
            // Update session tracking
            // IMPORTANT: Call updateSession FIRST to accumulate time, then startSession to update current app
            sessionTracker.updateSession()
            val pkg = screenTimeData.currentApp
            if (pkg != null) {
                // Apply same filtering as overlay for session tracking
                if (appFilterManager.shouldTrackApp(pkg)) {
                    val appName = memoryManager.getCachedAppName(pkg)
                    sessionTracker.startSession(pkg, appName)
                } else {
                    // Foreground app is excluded — end any ongoing session
                    sessionTracker.endCurrentSession()
                }
            } else {
                // No foreground app — end any ongoing session
                sessionTracker.endCurrentSession()
            }
            
            // Per-App Mode - HIDDEN (backed up in backup/per_app_mode_logic_backup.kt)
            /*
            val newText = if (showPerApp && screenTimeData.currentApp != null) {
                val currentAppUsage = screenTimeData.topApps.find { it.packageName == screenTimeData.currentApp }
                currentAppUsage?.getFormattedTime() ?: screenTimeData.getFormattedTime()
            } else {
                screenTimeData.getFormattedTime()
            }
            */
            // Check if day changed - reset UI accumulator at midnight
            val calendar = Calendar.getInstance()
            val todayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            if (currentDayKey.isNotEmpty() && currentDayKey != todayKey) {
                // New day detected - reset accumulator
                uiBaseTotalMs = 0L
                uiBaseTsMs = System.currentTimeMillis()
                lastUsageStatsTotalMs = 0L
                currentDayKey = todayKey
            } else if (currentDayKey.isEmpty()) {
                // First run - initialize
                currentDayKey = todayKey
            }
            
            // Prevent UI minute rollback: compute a clamped UI total that never decreases within the same day
            val nowTs = System.currentTimeMillis()
            val usageStatsTotal = screenTimeData.totalTime
            val uiTotalMs: Long
            if (usageStatsTotal > lastUsageStatsTotalMs) {
                // UsageStats has increased - it's current, use it directly
                lastUsageStatsTotalMs = usageStatsTotal
                uiBaseTotalMs = usageStatsTotal
                uiBaseTsMs = nowTs
                uiTotalMs = usageStatsTotal
            } else if (usageStatsTotal == lastUsageStatsTotalMs) {
                // UsageStats hasn't changed - check if we need to add elapsed time (UsageStats might be lagging)
                val elapsedSinceBase = nowTs - uiBaseTsMs
                // Only add time if UsageStats is lagging (more than 30 seconds since last update)
                if (elapsedSinceBase > 30000L) {
                    val uiAdvancedMs = (elapsedSinceBase / 60000L) * 60000L
                    uiTotalMs = kotlin.math.max(usageStatsTotal, uiBaseTotalMs + uiAdvancedMs)
                } else {
                    // UsageStats is current (updated recently), use it directly
                    uiTotalMs = usageStatsTotal
                }
            } else {
                // UsageStats decreased (shouldn't happen, but handle it) - use accumulator to prevent rollback
                val elapsedSinceBase = nowTs - uiBaseTsMs
                val uiAdvancedMs = if (elapsedSinceBase > 0) (elapsedSinceBase / 60000L) * 60000L else 0L
                uiTotalMs = kotlin.math.max(usageStatsTotal, uiBaseTotalMs + uiAdvancedMs)
            }
            val newText = formatDurationFromMs(uiTotalMs)

            // Ensure timeTextView is initialized before accessing it
            if (!::timeTextView.isInitialized) {
                return
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

            
            // Record update for adaptive analysis
            adaptiveUpdateManager.recordUpdate(hasSignificantChange)
            
            // Update progress bar with smart theming
            updateProgressBar(screenTimeData.totalTime)
            
            // Update goal text if needed
            updateGoalText()
            
            // Apply smart theming based on usage status
            applySmartTheming(screenTimeData.totalTime)

            // Check daily goal and show nudge if needed (use uiTotalMs to match displayed value)
            checkDailyGoal(uiTotalMs)
            
            // Check and trigger smart notifications
            checkSmartNotifications(screenTimeData)
            
            // Update performance metrics
            updatePerformanceMetrics()
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error in updateScreenTime", e)
            // Don't crash - just return silently
            return
        }
    }

    private fun formatDurationFromMs(ms: Long): String {
        val totalMinutes = (ms / 60000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) String.format("%dh %02dm", hours, minutes) else String.format("%dm", minutes)
    }

    private fun updateSecondsCounter() {
        // Update seconds counter every second - use current time seconds
        val currentTime = System.currentTimeMillis()
        val secs = TimeUnit.MILLISECONDS.toSeconds(currentTime) % 60
        if (::timeSecondsTextView.isInitialized && currentDisplayMode != DisplayMode.PROGRESS) {
            timeSecondsTextView.text = String.format("%02ds", secs)
        }
        // At the start of a new minute, force one immediate refresh to keep minutes/progress in sync
        if (secs == 0L) {
            updateScreenTime()
            // Also do a UI-only fallback minute bump to avoid UsageStats lag
            incrementDisplayedMinutesIfPossible()
        }
    }

    private fun incrementDisplayedMinutesIfPossible() {
        if (!::timeTextView.isInitialized) return
        val current = timeTextView.text?.toString() ?: return
        // Expect formats like "2h 35m" or "35m" or "2h"
        val regex = Regex("^(?:(\\d+)h\\s*)?(?:(\\d{1,2})m)?$")
        val match = regex.find(current) ?: return
        val hours = match.groups[1]?.value?.toIntOrNull() ?: 0
        val mins = match.groups[2]?.value?.toIntOrNull() ?: 0
        val newMinsTotal = hours * 60 + mins + 1
        val newHours = newMinsTotal / 60
        val newMins = newMinsTotal % 60
        val newText = if (newHours > 0) {
            String.format("%dh %02dm", newHours, newMins)
        } else {
            String.format("%dm", newMins)
        }
        timeTextView.text = newText
    }

    private fun getScreenTimeData(): ScreenTimeData {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        
        // Calculate start time as midnight (00:01 AM) of current day in user's timezone
        // This ensures usage resets daily at 00:01 AM based on user's local timezone
        val calendar = Calendar.getInstance()
        // Ensure we're working with today's date, not yesterday's
        calendar.timeInMillis = endTime
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 1) // 00:01 AM
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        // Ensure startTime is not in the future (shouldn't happen, but safety check)
        val actualStartTime = if (startTime > endTime) {
            // If startTime is in future, we're at midnight - use 00:01 AM of today
            calendar.timeInMillis = endTime
            calendar.add(Calendar.DAY_OF_YEAR, 0) // Ensure we're on the same day
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 1)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } else {
            startTime
        }

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            actualStartTime,
            endTime
        ) ?: return ScreenTimeData(0, emptyList())
        
        // Filter out stats that don't have activity within today's window
        // UsageStatsManager may return stats from previous day that have lastTimeUsed in range
        // but we only want stats that actually have usage within today's window
        val filteredStats = usageStats.filter { stats ->
            stats.lastTimeUsed >= actualStartTime && stats.lastTimeUsed <= endTime
        }
        
        // Debug logging for filter investigation
        if (BuildConfig.DEBUG) {
            android.util.Log.d("ScreenTimeDebug", "Filter Mode: ${appFilterManager.getFilterMode()}")
            android.util.Log.d("ScreenTimeDebug", "Total usage stats returned: ${usageStats.size}")
            android.util.Log.d("ScreenTimeDebug", "Whitelist size: ${appFilterManager.getWhitelist().size}")
            android.util.Log.d("ScreenTimeDebug", "Blacklist size: ${appFilterManager.getBlacklist().size}")
            android.util.Log.d("ScreenTimeDebug", "Excluded categories: ${appFilterManager.getExcludedCategories()}")
        }

        var totalTime = 0L
        val appUsages = mutableListOf<AppUsage>()

        filteredStats.forEach { stats ->
            // Apply app filtering
            if (appFilterManager.shouldTrackApp(stats.packageName)) {
                totalTime += stats.totalTimeInForeground
                if (stats.totalTimeInForeground > 0) {
                    val appName = getAppName(stats.packageName)
                    appUsages.add(AppUsage(stats.packageName, appName, stats.totalTimeInForeground))
                    
                    // Debug logging to investigate discrepancy
                    val hours = stats.totalTimeInForeground / (1000 * 60 * 60)
                    val minutes = (stats.totalTimeInForeground % (1000 * 60 * 60)) / (1000 * 60)
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("ScreenTimeDebug", "App: $appName ($stats.packageName) - Time: ${hours}h ${minutes}m")
                    }
                }
            } else {
                // Log filtered out apps for investigation
                if (stats.totalTimeInForeground > 0) {
                    val appName = getAppName(stats.packageName)
                    val hours = stats.totalTimeInForeground / (1000 * 60 * 60)
                    val minutes = (stats.totalTimeInForeground % (1000 * 60 * 60)) / (1000 * 60)
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("ScreenTimeDebug", "FILTERED OUT: $appName ($stats.packageName) - Time: ${hours}h ${minutes}m")
                    }
                }
            }
        }
        
        // Log total calculated time
        val totalHours = totalTime / (1000 * 60 * 60)
        val totalMinutes = (totalTime % (1000 * 60 * 60)) / (1000 * 60)
        if (BuildConfig.DEBUG) {
            android.util.Log.d("ScreenTimeDebug", "TOTAL CALCULATED TIME: ${totalHours}h ${totalMinutes}m")
            android.util.Log.d("ScreenTimeDebug", "Total apps tracked: ${appUsages.size}")
            android.util.Log.d("ScreenTimeDebug", "Time range: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(actualStartTime))} to ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(endTime))}")
        }

        // Sort by usage time and get top 5
        val topApps = appUsages.sortedByDescending { it.timeInForeground }.take(5)
        
        // Debug logging for top apps investigation
        if (BuildConfig.DEBUG) {
            android.util.Log.d("ScreenTimeDebug", "TOP 10 APPS BY USAGE:")
            appUsages.sortedByDescending { it.timeInForeground }.take(10).forEachIndexed { index, app ->
            val hours = app.timeInForeground / (1000 * 60 * 60)
            val minutes = (app.timeInForeground % (1000 * 60 * 60)) / (1000 * 60)
            android.util.Log.d("ScreenTimeDebug", "${index + 1}. ${app.appName} (${app.packageName}) - ${hours}h ${minutes}m")
            }
        }
        
        // Get current app (prefer accessibility service, fallback to usage stats)
        val currentApp = currentAppFromAccessibility ?: filteredStats.maxByOrNull { it.lastTimeUsed }?.packageName

        return ScreenTimeData(totalTime, topApps, currentApp)
    }

    private fun getAppName(packageName: String): String {
        // Use memory manager for caching
        return memoryManager.getCachedAppName(packageName)
    }

    private fun checkDailyGoal(totalTime: Long) {
        // Check for portion reminders (3 reminders throughout the day) - only if enabled
        if (reminderToneManager.areRemindersEnabled()) {
            val portionToRemind = limitPortionManager.shouldShowReminder(totalTime, dailyGoal, shownReminders)
            if (portionToRemind != null) {
                showPortionReminder(portionToRemind, totalTime)
                shownReminders.add(portionToRemind)
            }
        }
        
        // Original goal exceeded logic
        if (dailyGoal.isExceeded(totalTime)) {
            val currentTime = System.currentTimeMillis()
            // Show nudge only once every 30 minutes
            if (currentTime - lastNudgeTime > java.util.concurrent.TimeUnit.MINUTES.toMillis(30)) {
                showGoalNudge()
                lastNudgeTime = currentTime
            }
            // One-time daily system notification when limit is reached
            val notified = preferences.getBoolean("limit_end_notified_today", false)
            if (!notified) {
                try {
                    notificationManager.showLimitReached()
                    preferences.edit().putBoolean("limit_end_notified_today", true).apply()
                } catch (_: Throwable) {}
            }
        }
    }
    
    private fun showPortionReminder(portionNumber: Int, totalTime: Long) {
        // Prevent overlapping reminders
        if (::reminderTextView.isInitialized && reminderTextView.visibility == View.VISIBLE) {
            return
        }
        
        // Determine contextual portion and progress (use triggered portion for messaging)
        val currentPortion = limitPortionManager.getCurrentPortion(totalTime, dailyGoal)
        val remainingMinutes = limitPortionManager.getRemainingTime(totalTime, dailyGoal)
        val remainingTimeText = limitPortionManager.formatRemainingTime(remainingMinutes)
        
        // Get current tone
        val tone = reminderToneManager.getSelectedTone()
        
        // Get message based on tone and the TRIGGERED portion number to align with 33/66/100
        val message = reminderToneManager.getReminderMessage(tone, portionNumber, remainingTimeText)
        
        // Show message directly in overlay
        showOverlayMessage(message)
    }
    
    private fun showOverlayMessage(message: String) {
        // Skip if reminder TextView is not available (e.g., in progress mode)
        if (!::reminderTextView.isInitialized) return
        
        // Cancel any existing reminder animation
        reminderDismissRunnable?.let { handler.removeCallbacks(it) }
        reminderTextView.clearAnimation()
        
        // Set reminder message
        reminderTextView.text = message
        reminderTextView.visibility = View.VISIBLE
        
        // Enhanced fade in with scale animation
        reminderTextView.alpha = 0f
        reminderTextView.scaleX = 0.8f
        reminderTextView.scaleY = 0.8f
        
        reminderTextView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        
        // Subtle overlay animation to accommodate the reminder
        if (currentDisplayMode == DisplayMode.COMPACT) {
            val compactContainer = overlayView.findViewById<LinearLayout>(R.id.compactContainer)
            compactContainer?.animate()
                ?.scaleX(1.05f)
                ?.scaleY(1.05f)
                ?.setDuration(200)
                ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                ?.start()
        }
        
        // Auto-dismiss after 6 seconds with improved timing
        reminderDismissRunnable = Runnable {
            dismissReminderMessage()
        }
        handler.postDelayed(reminderDismissRunnable!!, 6000)
    }
    
    private fun dismissReminderMessage() {
        if (!::reminderTextView.isInitialized) return
        
        // Enhanced fade out with scale animation
        reminderTextView.animate()
            .alpha(0f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                reminderTextView.visibility = View.GONE
                reminderTextView.scaleX = 1f
                reminderTextView.scaleY = 1f
                
                // Animate overlay back to original size
                animateOverlayToOriginalSize()
            }
            .start()
    }
    
    private fun animateOverlayToOriginalSize() {
        if (!::overlayView.isInitialized) return
        
        // For compact mode, we need to ensure the container returns to its original size
        if (currentDisplayMode == DisplayMode.COMPACT) {
            val compactContainer = overlayView.findViewById<LinearLayout>(R.id.compactContainer)
            compactContainer?.animate()
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(300)
                ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                ?.start()
        } else {
            // For other modes, animate the main overlay
            overlayView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
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
                    
                    if (abs(deltaX) > 5 || abs(deltaY) > 5) {
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
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    private fun setupClickHandling() {
        timeTextView.setOnClickListener {
            if (!isDragging) {
                openMainAppInterface()
            }
        }
        
        // Set up close button click listener only if close button exists
        if (::closeButton.isInitialized) {
            closeButton.setOnClickListener {
                stopSelf()
            }
        }
    }
    
    private fun setupModernInteractions() {
        // Update goal text
        updateGoalText()
    }
    
    private fun openMainAppInterface() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: try to open the app using package name
            try {
                val packageManager = getPackageManager()
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(launchIntent)
                }
            } catch (ex: Exception) {
                // If all else fails, show a toast
                Toast.makeText(this, "Unable to open main interface", Toast.LENGTH_SHORT).show()
            }
        }
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
        
        val newDate = "${monthNames[month]} $day"
        
        // Check if date changed - reset portion reminders for new day
        if (newDate != currentDate) {
            shownReminders.clear()
            // Reset one-time daily notification flag
            preferences.edit().putBoolean("limit_end_notified_today", false).apply()
            
            // Reset UI accumulator variables at midnight
            uiBaseTotalMs = 0L
            uiBaseTsMs = System.currentTimeMillis()
            lastUsageStatsTotalMs = 0L
            val todayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            currentDayKey = todayKey
        }
        
        currentDate = newDate
        if (::dateTextView.isInitialized) {
            dateTextView.text = currentDate
        }
    }
    
    private fun updateGoalText() {
        if (::goalTextView.isInitialized) {
            goalTextView.text = "Goal: ${dailyGoal.maxHours}h ${dailyGoal.maxMinutes}m"
        }
        // For compact mode, goal text is not displayed, so we skip this update
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

        // Update progress info label (e.g., "68% • 1h 12m remaining")
        if (::progressInfoTextView.isInitialized) {
            val usedMinutes = TimeUnit.MILLISECONDS.toMinutes(totalTime).toInt()
            val remaining = (goalMinutes - usedMinutes).coerceAtLeast(0)
            val remainingText = if (remaining <= 0) "Limit reached" else formatRemaining(remaining)
            progressInfoTextView.text = "$progress% • $remainingText"
        }
    }

    private fun formatRemaining(remainingMinutes: Int): String {
        val hours = remainingMinutes / 60
        val mins = remainingMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m remaining"
            hours > 0 -> "${hours}h remaining"
            else -> "${mins}m remaining"
        }
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
                reminderTextView = overlayView.findViewById(R.id.reminderTextCompact)
                // No close button in compact mode
            }
            DisplayMode.PROGRESS -> {
                // Use progress layout elements
                timeTextView = overlayView.findViewById(R.id.timeTextProgress)
                // Progress mode doesn't have seconds display or reminder text
                progressBar = overlayView.findViewById(R.id.progressBarMain)
                goalTextView = overlayView.findViewById(R.id.goalTextProgress)
                progressInfoTextView = overlayView.findViewById(R.id.progressInfoText)
                closeButton = overlayView.findViewById(R.id.closeButtonProgress)
            }
            DisplayMode.DETAILED, DisplayMode.EXPANDED -> {
                titleTextView = overlayView.findViewById(R.id.titleText)
                timeTextView = overlayView.findViewById(R.id.timeText)
                timeSecondsTextView = overlayView.findViewById(R.id.timeSeconds)
                dateTextView = overlayView.findViewById(R.id.dateText)
                goalTextView = overlayView.findViewById(R.id.goalText)
                reminderTextView = overlayView.findViewById(R.id.reminderText)
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
                // Get overlay dimensions, use defaults if not measured yet
                val overlayWidth = if (overlayView.width > 0) overlayView.width else 200
                val overlayHeight = if (overlayView.height > 0) overlayView.height else 100
                val optimalPosition = smartPositioningManager.getOptimalPosition(
                    overlayWidth, overlayHeight
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
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Calculate which side the user dragged to based on current position
        val centerX = screenWidth / 2
        val currentX = params.x
        
        // Determine target position based on where user actually dragged
        val targetX = if (currentX < centerX) {
            // User dragged to left side - snap to left
            20
        } else {
            // User dragged to right side - snap to right
            screenWidth - overlayView.width - 20
        }
        
        // Keep Y position within bounds but respect user's vertical position
        val targetY = params.y.coerceIn(100, screenHeight - overlayView.height - 100)
        
        animateToPosition(targetX, targetY)
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
        if (::overlayView.isInitialized) {
            overlayView.visibility = View.GONE
        }
        isCurrentlyVisible = false
        // Save to preferences
        preferences.edit().putBoolean("auto_hide_enabled", true).apply()
    }
    
    private fun disableAutoHide() {
        autoHideEnabled = false
        autoHideTimer?.let { handler.removeCallbacks(it) }
        if (::overlayView.isInitialized) {
            overlayView.visibility = View.VISIBLE
            overlayView.alpha = 1f
        }
        isCurrentlyVisible = true
        // Save to preferences
        preferences.edit().putBoolean("auto_hide_enabled", false).apply()
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
        
        // Manage floating particles for ambient effect
        when (currentDisplayMode) {
            DisplayMode.DETAILED, DisplayMode.EXPANDED -> {
                if (::mainContainer.isInitialized) {
                    if (smartThemingManager.getCurrentUsageStatus() == SmartThemingManager.UsageStatus.GOOD) {
                        smartThemingManager.addFloatingParticles(mainContainer)
                    } else {
                        smartThemingManager.removeFloatingParticles(mainContainer)
                    }
                }
            }
            DisplayMode.COMPACT -> {
                val compactContainer = overlayView.findViewById<LinearLayout>(R.id.compactContainer)
                if (compactContainer != null) {
                    if (smartThemingManager.getCurrentUsageStatus() == SmartThemingManager.UsageStatus.GOOD) {
                        smartThemingManager.addFloatingParticles(compactContainer)
                    } else {
                        smartThemingManager.removeFloatingParticles(compactContainer)
                    }
                }
            }
            else -> {}
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

        // When exceeded 100%, apply a red accent theme; otherwise ensure any accent is cleared
        if (progress >= 100) {
            when (currentDisplayMode) {
                DisplayMode.DETAILED, DisplayMode.EXPANDED -> {
                    if (::mainContainer.isInitialized) {
                        smartThemingManager.applyExceededAccent(
                            mainContainer,
                            timeTextView,
                            dateTextView,
                            progressBar,
                            goalTextView
                        )
                    }
                }
                DisplayMode.PROGRESS -> {
                    val progressContainer = overlayView.findViewById<LinearLayout>(R.id.progressContainer)
                    if (progressContainer != null) {
                        smartThemingManager.applyExceededAccent(
                            progressContainer,
                            timeTextView,
                            null,
                            progressBar,
                            goalTextView
                        )
                    }
                }
                DisplayMode.COMPACT -> {
                    val compactContainer = overlayView.findViewById<LinearLayout>(R.id.compactContainer)
                    if (compactContainer != null) {
                        smartThemingManager.applyExceededAccent(
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
        } else {
            when (currentDisplayMode) {
                DisplayMode.DETAILED, DisplayMode.EXPANDED -> if (::mainContainer.isInitialized) smartThemingManager.clearExceededAccent(mainContainer)
                DisplayMode.PROGRESS -> overlayView.findViewById<LinearLayout>(R.id.progressContainer)?.let { smartThemingManager.clearExceededAccent(it) }
                DisplayMode.COMPACT -> overlayView.findViewById<LinearLayout>(R.id.compactContainer)?.let { smartThemingManager.clearExceededAccent(it) }
                else -> {}
            }
        }
    }
    
    fun toggleTouchPassthrough() {
        touchPassthrough = !touchPassthrough
        setTouchPassthrough(touchPassthrough)
    }
    
    private fun setTouchPassthrough(enabled: Boolean) {
        touchPassthrough = enabled
        params.flags = if (touchPassthrough) {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        }
        windowManager.updateViewLayout(overlayView, params)
        // Save to preferences
        preferences.edit().putBoolean("touch_passthrough", touchPassthrough).apply()
    }

    // Per-App Mode - HIDDEN (backed up in backup/per_app_mode_logic_backup.kt)
    /*
    fun togglePerAppMode() {
        showPerApp = !showPerApp
        // Force update on next cycle
        updateScreenTime()
    }
    */

    fun setDailyGoal(hours: Int, minutes: Int) {
        dailyGoal = DailyGoal(hours, minutes, true)
        // Save to SharedPreferences (single source of truth - hours/minutes format)
        preferences.edit()
            .putInt("daily_goal_hours", hours)
            .putInt("daily_goal_minutes", minutes)
            .apply()
    }

    fun getDailyGoal(): DailyGoal = dailyGoal
    
    /**
     * Set reminders enabled/disabled
     */
    fun setRemindersEnabled(enabled: Boolean) {
        reminderToneManager.setRemindersEnabled(enabled)
        preferences.edit().putBoolean("reminders_enabled", enabled).apply()
    }
    
    /**
     * Load saved settings from SharedPreferences
     */
    private fun loadSavedSettings() {
        // Load daily goal
        val savedHours = preferences.getInt("daily_goal_hours", 8)
        val savedMinutes = preferences.getInt("daily_goal_minutes", 0)
        dailyGoal = DailyGoal(savedHours, savedMinutes, true)
        
        // Load reminder settings
        val remindersEnabled = preferences.getBoolean("reminders_enabled", false)
        reminderToneManager.setRemindersEnabled(remindersEnabled)
        
        // Load saved position mode
        val savedPositionMode = preferences.getString("position_mode", "AUTO")
        currentPositionMode = when (savedPositionMode) {
            "AUTO" -> PositionMode.AUTO
            "TOP_LEFT" -> PositionMode.TOP_LEFT
            "TOP_RIGHT" -> PositionMode.TOP_RIGHT
            "BOTTOM_LEFT" -> PositionMode.BOTTOM_LEFT
            "BOTTOM_RIGHT" -> PositionMode.BOTTOM_RIGHT
            "CENTER_LEFT" -> PositionMode.CENTER_LEFT
            "CENTER_RIGHT" -> PositionMode.CENTER_RIGHT
            else -> PositionMode.AUTO
        }
        
        // Load touch passthrough setting (will be applied after overlay view is created)
        touchPassthrough = preferences.getBoolean("touch_passthrough", false)
        
        // Load auto hide setting
        val savedAutoHide = preferences.getBoolean("auto_hide_enabled", false)
        if (savedAutoHide) {
            enableAutoHide()
        } else {
            disableAutoHide()
        }
    }

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
        
        // Cleanup reminder system
        reminderDismissRunnable?.let { handler.removeCallbacks(it) }
        reminderDismissRunnable = null
        
        // Cleanup performance managers
        performanceOptimizer.cleanup()
        adaptiveUpdateManager.cleanup()
        memoryManager.cleanup()
        
        // Note: Accessibility receiver was removed
        
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Throwable) {
            }
        }
        try { unregisterReceiver(minuteTickReceiver) } catch (_: Throwable) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            intent?.let { handleIntent(it) }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error handling intent", e)
        }
        return START_STICKY
    }
    
    private fun handleIntent(intent: Intent) {
        try {
        when (intent.getStringExtra("action")) {
            "toggle_touch_passthrough" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setTouchPassthrough(enabled)
            }
            // Per-App Mode - HIDDEN (backed up in backup/per_app_mode_logic_backup.kt)
            /*
            "toggle_per_app_mode" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                if (enabled) {
                    togglePerAppMode()
                }
            }
            */
            "set_daily_goal" -> {
                val hours = intent.getIntExtra("hours", 8)
                val minutes = intent.getIntExtra("minutes", 0)
                setDailyGoal(hours, minutes)
                // Immediately refresh UI to reflect new limit without waiting for next cycle
                updateScreenTime()
            }
            "switch_display_mode" -> {
                val modeName = intent.getStringExtra("mode")
                val mode = when (modeName) {
                    "COMPACT" -> DisplayMode.COMPACT
                    // Progress mode removed: treat as COMPACT
                    "PROGRESS" -> DisplayMode.COMPACT
                    // Detailed mode disabled: fallback to COMPACT
                    "DETAILED" -> DisplayMode.COMPACT
                    "EXPANDED" -> DisplayMode.COMPACT
                    else -> DisplayMode.COMPACT
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
                // Save position mode to preferences
                preferences.edit().putString("position_mode", positionName).apply()
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
            "pause_tracking" -> {
                isTrackingPaused = true
                preferences.edit().putBoolean("tracking_paused", true).apply()
            }
            "resume_tracking" -> {
                isTrackingPaused = false
                preferences.edit().putBoolean("tracking_paused", false).apply()
            }
            "set_reminders_enabled" -> {
                val enabled = intent.getBooleanExtra("enabled", false)
                setRemindersEnabled(enabled)
            }
            "set_overlay_size" -> {
                val size = intent.getStringExtra("size") ?: "medium"
                setOverlaySize(size)
            }
            "set_overlay_opacity" -> {
                val opacity = intent.getIntExtra("opacity", 80)
                setOverlayOpacity(opacity)
            }
        }
        } catch (e: Exception) {
            android.util.Log.e("OverlayService", "Error in handleIntent", e)
        }
    }

    private fun setOverlaySize(size: String) {
        preferences.edit().putString("overlay_size", size).apply()
        // Size changes would require recreating the overlay with new dimensions
        // For now, we'll just save the preference for future use
    }

    private fun setOverlayOpacity(opacity: Int) {
        preferences.edit().putInt("overlay_opacity", opacity).apply()
        if (::overlayView.isInitialized) {
            val alpha = opacity / 100f
            overlayView.alpha = alpha
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

    // Note: Accessibility service receiver removed - using UsageStatsManager for app tracking

    companion object {
        private const val CHANNEL_ID = "screen_time_overlay_channel"
        private const val NOTIFICATION_ID = 101
        private const val UPDATE_INTERVAL_MS = 60_000L
    }
}


