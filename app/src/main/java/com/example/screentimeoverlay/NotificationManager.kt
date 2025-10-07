package com.example.screentimeoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar
import java.util.Date

/**
 * Smart notification manager for contextual reminders, break suggestions,
 * goal celebrations, and customizable alerts
 */
class NotificationManager(private val context: Context) {
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private val preferences = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    
    // Notification channels
    companion object {
        const val CHANNEL_REMINDERS = "reminders_channel"
        const val CHANNEL_BREAKS = "breaks_channel"
        const val CHANNEL_GOALS = "goals_channel"
        const val CHANNEL_CUSTOM = "custom_channel"
        
        const val NOTIFICATION_REMINDER = 1001
        const val NOTIFICATION_BREAK = 1002
        const val NOTIFICATION_GOAL = 1003
        const val NOTIFICATION_CUSTOM = 1004
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Reminders channel
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Contextual Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Smart reminders based on time and usage patterns"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Break suggestions channel
            val breaksChannel = NotificationChannel(
                CHANNEL_BREAKS,
                "Break Suggestions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smart break reminders based on usage patterns"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Goal celebrations channel
            val goalsChannel = NotificationChannel(
                CHANNEL_GOALS,
                "Goal Celebrations",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Positive reinforcement when goals are met"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Custom alerts channel
            val customChannel = NotificationChannel(
                CHANNEL_CUSTOM,
                "Custom Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "User-defined notification triggers"
                enableVibration(true)
                setShowBadge(true)
            }
            
            nm.createNotificationChannel(remindersChannel)
            nm.createNotificationChannel(breaksChannel)
            nm.createNotificationChannel(goalsChannel)
            nm.createNotificationChannel(customChannel)
        }
    }
    
    /**
     * Show contextual reminder based on time of day and usage patterns
     */
    fun showContextualReminder(
        timeOfDay: TimeOfDay,
        usageData: ScreenTimeData,
        sessionData: SessionStats
    ) {
        if (!isRemindersEnabled()) return
        
        val message = getContextualMessage(timeOfDay, usageData, sessionData)
        val title = getContextualTitle(timeOfDay)
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        notificationManager.notify(NOTIFICATION_REMINDER, notification)
    }
    
    /**
     * Show smart break suggestion based on usage patterns
     */
    fun showBreakSuggestion(
        breakType: BreakType,
        reason: String,
        duration: Int
    ) {
        if (!isBreakSuggestionsEnabled()) return
        
        val title = "Time for a Break!"
        val message = when (breakType) {
            BreakType.MICRO_BREAK -> "Take a 2-minute micro break. $reason"
            BreakType.SHORT_BREAK -> "Take a 5-minute break. $reason"
            BreakType.MEDIUM_BREAK -> "Take a 10-minute break. $reason"
            BreakType.LONG_BREAK -> "Take a 15-minute break. $reason"
            BreakType.WALK_BREAK -> "Go for a short walk. $reason"
            BreakType.LUNCH_BREAK -> "Time for lunch! Take a proper meal break. $reason"
            BreakType.EVENING_BREAK -> "Evening break time. Relax and unwind. $reason"
            BreakType.FOCUS_BREAK -> "Take a focused break to recharge. $reason"
        }
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_BREAKS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.mipmap.ic_launcher,
                "Start Break",
                pendingIntent
            )
            .build()
        
        notificationManager.notify(NOTIFICATION_BREAK, notification)
    }
    
    /**
     * Show goal celebration notification
     */
    fun showGoalCelebration(
        goalType: GoalType,
        achievement: String,
        streak: Int = 0
    ) {
        if (!isGoalCelebrationsEnabled()) return
        
        val title = when (goalType) {
            GoalType.DAILY_LIMIT -> "🎉 Daily Goal Achieved!"
            GoalType.WEEKLY_LIMIT -> "🎉 Weekly Goal Achieved!"
            GoalType.APP_SPECIFIC -> "🎉 App Goal Achieved!"
            GoalType.CATEGORY_LIMIT -> "🎉 Category Goal Achieved!"
            GoalType.FOCUS_TIME -> "🎯 Focus Goal Completed!"
            GoalType.BREAK_TIME -> "🏆 Break Goal Met!"
            GoalType.PRODUCTIVITY_SCORE -> "📈 Productivity Goal Met!"
            GoalType.BREAK_GOAL -> "🏆 Break Goal Met!"
            GoalType.FOCUS_GOAL -> "🎯 Focus Goal Completed!"
            GoalType.STREAK_GOAL -> "🔥 $streak Day Streak!"
        }
        
        val message = achievement
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_GOALS)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()
        
