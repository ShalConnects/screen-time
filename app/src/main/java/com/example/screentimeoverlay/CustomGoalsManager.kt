package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar
import java.util.Date

/**
 * Manages custom goals for different days of the week and user profiles
 */
class CustomGoalsManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("custom_goals", Context.MODE_PRIVATE)
    
    companion object {
        private const val DEFAULT_WEEKDAY_HOURS = 8
        private const val DEFAULT_WEEKDAY_MINUTES = 0
        private const val DEFAULT_WEEKEND_HOURS = 6
        private const val DEFAULT_WEEKEND_MINUTES = 0
        private const val DEFAULT_WORK_HOURS = 9
        private const val DEFAULT_WORK_MINUTES = 0
        private const val DEFAULT_PERSONAL_HOURS = 4
        private const val DEFAULT_PERSONAL_MINUTES = 0
    }
    
    /**
     * Set custom goal for weekdays
     */
    fun setWeekdayGoal(hours: Int, minutes: Int) {
        prefs.edit()
            .putInt("weekday_hours", hours)
            .putInt("weekday_minutes", minutes)
            .apply()
    }
    
    /**
     * Set custom goal for weekends
     */
    fun setWeekendGoal(hours: Int, minutes: Int) {
        prefs.edit()
            .putInt("weekend_hours", hours)
            .putInt("weekend_minutes", minutes)
            .apply()
    }
    
    /**
     * Set custom goal for specific day of week
     */
    fun setDayGoal(dayOfWeek: Int, hours: Int, minutes: Int) {
        prefs.edit()
            .putInt("day_${dayOfWeek}_hours", hours)
            .putInt("day_${dayOfWeek}_minutes", minutes)
            .apply()
    }
    
    /**
     * Set goal for specific profile
     */
    fun setProfileGoal(profileName: String, hours: Int, minutes: Int) {
        prefs.edit()
            .putInt("profile_${profileName}_hours", hours)
            .putInt("profile_${profileName}_minutes", minutes)
            .apply()
    }
    
    /**
     * Get goal for current day
     */
    fun getCurrentDayGoal(): DayGoal {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        
        return if (isWeekend) {
            getWeekendGoal()
        } else {
            getWeekdayGoal()
        }
    }
    
    /**
     * Get goal for specific day of week
     */
    fun getDayGoal(dayOfWeek: Int): DayGoal {
        val hours = prefs.getInt("day_${dayOfWeek}_hours", -1)
        val minutes = prefs.getInt("day_${dayOfWeek}_minutes", -1)
        
        return if (hours != -1 && minutes != -1) {
            DayGoal(hours, minutes, true)
        } else {
            // Fall back to weekday/weekend defaults
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            if (isWeekend) getWeekendGoal() else getWeekdayGoal()
        }
    }
    
    /**
     * Get weekday goal
     */
    fun getWeekdayGoal(): DayGoal {
        val hours = prefs.getInt("weekday_hours", DEFAULT_WEEKDAY_HOURS)
        val minutes = prefs.getInt("weekday_minutes", DEFAULT_WEEKDAY_MINUTES)
        return DayGoal(hours, minutes, true)
    }
    
    /**
     * Get weekend goal
     */
    fun getWeekendGoal(): DayGoal {
        val hours = prefs.getInt("weekend_hours", DEFAULT_WEEKEND_HOURS)
        val minutes = prefs.getInt("weekend_minutes", DEFAULT_WEEKEND_MINUTES)
        return DayGoal(hours, minutes, true)
    }
    
    /**
     * Get goal for specific profile
     */
    fun getProfileGoal(profileName: String): DayGoal {
        val hours = prefs.getInt("profile_${profileName}_hours", -1)
        val minutes = prefs.getInt("profile_${profileName}_minutes", -1)
        
        return if (hours != -1 && minutes != -1) {
            DayGoal(hours, minutes, true)
        } else {
            // Fall back to current day goal
            getCurrentDayGoal()
        }
    }
    
    /**
     * Get goal in milliseconds
     */
    fun getCurrentDayGoalMs(): Long {
        val goal = getCurrentDayGoal()
        return (goal.hours * 60 + goal.minutes) * 60 * 1000L
    }
    
    /**
     * Get goal for specific day in milliseconds
     */
    fun getDayGoalMs(dayOfWeek: Int): Long {
        val goal = getDayGoal(dayOfWeek)
        return (goal.hours * 60 + goal.minutes) * 60 * 1000L
    }
    
    /**
     * Get goal for profile in milliseconds
     */
    fun getProfileGoalMs(profileName: String): Long {
        val goal = getProfileGoal(profileName)
        return (goal.hours * 60 + goal.minutes) * 60 * 1000L
    }
    
    /**
     * Check if custom goals are enabled
     */
    fun isCustomGoalsEnabled(): Boolean = prefs.getBoolean("custom_goals_enabled", false)
    
    /**
     * Enable/disable custom goals
     */
    fun setCustomGoalsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("custom_goals_enabled", enabled).apply()
    }
    
    /**
     * Get all weekly goals
     */
    fun getAllWeeklyGoals(): Map<Int, DayGoal> {
        val goals = mutableMapOf<Int, DayGoal>()
        for (day in Calendar.SUNDAY..Calendar.SATURDAY) {
            goals[day] = getDayGoal(day)
        }
        return goals
    }
    
    /**
     * Reset all goals to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Get goal progress for current day
     */
    fun getGoalProgress(currentUsageMs: Long): GoalProgress {
        val goal = getCurrentDayGoal()
        val goalMs = getCurrentDayGoalMs()
        val progressPercentage = if (goalMs > 0) {
            ((currentUsageMs.toFloat() / goalMs.toFloat()) * 100).toInt()
        } else {
            0
        }
        
        val isOverGoal = currentUsageMs > goalMs
        val remainingMs = if (isOverGoal) 0 else goalMs - currentUsageMs
        
        return GoalProgress(
            goal = Goal(
                id = "current_day",
                name = "Daily Goal",
                type = GoalType.DAILY_LIMIT,
                targetValue = goalMs,
                startDate = Date(),
                endDate = Date(),
                isActive = true
            ),
            currentUsageMs = currentUsageMs,
            progressPercentage = progressPercentage.toDouble(),
            isOverGoal = isOverGoal,
            remainingMs = remainingMs
        )
    }
    
    /**
     * Get goal progress for specific day
     */
    fun getGoalProgressForDay(dayOfWeek: Int, currentUsageMs: Long): GoalProgress {
        val goal = getDayGoal(dayOfWeek)
        val goalMs = getDayGoalMs(dayOfWeek)
        val progressPercentage = if (goalMs > 0) {
            ((currentUsageMs.toFloat() / goalMs.toFloat()) * 100).toInt()
        } else {
            0
        }
        
        val isOverGoal = currentUsageMs > goalMs
        val remainingMs = if (isOverGoal) 0 else goalMs - currentUsageMs
        
        return GoalProgress(
            goal = Goal(
                id = "day_${dayOfWeek}",
                name = "Daily Goal",
                type = GoalType.DAILY_LIMIT,
                targetValue = goalMs,
                startDate = Date(),
                endDate = Date(),
                isActive = true
            ),
            currentUsageMs = currentUsageMs,
            progressPercentage = progressPercentage.toDouble(),
            isOverGoal = isOverGoal,
            remainingMs = remainingMs
        )
    }
}

data class DayGoal(
    val hours: Int,
    val minutes: Int,
    val isCustom: Boolean = false
) {
    fun getTotalMinutes(): Int = hours * 60 + minutes
    
    fun getDisplayString(): String = String.format("%02d:%02d", hours, minutes)
}

