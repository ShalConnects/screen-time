package com.example.screentimeoverlay

import java.util.*

/**
 * Common data classes and enums used across the application
 * This file consolidates all shared data structures to avoid redeclaration errors
 */

// Enums
enum class RecommendationType {
    USAGE_WARNING,
    BREAK_SUGGESTION,
    APP_SUGGESTION,
    HABIT_FORMATION,
    PRODUCTIVITY_INSIGHT,
    GOAL_REMINDER,
    FOCUS_MODE,
    BREAK_TIME,
    APP_BLOCKING,
    TIME_LIMIT,
    CONTENT_FILTERING,
    PARENTAL_CONTROL
}

enum class TrendDirection {
    IMPROVING,
    STABLE,
    DECLINING,
    INCREASING,
    DECREASING
}

enum class RecommendationPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class TimeOfDay {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT
}

enum class RecommendationFeedback {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}

enum class GoalType {
    DAILY_LIMIT,
    WEEKLY_LIMIT,
    APP_SPECIFIC,
    CATEGORY_LIMIT,
    FOCUS_TIME,
    BREAK_TIME,
    PRODUCTIVITY_SCORE,
    BREAK_GOAL,
    FOCUS_GOAL,
    STREAK_GOAL
}

enum class BreakType {
    SHORT_BREAK,
    MEDIUM_BREAK,
    LONG_BREAK,
    MICRO_BREAK,
    WALK_BREAK,
    LUNCH_BREAK,
    EVENING_BREAK,
    FOCUS_BREAK
}

enum class AlertType {
    TIME_LIMIT,
    APP_BLOCKING,
    CONTENT_FILTERING,
    PARENTAL_CONTROL,
    PRODUCTIVITY_REMINDER,
    BREAK_REMINDER,
    TIME_LIMIT_VIOLATION,
    APP_BLOCKING_VIOLATION,
    CONTENT_VIOLATION
}

enum class AlertPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class PerformanceCategory {
    MEMORY,
    BATTERY,
    PERFORMANCE,
    GENERAL
}

// Data classes
data class AppInfo(
    val packageName: String,
    val appName: String,
    val category: String,
    val isWhitelisted: Boolean,
    val isBlacklisted: Boolean,
    val name: String = appName,
    val isProductive: Boolean = false,
    val isTimeWasting: Boolean = false,
    val productivityScore: Int = 0,
    val timeWastingScore: Int = 0,
    val focusScore: Int = 0,
    val benefits: List<String> = emptyList()
)

data class AppUsageData(
    val packageName: String,
    val appName: String,
    val timeSpent: Long,
    val category: String,
    val isProductive: Boolean,
    val isTimeWasting: Boolean = false,
    val productivityScore: Int = 0,
    val timeWastingScore: Int = 0,
    val focusScore: Int = 0,
    val name: String = appName
)

data class UsageData(
    val totalTime: Long,
    val appUsage: List<AppUsageData>,
    val sessionCount: Int,
    val focusScore: Int,
    val timestamp: Date
)

data class UsagePatterns(
    val averageDailyUsage: Long,
    val peakHours: List<Int>,
    val mostUsedApps: List<String>,
    val focusPatterns: List<FocusPattern>
)

data class UsageTrends(
    val usageTrend: TrendDirection,
    val focusTrend: TrendDirection,
    val productivityTrend: TrendDirection
)

data class FocusPattern(
    val hour: Int,
    val focusScore: Int,
    val sessionCount: Int
)

data class ProductivityInsights(
    val date: Date,
    val productivityScore: Int,
    val focusScore: Int,
    val distractionScore: Int,
    val recommendations: List<String>,
    val trends: UsageTrends,
    val productivityRatio: Int = 0,
    val distractionRatio: Int = 0,
    val topDistractingApps: List<AppUsageData> = emptyList()
)

data class GoalProgress(
    val goal: Goal,
    val currentUsageMs: Long,
    val progressPercentage: Double,
    val isOverGoal: Boolean,
    val remainingMs: Long
)

data class Goal(
    val id: String,
    val name: String,
    val type: GoalType,
    val targetValue: Long,
    val startDate: Date,
    val endDate: Date,
    val isActive: Boolean
)

data class DateRange(
    val startDate: Date,
    val endDate: Date
)

