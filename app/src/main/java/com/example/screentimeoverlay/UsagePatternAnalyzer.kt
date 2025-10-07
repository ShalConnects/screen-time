package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Analyzes usage patterns to provide insights and recommendations
 */
class UsagePatternAnalyzer(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("usage_patterns", Context.MODE_PRIVATE)
    private val sessionTracker: SessionTracker = SessionTracker(context)
    private val productivityScorer: ProductivityScorer = ProductivityScorer(context)
    
    /**
     * Analyze usage patterns for a date range
     */
    fun analyzeUsagePatterns(dateRange: DateRange): UsagePatternAnalysis {
        val sessions = sessionTracker.getSessionsForDateRange(dateRange.startDate, dateRange.endDate)
        val dailyPatterns = mutableListOf<DailyPattern>()
        val hourlyUsage = mutableMapOf<Int, Long>()
        val weeklyPatterns = mutableMapOf<Int, Long>()
        val appUsagePatterns = mutableMapOf<String, AppUsagePattern>()
        
        // Analyze each day in the range
        val calendar = Calendar.getInstance()
        calendar.time = dateRange.startDate
        
        while (calendar.time.before(dateRange.endDate) || calendar.time == dateRange.endDate) {
            val daySessions = sessions.filter { session ->
                val sessionCalendar = Calendar.getInstance()
                sessionCalendar.timeInMillis = session.startTime
                sessionCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                sessionCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
            }
            
            val dailyPattern = analyzeDailyPattern(calendar.time, daySessions)
            dailyPatterns.add(dailyPattern)
            
            // Aggregate hourly usage
            daySessions.forEach { session ->
                val sessionCalendar = Calendar.getInstance()
                sessionCalendar.timeInMillis = session.startTime
                val hour = sessionCalendar.get(Calendar.HOUR_OF_DAY)
                hourlyUsage[hour] = (hourlyUsage[hour] ?: 0) + session.totalTime
            }
            
            // Aggregate weekly patterns
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val dayUsage = daySessions.sumOf { it.totalTime }
            weeklyPatterns[dayOfWeek] = (weeklyPatterns[dayOfWeek] ?: 0) + dayUsage
            
            // Analyze app usage patterns
            daySessions.forEach { session ->
                val existing = appUsagePatterns[session.packageName]
                if (existing != null) {
                    existing.totalTime += session.totalTime
                    existing.sessionCount++
                    existing.usageDays.add(calendar.time)
                } else {
                    appUsagePatterns[session.packageName] = AppUsagePattern(
                        packageName = session.packageName,
                        appName = session.appName,
                        totalTime = session.totalTime,
                        sessionCount = 1,
                        usageDays = mutableSetOf(calendar.time),
                        averageSessionTime = session.totalTime,
                        peakUsageHour = Calendar.getInstance().apply { timeInMillis = session.startTime }.get(Calendar.HOUR_OF_DAY)
                    )
                }
            }
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Calculate insights
        val totalDays = dailyPatterns.size
        val totalUsage = dailyPatterns.sumOf { it.totalTime }
        val averageDailyUsage = if (totalDays > 0) totalUsage / totalDays else 0L
        
        // Find peak usage hours
        val peakHours = hourlyUsage.entries.sortedByDescending { it.value }.take(3)
        val peakHour = peakHours.firstOrNull()?.key ?: 12
        
        // Find most active day of week
        val mostActiveDay = weeklyPatterns.entries.maxByOrNull { it.value }?.key ?: Calendar.MONDAY
        
        // Calculate focus patterns
        val focusScore = calculateFocusScore(dailyPatterns)
        val consistencyScore = calculateConsistencyScore(dailyPatterns)
        
        // Generate recommendations
        val recommendations = generatePatternRecommendations(
            averageDailyUsage, peakHour, mostActiveDay, focusScore, consistencyScore, appUsagePatterns
        )
        
        return UsagePatternAnalysis(
            dateRange = dateRange,
            totalDays = totalDays,
            totalUsage = totalUsage,
            averageDailyUsage = averageDailyUsage,
            peakUsageHour = peakHour,
            mostActiveDay = mostActiveDay,
            focusScore = focusScore,
            consistencyScore = consistencyScore,
            hourlyUsage = hourlyUsage,
            weeklyPatterns = weeklyPatterns,
            dailyPatterns = dailyPatterns,
            appUsagePatterns = appUsagePatterns.values.toList(),
            recommendations = recommendations
        )
    }
    
    /**
     * Analyze daily usage pattern
     */
    private fun analyzeDailyPattern(date: Date, sessions: List<AppSession>): DailyPattern {
        val totalTime = sessions.sumOf { it.totalTime }
        val sessionCount = sessions.size
        val averageSessionTime = if (sessionCount > 0) totalTime / sessionCount else 0L
        
        // Find peak usage hour for the day
        val hourlyUsage = mutableMapOf<Int, Long>()
        sessions.forEach { session ->
            val sessionCalendar = Calendar.getInstance()
            sessionCalendar.timeInMillis = session.startTime
            val hour = sessionCalendar.get(Calendar.HOUR_OF_DAY)
            hourlyUsage[hour] = (hourlyUsage[hour] ?: 0) + session.totalTime
        }
        val peakHour = hourlyUsage.entries.maxByOrNull { it.value }?.key ?: 12
        
        // Calculate focus metrics
        val longSessions = sessions.count { it.totalTime >= TimeUnit.MINUTES.toMillis(15) }
        val focusRatio = if (sessionCount > 0) (longSessions * 100) / sessionCount else 0
        
        // Calculate productivity score for the day
        val appUsages = sessions.map { session ->
            AppUsage(session.packageName, session.appName, session.totalTime)
        }
        val productivityScore = productivityScorer.calculateDailyProductivityScore(appUsages)
        
        return DailyPattern(
            date = date,
            totalTime = totalTime,
            sessionCount = sessionCount,
            averageSessionTime = averageSessionTime,
            peakHour = peakHour,
            focusRatio = focusRatio,
            productivityScore = productivityScore.overallScore,
            longSessions = longSessions
        )
    }
    
    /**
     * Calculate focus score based on session patterns
     */
    private fun calculateFocusScore(dailyPatterns: List<DailyPattern>): Int {
        if (dailyPatterns.isEmpty()) return 0
        
        val totalSessions = dailyPatterns.sumOf { it.sessionCount }
        val totalLongSessions = dailyPatterns.sumOf { it.longSessions }
        
        return if (totalSessions > 0) (totalLongSessions * 100) / totalSessions else 0
    }
    
    /**
     * Calculate consistency score based on daily usage variance
     */
    private fun calculateConsistencyScore(dailyPatterns: List<DailyPattern>): Int {
        if (dailyPatterns.size < 2) return 100
        
        val averageUsage = dailyPatterns.map { it.totalTime }.average()
        val variance = dailyPatterns.map { (it.totalTime - averageUsage).let { diff -> diff * diff } }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        
        // Convert to 0-100 scale (lower deviation = higher consistency)
        val maxExpectedDeviation = TimeUnit.HOURS.toMillis(2) // 2 hours
        val consistencyRatio = (1.0 - (standardDeviation / maxExpectedDeviation)).coerceIn(0.0, 1.0)
        
        return (consistencyRatio * 100).toInt()
    }
    
    /**
     * Generate personalized recommendations based on patterns
     */
    private fun generatePatternRecommendations(
        averageDailyUsage: Long,
        peakHour: Int,
        mostActiveDay: Int,
        focusScore: Int,
        consistencyScore: Int,
        appUsagePatterns: Map<String, AppUsagePattern>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Usage time recommendations
        when {
            averageDailyUsage > TimeUnit.HOURS.toMillis(8) -> {
                recommendations.add("You're spending over 8 hours daily on your device. Consider setting usage limits.")
            }
            averageDailyUsage < TimeUnit.HOURS.toMillis(2) -> {
                recommendations.add("Your usage is quite low. Consider using more productive apps to maximize your screen time.")
            }
        }
        
        // Peak hour recommendations
        when (peakHour) {
            in 0..6 -> {
                recommendations.add("Your peak usage is during early morning hours. Consider if this aligns with your sleep schedule.")
            }
            in 22..23 -> {
                recommendations.add("Your peak usage is late at night. Consider reducing screen time before bed for better sleep.")
            }
            in 9..17 -> {
                recommendations.add("Your peak usage is during work hours. Great for productivity!")
            }
        }
        
        // Focus recommendations
        when {
            focusScore >= 70 -> {
                recommendations.add("Excellent focus! You maintain long, productive sessions.")
            }
            focusScore >= 40 -> {
                recommendations.add("Good focus habits. Try to extend your productive sessions.")
            }
            focusScore < 40 -> {
                recommendations.add("Consider using focus techniques like Pomodoro to improve your session length.")
            }
        }
        
        // Consistency recommendations
        when {
            consistencyScore >= 80 -> {
                recommendations.add("Great consistency! You maintain regular usage patterns.")
            }
            consistencyScore >= 60 -> {
                recommendations.add("Good consistency. Try to maintain more regular daily routines.")
            }
            consistencyScore < 60 -> {
                recommendations.add("Your usage patterns vary significantly. Consider establishing a more consistent routine.")
            }
        }
        
        // App-specific recommendations
        val topDistractingApps = appUsagePatterns.values
            .filter { pattern ->
                val productivityScore = productivityScorer.getProductivityScore(pattern.packageName)
                productivityScore < 40
            }
            .sortedByDescending { it.totalTime }
            .take(3)
        
        if (topDistractingApps.isNotEmpty()) {
            val topApp = topDistractingApps.first()
            recommendations.add("Consider reducing time on ${topApp.appName} - it's your most used distracting app.")
        }
        
        return recommendations
    }
    
    /**
     * Get optimal break times based on usage patterns
     */
    fun getOptimalBreakTimes(): List<BreakTimeRecommendation> {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        val recommendations = mutableListOf<BreakTimeRecommendation>()
        
        // Morning break (if currently morning)
        if (currentHour in 9..11) {
            recommendations.add(BreakTimeRecommendation(
                time = "10:30 AM",
                duration = "15 minutes",
                reason = "Take a break after 1.5 hours of morning work",
                type = BreakType.SHORT_BREAK
            ))
        }
        
        // Lunch break
        if (currentHour in 11..14) {
            recommendations.add(BreakTimeRecommendation(
                time = "12:00 PM",
                duration = "30 minutes",
                reason = "Lunch break - step away from screens",
                type = BreakType.LUNCH_BREAK
            ))
        }
        
        // Afternoon break
        if (currentHour in 14..16) {
            recommendations.add(BreakTimeRecommendation(
                time = "3:00 PM",
                duration = "10 minutes",
                reason = "Afternoon energy dip - refresh your mind",
                type = BreakType.SHORT_BREAK
            ))
        }
        
        // Evening break
        if (currentHour in 18..20) {
            recommendations.add(BreakTimeRecommendation(
                time = "7:00 PM",
                duration = "20 minutes",
                reason = "Evening transition - prepare for relaxation",
                type = BreakType.EVENING_BREAK
            ))
        }
        
        return recommendations
    }
    
    /**
     * Save pattern analysis for future reference
     */
    fun savePatternAnalysis(analysis: UsagePatternAnalysis) {
        val analysisJson = JSONObject().apply {
            put("dateRange", JSONObject().apply {
                put("startDate", analysis.dateRange.startDate.time)
                put("endDate", analysis.dateRange.endDate.time)
            })
            put("totalDays", analysis.totalDays)
            put("totalUsage", analysis.totalUsage)
            put("averageDailyUsage", analysis.averageDailyUsage)
            put("peakUsageHour", analysis.peakUsageHour)
            put("mostActiveDay", analysis.mostActiveDay)
            put("focusScore", analysis.focusScore)
            put("consistencyScore", analysis.consistencyScore)
            put("recommendations", JSONArray(analysis.recommendations))
        }
        
        val analysesJson = prefs.getString("pattern_analyses", "[]") ?: "[]"
        val analysesArray = try {
            JSONArray(analysesJson)
        } catch (e: Exception) {
            JSONArray()
        }
        
        analysesArray.put(analysisJson)
        prefs.edit().putString("pattern_analyses", analysesArray.toString()).apply()
    }
    
    companion object {
        private const val TAG = "UsagePatternAnalyzer"
    }
}

data class UsagePatternAnalysis(
    val dateRange: DateRange,
    val totalDays: Int,
    val totalUsage: Long,
    val averageDailyUsage: Long,
    val peakUsageHour: Int,
    val mostActiveDay: Int,
    val focusScore: Int,
    val consistencyScore: Int,
    val hourlyUsage: Map<Int, Long>,
    val weeklyPatterns: Map<Int, Long>,
    val dailyPatterns: List<DailyPattern>,
    val appUsagePatterns: List<AppUsagePattern>,
    val recommendations: List<String>
)

data class DailyPattern(
    val date: Date,
    val totalTime: Long,
    val sessionCount: Int,
    val averageSessionTime: Long,
    val peakHour: Int,
    val focusRatio: Int,
    val productivityScore: Int,
    val longSessions: Int
)

data class AppUsagePattern(
    val packageName: String,
    val appName: String,
    var totalTime: Long,
    var sessionCount: Int,
    val usageDays: MutableSet<Date>,
    var averageSessionTime: Long,
    val peakUsageHour: Int
)

data class BreakTimeRecommendation(
    val time: String,
    val duration: String,
    val reason: String,
    val type: BreakType
)

// BreakType enum is defined in NotificationManager.kt
