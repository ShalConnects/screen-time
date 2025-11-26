package com.example.screentimeoverlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.RotateAnimation
import android.widget.Button
import android.os.Handler
import android.os.Looper
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.Date

class MonitorFragment : Fragment(R.layout.fragment_monitor) {
    
    private lateinit var toggleOverlayButton: Button
    private lateinit var mainActivity: MainActivity
    
    // Modern Goal Controls - Class level properties
    private lateinit var goalHoursSlider: SeekBar
    private lateinit var goalMinutesSlider: SeekBar
    private lateinit var goalHoursValue: TextView
    private lateinit var goalMinutesValue: TextView
    private lateinit var goalStatusText: TextView
    
    // Reminder settings
    private lateinit var reminderToneManager: ReminderToneManager
    private lateinit var reminderSettingsContainer: LinearLayout
    private lateinit var enableRemindersSwitch: Switch
    private lateinit var currentToneText: TextView
    private lateinit var reminderDescriptionText: TextView

    // App filtering (same as overlay)
    private lateinit var appFilterManager: AppFilterManager

    // UI accumulator to prevent minute rollback and sync with overlay (same logic as OverlayService)
    private var uiBaseTotalMs: Long = 0L
    private var uiBaseTsMs: Long = 0L
    private var lastUsageStatsTotalMs: Long = 0L
    private var currentDayKey: String = ""