data class BreakRecommendation(
    val time: String,
    val duration: String,
    val type: BreakType,
    val reason: String,
    val priority: Int,
    val confidence: Int,
    val benefits: List<String>
)

data class AppRecommendation(
    val currentApp: String,
    val alternativeApp: String,
    val category: String,
    val reason: String,
    val confidence: Int,
    val benefits: List<String>,
    val timeSaved: Long,
    val productivityGain: Int
)

data class PersonalizedRecommendation(
    val type: RecommendationType,
    val title: String,
    val description: String,
    val priority: RecommendationPriority,
    val action: String,
    val confidence: Int
)

data class DailyInsights(
    val date: Date,
    val usagePredictions: UsagePredictions,
    val breakRecommendations: List<BreakRecommendation>,
    val appRecommendations: List<AppRecommendation>,
    val habitInsights: HabitInsights,
    val overallScore: Int
)

data class PeriodInsights(
    val dateRange: DateRange,
    val usagePredictions: UsagePredictions,
    val patterns: UsagePatterns,
    val trends: UsageTrends,
    val recommendations: List<String>
)

data class UsagePredictions(
    val predictedTotalTime: Long,
    val isExceedingGoal: Boolean,
    val excessTime: Long,
    val confidence: Int,
    val hourlyPredictions: List<HourlyPrediction> = emptyList(),
    val riskFactors: List<String>
)

data class HourlyPrediction(
    val hour: Int,
    val predictedUsage: Long,
    val confidence: Int
)

data class HabitInsights(
    val overallScore: Int,
    val habits: List<Habit>,
    val recommendations: List<String>,
    val activeHabits: List<Habit> = habits,
    val habitProgress: Map<String, Int> = emptyMap(),
    val habitStreaks: Map<String, Int> = emptyMap(),
    val habitRecommendations: List<String> = recommendations,
    val nextHabit: Habit? = null
)

data class Habit(
    val name: String,
    val description: String,
    val strength: Int,
    val frequency: Int,
    val id: String = name,
    val type: HabitType = HabitType.PRODUCTIVITY,
    val startDate: Date = Date(),
    val isActive: Boolean = true,
    val difficulty: Int = 1
)

enum class HabitType {
    PRODUCTIVITY,
    BREAK,
    FOCUS,
    HEALTH,
    REDUCE_SCREEN_TIME,
    IMPROVE_FOCUS,
    BREAK_ADDICTION,
    PRODUCTIVE_USAGE
}

enum class HabitFrequency {
    DAILY,
    WEEKLY,
    MONTHLY
}

data class FamilyAlert(
    val id: String,
    val type: AlertType,
    val message: String,
    val timestamp: Date,
    val priority: AlertPriority
)

data class HabitProgress(
    val habitId: String,
    val progress: Int,
    val streak: Int,
    val lastUpdate: Date,
    val totalCompletions: Int = 0,
    val completionRate: Int = 0,
    val progressPercentage: Int = progress,
    val daysSinceStart: Int = 0
)

data class HabitRecommendation(
    val id: String,
    val title: String,
    val description: String,
    val type: HabitType,
    val difficulty: Int
)

// Utility classes
class WorkHoursAnalyzer {
    fun analyzeWorkHours(calendarData: List<CalendarEvent>): WorkPatterns {
        return WorkPatterns(patterns = listOf("Standard 9-5", "Flexible hours"))
    }
}

data class UserPreferences(
    val preferredCategories: List<String>,
    val focusOnProductivity: Boolean,
    val reduceScreenTime: Boolean,
    val improveFocus: Boolean
)

data class RecommendationFeedbackRecord(
    val recommendation: AppRecommendation,
    val feedback: RecommendationFeedback,
    val timestamp: Date
)

// Constants
object Constants {
    const val MEDIUM_BREAK = "MEDIUM_BREAK"
    const val WORK_LIMIT = "WORK_LIMIT"
    const val FOCUS_MODE = "FOCUS_MODE"
    const val RELAXATION = "RELAXATION"
    const val TIME_LIMIT = "TIME_LIMIT"
    const val APP_BLOCKING = "APP_BLOCKING"
    const val CONTENT_FILTERING = "CONTENT_FILTERING"
    const val MEMORY = "MEMORY"
    const val BATTERY = "BATTERY"
    const val PERFORMANCE = "PERFORMANCE"
    const val GENERAL = "GENERAL"
}
