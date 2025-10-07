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
 * AI-powered insights manager for usage predictions, break recommendations, 
 * app suggestions, and habit formation
 */
class AIInsightsManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_insights", Context.MODE_PRIVATE)
    private val usagePredictor: UsagePredictor = UsagePredictor(context)
    private val breakTimeOptimizer: BreakTimeOptimizer = BreakTimeOptimizer(context)
    private val appRecommendationEngine: AppRecommendationEngine = AppRecommendationEngine(context)
    private val habitFormationHelper: HabitFormationHelper = HabitFormationHelper(context)
    
    /**
     * Get comprehensive AI insights for the current day
     */
    fun getDailyInsights(): DailyInsights {
        val currentTime = Date()
        val usageData = getCurrentUsageData()
        val predictions = usagePredictor.predictDailyUsage(currentTime, usageData)
        val breakRecommendations = breakTimeOptimizer.getOptimalBreakTimes(currentTime, usageData)
        val appRecommendations = appRecommendationEngine.getAppRecommendations(usageData)
        val habitInsights = habitFormationHelper.getHabitInsights(currentTime, usageData)
        
        return DailyInsights(
            date = currentTime,
            usagePredictions = predictions,
            breakRecommendations = breakRecommendations,
            appRecommendations = appRecommendations,
            habitInsights = habitInsights,
            overallScore = calculateOverallScore(predictions, habitInsights)
        )
    }
    
    /**
     * Get personalized recommendations based on usage patterns
     */
    fun getPersonalizedRecommendations(): List<PersonalizedRecommendation> {
        val usagePatterns = getUsagePatterns()
        val recommendations = mutableListOf<PersonalizedRecommendation>()
        
        // Usage prediction recommendations
        val predictions = usagePredictor.getUsagePredictions()
        if (predictions.isExceedingGoal) {
            recommendations.add(
                PersonalizedRecommendation(
                    type = RecommendationType.USAGE_WARNING,
                    title = "Goal Exceeded Prediction",
                    description = "You're on track to exceed your daily goal by ${predictions.excessTime}",
                    priority = RecommendationPriority.HIGH,
                    action = "Consider taking a break or switching to productive apps",
                    confidence = predictions.confidence
                )
            )
        }
        
        // Break time recommendations
        val breakTimes = breakTimeOptimizer.getOptimalBreakTimes(Date(), getCurrentUsageData())
        if (breakTimes.isNotEmpty()) {
            recommendations.add(
                PersonalizedRecommendation(
                    type = RecommendationType.BREAK_SUGGESTION,
                    title = "Optimal Break Time",
                    description = "Best time for a break: ${breakTimes.first().time}",
                    priority = RecommendationPriority.MEDIUM,
                    action = "Take a ${breakTimes.first().duration} break now",
                    confidence = breakTimes.first().confidence
                )
            )
        }
        
        // App recommendations
        val appRecs = appRecommendationEngine.getAppRecommendations(getCurrentUsageData())
        if (appRecs.isNotEmpty()) {
            recommendations.add(
                PersonalizedRecommendation(
                    type = RecommendationType.APP_SUGGESTION,
                    title = "Better App Alternative",
                    description = "Consider using ${appRecs.first().alternativeApp} instead of ${appRecs.first().currentApp}",
                    priority = RecommendationPriority.MEDIUM,
                    action = "Switch to a more productive app",
                    confidence = appRecs.first().confidence
                )
            )
        }
        
        // Habit formation recommendations
        val habitRecs = habitFormationHelper.getHabitRecommendations()
        if (habitRecs.isNotEmpty()) {
            recommendations.add(
                PersonalizedRecommendation(
                    type = RecommendationType.HABIT_FORMATION,
                    title = "Build Better Habits",
                    description = habitRecs.first().description,
                    priority = RecommendationPriority.LOW,
                    action = "Start building this habit",
                    confidence = 75
                )
            )
        }
        
        return recommendations.sortedBy { it.priority.ordinal }
    }
    
    /**
     * Get insights for a specific time period
     */
    fun getInsightsForPeriod(startDate: Date, endDate: Date): PeriodInsights {
        val usageData = getUsageDataForPeriod(startDate, endDate)
        val predictions = usagePredictor.predictPeriodUsage(startDate, endDate, usageData)
        val patterns = analyzeUsagePatterns(usageData)
        val trends = analyzeTrends(usageData)
        
        return PeriodInsights(
            dateRange = DateRange(startDate, endDate),
            usagePredictions = predictions,
            patterns = patterns,
            trends = trends,
            recommendations = generatePeriodRecommendations(patterns, trends)
        )
    }
    
    /**
     * Update AI model with new usage data
     */
    fun updateModel(usageData: UsageData) {
        usagePredictor.updateModel(usageData)
        breakTimeOptimizer.updateModel(usageData)
        appRecommendationEngine.updateModel(usageData)
        habitFormationHelper.updateModel(usageData)
        
        // Save insights for future reference
        saveInsights(usageData)
    }
    
    private fun getCurrentUsageData(): UsageData {
        // This would integrate with the existing usage tracking system
        return UsageData(
            totalTime = 0L,
            appUsage = emptyList(),
            sessionCount = 0,
            focusScore = 0,
            timestamp = Date()
        )
    }
    
    private fun getUsageDataForPeriod(startDate: Date, endDate: Date): List<UsageData> {
        // This would retrieve historical usage data
        return emptyList()
    }
    
    private fun getUsagePatterns(): UsagePatterns {
        // This would analyze historical patterns
        return UsagePatterns(
            averageDailyUsage = 0L,
            peakHours = emptyList(),
            mostUsedApps = emptyList(),
            focusPatterns = emptyList()
        )
    }
    
    private fun analyzeUsagePatterns(usageData: List<UsageData>): UsagePatterns {
        // Analyze patterns in the usage data
        return UsagePatterns(
            averageDailyUsage = 0L,
            peakHours = emptyList(),
            mostUsedApps = emptyList(),
            focusPatterns = emptyList()
        )
    }
    
    private fun analyzeTrends(usageData: List<UsageData>): UsageTrends {
        // Analyze trends in the usage data
        return UsageTrends(
            usageTrend = TrendDirection.STABLE,
            focusTrend = TrendDirection.IMPROVING,
            productivityTrend = TrendDirection.IMPROVING
        )
    }
    
    private fun generatePeriodRecommendations(patterns: UsagePatterns, trends: UsageTrends): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (trends.usageTrend == TrendDirection.INCREASING) {
            recommendations.add("Consider setting stricter daily limits")
        }
        
        if (trends.focusTrend == TrendDirection.DECLINING) {
            recommendations.add("Try using focus mode during work hours")
        }
        
        if (trends.productivityTrend == TrendDirection.IMPROVING) {
            recommendations.add("Great job! Keep up the good work")
        }
        
        return recommendations
    }
    
    private fun calculateOverallScore(predictions: UsagePredictions, habitInsights: HabitInsights): Int {
        // Calculate overall AI insights score
        val predictionScore = if (predictions.isExceedingGoal) 30 else 80
        val habitScore = habitInsights.overallScore
        return (predictionScore + habitScore) / 2
    }
    
    private fun saveInsights(usageData: UsageData) {
        val insightsJson = JSONObject().apply {
            put("timestamp", usageData.timestamp.time)
            put("totalTime", usageData.totalTime)
            put("sessionCount", usageData.sessionCount)
            put("focusScore", usageData.focusScore)
        }
        
        val insightsArray = try {
            JSONArray(prefs.getString("insights_history", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        
        insightsArray.put(insightsJson)
        prefs.edit().putString("insights_history", insightsArray.toString()).apply()
    }
    
    companion object {
        private const val TAG = "AIInsightsManager"
    }
}


