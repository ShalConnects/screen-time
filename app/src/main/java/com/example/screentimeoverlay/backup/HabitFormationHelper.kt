package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * AI-powered habit formation helper that helps users build better digital habits
 * based on behavioral psychology and usage patterns
 */
class HabitFormationHelper(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("habit_formation", Context.MODE_PRIVATE)
    private val habitTracker = HabitTracker()
    private val behaviorAnalyzer = BehaviorAnalyzer()
    
    /**
     * Get habit insights for the current day
     */
    fun getHabitInsights(currentTime: Date, usageData: UsageData): HabitInsights {
        val activeHabits = getActiveHabits()
        val habitProgress = calculateHabitProgress(activeHabits, usageData)
        val habitStreaks = calculateHabitStreaks(activeHabits)
        val habitRecommendations = generateHabitRecommendations(activeHabits, usageData)
        
        return HabitInsights(
            overallScore = calculateOverallHabitScore(habitProgress, habitStreaks),
            habits = activeHabits,
            recommendations = habitRecommendations.map { it.title },
            activeHabits = activeHabits,
            habitProgress = habitProgress.mapValues { it.value.progress },
            habitStreaks = habitStreaks,
            habitRecommendations = habitRecommendations.map { it.title },
            nextHabit = suggestNextHabit(usageData)
        )
    }
    
    /**
     * Get habit recommendations based on usage patterns
     */
    fun getHabitRecommendations(): List<HabitRecommendation> {
        val usagePatterns = analyzeUsagePatterns()
        val currentHabits = getActiveHabits()
        val recommendations = mutableListOf<HabitRecommendation>()
        
        // Analyze gaps in current habits
        val habitGaps = identifyHabitGaps(currentHabits, usagePatterns)
        habitGaps.forEach { gap ->
            recommendations.add(createHabitRecommendation(gap))
        }
        
        // Suggest habit improvements
        val habitImprovements = suggestHabitImprovements(currentHabits)
        recommendations.addAll(habitImprovements)
        
        return recommendations.sortedBy { it.difficulty }
    }
    
    /**
     * Track habit completion
     */
    fun trackHabitCompletion(habitId: String, completed: Boolean) {
        val habit = getHabitById(habitId)
        if (habit != null) {
            val completion = HabitCompletion(
                habitId = habitId,
                completed = completed,
                timestamp = Date(),
                context = getHabitContext()
            )
            
            habitTracker.recordCompletion(completion)
            updateHabitStreak(habitId, completed)
            
            // Provide feedback based on completion
            provideHabitFeedback(habit, completed)
        }
    }
    
    /**
     * Get habit formation progress
     */
    fun getHabitProgress(habitId: String): HabitProgress {
        val habit = getHabitById(habitId)
        if (habit == null) {
            return HabitProgress(habitId, 0, 0, Date(), 0, 0, 0, 0)
        }
        
        val completions = habitTracker.getCompletions(habitId)
        val streak = calculateStreak(completions)
        val totalCompletions = completions.count { it.completed }
        val completionRate = if (completions.isNotEmpty()) {
            (totalCompletions.toDouble() / completions.size * 100).toInt()
        } else 0
        
        val daysSinceStart = getDaysSinceStart(habit.startDate)
        val progressPercentage = if (daysSinceStart > 0) {
            (totalCompletions.toDouble() / daysSinceStart * 100).toInt()
        } else 0
        
        return HabitProgress(
            habitId = habitId,
            progress = progressPercentage,
            streak = streak,
            lastUpdate = Date(),
            totalCompletions = totalCompletions,
            completionRate = completionRate,
            progressPercentage = progressPercentage,
            daysSinceStart = daysSinceStart
        )
    }
    
    /**
     * Get habit formation tips and strategies
     */
    fun getHabitFormationTips(habitType: HabitType): List<HabitTip> {
        val tips = mutableListOf<HabitTip>()
        
        when (habitType) {
            HabitType.REDUCE_SCREEN_TIME -> {
                tips.addAll(getScreenTimeReductionTips())
            }
            HabitType.IMPROVE_FOCUS -> {
                tips.addAll(getFocusImprovementTips())
            }
            HabitType.BREAK_ADDICTION -> {
                tips.addAll(getAddictionBreakingTips())
            }
            HabitType.PRODUCTIVE_USAGE -> {
                tips.addAll(getProductiveUsageTips())
            }
            HabitType.PRODUCTIVITY -> {
                tips.addAll(getProductiveUsageTips())
            }
            HabitType.BREAK -> {
                tips.addAll(getScreenTimeReductionTips())
            }
            HabitType.FOCUS -> {
                tips.addAll(getFocusImprovementTips())
            }
            HabitType.HEALTH -> {
                tips.addAll(getScreenTimeReductionTips())
            }
        }
        
        return tips
    }
    
    /**
     * Get habit formation milestones
     */
    fun getHabitMilestones(habitId: String): List<HabitMilestone> {
        val habit = getHabitById(habitId)
        if (habit == null) return emptyList()
        
        val progress = getHabitProgress(habitId)
        val milestones = mutableListOf<HabitMilestone>()
        
        // Define milestones based on habit type
        val milestoneDays = when (habit.type) {
            HabitType.REDUCE_SCREEN_TIME -> listOf(7, 21, 30, 60, 90)
            HabitType.IMPROVE_FOCUS -> listOf(3, 7, 14, 30, 60)
            HabitType.BREAK_ADDICTION -> listOf(1, 3, 7, 14, 30)
            HabitType.PRODUCTIVE_USAGE -> listOf(5, 10, 21, 45, 90)
            HabitType.PRODUCTIVITY -> listOf(5, 10, 21, 45, 90)
            HabitType.BREAK -> listOf(3, 7, 14, 30, 60)
            HabitType.FOCUS -> listOf(3, 7, 14, 30, 60)
            HabitType.HEALTH -> listOf(7, 21, 30, 60, 90)
        }
        
        milestoneDays.forEach { days ->
            val isAchieved = progress.daysSinceStart >= days
            val isNext = !isAchieved && days > progress.daysSinceStart && 
                        milestoneDays.indexOf(days) == milestoneDays.indexOfFirst { it > progress.daysSinceStart }
            
            milestones.add(
                HabitMilestone(
                    days = days,
                    title = getMilestoneTitle(habit.type, days),
                    description = getMilestoneDescription(habit.type, days),
                    isAchieved = isAchieved,
                    isNext = isNext,
                    reward = getMilestoneReward(habit.type, days)
                )
            )
        }
        
        return milestones
    }
    
    private fun getActiveHabits(): List<Habit> {
        val habitsJson = prefs.getString("active_habits", "[]") ?: "[]"
        return try {
            JSONArray(habitsJson).let { array ->
                (0 until array.length()).map { index ->
                    val habitObj = array.getJSONObject(index)
                    Habit(
                        name = habitObj.getString("name"),
                        description = habitObj.getString("description"),
                        strength = habitObj.optInt("strength", 1),
                        frequency = habitObj.optInt("frequency", 1),
                        id = habitObj.optString("id", habitObj.getString("name")),
                        type = HabitType.valueOf(habitObj.getString("type")),
                        startDate = Date(habitObj.getLong("startDate")),
                        isActive = habitObj.getBoolean("isActive"),
                        difficulty = habitObj.getInt("difficulty")
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun calculateHabitProgress(habits: List<Habit>, usageData: UsageData): Map<String, HabitProgress> {
        val progress = mutableMapOf<String, HabitProgress>()
        
        habits.forEach { habit ->
            progress[habit.id] = getHabitProgress(habit.id)
        }
        
        return progress
    }
    
    private fun calculateHabitStreaks(habits: List<Habit>): Map<String, Int> {
        val streaks = mutableMapOf<String, Int>()
        
        habits.forEach { habit ->
            val completions = habitTracker.getCompletions(habit.id)
            streaks[habit.id] = calculateStreak(completions)
        }
        
        return streaks
    }
    
    private fun generateHabitRecommendations(habits: List<Habit>, usageData: UsageData): List<HabitRecommendation> {
        val recommendations = mutableListOf<HabitRecommendation>()
        
        habits.forEach { habit ->
            val progress = getHabitProgress(habit.id)
            if (progress.progress < 70) {
                recommendations.add(
                    HabitRecommendation(
                        id = habit.id,
                        title = "Improve ${habit.name}",
                        description = "Your completion rate is ${progress.progress}%. Try to be more consistent.",
                        type = habit.type,
                        difficulty = 2
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private fun calculateOverallHabitScore(progress: Map<String, HabitProgress>, streaks: Map<String, Int>): Int {
        if (progress.isEmpty()) return 0
        
        val averageProgress = progress.values.map { it.progressPercentage }.average()
        val averageStreak = streaks.values.average()
        
        return ((averageProgress * 0.6) + (averageStreak * 0.4)).toInt().coerceIn(0, 100)
    }
    
    private fun suggestNextHabit(usageData: UsageData): Habit? {
        val currentHabits = getActiveHabits()
        val usagePatterns = analyzeUsagePatterns()
        
        // Analyze usage patterns to suggest new habits
        val suggestions = mutableListOf<Habit>()
        
        if (usageData.totalTime > TimeUnit.HOURS.toMillis(6)) {
            suggestions.add(
                Habit(
                    name = "Reduce Screen Time",
                    description = "Limit daily screen time to 6 hours",
                    strength = 3,
                    frequency = 1,
                    id = UUID.randomUUID().toString(),
                    type = HabitType.REDUCE_SCREEN_TIME,
                    startDate = Date(),
                    isActive = true,
                    difficulty = 3
                )
            )
        }
        
        if (usageData.focusScore < 60) {
            suggestions.add(
                Habit(
                    name = "Improve Focus",
                    description = "Practice focused work sessions",
                    strength = 2,
                    frequency = 1,
                    id = UUID.randomUUID().toString(),
                    type = HabitType.IMPROVE_FOCUS,
                    startDate = Date(),
                    isActive = true,
                    difficulty = 2
                )
            )
        }
        
        return suggestions.firstOrNull()
    }
    
    private fun analyzeUsagePatterns(): UsagePatterns {
        // This would analyze usage patterns from the system
        return UsagePatterns(
            averageDailyUsage = 0L,
            peakHours = emptyList(),
            mostUsedApps = emptyList(),
            focusPatterns = emptyList()
        )
    }
    
    private fun identifyHabitGaps(currentHabits: List<Habit>, patterns: UsagePatterns): List<HabitGap> {
        val gaps = mutableListOf<HabitGap>()
        
        // Analyze gaps based on usage patterns
        if (patterns.averageDailyUsage > TimeUnit.HOURS.toMillis(8)) {
            gaps.add(
                HabitGap(
                    type = HabitType.REDUCE_SCREEN_TIME,
                    severity = HabitGapSeverity.HIGH,
                    description = "High screen time usage detected"
                )
            )
        }
        
        if (patterns.focusPatterns.any { it.focusScore < 50 }) {
            gaps.add(
                HabitGap(
                    type = HabitType.IMPROVE_FOCUS,
                    severity = HabitGapSeverity.MEDIUM,
                    description = "Low focus patterns detected"
                )
            )
        }
        
        return gaps
    }
    
    private fun createHabitRecommendation(gap: HabitGap): HabitRecommendation {
        return HabitRecommendation(
            id = "",
            title = "New Habit: ${gap.type.name}",
            description = gap.description,
            type = HabitType.PRODUCTIVITY,
            difficulty = when (gap.severity) {
                HabitGapSeverity.HIGH -> 1
                HabitGapSeverity.MEDIUM -> 2
                HabitGapSeverity.LOW -> 3
            }
        )
    }
    
    private fun suggestHabitImprovements(habits: List<Habit>): List<HabitRecommendation> {
        val improvements = mutableListOf<HabitRecommendation>()
        
        habits.forEach { habit ->
            val progress = getHabitProgress(habit.id)
            if (progress.progress < 50) {
                improvements.add(
                    HabitRecommendation(
                        id = habit.id,
                        title = "Improve ${habit.name}",
                        description = "Your completion rate is low. Consider adjusting the difficulty.",
                        type = habit.type,
                        difficulty = 2
                    )
                )
            }
        }
        
        return improvements
    }
    
    private fun getHabitById(habitId: String): Habit? {
        return getActiveHabits().find { it.id == habitId }
    }
    
    private fun calculateStreak(completions: List<HabitCompletion>): Int {
        if (completions.isEmpty()) return 0
        
        val sortedCompletions = completions.sortedByDescending { it.timestamp }
        var streak = 0
        var currentDate = Date()
        
        for (completion in sortedCompletions) {
            val completionDate = getDateOnly(completion.timestamp)
            val expectedDate = getDateOnly(currentDate)
            
            if (completionDate == expectedDate && completion.completed) {
                streak++
                currentDate = Date(currentDate.time - TimeUnit.DAYS.toMillis(1))
            } else if (completionDate == expectedDate && !completion.completed) {
                break
            } else if (completionDate < expectedDate) {
                break
            }
        }
        
        return streak
    }
    
    private fun getDaysSinceStart(startDate: Date): Int {
        val currentDate = Date()
        val diffInMillis = currentDate.time - startDate.time
        return (diffInMillis / TimeUnit.DAYS.toMillis(1)).toInt()
    }
    
    private fun updateHabitStreak(habitId: String, completed: Boolean) {
        val currentStreak = prefs.getInt("streak_$habitId", 0)
        val newStreak = if (completed) currentStreak + 1 else 0
        prefs.edit().putInt("streak_$habitId", newStreak).apply()
    }
    
    private fun provideHabitFeedback(habit: Habit, completed: Boolean) {
        val progress = getHabitProgress(habit.id)
        val message = when {
            completed && progress.streak > 7 -> "Amazing! You're building a strong habit!"
            completed && progress.streak > 3 -> "Great job! Keep up the consistency!"
            completed -> "Good work! Every completion counts."
            else -> "Don't worry, tomorrow is a new opportunity!"
        }
        
        // This would show a notification or update the UI
        Log.d("HabitFormationHelper", message)
    }
    
    private fun getHabitContext(): HabitContext {
        // This would capture the context when the habit was completed
        return HabitContext(
            timeOfDay = getTimeOfDay(),
            location = "Unknown",
            mood = "Neutral",
            energyLevel = 50
        )
    }
    
    private fun getTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..11 -> TimeOfDay.MORNING
            in 12..17 -> TimeOfDay.AFTERNOON
            in 18..22 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }
    
    private fun getDateOnly(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.DAY_OF_MONTH)}"
    }
    
    private fun getScreenTimeReductionTips(): List<HabitTip> {
        return listOf(
            HabitTip("Set app limits", "Use built-in app limits to restrict usage", HabitTipType.STRATEGY),
            HabitTip("Use grayscale mode", "Switch to grayscale to reduce visual appeal", HabitTipType.TECHNIQUE),
            HabitTip("Create phone-free zones", "Designate areas where phones aren't allowed", HabitTipType.ENVIRONMENT),
            HabitTip("Use a physical clock", "Replace phone clock with a physical one", HabitTipType.SUBSTITUTION)
        )
    }
    
    private fun getFocusImprovementTips(): List<HabitTip> {
        return listOf(
            HabitTip("Use focus mode", "Enable focus mode during work hours", HabitTipType.STRATEGY),
            HabitTip("Practice meditation", "Start with 5 minutes of daily meditation", HabitTipType.PRACTICE),
            HabitTip("Single-tasking", "Focus on one task at a time", HabitTipType.BEHAVIOR),
            HabitTip("Remove distractions", "Clear your workspace of distractions", HabitTipType.ENVIRONMENT)
        )
    }
    
    private fun getAddictionBreakingTips(): List<HabitTip> {
        return listOf(
            HabitTip("Identify triggers", "Recognize what causes the addictive behavior", HabitTipType.AWARENESS),
            HabitTip("Replace the behavior", "Find a healthy alternative activity", HabitTipType.SUBSTITUTION),
            HabitTip("Seek support", "Talk to friends or professionals about your goals", HabitTipType.SUPPORT),
            HabitTip("Track progress", "Keep a journal of your progress", HabitTipType.TRACKING)
        )
    }
    
    private fun getProductiveUsageTips(): List<HabitTip> {
        return listOf(
            HabitTip("Use productivity apps", "Switch to apps that help you achieve goals", HabitTipType.STRATEGY),
            HabitTip("Set specific goals", "Define clear objectives for your screen time", HabitTipType.PLANNING),
            HabitTip("Time-blocking", "Schedule specific times for different activities", HabitTipType.TECHNIQUE),
            HabitTip("Regular breaks", "Take breaks to maintain productivity", HabitTipType.BEHAVIOR)
        )
    }
    
    private fun getMilestoneTitle(habitType: HabitType, days: Int): String {
        return when (habitType) {
            HabitType.REDUCE_SCREEN_TIME -> when (days) {
                7 -> "First Week Complete"
                21 -> "Habit Formation"
                30 -> "One Month Strong"
                60 -> "Two Months"
                90 -> "Three Months"
                else -> "$days Days"
            }
            HabitType.IMPROVE_FOCUS -> when (days) {
                3 -> "First Steps"
                7 -> "One Week"
                14 -> "Two Weeks"
                30 -> "One Month"
                60 -> "Two Months"
                else -> "$days Days"
            }
            HabitType.BREAK_ADDICTION -> when (days) {
                1 -> "First Day"
                3 -> "Three Days"
                7 -> "One Week"
                14 -> "Two Weeks"
                30 -> "One Month"
                else -> "$days Days"
            }
            HabitType.PRODUCTIVE_USAGE -> when (days) {
                5 -> "First Week"
                10 -> "Ten Days"
                21 -> "Three Weeks"
                45 -> "Six Weeks"
                90 -> "Three Months"
                else -> "$days Days"
            }
            HabitType.PRODUCTIVITY -> when (days) {
                5 -> "First Week"
                10 -> "Ten Days"
                21 -> "Three Weeks"
                45 -> "Six Weeks"
                90 -> "Three Months"
                else -> "$days Days"
            }
            HabitType.BREAK -> when (days) {
                3 -> "First Steps"
                7 -> "One Week"
                14 -> "Two Weeks"
                30 -> "One Month"
                60 -> "Two Months"
                else -> "$days Days"
            }
            HabitType.FOCUS -> when (days) {
                3 -> "First Steps"
                7 -> "One Week"
                14 -> "Two Weeks"
                30 -> "One Month"
                60 -> "Two Months"
                else -> "$days Days"
            }
            HabitType.HEALTH -> when (days) {
                7 -> "First Week Complete"
                21 -> "Habit Formation"
                30 -> "One Month Strong"
                60 -> "Two Months"
                90 -> "Three Months"
                else -> "$days Days"
            }
        }
    }
    
    private fun getMilestoneDescription(habitType: HabitType, days: Int): String {
        return when (habitType) {
            HabitType.REDUCE_SCREEN_TIME -> "You've successfully reduced your screen time for $days days!"
            HabitType.IMPROVE_FOCUS -> "You've been practicing focus for $days days!"
            HabitType.BREAK_ADDICTION -> "You've been free from the addictive behavior for $days days!"
            HabitType.PRODUCTIVE_USAGE -> "You've been using your screen time productively for $days days!"
            HabitType.PRODUCTIVITY -> "You've been using your screen time productively for $days days!"
            HabitType.BREAK -> "You've been taking regular breaks for $days days!"
            HabitType.FOCUS -> "You've been practicing focus for $days days!"
            HabitType.HEALTH -> "You've been maintaining healthy screen habits for $days days!"
        }
    }
    
    private fun getMilestoneReward(habitType: HabitType, days: Int): String {
        return when (habitType) {
            HabitType.REDUCE_SCREEN_TIME -> "Treat yourself to a relaxing activity"
            HabitType.IMPROVE_FOCUS -> "You've earned a focused work session"
            HabitType.BREAK_ADDICTION -> "Celebrate your progress with a healthy reward"
            HabitType.PRODUCTIVE_USAGE -> "You've earned some well-deserved rest"
            HabitType.PRODUCTIVITY -> "You've earned some well-deserved rest"
            HabitType.BREAK -> "Treat yourself to a relaxing activity"
            HabitType.FOCUS -> "You've earned a focused work session"
            HabitType.HEALTH -> "Celebrate your progress with a healthy reward"
        }
    }
    
    /**
     * Update the model with new usage data
     */
    fun updateModel(usageData: UsageData) {
        // Update habit formation model based on usage data
        val habitData = JSONObject().apply {
            put("timestamp", usageData.timestamp.time)
            put("totalTime", usageData.totalTime)
            put("focusScore", usageData.focusScore)
            put("sessionCount", usageData.sessionCount)
        }
        
        val dataArray = try {
            JSONArray(prefs.getString("habit_formation_data", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        
        dataArray.put(habitData)
        prefs.edit().putString("habit_formation_data", dataArray.toString()).apply()
    }
    
    companion object {
        private const val TAG = "HabitFormationHelper"
    }
}

// Data classes



data class HabitCompletion(
    val habitId: String,
    val completed: Boolean,
    val timestamp: Date,
    val context: HabitContext
)

data class HabitContext(
    val timeOfDay: TimeOfDay,
    val location: String,
    val mood: String,
    val energyLevel: Int
)

data class HabitGap(
    val type: HabitType,
    val severity: HabitGapSeverity,
    val description: String
)

data class HabitTip(
    val title: String,
    val description: String,
    val type: HabitTipType
)

data class HabitMilestone(
    val days: Int,
    val title: String,
    val description: String,
    val isAchieved: Boolean,
    val isNext: Boolean,
    val reward: String
)



enum class HabitGapSeverity {
    LOW,
    MEDIUM,
    HIGH
}

enum class HabitTipType {
    STRATEGY,
    TECHNIQUE,
    ENVIRONMENT,
    SUBSTITUTION,
    PRACTICE,
    BEHAVIOR,
    AWARENESS,
    SUPPORT,
    TRACKING,
    PLANNING
}

class HabitTracker {
    fun recordCompletion(completion: HabitCompletion) {
        // Record habit completion
    }
    
    fun getCompletions(habitId: String): List<HabitCompletion> {
        // Get habit completions
        return emptyList()
    }
}

class BehaviorAnalyzer {
    fun analyzeBehavior(usageData: UsageData): BehaviorAnalysis {
        // Analyze behavior patterns
        return BehaviorAnalysis(
            patterns = emptyList(),
            triggers = emptyList(),
            recommendations = emptyList()
        )
    }
}

data class BehaviorAnalysis(
    val patterns: List<String>,
    val triggers: List<String>,
    val recommendations: List<String>
)