        notificationManager.notify(NOTIFICATION_GOAL, notification)
    }
    
    /**
     * Show custom alert based on user-defined triggers
     */
    fun showCustomAlert(
        trigger: CustomTrigger,
        message: String,
        priority: AlertPriority = AlertPriority.MEDIUM
    ) {
        if (!isCustomAlertsEnabled()) return
        
        val title = "Screen Time Alert"
        val priorityLevel = when (priority) {
            AlertPriority.LOW -> NotificationCompat.PRIORITY_LOW
            AlertPriority.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
            AlertPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
            AlertPriority.CRITICAL -> NotificationCompat.PRIORITY_MAX
        }
        
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_CUSTOM)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(priorityLevel)
            .build()
        
        notificationManager.notify(NOTIFICATION_CUSTOM, notification)
    }
    
    private fun getContextualMessage(
        timeOfDay: TimeOfDay,
        usageData: ScreenTimeData,
        sessionData: SessionStats
    ): String {
        return when (timeOfDay) {
            TimeOfDay.MORNING -> {
                if (usageData.totalTime > 0) {
                    "Good morning! You've already used your device for ${formatTime(usageData.totalTime)} today."
                } else {
                    "Good morning! Start your day with intention. Set a screen time goal for today."
                }
            }
            TimeOfDay.AFTERNOON -> {
                val progress = calculateProgress(usageData.totalTime)
                if (progress > 0.8) {
                    "You're at ${(progress * 100).toInt()}% of your daily goal. Consider taking a break."
                } else {
                    "Afternoon check-in: You've used your device for ${formatTime(usageData.totalTime)} today."
                }
            }
            TimeOfDay.EVENING -> {
                if (usageData.totalTime > 0) {
                    "Evening reflection: You've spent ${formatTime(usageData.totalTime)} on your device today."
                } else {
                    "Evening wind-down: Great job managing your screen time today!"
                }
            }
            TimeOfDay.NIGHT -> {
                "Late night usage detected. Consider winding down for better sleep quality."
            }
        }
    }
    
    private fun getContextualTitle(timeOfDay: TimeOfDay): String {
        return when (timeOfDay) {
            TimeOfDay.MORNING -> "🌅 Morning Reminder"
            TimeOfDay.AFTERNOON -> "☀️ Afternoon Check-in"
            TimeOfDay.EVENING -> "🌆 Evening Reflection"
            TimeOfDay.NIGHT -> "🌙 Night Alert"
        }
    }
    
    private fun calculateProgress(totalTime: Long): Float {
        val dailyGoal = getDailyGoal()
        return if (dailyGoal > 0) {
            (totalTime.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }
    
    private fun formatTime(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
    
    private fun getDailyGoal(): Long {
        return preferences.getLong("daily_goal_ms", 8 * 60 * 60 * 1000) // 8 hours default
    }
    
    // Settings methods
    fun isRemindersEnabled(): Boolean = preferences.getBoolean("reminders_enabled", true)
    fun isBreakSuggestionsEnabled(): Boolean = preferences.getBoolean("break_suggestions_enabled", true)
    fun isGoalCelebrationsEnabled(): Boolean = preferences.getBoolean("goal_celebrations_enabled", true)
    fun isCustomAlertsEnabled(): Boolean = preferences.getBoolean("custom_alerts_enabled", true)
    
    fun setRemindersEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("reminders_enabled", enabled).apply()
    }
    
    fun setBreakSuggestionsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("break_suggestions_enabled", enabled).apply()
    }
    
    fun setGoalCelebrationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("goal_celebrations_enabled", enabled).apply()
    }
    
    fun setCustomAlertsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("custom_alerts_enabled", enabled).apply()
    }
    
    fun setDailyGoal(hours: Int, minutes: Int) {
        val totalMs = (hours * 60 + minutes) * 60 * 1000L
        preferences.edit().putLong("daily_goal_ms", totalMs).apply()
    }
    
    fun setReminderFrequency(frequency: ReminderFrequency) {
        preferences.edit().putString("reminder_frequency", frequency.name).apply()
    }
    
    fun getReminderFrequency(): ReminderFrequency {
        val frequencyName = preferences.getString("reminder_frequency", ReminderFrequency.MODERATE.name)
        return ReminderFrequency.valueOf(frequencyName ?: ReminderFrequency.MODERATE.name)
    }
}

// Enums for notification types


enum class ReminderFrequency {
    MINIMAL, MODERATE, FREQUENT
}

data class CustomTrigger(
    val name: String,
    val condition: String,
    val message: String,
    val priority: AlertPriority = AlertPriority.MEDIUM
)
