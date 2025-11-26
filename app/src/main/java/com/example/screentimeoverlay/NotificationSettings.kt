package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Manages notification settings and user preferences for smart notifications
 */
class NotificationSettings(private val context: Context) {
    
    private val preferences: SharedPreferences = context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE)
    
    // Default settings
    companion object {
        private const val DEFAULT_DAILY_GOAL_HOURS = 8
        private const val DEFAULT_DAILY_GOAL_MINUTES = 0
        private const val DEFAULT_REMINDER_FREQUENCY = "MODERATE"
        private const val DEFAULT_BREAK_INTERVAL = 25 // minutes
        private const val DEFAULT_QUIET_HOURS_START = 22 // 10 PM
        private const val DEFAULT_QUIET_HOURS_END = 7 // 7 AM
    }
    
    // General notification settings
    fun isNotificationsEnabled(): Boolean = preferences.getBoolean("notifications_enabled", true)
    fun setNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("notifications_enabled", enabled).apply()
    }
    
    // Reminder settings
    fun isRemindersEnabled(): Boolean = preferences.getBoolean("reminders_enabled", true)
    fun setRemindersEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("reminders_enabled", enabled).apply()
    }
    
    fun getReminderFrequency(): ReminderFrequency {
        val frequencyName = preferences.getString("reminder_frequency", DEFAULT_REMINDER_FREQUENCY)
        return ReminderFrequency.valueOf(frequencyName ?: DEFAULT_REMINDER_FREQUENCY)
    }
    
    fun setReminderFrequency(frequency: ReminderFrequency) {
        preferences.edit().putString("reminder_frequency", frequency.name).apply()
    }
    
    // Break suggestion settings
    fun isBreakSuggestionsEnabled(): Boolean = preferences.getBoolean("break_suggestions_enabled", true)
    fun setBreakSuggestionsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("break_suggestions_enabled", enabled).apply()
    }
    
    fun getBreakInterval(): Int = preferences.getInt("break_interval", DEFAULT_BREAK_INTERVAL)
    fun setBreakInterval(minutes: Int) {
        preferences.edit().putInt("break_interval", minutes).apply()
    }
    
    fun isSmartBreakTimingEnabled(): Boolean = preferences.getBoolean("smart_break_timing", true)
    fun setSmartBreakTimingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("smart_break_timing", enabled).apply()
    }
    
    // Goal celebration settings
    fun isGoalCelebrationsEnabled(): Boolean = preferences.getBoolean("goal_celebrations_enabled", true)
    fun setGoalCelebrationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("goal_celebrations_enabled", enabled).apply()
    }
    
    fun isStreakTrackingEnabled(): Boolean = preferences.getBoolean("streak_tracking_enabled", true)
    fun setStreakTrackingEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("streak_tracking_enabled", enabled).apply()
    }
    
    // Custom alert settings
    fun isCustomAlertsEnabled(): Boolean = preferences.getBoolean("custom_alerts_enabled", true)
    fun setCustomAlertsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("custom_alerts_enabled", enabled).apply()
    }
    
    fun getCustomTriggers(): List<CustomTrigger> {
        val triggersJson = preferences.getString("custom_triggers", "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(triggersJson)
            val triggers = mutableListOf<CustomTrigger>()
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val name = jsonObject.getString("name")
                val condition = jsonObject.getString("condition")
                val message = jsonObject.getString("message")
                val priorityName = jsonObject.optString("priority", AlertPriority.MEDIUM.name)
                val priority = try {
                    AlertPriority.valueOf(priorityName)
                } catch (e: Exception) {
                    AlertPriority.MEDIUM
                }
                triggers.add(CustomTrigger(name, condition, message, priority))
            }
            triggers
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun setCustomTriggers(triggers: List<CustomTrigger>) {
        try {
            val jsonArray = JSONArray()
            triggers.forEach { trigger ->
                val jsonObject = JSONObject().apply {
                    put("name", trigger.name)
                    put("condition", trigger.condition)
                    put("message", trigger.message)
                    put("priority", trigger.priority.name)
                }
                jsonArray.put(jsonObject)
            }
            preferences.edit().putString("custom_triggers", jsonArray.toString()).apply()
        } catch (e: Exception) {
            // If serialization fails, clear the triggers
            preferences.edit().putString("custom_triggers", "[]").apply()
        }
    }
    
    // Daily goal settings
    fun getDailyGoalHours(): Int = preferences.getInt("daily_goal_hours", DEFAULT_DAILY_GOAL_HOURS)
    fun getDailyGoalMinutes(): Int = preferences.getInt("daily_goal_minutes", DEFAULT_DAILY_GOAL_MINUTES)
    
    fun setDailyGoal(hours: Int, minutes: Int) {
        preferences.edit()
            .putInt("daily_goal_hours", hours)
            .putInt("daily_goal_minutes", minutes)
            .apply()
    }
    
    fun getDailyGoalMs(): Long {
        val hours = getDailyGoalHours()
        val minutes = getDailyGoalMinutes()
        return (hours * 60 + minutes) * 60 * 1000L
    }
    
    // Quiet hours settings
    fun isQuietHoursEnabled(): Boolean = preferences.getBoolean("quiet_hours_enabled", false)
    fun setQuietHoursEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("quiet_hours_enabled", enabled).apply()
    }
    
    fun getQuietHoursStart(): Int = preferences.getInt("quiet_hours_start", DEFAULT_QUIET_HOURS_START)
    fun getQuietHoursEnd(): Int = preferences.getInt("quiet_hours_end", DEFAULT_QUIET_HOURS_END)
    
    fun setQuietHours(startHour: Int, endHour: Int) {
        preferences.edit()
            .putInt("quiet_hours_start", startHour)
            .putInt("quiet_hours_end", endHour)
            .apply()
    }
    
    fun isInQuietHours(): Boolean {
        if (!isQuietHoursEnabled()) return false
        
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val startHour = getQuietHoursStart()
        val endHour = getQuietHoursEnd()
        
        return if (startHour <= endHour) {
            currentHour in startHour..endHour
        } else {
            currentHour >= startHour || currentHour <= endHour
        }
    }
    
    // Notification timing settings
    fun getMorningReminderTime(): Int = preferences.getInt("morning_reminder_time", 9) // 9 AM
    fun setMorningReminderTime(hour: Int) {
        preferences.edit().putInt("morning_reminder_time", hour).apply()
    }
    
    fun getAfternoonReminderTime(): Int = preferences.getInt("afternoon_reminder_time", 15) // 3 PM
    fun setAfternoonReminderTime(hour: Int) {
        preferences.edit().putInt("afternoon_reminder_time", hour).apply()
    }
    
    fun getEveningReminderTime(): Int = preferences.getInt("evening_reminder_time", 20) // 8 PM
    fun setEveningReminderTime(hour: Int) {
        preferences.edit().putInt("evening_reminder_time", hour).apply()
    }
    
    // Vibration and sound settings
    fun isVibrationEnabled(): Boolean = preferences.getBoolean("vibration_enabled", true)
    fun setVibrationEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("vibration_enabled", enabled).apply()
    }
    
    fun isSoundEnabled(): Boolean = preferences.getBoolean("sound_enabled", true)
    fun setSoundEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("sound_enabled", enabled).apply()
    }
    
    fun getNotificationSound(): String = preferences.getString("notification_sound", "default") ?: "default"
    fun setNotificationSound(sound: String) {
        preferences.edit().putString("notification_sound", sound).apply()
    }
    
    // Advanced settings
    fun isAdaptiveNotificationsEnabled(): Boolean = preferences.getBoolean("adaptive_notifications", true)
    fun setAdaptiveNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("adaptive_notifications", enabled).apply()
    }
    
    fun isLearningEnabled(): Boolean = preferences.getBoolean("learning_enabled", true)
    fun setLearningEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("learning_enabled", enabled).apply()
    }
    
    fun getNotificationSensitivity(): NotificationSensitivity {
        val sensitivityName = preferences.getString("notification_sensitivity", NotificationSensitivity.MEDIUM.name)
        return NotificationSensitivity.valueOf(sensitivityName ?: NotificationSensitivity.MEDIUM.name)
    }
    
    fun setNotificationSensitivity(sensitivity: NotificationSensitivity) {
        preferences.edit().putString("notification_sensitivity", sensitivity.name).apply()
    }
    
    // Reset to defaults
    fun resetToDefaults() {
        preferences.edit().clear().apply()
    }
    
    // Export/Import settings
    fun exportSettings(): String {
        // In a real implementation, you would export settings as JSON
        return "{}"
    }
    
    fun importSettings(settingsJson: String) {
        // In a real implementation, you would import settings from JSON
    }
}

// Additional enums for notification settings
enum class NotificationSensitivity {
    LOW, MEDIUM, HIGH
}

enum class NotificationSound {
    DEFAULT, GENTLE, CHIME, BELL, NONE
}

enum class NotificationVibration {
    DEFAULT, SHORT, LONG, CUSTOM, NONE
}