    // Minute-based updater for the Daily Limit usage text
    private val usageBadgeHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var usageBadgeRunnable: Runnable? = null
    private val minuteTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == Intent.ACTION_TIME_TICK) {
                updateDailyLimitUsageText()
            }
        }
    }

    // Current Session Tracker
    private var currentSessionCard: LinearLayout? = null
    private var currentAppName: TextView? = null
    private var currentSessionTime: TextView? = null
    private var currentAppCategory: TextView? = null
    private val sessionUpdateHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var sessionUpdateRunnable: Runnable? = null
    private val SESSION_UPDATE_INTERVAL_MS = 1000L // Update every second

    // Break Reminders
    private var breakTimerContainer: LinearLayout? = null
    private var breakTimerText: TextView? = null
    private var breakHistoryText: TextView? = null
    private var isBreakActive = false
    private var breakEndTime = 0L
    private val breakUpdateHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var breakUpdateRunnable: Runnable? = null

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toggleOverlayButton = view.findViewById<Button>(R.id.toggleOverlayButton)
        val touchPassthroughSwitch = view.findViewById<Switch>(R.id.touchPassthroughSwitch)
        // Per-App Mode - HIDDEN (backed up in backup/per_app_mode_fragment_backup.kt)
        // val perAppSwitch = view.findViewById<Switch>(R.id.perAppSwitch)
        val autoHideSwitch = view.findViewById<Switch>(R.id.autoHideSwitch)
        // Modern Goal Controls
        goalHoursSlider = view.findViewById<SeekBar>(R.id.goalHoursSlider)
        goalMinutesSlider = view.findViewById<SeekBar>(R.id.goalMinutesSlider)
        goalHoursValue = view.findViewById<TextView>(R.id.goalHoursValue)
        goalMinutesValue = view.findViewById<TextView>(R.id.goalMinutesValue)
        goalStatusText = view.findViewById<TextView>(R.id.goalStatusText)
        
        // Quick Preset Buttons
        val preset4hButton = view.findViewById<Button>(R.id.preset4hButton)
        val preset6hButton = view.findViewById<Button>(R.id.preset6hButton)
        val preset8hButton = view.findViewById<Button>(R.id.preset8hButton)
        val preset10hButton = view.findViewById<Button>(R.id.preset10hButton)
        
        val autoPositionButton = view.findViewById<Button>(R.id.autoPositionButton)
        val topRightPositionButton = view.findViewById<Button>(R.id.topRightPositionButton)
        val bottomRightPositionButton = view.findViewById<Button>(R.id.bottomRightPositionButton)

        // Get reference to MainActivity to access its methods
        mainActivity = requireActivity() as MainActivity
        
        // Initialize app filter manager (same as overlay)
        appFilterManager = AppFilterManager(requireContext())
        
        // Initialize reminder settings
        initializeReminderSettings(view)
        
        // Load saved daily goal settings
        loadSavedDailyGoal()
        updateDailyLimitUsageText()
        
        // Load saved switch states
        loadSavedSwitchStates(touchPassthroughSwitch, autoHideSwitch)

        // Update button state based on current overlay status
        updateToggleButtonState(toggleOverlayButton, mainActivity)
        
        toggleOverlayButton.setOnClickListener {
            // Add press animation
            animateButtonPress(toggleOverlayButton) {
                if (isOverlayServiceRunning()) {
                    // Overlay is running, so stop it
                    mainActivity.stopOverlayService()
                    updateToggleButtonState(toggleOverlayButton, mainActivity)
                } else {
                    // Overlay is not running, so start it
                    if (mainActivity.hasAllPermissions()) {
                        mainActivity.startForegroundOverlayService()
                        updateToggleButtonState(toggleOverlayButton, mainActivity)
                    } else {
                        mainActivity.requestPermissions()
                    }
                }
            }
        }

        touchPassthroughSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isOverlayServiceRunning()) {
                touchPassthroughSwitch.isChecked = false
                Toast.makeText(requireContext(), "Please start the overlay first", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "toggle_touch_passthrough")
            intent.putExtra("enabled", isChecked)
            requireContext().startService(intent)
            Toast.makeText(requireContext(), "Touch passthrough: $isChecked", Toast.LENGTH_SHORT).show()
        }

        // Per-App Mode - HIDDEN (backed up in backup/per_app_mode_fragment_backup.kt)
        /*
        perAppSwitch.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "toggle_per_app_mode")
            intent.putExtra("enabled", isChecked)
            requireContext().startService(intent)
            Toast.makeText(requireContext(), "Per-app mode: $isChecked", Toast.LENGTH_SHORT).show()
        }
        */

        autoHideSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isOverlayServiceRunning()) {
                autoHideSwitch.isChecked = false
                Toast.makeText(requireContext(), "Please start the overlay first", Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "toggle_auto_hide")
            intent.putExtra("enabled", isChecked)
            requireContext().startService(intent)
            Toast.makeText(requireContext(), "Auto-hide mode: $isChecked", Toast.LENGTH_SHORT).show()
        }

        // Interactive Slider Controls
        goalHoursSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                goalHoursValue.text = progress.toString()
                if (fromUser) {
                    // Persist immediately and notify service
                    saveAndNotifyDailyGoal(progress, goalMinutesSlider.progress)
                    updateDailyLimitUsageText()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        goalMinutesSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                goalMinutesValue.text = String.format("%02d", progress)
                if (fromUser) {
                    // Persist immediately and notify service
                    saveAndNotifyDailyGoal(goalHoursSlider.progress, progress)
                    updateDailyLimitUsageText()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Quick Preset Buttons
        preset4hButton.setOnClickListener { setPresetGoal(4, 0); saveAndNotifyDailyGoal(4, 0); updateDailyLimitUsageText() }
        preset6hButton.setOnClickListener { setPresetGoal(6, 0); saveAndNotifyDailyGoal(6, 0); updateDailyLimitUsageText() }
        preset8hButton.setOnClickListener { setPresetGoal(8, 0); saveAndNotifyDailyGoal(8, 0); updateDailyLimitUsageText() }
        preset10hButton.setOnClickListener { setPresetGoal(10, 0); saveAndNotifyDailyGoal(10, 0); updateDailyLimitUsageText() }

        autoPositionButton.setOnClickListener {
            mainActivity.setPositionMode("AUTO")
        }

        topRightPositionButton.setOnClickListener {
            mainActivity.setPositionMode("TOP_RIGHT")
        }

        bottomRightPositionButton.setOnClickListener {
            mainActivity.setPositionMode("BOTTOM_RIGHT")
        }

        // Initialize Current Session Tracker
        initializeCurrentSessionTracker(view)

        // Initialize Overlay Customization
        initializeOverlayCustomization(view)

        // Initialize Smart Break Reminders
        initializeBreakReminders(view)
    }
    
    private fun initializeReminderSettings(view: android.view.View) {
        // Initialize reminder tone manager
        reminderToneManager = ReminderToneManager(requireContext())
        
        // Get UI references
        reminderSettingsContainer = view.findViewById<LinearLayout>(R.id.reminderSettingsContainer)
        enableRemindersSwitch = view.findViewById<Switch>(R.id.enableRemindersSwitch)
        currentToneText = view.findViewById<TextView>(R.id.currentToneText)
        reminderDescriptionText = view.findViewById<TextView>(R.id.reminderDescriptionText)
        
        // Tone selection buttons
        val toneHumorButton = view.findViewById<Button>(R.id.toneHumorButton)
        val toneRudeButton = view.findViewById<Button>(R.id.toneRudeButton)
        val toneVeryRudeButton = view.findViewById<Button>(R.id.toneVeryRudeButton)
        val toneMotivateButton = view.findViewById<Button>(R.id.toneMotivateButton)
        val toneFriendlyButton = view.findViewById<Button>(R.id.toneFriendlyButton)
        
        // Set up enable/disable switch
        enableRemindersSwitch.setOnCheckedChangeListener { _, isChecked ->
            reminderToneManager.setRemindersEnabled(isChecked)
            
            // Also save to overlay service preferences for persistence
            val overlayPrefs = requireContext().getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
            overlayPrefs.edit().putBoolean("reminders_enabled", isChecked).apply()
            
            // Keep contextual notifications in sync with the same toggle
            try {
                val notificationManager = com.example.screentimeoverlay.NotificationManager(requireContext())
                notificationManager.setRemindersEnabled(isChecked)
            } catch (_: Throwable) {}

            // Send to OverlayService if it's running
            if (isOverlayServiceRunning()) {
                val intent = Intent(requireContext(), OverlayService::class.java)
                intent.putExtra("action", "set_reminders_enabled")
                intent.putExtra("enabled", isChecked)
                requireContext().startService(intent)
            }
            
            if (isChecked) {
                reminderSettingsContainer.visibility = android.view.View.VISIBLE
                updateCurrentToneDisplay()
            } else {
                reminderSettingsContainer.visibility = android.view.View.GONE
            }
        }
        
        // Load current state
        enableRemindersSwitch.isChecked = reminderToneManager.areRemindersEnabled()
        if (enableRemindersSwitch.isChecked) {
            reminderSettingsContainer.visibility = android.view.View.VISIBLE
        }
        
        // Set up tone selection buttons
        toneHumorButton.setOnClickListener { setReminderTone(ReminderTone.HUMOR) }
        toneRudeButton.setOnClickListener { setReminderTone(ReminderTone.RUDE) }
        toneVeryRudeButton.setOnClickListener { setReminderTone(ReminderTone.VERY_RUDE) }
        toneMotivateButton.setOnClickListener { setReminderTone(ReminderTone.MOTIVATE) }
        toneFriendlyButton.setOnClickListener { setReminderTone(ReminderTone.FRIENDLY) }
        
        // Initialize display
        updateCurrentToneDisplay()
        updateReminderDescriptionText()
    }
    
    private fun setReminderTone(tone: ReminderTone) {
        reminderToneManager.setSelectedTone(tone)
        updateCurrentToneDisplay()
        updateReminderDescriptionText()
        Toast.makeText(requireContext(), "Reminder tone set to ${tone.displayName}", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateCurrentToneDisplay() {
        val currentTone = reminderToneManager.getSelectedTone()
        currentToneText.text = "Selected: ${currentTone.displayName}"
    }
    
    private fun updateReminderDescriptionText() {
        val currentTone = reminderToneManager.getSelectedTone()
        reminderDescriptionText.text = reminderToneManager.getReminderDescriptionText(currentTone)
    }
    
    override fun onResume() {
        super.onResume()
        try {
            // Refresh button state when fragment becomes visible
            if (::toggleOverlayButton.isInitialized && ::mainActivity.isInitialized) {
                updateToggleButtonState(toggleOverlayButton, mainActivity)
            }
            updateDailyLimitUsageText()
            scheduleUsageBadgeUpdates()
            if (view != null && ::mainActivity.isInitialized) {
                startSessionUpdates()
                checkBreakStatus()
                updateBreakHistory()
            }
            try {
                requireContext().registerReceiver(minuteTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
            } catch (_: Throwable) {}
        } catch (e: Exception) {
            // Silently handle any initialization errors
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop minute updates when fragment is not visible
        usageBadgeRunnable?.let { usageBadgeHandler.removeCallbacks(it) }
        usageBadgeRunnable = null
        stopSessionUpdates()
        stopBreakUpdates()
        try { requireContext().unregisterReceiver(minuteTickReceiver) } catch (_: Throwable) {}
    }
    
    /**
     * Check if the OverlayService is currently running
     */
    private fun isOverlayServiceRunning(): Boolean {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (serviceInfo in runningServices) {
            if (OverlayService::class.java.name == serviceInfo.service.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * Update the toggle button state based on overlay service status with smooth animations
     */
    private fun updateToggleButtonState(button: Button, mainActivity: MainActivity) {
        val isRunning = isOverlayServiceRunning()
        val newText = if (isRunning) "STOP OVERLAY" else "START OVERLAY"
        
        // Animate text change with scale effect
        animateButtonStateChange(button, newText, isRunning)
    }
    
    /**
     * Animate button state change with smooth transitions
     */
    private fun animateButtonStateChange(button: Button, newText: String, isRunning: Boolean) {
        // Create scale down animation
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f)
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f)
        val fadeOut = ObjectAnimator.ofFloat(button, "alpha", 1f, 0.7f)
        
        val scaleDownSet = AnimatorSet()
        scaleDownSet.playTogether(scaleDownX, scaleDownY, fadeOut)
        scaleDownSet.duration = 150
        scaleDownSet.interpolator = AccelerateDecelerateInterpolator()
        
        // Create scale up animation
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1f)
        val fadeIn = ObjectAnimator.ofFloat(button, "alpha", 0.7f, 1f)
        
        val scaleUpSet = AnimatorSet()
        scaleUpSet.playTogether(scaleUpX, scaleUpY, fadeIn)
        scaleUpSet.duration = 200
        scaleUpSet.interpolator = OvershootInterpolator(1.2f)
        
        // Chain animations
        scaleDownSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Update text and background during the transition
                button.text = newText
                updateButtonBackground(button, isRunning)
                scaleUpSet.start()
            }
        })
        
        scaleDownSet.start()
    }
    
    /**
     * Update button background with glass morphism and animated icons
     */
    private fun updateButtonBackground(button: Button, isRunning: Boolean) {
        val backgroundRes = if (isRunning) R.drawable.button_glass_stop else R.drawable.button_glass_start
        val iconRes = if (isRunning) R.drawable.ic_stop_animated else R.drawable.ic_play_animated
        
        button.setBackgroundResource(backgroundRes)
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        
        // Animate icon transition
        animateIconTransition(button, isRunning)
    }
    
    
    /**
     * Simple icon transition without rotation animation
     */
    private fun animateIconTransition(button: Button, isRunning: Boolean) {
        // Simple scale animation for icon emphasis
        val scaleXAnimator = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.05f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.05f, 1f)
        scaleXAnimator.duration = 300
        scaleYAnimator.duration = 300
        
        // Simple shimmer effect
        val alphaAnimator = ObjectAnimator.ofFloat(button, "alpha", 1f, 0.8f, 1f)
        alphaAnimator.duration = 200
        
        // Chain animations
        val iconAnimationSet = AnimatorSet()
        iconAnimationSet.playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
        iconAnimationSet.start()
    }
    
    /**
     * Animate button press with satisfying feedback
     */
    private fun animateButtonPress(button: Button, onComplete: () -> Unit) {
        // Scale down animation for press
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.95f)
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.95f)
        
        val pressSet = AnimatorSet()
        pressSet.playTogether(scaleDownX, scaleDownY)
        pressSet.duration = 100
        pressSet.interpolator = AccelerateDecelerateInterpolator()
        
        // Scale up animation for release
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.95f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.95f, 1f)
        
        val releaseSet = AnimatorSet()
        releaseSet.playTogether(scaleUpX, scaleUpY)
        releaseSet.duration = 150
        releaseSet.interpolator = OvershootInterpolator(1.1f)
        
        // Chain animations
        pressSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                releaseSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onComplete()
                    }
                })
                releaseSet.start()
            }
        })
        
        pressSet.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
    
    // Modern Goal Helper Methods
    private fun updateGoalStatus() {
        // Don't update goalStatusText here - it's managed by updateDailyLimitUsageText()
        // This function is kept for potential future use but doesn't modify the status text
        // The status text should always show "usage / limit" format, not just the limit
    }
    
    private fun setPresetGoal(hours: Int, minutes: Int) {
        goalHoursSlider.progress = hours
        goalMinutesSlider.progress = minutes
        goalHoursValue.text = hours.toString()
        goalMinutesValue.text = String.format("%02d", minutes)
        updateGoalStatus()
        updateDailyLimitUsageText()
    }
    
    /**
     * Load saved daily goal settings from SharedPreferences
     */
    private fun loadSavedDailyGoal() {
        val prefs = requireContext().getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
        val savedHours = prefs.getInt("daily_goal_hours", 8)
        val savedMinutes = prefs.getInt("daily_goal_minutes", 0)
        
        // Set the sliders to the saved values
        goalHoursSlider.progress = savedHours
        goalMinutesSlider.progress = savedMinutes
        goalHoursValue.text = savedHours.toString()
        goalMinutesValue.text = String.format("%02d", savedMinutes)
        // Note: updateDailyLimitUsageText() is called after this in onViewCreated
    }

    private fun saveAndNotifyDailyGoal(hours: Int, minutes: Int) {
        // Persist immediately
        val prefs = requireContext().getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("daily_goal_hours", hours)
            .putInt("daily_goal_minutes", minutes)
            .apply()

        // Notify OverlayService if running to keep single source of truth
        val intent = android.content.Intent(requireContext(), OverlayService::class.java)
        intent.putExtra("action", "set_daily_goal")
        intent.putExtra("hours", hours)
        intent.putExtra("minutes", minutes)
        requireContext().startService(intent)
    }

    /**
     * Update the text "{actualUsage} / {limit}" in the Daily Limit section.
     * Now uses the same filtering and accumulator logic as overlay for perfect sync.
     */
    private fun updateDailyLimitUsageText() {
        val usageMs = getTodayTotalUsageMs()
        // Use the accumulator result directly (already clamped by getTodayTotalUsageMs)
        val clampedUsage = usageMs
        val (limitHours, limitMinutes) = getSavedGoal()
        val usageText = formatDuration(clampedUsage)
        val limitText = String.format("%dh %02dm", limitHours, limitMinutes)
        val combined = "$usageText / $limitText"
        // Reflect the combined text in the Daily Limit badge
        goalStatusText.text = combined

        // Color rule: default green; if usage >= limit then red
        val limitMs = (limitHours * 60L + limitMinutes) * 60_000L
        val colorRes = if (clampedUsage >= limitMs) R.color.status_error else R.color.status_success
        val colorInt = androidx.core.content.ContextCompat.getColor(requireContext(), colorRes)
        goalStatusText.setTextColor(colorInt)
    }

    private fun getSavedGoal(): Pair<Int, Int> {
        val prefs = requireContext().getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
        val hours = prefs.getInt("daily_goal_hours", 8)
        val minutes = prefs.getInt("daily_goal_minutes", 0)
        return hours to minutes
    }
    
    /**
     * Load saved switch states from preferences
     */
    private fun loadSavedSwitchStates(touchPassthroughSwitch: Switch, autoHideSwitch: Switch) {
        val prefs = requireContext().getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
        
        // Load touch passthrough state
        val touchPassthroughEnabled = prefs.getBoolean("touch_passthrough", false)
        touchPassthroughSwitch.isChecked = touchPassthroughEnabled
        
        // Load auto hide state
        val autoHideEnabled = prefs.getBoolean("auto_hide_enabled", false)
        autoHideSwitch.isChecked = autoHideEnabled
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = (ms / 60000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return String.format("%dh %02dm", hours, minutes)
    }

    private fun getTodayTotalUsageMs(): Long {
        return try {
            val usageStatsManager = requireContext().getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val endTime = System.currentTimeMillis()
            
            // Calculate start time as midnight (00:01 AM) of current day - same as overlay
            val calendar = java.util.Calendar.getInstance()
            // Ensure we're working with today's date, not yesterday's
            calendar.timeInMillis = endTime
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 1) // 00:01 AM
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            // Ensure startTime is not in the future (shouldn't happen, but safety check)
            val actualStartTime = if (startTime > endTime) {
                // If startTime is in future, use 00:01 AM of today
                calendar.timeInMillis = endTime
                calendar.add(java.util.Calendar.DAY_OF_YEAR, 0) // Ensure we're on the same day
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 1)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else {
                startTime
            }

            val usageStats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                actualStartTime,
                endTime
            ) ?: return 0L

            // Filter out stats that don't have activity within today's window
            // UsageStatsManager may return stats from previous day that have lastTimeUsed in range
            // but we only want stats that actually have usage within today's window
            val filteredStats = usageStats.filter { stats ->
                stats.lastTimeUsed >= actualStartTime && stats.lastTimeUsed <= endTime
            }

            // Check if day changed - reset accumulator at midnight (same as overlay)
            val todayKey = "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.DAY_OF_YEAR)}"
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

            // Apply app filtering (same as overlay)
            var totalTime = 0L
            filteredStats.forEach { stats ->
                if (appFilterManager.shouldTrackApp(stats.packageName)) {
                    totalTime += stats.totalTimeInForeground
                }
            }

            // Prevent UI minute rollback: compute a clamped UI total that never decreases within the same day (same as overlay)
            val nowTs = System.currentTimeMillis()
            val usageStatsTotal = totalTime
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
            
            return uiTotalMs
        } catch (_: Throwable) {
            0L
        }
    }

    private fun scheduleUsageBadgeUpdates() {
        // Kick off at next minute boundary, then every 60s
        val now = System.currentTimeMillis()
        val delay = 60000L - (now % 60000L)
        usageBadgeRunnable = object : Runnable {
            override fun run() {
                updateDailyLimitUsageText()
                usageBadgeHandler.postDelayed(this, 60000L)
            }
        }
        usageBadgeHandler.postDelayed(usageBadgeRunnable!!, delay)
    }

    private fun initializeCurrentSessionTracker(view: android.view.View) {
        try {
            currentSessionCard = view.findViewById<LinearLayout>(R.id.currentSessionCard)
            currentAppName = view.findViewById<TextView>(R.id.currentAppName)
            currentSessionTime = view.findViewById<TextView>(R.id.currentSessionTime)
            currentAppCategory = view.findViewById<TextView>(R.id.currentAppCategory)
            
            updateCurrentSession()
        } catch (e: Exception) {
            // Silently handle initialization errors
        }
    }

    private fun updateCurrentSession() {
        try {
            if (!::mainActivity.isInitialized) return
            val sessionTracker = mainActivity.getSessionTracker()
            val currentSession = sessionTracker.getCurrentSession()
            
            if (currentSession != null && currentSession.isActive) {
                currentSessionCard?.visibility = android.view.View.VISIBLE
                
                // Format app name with title case
                val titleCased = currentSession.appName.split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part ->
                        if (part.isNotEmpty()) {
                            part[0].uppercaseChar() + part.substring(1).lowercase()
                        } else {
                            part
                        }
                    }
                currentAppName?.text = titleCased
                
                // Calculate current session duration including active time
                val currentDuration = currentSession.totalTime + (System.currentTimeMillis() - currentSession.lastActivityTime)
                currentSessionTime?.text = formatDurationCompact(currentDuration)
                
                // Get app category
                val category = appFilterManager.getAppCategory(currentSession.packageName)
                if (category != "other") {
                    currentAppCategory?.text = category.replaceFirstChar { it.uppercaseChar() }
                    currentAppCategory?.visibility = android.view.View.VISIBLE
                } else {
                    currentAppCategory?.visibility = android.view.View.GONE
                }
            } else {
                currentSessionCard?.visibility = android.view.View.GONE
            }
        } catch (e: Exception) {
            // Silently handle errors
            currentSessionCard?.visibility = android.view.View.GONE
        }
    }

    private fun formatDurationCompact(timeMs: Long): String {
        val totalMinutes = (timeMs / 60000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }

    private fun startSessionUpdates() {
        sessionUpdateRunnable = object : Runnable {
            override fun run() {
                updateCurrentSession()
                sessionUpdateHandler.postDelayed(this, SESSION_UPDATE_INTERVAL_MS)
            }
        }
        sessionUpdateHandler.post(sessionUpdateRunnable!!)
    }

    private fun stopSessionUpdates() {
        sessionUpdateRunnable?.let { sessionUpdateHandler.removeCallbacks(it) }
        sessionUpdateRunnable = null
    }

    private fun initializeOverlayCustomization(view: android.view.View) {
        try {
            val opacitySlider = view.findViewById<SeekBar>(R.id.opacitySlider)
            val opacityValue = view.findViewById<TextView>(R.id.opacityValue)

            if (opacitySlider == null || opacityValue == null) {
                return
            }

            // Load saved opacity
            val overlayPrefs = requireContext().getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
            val savedOpacity = overlayPrefs.getInt("overlay_opacity", 80)
            opacitySlider.progress = savedOpacity
            opacityValue.text = "$savedOpacity%"

            opacitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    opacityValue.text = "$progress%"
                    if (fromUser) {
                        setOverlayOpacity(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        } catch (e: Exception) {
            // Silently handle initialization errors
        }
    }

    private fun setOverlayOpacity(opacity: Int) {
        val overlayPrefs = requireContext().getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
        overlayPrefs.edit().putInt("overlay_opacity", opacity).apply()
        
        if (isOverlayServiceRunning()) {
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "set_overlay_opacity")
            intent.putExtra("opacity", opacity)
            requireContext().startService(intent)
        }
    }

    private fun initializeBreakReminders(view: android.view.View) {
        try {
            val takeBreakButton = view.findViewById<Button>(R.id.takeBreakButton)
            breakTimerContainer = view.findViewById<LinearLayout>(R.id.breakTimerContainer)
            breakTimerText = view.findViewById<TextView>(R.id.breakTimerText)
            breakHistoryText = view.findViewById<TextView>(R.id.breakHistoryText)

            takeBreakButton?.setOnClickListener {
                startBreak(5) // 5 minute break
            }

            checkBreakStatus()
            updateBreakHistory()
        } catch (e: Exception) {
            // Silently handle initialization errors
        }
    }

    private fun startBreak(minutes: Int) {
        if (!isOverlayServiceRunning()) {
            Toast.makeText(requireContext(), "Please start the overlay first", Toast.LENGTH_SHORT).show()
            return
        }

        isBreakActive = true
        breakEndTime = System.currentTimeMillis() + (minutes * 60 * 1000L)

        // Save break info
        val breakPrefs = requireContext().getSharedPreferences("break_prefs", Context.MODE_PRIVATE)
        val breaksToday = breakPrefs.getInt("breaks_today", 0)
        breakPrefs.edit()
            .putInt("breaks_today", breaksToday + 1)
            .putLong("current_break_end", breakEndTime)
            .apply()

        // Pause tracking during break
        val intent = Intent(requireContext(), OverlayService::class.java)
        intent.putExtra("action", "pause_tracking")
        requireContext().startService(intent)

        // Show timer
        breakTimerContainer?.visibility = android.view.View.VISIBLE
        startBreakUpdates()

        Toast.makeText(requireContext(), "Break started - tracking paused", Toast.LENGTH_SHORT).show()
    }

    private fun checkBreakStatus() {
        try {
            val breakPrefs = requireContext().getSharedPreferences("break_prefs", Context.MODE_PRIVATE)
            val savedBreakEnd = breakPrefs.getLong("current_break_end", 0L)

            if (savedBreakEnd > System.currentTimeMillis()) {
                isBreakActive = true
                breakEndTime = savedBreakEnd
                breakTimerContainer?.visibility = android.view.View.VISIBLE
                startBreakUpdates()
            } else if (savedBreakEnd > 0) {
                // Break ended while app was closed
                endBreak()
            }
        } catch (e: Exception) {
            // Silently handle errors
        }
    }

    private fun startBreakUpdates() {
        breakUpdateRunnable = object : Runnable {
            override fun run() {
                val remaining = breakEndTime - System.currentTimeMillis()
                
                if (remaining > 0) {
                    val minutes = (remaining / 60000).toInt()
                    val seconds = ((remaining % 60000) / 1000).toInt()
                    breakTimerText?.text = String.format("%d:%02d", minutes, seconds)
                    breakUpdateHandler.postDelayed(this, 1000L)
                } else {
                    endBreak()
                }
            }
        }
        breakUpdateHandler.post(breakUpdateRunnable!!)
    }

    private fun stopBreakUpdates() {
        breakUpdateRunnable?.let { breakUpdateHandler.removeCallbacks(it) }
        breakUpdateRunnable = null
    }

    private fun endBreak() {
        isBreakActive = false
        breakTimerContainer?.visibility = android.view.View.GONE

        // Resume tracking
        if (isOverlayServiceRunning()) {
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "resume_tracking")
            requireContext().startService(intent)
        }

        // Clear break end time
        val breakPrefs = requireContext().getSharedPreferences("break_prefs", Context.MODE_PRIVATE)
        breakPrefs.edit().putLong("current_break_end", 0).apply()

        updateBreakHistory()
        Toast.makeText(requireContext(), "Break ended - tracking resumed", Toast.LENGTH_SHORT).show()
    }

    private fun updateBreakHistory() {
        val breakPrefs = requireContext().getSharedPreferences("break_prefs", Context.MODE_PRIVATE)
        val breaksToday = breakPrefs.getInt("breaks_today", 0)

        if (breaksToday > 0) {
            breakHistoryText?.text = "$breaksToday break${if (breaksToday > 1) "s" else ""} taken today"
        } else {
            breakHistoryText?.text = "No breaks taken today"
        }
    }
    
}
