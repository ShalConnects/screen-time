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
 * AI-powered app recommendation engine that suggests alternatives
 * to time-wasting apps based on usage patterns and productivity goals
 */
class AppRecommendationEngine(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_recommendations", Context.MODE_PRIVATE)
    private val appDatabase = AppDatabase()
    private val recommendationHistory = mutableListOf<AppRecommendation>()
    
    /**
     * Get app recommendations based on current usage data
     */
    fun getAppRecommendations(usageData: UsageData): List<AppRecommendation> {
        val recommendations = mutableListOf<AppRecommendation>()
        val timeWastingApps = identifyTimeWastingApps(usageData)
        
        timeWastingApps.forEach { app ->
            val alternatives = findAlternatives(app)
            if (alternatives.isNotEmpty()) {
                recommendations.add(
                    AppRecommendation(
                        currentApp = app.appName,
                        alternativeApp = alternatives.first().appName,
                        category = app.category,
                        reason = generateRecommendationReason(app, alternatives.first()),
                        confidence = calculateConfidence(app, alternatives.first()),
                        benefits = alternatives.first().benefits,
                        timeSaved = calculateTimeSaved(app, alternatives.first()),
                        productivityGain = calculateProductivityGain(app, alternatives.first())
                    )
                )
            }
        }
        
        return recommendations.sortedByDescending { it.confidence }
    }
    
    /**
     * Get personalized app recommendations based on user preferences
     */
    fun getPersonalizedRecommendations(): List<AppRecommendation> {
        val userPreferences = getUserPreferences()
        val usagePatterns = getUsagePatterns()
        val recommendations = mutableListOf<AppRecommendation>()
        
        // Get recommendations based on user preferences
        val preferredCategories = userPreferences.preferredCategories
        val timeWastingApps = identifyTimeWastingAppsFromPatterns(usagePatterns)
        
        timeWastingApps.forEach { app ->
            val alternatives = findAlternativesInCategories(app, preferredCategories)
            if (alternatives.isNotEmpty()) {
                recommendations.add(
                    AppRecommendation(
                        currentApp = app.appName,
                        alternativeApp = alternatives.first().appName,
                        category = app.category,
                        reason = generatePersonalizedReason(app, alternatives.first(), userPreferences),
                        confidence = calculatePersonalizedConfidence(app, alternatives.first(), userPreferences),
                        benefits = alternatives.first().benefits,
                        timeSaved = calculateTimeSaved(app, alternatives.first()),
                        productivityGain = calculateProductivityGain(app, alternatives.first())
                    )
                )
            }
        }
        
        return recommendations.sortedByDescending { it.confidence }
    }
    
    /**
     * Get recommendations for specific categories
     */
    fun getCategoryRecommendations(category: String): List<AppRecommendation> {
        val categoryApps = appDatabase.getAppsByCategory(category)
        val timeWastingApps = categoryApps.filter { it.isTimeWasting }
        val recommendations = mutableListOf<AppRecommendation>()
        
        timeWastingApps.forEach { app ->
            val alternatives = findProductiveAlternatives(app)
            if (alternatives.isNotEmpty()) {
                // Convert AppInfo to AppUsageData for method calls
                val appUsageData = AppUsageData(
                    packageName = app.packageName,
                    appName = app.appName,
                    timeSpent = 0L, // Default value since we don't have actual usage data
                    category = app.category,
                    isProductive = app.isProductive,
                    isTimeWasting = app.isTimeWasting,
                    productivityScore = app.productivityScore,
                    timeWastingScore = app.timeWastingScore,
                    focusScore = app.focusScore,
                    name = app.name
                )
                recommendations.add(
                    AppRecommendation(
                        currentApp = app.name,
                        alternativeApp = alternatives.first().appName,
                        category = category,
                        reason = "More productive alternative in $category",
                        confidence = 80,
                        benefits = alternatives.first().benefits,
                        timeSaved = calculateTimeSaved(appUsageData, alternatives.first()),
                        productivityGain = calculateProductivityGain(appUsageData, alternatives.first())
                    )
                )
            }
        }
        
        return recommendations
    }
    
    /**
     * Record user feedback on recommendations
     */
    fun recordFeedback(recommendation: AppRecommendation, feedback: RecommendationFeedback) {
        val feedbackRecord = RecommendationFeedbackRecord(
            recommendation = recommendation,
            feedback = feedback,
            timestamp = Date()
        )
        
        saveFeedbackRecord(feedbackRecord)
        updateRecommendationModel(recommendation, feedback)
    }
    
    /**
     * Get trending productive apps
     */
    fun getTrendingProductiveApps(): List<AppInfo> {
        val trendingApps = appDatabase.getTrendingApps()
        return trendingApps.filter { it.isProductive }
    }
    
    /**
     * Get app recommendations based on time of day
     */
    fun getTimeBasedRecommendations(timeOfDay: TimeOfDay): List<AppRecommendation> {
        val recommendations = mutableListOf<AppRecommendation>()
        val timeBasedApps = getTimeBasedApps(timeOfDay)
        
        timeBasedApps.forEach { app ->
            val alternatives = findTimeBasedAlternatives(app, timeOfDay)
            if (alternatives.isNotEmpty()) {
                // Convert AppInfo to AppUsageData for method calls
                val appUsageData = AppUsageData(
                    packageName = app.packageName,
                    appName = app.appName,
                    timeSpent = 0L, // Default value since we don't have actual usage data
                    category = app.category,
                    isProductive = app.isProductive,
                    isTimeWasting = app.isTimeWasting,
                    productivityScore = app.productivityScore,
                    timeWastingScore = app.timeWastingScore,
                    focusScore = app.focusScore,
                    name = app.name
                )
                recommendations.add(
                    AppRecommendation(
                        currentApp = app.name,
                        alternativeApp = alternatives.first().appName,
                        category = app.category,
                        reason = "Better for ${timeOfDay.name.lowercase()} productivity",
                        confidence = 75,
                        benefits = alternatives.first().benefits,
                        timeSaved = calculateTimeSaved(appUsageData, alternatives.first()),
                        productivityGain = calculateProductivityGain(appUsageData, alternatives.first())
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private fun identifyTimeWastingApps(usageData: UsageData): List<AppUsageData> {
        return usageData.appUsage.filter { app ->
            app.isTimeWasting && app.timeSpent > TimeUnit.MINUTES.toMillis(30)
        }
    }
    
    private fun identifyTimeWastingAppsFromPatterns(patterns: UsagePatterns): List<AppUsageData> {
        // This would analyze usage patterns to identify time-wasting apps
        return emptyList()
    }
    
    private fun findAlternatives(app: AppUsageData): List<AppInfo> {
        val alternatives = mutableListOf<AppInfo>()
        
        // Find apps in the same category that are more productive
        val categoryApps = appDatabase.getAppsByCategory(app.category)
        val productiveApps = categoryApps.filter { it.isProductive && it.appName != app.appName }
        
        alternatives.addAll(productiveApps)
        
        // Find apps with similar functionality but better productivity
        val similarApps = appDatabase.getSimilarApps(app.appName)
        val productiveSimilarApps = similarApps.filter { it.isProductive }
        
        alternatives.addAll(productiveSimilarApps)
        
        return alternatives.distinctBy { it.appName }
    }
    
    private fun findAlternativesInCategories(app: AppUsageData, preferredCategories: List<String>): List<AppInfo> {
        val alternatives = mutableListOf<AppInfo>()
        
        preferredCategories.forEach { category ->
            val categoryApps = appDatabase.getAppsByCategory(category)
            val productiveApps = categoryApps.filter { it.isProductive }
            alternatives.addAll(productiveApps)
        }
        
        return alternatives.distinctBy { it.appName }
    }
    
    private fun findProductiveAlternatives(app: AppInfo): List<AppInfo> {
        val categoryApps = appDatabase.getAppsByCategory(app.category)
        return categoryApps.filter { it.isProductive && it.appName != app.appName }
    }
    
    private fun findTimeBasedAlternatives(app: AppInfo, timeOfDay: TimeOfDay): List<AppInfo> {
        val timeBasedCategories = getTimeBasedCategories(timeOfDay)
        val alternatives = mutableListOf<AppInfo>()
        
        timeBasedCategories.forEach { category ->
            val categoryApps = appDatabase.getAppsByCategory(category)
            val productiveApps = categoryApps.filter { it.isProductive }
            alternatives.addAll(productiveApps)
        }
        
        return alternatives.distinctBy { it.appName }
    }
    
    private fun generateRecommendationReason(app: AppUsageData, alternative: AppInfo): String {
        return when {
            app.timeSpent > TimeUnit.HOURS.toMillis(2) -> "You spend a lot of time on ${app.appName}. ${alternative.appName} offers similar functionality with better productivity features."
            app.category == "Social Media" -> "Social media can be distracting. ${alternative.appName} provides a more focused experience."
            app.category == "Entertainment" -> "Consider switching to ${alternative.appName} for more productive entertainment."
            else -> "${alternative.appName} is a more productive alternative to ${app.appName}."
        }
    }
    
    private fun generatePersonalizedReason(app: AppUsageData, alternative: AppInfo, preferences: UserPreferences): String {
        val personalizedReason = when {
            preferences.focusOnProductivity -> "Based on your productivity goals, ${alternative.appName} will help you stay focused."
            preferences.reduceScreenTime -> "To reduce your screen time, try ${alternative.appName} instead of ${app.appName}."
            preferences.improveFocus -> "For better focus, ${alternative.appName} offers fewer distractions than ${app.appName}."
            else -> "${alternative.appName} is a better choice for your goals."
        }
        
        return personalizedReason
    }
    
    private fun calculateConfidence(app: AppUsageData, alternative: AppInfo): Int {
        var confidence = 50
        
        // Increase confidence based on app productivity score
        confidence += alternative.productivityScore / 2
        
        // Increase confidence if alternative is in same category
        if (app.category == alternative.category) {
            confidence += 20
        }
        
        // Increase confidence based on user feedback
        val feedbackScore = getFeedbackScore(alternative.name)
        confidence += feedbackScore
        
        return confidence.coerceIn(0, 100)
    }
    
    private fun calculatePersonalizedConfidence(app: AppUsageData, alternative: AppInfo, preferences: UserPreferences): Int {
        var confidence = calculateConfidence(app, alternative)
        
        // Adjust confidence based on user preferences
        when {
            preferences.focusOnProductivity && alternative.isProductive -> confidence += 15
            preferences.reduceScreenTime && alternative.timeWastingScore < 30 -> confidence += 10
            preferences.improveFocus && alternative.focusScore > 70 -> confidence += 15
        }
        
        return confidence.coerceIn(0, 100)
    }
    
    private fun calculateTimeSaved(app: AppUsageData, alternative: AppInfo): Long {
        val timeWastingScore = app.timeWastingScore
        val alternativeScore = alternative.timeWastingScore
        val timeSpent = app.timeSpent
        
        val timeSaved = (timeSpent * (timeWastingScore - alternativeScore) / 100).toLong()
        return maxOf(0, timeSaved)
    }
    
    private fun calculateProductivityGain(app: AppUsageData, alternative: AppInfo): Int {
        val appProductivity = app.productivityScore
        val alternativeProductivity = alternative.productivityScore
        
        return alternativeProductivity - appProductivity
    }
    
    private fun getUserPreferences(): UserPreferences {
        return UserPreferences(
            preferredCategories = getPreferredCategories(),
            focusOnProductivity = prefs.getBoolean("focus_on_productivity", true),
            reduceScreenTime = prefs.getBoolean("reduce_screen_time", true),
            improveFocus = prefs.getBoolean("improve_focus", true)
        )
    }
    
    private fun getUsagePatterns(): UsagePatterns {
        // This would retrieve usage patterns from the system
        return UsagePatterns(
            averageDailyUsage = 0L,
            peakHours = emptyList(),
            mostUsedApps = emptyList(),
            focusPatterns = emptyList()
        )
    }
    
    private fun getPreferredCategories(): List<String> {
        val categoriesJson = prefs.getString("preferred_categories", "[]") ?: "[]"
        return try {
            JSONArray(categoriesJson).let { array ->
                (0 until array.length()).map { array.getString(it) }
            }
        } catch (e: Exception) {
            listOf("Productivity", "Education", "Health")
        }
    }
    
    private fun getTimeBasedCategories(timeOfDay: TimeOfDay): List<String> {
        return when (timeOfDay) {
            TimeOfDay.MORNING -> listOf("Productivity", "Education", "Health")
            TimeOfDay.AFTERNOON -> listOf("Productivity", "Communication", "Work")
            TimeOfDay.EVENING -> listOf("Entertainment", "Social", "Relaxation")
            TimeOfDay.NIGHT -> listOf("Relaxation", "Health", "Sleep")
        }
    }
    
    private fun getTimeBasedApps(timeOfDay: TimeOfDay): List<AppInfo> {
        val categories = getTimeBasedCategories(timeOfDay)
        val apps = mutableListOf<AppInfo>()
        
        categories.forEach { category ->
            apps.addAll(appDatabase.getAppsByCategory(category))
        }
        
        return apps.filter { it.isTimeWasting }
    }
    
    private fun getFeedbackScore(appName: String): Int {
        return prefs.getInt("feedback_score_$appName", 0)
    }
    
    private fun saveFeedbackRecord(record: RecommendationFeedbackRecord) {
        val recordJson = JSONObject().apply {
            put("timestamp", record.timestamp.time)
            put("currentApp", record.recommendation.currentApp)
            put("alternativeApp", record.recommendation.alternativeApp)
            put("feedback", record.feedback.name)
        }
        
        val recordsArray = try {
            JSONArray(prefs.getString("feedback_records", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        
        recordsArray.put(recordJson)
        prefs.edit().putString("feedback_records", recordsArray.toString()).apply()
    }
    
    private fun updateRecommendationModel(recommendation: AppRecommendation, feedback: RecommendationFeedback) {
        val alternativeApp = recommendation.alternativeApp
        val currentScore = getFeedbackScore(alternativeApp)
        val newScore = when (feedback) {
            RecommendationFeedback.POSITIVE -> currentScore + 10
            RecommendationFeedback.NEGATIVE -> currentScore - 10
            RecommendationFeedback.NEUTRAL -> currentScore
        }
        
        prefs.edit().putInt("feedback_score_$alternativeApp", newScore.coerceIn(-100, 100)).apply()
    }
    
    /**
     * Update the model with new usage data
     */
    fun updateModel(usageData: UsageData) {
        // Update recommendation model based on usage data
        val appUsage = usageData.appUsage
        
        appUsage.forEach { app ->
            val usageRecord = JSONObject().apply {
                put("timestamp", usageData.timestamp.time)
                put("appName", app.appName)
                put("timeSpent", app.timeSpent)
                put("category", app.category)
                put("isProductive", app.isProductive)
            }
            
            val usageArray = try {
                JSONArray(prefs.getString("app_usage_data", "[]") ?: "[]")
            } catch (e: Exception) {
                JSONArray()
            }
            
            usageArray.put(usageRecord)
            prefs.edit().putString("app_usage_data", usageArray.toString()).apply()
        }
    }
    
    companion object {
        private const val TAG = "AppRecommendationEngine"
    }
}


class AppDatabase {
    fun getAppsByCategory(category: String): List<AppInfo> {
        // This would contain a database of apps with their information
        return when (category) {
            "Productivity" -> listOf(
                AppInfo("com.notion.id", "Notion", "Productivity", false, false, "Notion", true, false, 90, 10, 85, listOf("Task management", "Note taking", "Collaboration")),
                AppInfo("com.todoist", "Todoist", "Productivity", false, false, "Todoist", true, false, 85, 15, 80, listOf("Task management", "Project planning")),
                AppInfo("com.trello", "Trello", "Productivity", false, false, "Trello", true, false, 80, 20, 75, listOf("Project management", "Team collaboration"))
            )
            "Social Media" -> listOf(
                AppInfo("com.linkedin.android", "LinkedIn", "Social Media", false, false, "LinkedIn", true, false, 70, 30, 60, listOf("Professional networking", "Career development")),
                AppInfo("com.twitter.android", "Twitter", "Social Media", false, false, "Twitter", false, true, 40, 60, 30, listOf("News", "Social connection")),
                AppInfo("com.instagram.android", "Instagram", "Social Media", false, false, "Instagram", false, true, 20, 80, 20, listOf("Photo sharing", "Social connection"))
            )
            "Entertainment" -> listOf(
                AppInfo("com.spotify.music", "Spotify", "Entertainment", false, false, "Spotify", false, true, 30, 70, 40, listOf("Music streaming", "Relaxation")),
                AppInfo("com.google.android.youtube", "YouTube", "Entertainment", false, false, "YouTube", false, true, 25, 75, 25, listOf("Video content", "Education")),
                AppInfo("com.netflix.mediaclient", "Netflix", "Entertainment", false, false, "Netflix", false, true, 20, 80, 20, listOf("Video streaming", "Entertainment"))
            )
            else -> emptyList()
        }
    }
    
    fun getSimilarApps(appName: String): List<AppInfo> {
        // This would find similar apps based on functionality
        return emptyList()
    }
    
    fun getTrendingApps(): List<AppInfo> {
        // This would return trending apps
        return emptyList()
    }
}
