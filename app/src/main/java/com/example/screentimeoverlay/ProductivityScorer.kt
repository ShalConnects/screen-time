package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Manages productivity scoring for apps to help users understand their usage patterns
 */
class ProductivityScorer(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("productivity_scorer", Context.MODE_PRIVATE)
    
    // Default productivity categories and scores
    private val defaultProductivityScores = mapOf(
        // Highly Productive (90-100)
        "com.microsoft.office.excel" to 95,
        "com.microsoft.office.word" to 95,
        "com.microsoft.office.powerpoint" to 95,
        "com.google.android.apps.docs" to 90,
        "com.google.android.apps.sheets" to 90,
        "com.google.android.apps.slides" to 90,
        "com.notion.id" to 95,
        "com.todoist" to 90,
        "com.evernote" to 85,
        "com.onenote" to 85,
        
        // Educational (80-90)
        "com.duolingo" to 85,
        "com.khan.academy" to 90,
        "com.coursera.android" to 90,
        "com.udemy.android" to 85,
        "com.edx.mobile" to 90,
        "com.skillshare" to 80,
        
        // Health & Fitness (70-85)
        "com.myfitnesspal.android" to 80,
        "com.nike.ntc" to 75,
        "com.strava" to 70,
        "com.underarmour.mapmyrun" to 70,
        "com.fitbit.FitbitMobile" to 75,
        "com.headspace" to 85,
        "com.calm" to 85,
        
        // Communication (60-80)
        "com.whatsapp" to 60,
        "com.telegram.messenger" to 65,
        "com.skype.raider" to 70,
        "com.microsoft.teams" to 80,
        "com.slack" to 75,
        "com.discord" to 50,
        
        // Reading (80-90)
        "com.amazon.kindle" to 85,
        "com.google.android.apps.books" to 85,
        "com.adobe.reader" to 80,
        "com.fbreader.fbreader" to 85,
        "com.overdrive.mobile.android.libby" to 85,
        "com.audible.application" to 80,
        
        // Social Media (20-50)
        "com.facebook.katana" to 30,
        "com.instagram.android" to 25,
        "com.twitter.android" to 40,
        "com.snapchat.android" to 20,
        "com.tiktok.android" to 15,
        "com.pinterest" to 35,
        
        // Entertainment (10-40)
        "com.netflix.mediaclient" to 20,
        "com.disney.disneyplus" to 15,
        "com.hulu.plus" to 20,
        "com.amazon.avod.thirdpartyclient" to 20,
        "com.spotify.music" to 40,
        "com.youtube.android" to 30,
        "com.twitch.tv" to 25,
        "com.reddit" to 35,
        
        // Games (10-30)
        "com.king.candycrushsaga" to 10,
        "com.supercell.clashofclans" to 15,
        "com.epicgames.fortnite" to 20,
        "com.mojang.minecraftpe" to 25,
        "com.roblox.client" to 15
    )
    
    /**
     * Get productivity score for an app
     */
    fun getProductivityScore(packageName: String): Int {
        // Check if user has custom score
        val customScore = prefs.getInt("score_$packageName", -1)
        if (customScore != -1) {
            return customScore
        }
        
        // Return default score or neutral score
        return defaultProductivityScores[packageName] ?: 50
    }
    
    /**
     * Set custom productivity score for an app
     */
    fun setProductivityScore(packageName: String, score: Int) {
        prefs.edit().putInt("score_$packageName", score.coerceIn(0, 100)).apply()
        Log.d(TAG, "Set productivity score for $packageName: $score")
    }
    
    /**
     * Get productivity category based on score
     */
    fun getProductivityCategory(score: Int): ProductivityCategory {
        return when {
            score >= 80 -> ProductivityCategory.HIGHLY_PRODUCTIVE
            score >= 60 -> ProductivityCategory.MODERATELY_PRODUCTIVE
            score >= 40 -> ProductivityCategory.NEUTRAL
            score >= 20 -> ProductivityCategory.DISTRACTING
            else -> ProductivityCategory.HIGHLY_DISTRACTING
        }
    }
    
    /**
     * Calculate overall productivity score for a day
     */
    fun calculateDailyProductivityScore(appUsages: List<AppUsage>): ProductivityScore {
        if (appUsages.isEmpty()) {
            return ProductivityScore(0, 0, 0, 0, 0, emptyList())
        }
        
        var totalTime = 0L
        var weightedScore = 0.0
        val appScores = mutableListOf<AppProductivityScore>()
        
        appUsages.forEach { appUsage ->
            val productivityScore = getProductivityScore(appUsage.packageName)
            val timeWeight = appUsage.timeInForeground.toDouble()
            
            totalTime += appUsage.timeInForeground
            weightedScore += productivityScore * timeWeight
            
            appScores.add(AppProductivityScore(
                packageName = appUsage.packageName,
                appName = appUsage.appName,
                timeSpent = appUsage.timeInForeground,
                productivityScore = productivityScore,
                category = getProductivityCategory(productivityScore)
            ))
        }
        
        val overallScore = if (totalTime > 0) (weightedScore / totalTime).toInt() else 0
        
        // Calculate time distribution
        val productiveTime = appScores.filter { it.productivityScore >= 60 }
            .sumOf { it.timeSpent }
        val distractingTime = appScores.filter { it.productivityScore < 40 }
            .sumOf { it.timeSpent }
        val neutralTime = totalTime - productiveTime - distractingTime
        
        return ProductivityScore(
            overallScore = overallScore,
            totalTime = totalTime,
            productiveTime = productiveTime,
            neutralTime = neutralTime,
            distractingTime = distractingTime,
            appScores = appScores.sortedByDescending { it.timeSpent }
        )
    }
    
    /**
     * Get productivity insights for a date range
     */
    fun getProductivityInsights(dateRange: DateRange, appUsages: List<AppUsage>): ProductivityInsights {
        val dailyScores = mutableListOf<Int>()
        val categoryBreakdown = mutableMapOf<ProductivityCategory, Long>()
        val topDistractingApps = mutableListOf<AppProductivityScore>()
        val topProductiveApps = mutableListOf<AppProductivityScore>()
        
        // Calculate daily scores and breakdowns
        appUsages.forEach { appUsage ->
            val score = getProductivityScore(appUsage.packageName)
            val category = getProductivityCategory(score)
            
            categoryBreakdown[category] = (categoryBreakdown[category] ?: 0) + appUsage.timeInForeground
            
            val appScore = AppProductivityScore(
                packageName = appUsage.packageName,
                appName = appUsage.appName,
                timeSpent = appUsage.timeInForeground,
                productivityScore = score,
                category = category
            )
            
            if (category == ProductivityCategory.HIGHLY_DISTRACTING || category == ProductivityCategory.DISTRACTING) {
                topDistractingApps.add(appScore)
            } else if (category == ProductivityCategory.HIGHLY_PRODUCTIVE || category == ProductivityCategory.MODERATELY_PRODUCTIVE) {
                topProductiveApps.add(appScore)
            }
        }
        
        // Sort and get top apps
        val sortedDistracting = topDistractingApps.sortedByDescending { it.timeSpent }.take(5)
        val sortedProductive = topProductiveApps.sortedByDescending { it.timeSpent }.take(5)
        
        // Calculate trends
        val totalTime = appUsages.sumOf { it.timeInForeground }
        val productiveTime = categoryBreakdown[ProductivityCategory.HIGHLY_PRODUCTIVE] ?: 0 +
                (categoryBreakdown[ProductivityCategory.MODERATELY_PRODUCTIVE] ?: 0)
        val distractingTime = categoryBreakdown[ProductivityCategory.HIGHLY_DISTRACTING] ?: 0 +
                (categoryBreakdown[ProductivityCategory.DISTRACTING] ?: 0)
        
        val productivityRatio = if (totalTime > 0) ((productiveTime * 100) / totalTime).toInt() else 0
        val distractionRatio = if (totalTime > 0) ((distractingTime * 100) / totalTime).toInt() else 0
        
        return ProductivityInsights(
            date = Date(),
            productivityScore = productivityRatio,
            focusScore = 0,
            distractionScore = distractionRatio,
            recommendations = generateRecommendations(productivityRatio, distractionRatio, sortedDistracting),
            trends = UsageTrends(TrendDirection.STABLE, TrendDirection.STABLE, TrendDirection.STABLE),
            productivityRatio = productivityRatio,
            distractionRatio = distractionRatio,
            topDistractingApps = sortedDistracting.map { 
                AppUsageData(
                    packageName = it.packageName,
                    appName = it.appName,
                    timeSpent = it.timeSpent,
                    category = it.category.name,
                    isProductive = false
                )
            }
        )
    }
    
    /**
     * Generate personalized recommendations
     */
    private fun generateRecommendations(
        productivityRatio: Int,
        distractionRatio: Int,
        topDistractingApps: List<AppProductivityScore>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        when {
            productivityRatio >= 70 -> {
                recommendations.add("Great job! You're spending most of your time on productive apps.")
                recommendations.add("Consider setting specific goals for your productive app usage.")
            }
            distractionRatio >= 50 -> {
                recommendations.add("You're spending a lot of time on distracting apps. Consider setting usage limits.")
                if (topDistractingApps.isNotEmpty()) {
                    val topApp = topDistractingApps.first()
                    recommendations.add("Try reducing time on ${topApp.appName} - it's your most used distracting app.")
                }
            }
            productivityRatio >= 40 && distractionRatio <= 30 -> {
                recommendations.add("Good balance! You're using productive apps more than distracting ones.")
                recommendations.add("Consider adding more educational or skill-building apps to your routine.")
            }
            else -> {
                recommendations.add("Try to increase time spent on productive apps like productivity tools or educational content.")
                recommendations.add("Set specific times for checking social media and entertainment apps.")
            }
        }
        
        return recommendations
    }
    
    /**
     * Get all apps with their productivity scores
     */
    fun getAllAppsWithScores(): List<AppProductivityInfo> {
        val allApps = mutableListOf<AppProductivityInfo>()
        
        // Add default apps
        defaultProductivityScores.forEach { (packageName, score) ->
            allApps.add(AppProductivityInfo(
                packageName = packageName,
                appName = getAppName(packageName),
                defaultScore = score,
                customScore = prefs.getInt("score_$packageName", -1),
                category = getProductivityCategory(score)
            ))
        }
        
        return allApps.sortedBy { it.appName }
    }
    
    /**
     * Reset all custom scores
     */
    fun resetCustomScores() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("score_") }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
        Log.d(TAG, "Reset all custom productivity scores")
    }
    
    /**
     * Get app name from package name
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
    
    companion object {
        private const val TAG = "ProductivityScorer"
    }
}

enum class ProductivityCategory(val displayName: String, val color: Int) {
    HIGHLY_PRODUCTIVE("Highly Productive", 0xFF4CAF50.toInt()),
    MODERATELY_PRODUCTIVE("Moderately Productive", 0xFF8BC34A.toInt()),
    NEUTRAL("Neutral", 0xFFFFC107.toInt()),
    DISTRACTING("Distracting", 0xFFFF9800.toInt()),
    HIGHLY_DISTRACTING("Highly Distracting", 0xFFF44336.toInt())
}

data class ProductivityScore(
    val overallScore: Int,
    val totalTime: Long,
    val productiveTime: Long,
    val neutralTime: Long,
    val distractingTime: Long,
    val appScores: List<AppProductivityScore>
)

data class AppProductivityScore(
    val packageName: String,
    val appName: String,
    val timeSpent: Long,
    val productivityScore: Int,
    val category: ProductivityCategory
)


data class AppProductivityInfo(
    val packageName: String,
    val appName: String,
    val defaultScore: Int,
    val customScore: Int,
    val category: ProductivityCategory
)
