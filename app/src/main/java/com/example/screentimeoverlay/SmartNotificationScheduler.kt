package com.example.screentimeoverlay

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Smart notification scheduler that determines when to show contextual reminders,
 * break suggestions, and goal celebrations based on usage patterns and time
 */
class SmartNotificationScheduler(private val context: Context) {
    
    private val notificationManager = NotificationManager(context)
    private val preferences = context.getSharedPreferences("notification_scheduler", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val appFilterManager = AppFilterManager(context)
    
    // Tracking variables
    private var lastReminderTime = 0L
    private var lastBreakSuggestionTime = 0L
    private var lastGoalCelebrationTime = 0L
    private var currentStreak = 0
    private var lastGoalAchievedDate = ""
    
    companion object {
        private const val MIN_REMINDER_INTERVAL = 2 * 60 * 60 * 1000L // 2 hours
        private const val MIN_BREAK_INTERVAL = 30 * 60 * 1000L // 30 minutes
        private const val MIN_GOAL_CELEBRATION_INTERVAL = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    /**
     * Check and trigger contextual reminders based on current time and usage
     */
    fun checkContextualReminders(
        screenTimeData: ScreenTimeData,
        sessionStats: SessionStats
    ) {
        if (!notificationManager.isRemindersEnabled()) return
        
        val currentTime = System.currentTimeMillis()
        val timeOfDay = getCurrentTimeOfDay()
        
        // Check if enough time has passed since last reminder
        if (currentTime - lastReminderTime < MIN_REMINDER_INTERVAL) return
        
        // Determine if we should show a reminder based on usage patterns
        val shouldShowReminder = shouldShowContextualReminder(timeOfDay, screenTimeData, sessionStats)
        
        if (shouldShowReminder) {
            notificationManager.showContextualReminder(timeOfDay, screenTimeData, sessionStats)
            lastReminderTime = currentTime
        }
    }
    
    /**
     * Check and trigger break suggestions based on usage patterns
     */
    fun checkBreakSuggestions(
        screenTimeData: ScreenTimeData,
        sessionStats: SessionStats,
        currentSession: AppSession?
    ) {
        if (!notificationManager.isBreakSuggestionsEnabled()) return
        
        val currentTime = System.currentTimeMillis()
        
        // Check if enough time has passed since last break suggestion
        if (currentTime - lastBreakSuggestionTime < MIN_BREAK_INTERVAL) return
        
        val breakSuggestion = analyzeBreakNeeds(screenTimeData, sessionStats, currentSession)
        
        if (breakSuggestion != null) {
            notificationManager.showBreakSuggestion(
                breakSuggestion.type,
                breakSuggestion.reason,
                breakSuggestion.duration
            )
            lastBreakSuggestionTime = currentTime
        }
    }
    
    /**
     * Check and trigger goal celebrations
     */
    fun checkGoalCelebrations(
        screenTimeData: ScreenTimeData,
        sessionStats: SessionStats,
        dailyGoal: DailyGoal
    ) {
        if (!notificationManager.isGoalCelebrationsEnabled()) return
        
        val currentTime = System.currentTimeMillis()
        val today = getTodayString()
        
        // Check if we've already celebrated today
        if (lastGoalAchievedDate == today) return
        
        val achievements = analyzeGoalAchievements(screenTimeData, sessionStats, dailyGoal)
        
        achievements.forEach { achievement ->
            when (achievement.type) {
                GoalType.DAILY_LIMIT -> {
                    notificationManager.showGoalCelebration(
                        GoalType.DAILY_LIMIT,
                        "You've stayed within your daily screen time limit! 🎉",
                        currentStreak
                    )
                }
                GoalType.WEEKLY_LIMIT -> {
                    notificationManager.showGoalCelebration(
                        GoalType.WEEKLY_LIMIT,
                        "You've stayed within your weekly screen time limit! 🎉",
                        currentStreak
                    )
                }
                GoalType.APP_SPECIFIC -> {
                    notificationManager.showGoalCelebration(
                        GoalType.APP_SPECIFIC,
                        "You've met your app-specific goal! 🎉",
                        currentStreak
                    )
                }
                GoalType.CATEGORY_LIMIT -> {
                    notificationManager.showGoalCelebration(
                        GoalType.CATEGORY_LIMIT,
                        "You've met your category limit goal! 🎉",
                        currentStreak
                    )
                }
                GoalType.FOCUS_TIME -> {
                    notificationManager.showGoalCelebration(
                        GoalType.FOCUS_TIME,
                        "Excellent focus session completed! 🎯",
                        currentStreak
                    )
                }
                GoalType.BREAK_TIME -> {
                    notificationManager.showGoalCelebration(
                        GoalType.BREAK_TIME,
                        "Great job taking regular breaks today! 🏆",
                        currentStreak
                    )
                }
                GoalType.PRODUCTIVITY_SCORE -> {
                    notificationManager.showGoalCelebration(
                        GoalType.PRODUCTIVITY_SCORE,
                        "You've achieved your productivity score goal! 📈",
                        currentStreak
                    )
                }
                GoalType.BREAK_GOAL -> {
                    notificationManager.showGoalCelebration(
                        GoalType.BREAK_GOAL,
                        "Great job taking regular breaks today! 🏆",
                        currentStreak
                    )
                }
                GoalType.FOCUS_GOAL -> {
                    notificationManager.showGoalCelebration(
                        GoalType.FOCUS_GOAL,
                        "Excellent focus session completed! 🎯",
                        currentStreak
                    )
                }
                GoalType.STREAK_GOAL -> {
                    currentStreak++
                    notificationManager.showGoalCelebration(
                        GoalType.STREAK_GOAL,
                        "Amazing! You've maintained your goal for $currentStreak days! 🔥",
                        currentStreak
                    )
                }
            }
        }
        
        if (achievements.isNotEmpty()) {
            lastGoalAchievedDate = today
            lastGoalCelebrationTime = currentTime
        }
    }
    
    /**
     * Check custom alerts based on user-defined triggers
     */
    fun checkCustomAlerts(
        screenTimeData: ScreenTimeData,
        sessionStats: SessionStats,
        customTriggers: List<CustomTrigger>
    ) {
        if (!notificationManager.isCustomAlertsEnabled()) return
        
        customTriggers.forEach { trigger ->
            if (evaluateCustomTrigger(trigger, screenTimeData, sessionStats)) {
                notificationManager.showCustomAlert(trigger, trigger.message, trigger.priority)
            }
        }
    }
    
    private fun getCurrentTimeOfDay(): TimeOfDay {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..17 -> TimeOfDay.AFTERNOON
            in 18..22 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }
    
    private fun shouldShowContextualReminder(
        timeOfDay: TimeOfDay,
        screenTimeData: ScreenTimeData,
        sessionStats: SessionStats
    ): Boolean {
        return when (timeOfDay) {
            TimeOfDay.MORNING -> {
                // Show reminder if user has been using device for more than 30 minutes
                screenTimeData.totalTime > 30 * 60 * 1000
            }
            TimeOfDay.AFTERNOON -> {
                // Show reminder if approaching daily goal (80% or more)
                val dailyGoal = getDailyGoalMs()
                if (dailyGoal > 0) {
                    val progress = screenTimeData.totalTime.toFloat() / dailyGoal.toFloat()
                    progress >= 0.8f
                } else false
            }
            TimeOfDay.EVENING -> {
                // Show reminder if over daily goal
                val dailyGoal = getDailyGoalMs()
                if (dailyGoal > 0) {
                    screenTimeData.totalTime > dailyGoal
                } else false
            }
            TimeOfDay.NIGHT -> {
                // Show reminder if using device late at night
                screenTimeData.totalTime > 0
            }
        }
    }
    
    private fun analyzeBreakNeeds(
        screenTimeData: ScreenTimeData,
        sessionStats: SessionStats,
        currentSession: AppSession?
    ): BreakSuggestion? {
        val currentTime = System.currentTimeMillis()
        
        // Check for continuous usage without breaks
        if (currentSession != null) {
            val sessionDuration = currentSession.totalTime
            val lastBreakTime = getLastBreakTime()
            
            // Suggest micro break after 25 minutes of continuous usage
            if (sessionDuration > 25 * 60 * 1000 && currentTime - lastBreakTime > 25 * 60 * 1000) {
                return BreakSuggestion(
                    BreakType.MICRO_BREAK,
                    "You've been focused for 25+ minutes. Time for a quick break!",
                    2
                )
            }
            
            // Suggest short break after 50 minutes
            if (sessionDuration > 50 * 60 * 1000 && currentTime - lastBreakTime > 50 * 60 * 1000) {
                return BreakSuggestion(
                    BreakType.SHORT_BREAK,
                    "You've been working for almost an hour. Take a 5-minute break!",
                    5
                )
            }
            
            // Suggest long break after 90 minutes
            if (sessionDuration > 90 * 60 * 1000 && currentTime - lastBreakTime > 90 * 60 * 1000) {
                return BreakSuggestion(
                    BreakType.LONG_BREAK,
                    "You've been focused for 90+ minutes. Time for a longer break!",
                    15
                )
            }
        }
        
        // Check for high usage in short time periods
        val recentUsage = getRecentUsage(30) // Last 30 minutes
        if (recentUsage > 25 * 60 * 1000) {
            return BreakSuggestion(
                BreakType.WALK_BREAK,
                "You've been very active on your device. Consider a short walk!",
                5
            )
        }
        
        return null
    }
    
    private fun analyzeGoalAchievements(
        screenTimeData: ScreenTimeData,
        sessionStats: SessionStats,
        dailyGoal: DailyGoal
    ): List<GoalAchievement> {
        val achievements = mutableListOf<GoalAchievement>()
        
        // Check daily limit goal - only celebrate if user is close to goal (within 5% or at goal) but hasn't exceeded
        val totalGoalMs = dailyGoal.getTotalMinutes() * 60 * 1000L
        if (totalGoalMs > 0 && screenTimeData.totalTime > 0) {
            val progress = screenTimeData.totalTime.toFloat() / totalGoalMs.toFloat()
            // Only celebrate if user is at least 80% of goal and hasn't exceeded it
            if (progress >= 0.8f && progress <= 1.0f) {
                achievements.add(GoalAchievement(GoalType.DAILY_LIMIT, "Daily limit maintained"))
            }
        }
        
        // Check break goal (if user took at least 3 breaks today)
        if (sessionStats.totalSessions >= 3) {
            achievements.add(GoalAchievement(GoalType.BREAK_GOAL, "Regular breaks taken"))
        }
        
        // Check focus goal (if user had focused sessions)
        if (sessionStats.focusedSessions > 0) {
            achievements.add(GoalAchievement(GoalType.FOCUS_GOAL, "Focused work completed"))
        }
        
        // Check streak goal
        val today = getTodayString()
        val lastGoalDate = preferences.getString("last_goal_date", "")
        if (lastGoalDate != today) {
            currentStreak++
            achievements.add(GoalAchievement(GoalType.STREAK_GOAL, "Streak maintained"))
            preferences.edit().putString("last_goal_date", today).apply()
        }
        
        return achievements
    }
    
    private fun evaluateCustomTrigger(
        trigger: CustomTrigger,
        screenTimeData: ScreenTimeData,
        sessionStats: SessionStats
    ): Boolean {
        // Simple condition evaluation (can be extended for more complex logic)
        return when (trigger.condition) {
            "daily_limit_exceeded" -> {
                val dailyGoal = getDailyGoalMs()
                dailyGoal > 0 && screenTimeData.totalTime > dailyGoal
            }
            "long_session" -> {
                sessionStats.averageSessionTime > 60 * 60 * 1000 // 1 hour
            }
            "high_usage" -> {
                screenTimeData.totalTime > 6 * 60 * 60 * 1000 // 6 hours
            }
            "no_breaks" -> {
                sessionStats.totalSessions < 2
            }
            else -> false
        }
    }
    
    private fun getDailyGoalMs(): Long {
        // Compute from hours/minutes in overlay_settings (single source of truth)
        val overlayPrefs = context.getSharedPreferences("overlay_settings", Context.MODE_PRIVATE)
        val hours = overlayPrefs.getInt("daily_goal_hours", 8)
        val minutes = overlayPrefs.getInt("daily_goal_minutes", 0)
        return (hours * 60 + minutes) * 60 * 1000L
    }
    
    private fun getLastBreakTime(): Long {
        return preferences.getLong("last_break_time", 0)
    }
    
    private fun getRecentUsage(minutes: Int): Long {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - TimeUnit.MINUTES.toMillis(minutes.toLong())
            
            // Query usage stats for the specified time range
            // Use INTERVAL_BEST to get the most accurate data for custom time ranges
            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            ) ?: return 0L
            
            // Filter stats to only include usage within the time range
            val filteredStats = usageStats.filter { stats ->
                stats.lastTimeUsed >= startTime && stats.lastTimeUsed <= endTime
            }
            
            // Apply app filtering using AppFilterManager
            var totalTime = 0L
            filteredStats.forEach { stats ->
                if (appFilterManager.shouldTrackApp(stats.packageName)) {
                    totalTime += stats.totalTimeInForeground
                }
            }
            
            totalTime
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun getTodayString(): String {
        val calendar = Calendar.getInstance()
        // Calendar.MONTH is 0-based (0-11), so add 1 for correct month
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }
    
    fun setLastBreakTime() {
        preferences.edit().putLong("last_break_time", System.currentTimeMillis()).apply()
    }
    
    fun resetStreak() {
        currentStreak = 0
        preferences.edit().remove("last_goal_date").apply()
    }
}

// Data classes for break suggestions and goal achievements
data class BreakSuggestion(
    val type: BreakType,
    val reason: String,
    val duration: Int
)

data class GoalAchievement(
    val type: GoalType,
    val message: String
)
