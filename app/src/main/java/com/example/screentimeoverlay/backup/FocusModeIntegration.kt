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
 * Focus Mode integration manager for Android's Focus Mode
 * and custom focus mode features
 */
class FocusModeIntegration(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("focus_mode_integration", Context.MODE_PRIVATE)
    private val focusModeScheduler = FocusModeScheduler()
    private val appBlockingManager = AppBlockingManager()
    
    /**
     * Enable focus mode with custom settings
     */
    fun enableFocusMode(settings: FocusModeSettings): FocusModeResult {
        val result = try {
            // Enable Android's built-in focus mode
            enableAndroidFocusMode(settings)
            
            // Apply custom app blocking
            appBlockingManager.blockApps(settings.blockedApps)
            
            // Set up notifications
            setupFocusModeNotifications(settings)
            
            FocusModeResult(
                success = true,
                message = "Focus mode enabled successfully",
                duration = settings.duration,
                blockedApps = settings.blockedApps.size,
                allowedApps = settings.allowedApps.size
            )
        } catch (e: Exception) {
            FocusModeResult(
                success = false,
                message = "Failed to enable focus mode: ${e.message}",
                duration = 0,
                blockedApps = 0,
                allowedApps = 0
            )
        }
        
        // Record focus mode session
        recordFocusModeSession(settings, result)
        
        return result
    }
    
    /**
     * Disable focus mode
     */
    fun disableFocusMode(): FocusModeResult {
        val result = try {
            // Disable Android's focus mode
            disableAndroidFocusMode()
            
            // Unblock all apps
            appBlockingManager.unblockAllApps()
            
            // Clear notifications
            clearFocusModeNotifications()
            
            FocusModeResult(
                success = true,
                message = "Focus mode disabled successfully",
                duration = 0,
                blockedApps = 0,
                allowedApps = 0
            )
        } catch (e: Exception) {
            FocusModeResult(
                success = false,
                message = "Failed to disable focus mode: ${e.message}",
                duration = 0,
                blockedApps = 0,
                allowedApps = 0
            )
        }
        
        return result
    }
    
    /**
     * Schedule focus mode sessions
     */
    fun scheduleFocusModeSessions(sessions: List<FocusModeSession>): SchedulingResult {
        val result = try {
            focusModeScheduler.scheduleSessions(sessions)
            
            SchedulingResult(
                success = true,
                message = "Focus mode sessions scheduled successfully",
                scheduledSessions = sessions.size
            )
        } catch (e: Exception) {
            SchedulingResult(
                success = false,
                message = "Failed to schedule focus mode sessions: ${e.message}",
                scheduledSessions = 0
            )
        }
        
        return result
    }
    
    /**
     * Get current focus mode status
     */
    fun getFocusModeStatus(): FocusModeStatus {
        val isActive = isFocusModeActive()
        val currentSession = getCurrentFocusModeSession()
        val remainingTime = getRemainingFocusTime()
        
        return FocusModeStatus(
            isActive = isActive,
            currentSession = currentSession,
            remainingTime = remainingTime,
            blockedApps = if (isActive) getBlockedApps() else emptyList(),
            allowedApps = if (isActive) getAllowedApps() else emptyList()
        )
    }
    
    /**
     * Get focus mode recommendations based on usage patterns
     */
    fun getFocusModeRecommendations(): List<FocusModeRecommendation> {
        val recommendations = mutableListOf<FocusModeRecommendation>()
        val usagePatterns = analyzeUsagePatterns()
        val currentTime = Date()
        
        // Time-based recommendations
        val timeBasedRecs = getTimeBasedRecommendations(currentTime, usagePatterns)
        recommendations.addAll(timeBasedRecs)
        
        // App-based recommendations
        val appBasedRecs = getAppBasedRecommendations(usagePatterns)
        recommendations.addAll(appBasedRecs)
        
        // Productivity-based recommendations
        val productivityRecs = getProductivityRecommendations(usagePatterns)
        recommendations.addAll(productivityRecs)
        
        return recommendations.sortedBy { it.priority }
    }
    
    /**
     * Get focus mode statistics
     */
    fun getFocusModeStatistics(): FocusModeStatistics {
        val totalSessions = getTotalFocusModeSessions()
        val totalTime = getTotalFocusModeTime()
        val averageSessionLength = getAverageSessionLength()
        val mostBlockedApps = getMostBlockedApps()
        val productivityGains = getProductivityGains()
        
        return FocusModeStatistics(
            totalSessions = totalSessions,
            totalTime = totalTime,
            averageSessionLength = averageSessionLength,
            mostBlockedApps = mostBlockedApps,
            productivityGains = productivityGains,
            focusScore = calculateFocusScore(totalSessions, totalTime, productivityGains)
        )
    }
    
    /**
     * Get focus mode templates
     */
    fun getFocusModeTemplates(): List<FocusModeTemplate> {
        return listOf(
            FocusModeTemplate(
                name = "Deep Work",
                description = "Block all distractions for focused work",
                duration = 120, // 2 hours
                blockedApps = listOf("Social Media", "Entertainment", "Games"),
                allowedApps = listOf("Productivity", "Communication", "Work"),
                priority = 1
            ),
            FocusModeTemplate(
                name = "Study Session",
                description = "Focused study time with minimal distractions",
                duration = 90, // 1.5 hours
                blockedApps = listOf("Social Media", "Entertainment", "Games", "News"),
                allowedApps = listOf("Education", "Productivity", "Reference"),
                priority = 1
            ),
            FocusModeTemplate(
                name = "Creative Work",
                description = "Block distractions while allowing creative tools",
                duration = 180, // 3 hours
                blockedApps = listOf("Social Media", "Entertainment", "Games"),
                allowedApps = listOf("Creative", "Productivity", "Reference"),
                priority = 2
            ),
            FocusModeTemplate(
                name = "Quick Focus",
                description = "Short focused session for quick tasks",
                duration = 30, // 30 minutes
                blockedApps = listOf("Social Media", "Entertainment"),
                allowedApps = listOf("Productivity", "Communication"),
                priority = 3
            )
        )
    }
    
    private fun enableAndroidFocusMode(settings: FocusModeSettings): Boolean {
        // This would integrate with Android's Focus Mode API
        // For now, return true as a mock implementation
        return true
    }
    
    private fun disableAndroidFocusMode(): Boolean {
        // This would disable Android's Focus Mode
        return true
    }
    
    private fun setupFocusModeNotifications(settings: FocusModeSettings) {
        // Set up notifications for focus mode
        val notificationManager = FocusModeNotificationManager(context)
        notificationManager.setupFocusModeNotifications(settings)
    }
    
    private fun clearFocusModeNotifications() {
        // Clear focus mode notifications
        val notificationManager = FocusModeNotificationManager(context)
        notificationManager.clearFocusModeNotifications()
    }
    
    private fun recordFocusModeSession(settings: FocusModeSettings, result: FocusModeResult) {
        val session = FocusModeSessionRecord(
            timestamp = Date(),
            settings = settings,
            result = result,
            duration = if (result.success) settings.duration else 0
        )
        
        saveFocusModeSession(session)
    }
    
    private fun isFocusModeActive(): Boolean {
        return prefs.getBoolean("focus_mode_active", false)
    }
    
    private fun getCurrentFocusModeSession(): FocusModeSession? {
        val sessionJson = prefs.getString("current_focus_session", null)
        return if (sessionJson != null) {
            try {
                val sessionObj = JSONObject(sessionJson)
                FocusModeSession(
                    startTime = Date(sessionObj.getLong("startTime")),
                    endTime = Date(sessionObj.getLong("endTime")),
                    blockedApps = sessionObj.getJSONArray("blockedApps").let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    },
                    allowedApps = sessionObj.getJSONArray("allowedApps").let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    }
                )
            } catch (e: Exception) {
                null
            }
        } else null
    }
    
    private fun getRemainingFocusTime(): Long {
        val currentSession = getCurrentFocusModeSession()
        if (currentSession == null) return 0
        
        val currentTime = System.currentTimeMillis()
        val endTime = currentSession.endTime.time
        
        return if (currentTime < endTime) endTime - currentTime else 0
    }
    
    private fun getBlockedApps(): List<String> {
        return prefs.getString("blocked_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }
    
    private fun getAllowedApps(): List<String> {
        return prefs.getString("allowed_apps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
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
    
    private fun getTimeBasedRecommendations(currentTime: Date, patterns: UsagePatterns): List<FocusModeRecommendation> {
        val recommendations = mutableListOf<FocusModeRecommendation>()
        val hour = Calendar.getInstance().apply { time = currentTime }.get(Calendar.HOUR_OF_DAY)
        
        when (hour) {
            in 9..11 -> {
                recommendations.add(
                    FocusModeRecommendation(
                        title = "Morning Focus",
                        description = "Start your day with focused work",
                        duration = 60,
                        priority = 1,
                        reason = "Morning hours are most productive"
                    )
                )
            }
            in 14..16 -> {
                recommendations.add(
                    FocusModeRecommendation(
                        title = "Afternoon Focus",
                        description = "Combat afternoon fatigue with focus mode",
                        duration = 45,
                        priority = 2,
                        reason = "Afternoon focus helps maintain productivity"
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private fun getAppBasedRecommendations(patterns: UsagePatterns): List<FocusModeRecommendation> {
        val recommendations = mutableListOf<FocusModeRecommendation>()
        
        if (patterns.mostUsedApps.contains("Social Media")) {
            recommendations.add(
                FocusModeRecommendation(
                    title = "Social Media Break",
                    description = "Block social media apps to reduce distractions",
                    duration = 30,
                    priority = 1,
                    reason = "Social media is a major distraction"
                )
            )
        }
        
        if (patterns.mostUsedApps.contains("Entertainment")) {
            recommendations.add(
                FocusModeRecommendation(
                    title = "Entertainment Block",
                    description = "Block entertainment apps during work hours",
                    duration = 120,
                    priority = 2,
                    reason = "Entertainment apps reduce productivity"
                )
            )
        }
        
        return recommendations
    }
    
    private fun getProductivityRecommendations(patterns: UsagePatterns): List<FocusModeRecommendation> {
        val recommendations = mutableListOf<FocusModeRecommendation>()
        
        if (patterns.focusPatterns.any { it.focusScore < 50 }) {
            recommendations.add(
                FocusModeRecommendation(
                    title = "Focus Improvement",
                    description = "Use focus mode to improve concentration",
                    duration = 90,
                    priority = 1,
                    reason = "Your focus score is low"
                )
            )
        }
        
        return recommendations
    }
    
    private fun getTotalFocusModeSessions(): Int {
        return prefs.getInt("total_focus_sessions", 0)
    }
    
    private fun getTotalFocusModeTime(): Long {
        return prefs.getLong("total_focus_time", 0)
    }
    
    private fun getAverageSessionLength(): Long {
        val totalSessions = getTotalFocusModeSessions()
        val totalTime = getTotalFocusModeTime()
        return if (totalSessions > 0) totalTime / totalSessions else 0
    }
    
    private fun getMostBlockedApps(): List<String> {
        val blockedAppsJson = prefs.getString("most_blocked_apps", "[]") ?: "[]"
        return try {
            JSONArray(blockedAppsJson).let { array ->
                (0 until array.length()).map { array.getString(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun getProductivityGains(): ProductivityGains {
        val focusTime = getTotalFocusModeTime()
        val totalTime = prefs.getLong("total_screen_time", 0)
        
        return ProductivityGains(
            focusTimeRatio = if (totalTime > 0) (focusTime.toDouble() / totalTime * 100).toInt() else 0,
            productivityIncrease = 25, // Mock value
            distractionReduction = 40 // Mock value
        )
    }
    
    private fun calculateFocusScore(totalSessions: Int, totalTime: Long, gains: ProductivityGains): Int {
        val sessionScore = (totalSessions * 2).coerceAtMost(50)
        val timeScore = (totalTime / TimeUnit.HOURS.toMillis(1) * 5).toInt().coerceAtMost(30)
        val productivityScore = gains.productivityIncrease / 2
        
        return (sessionScore + timeScore + productivityScore).coerceIn(0, 100)
    }
    
    private fun saveFocusModeSession(session: FocusModeSessionRecord) {
        val sessionJson = JSONObject().apply {
            put("timestamp", session.timestamp.time)
            put("duration", session.duration)
            put("success", session.result.success)
            put("blockedApps", JSONArray(session.settings.blockedApps))
            put("allowedApps", JSONArray(session.settings.allowedApps))
        }
        
        val sessionsArray = try {
            JSONArray(prefs.getString("focus_sessions", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        
        sessionsArray.put(sessionJson)
        prefs.edit().putString("focus_sessions", sessionsArray.toString()).apply()
        
        // Update statistics
        if (session.result.success) {
            val totalSessions = getTotalFocusModeSessions() + 1
            val totalTime = getTotalFocusModeTime() + session.duration
            
            prefs.edit()
                .putInt("total_focus_sessions", totalSessions)
                .putLong("total_focus_time", totalTime)
                .apply()
        }
    }
    
    companion object {
        private const val TAG = "FocusModeIntegration"
    }
}

// Data classes
data class FocusModeSettings(
    val duration: Int, // in minutes
    val blockedApps: List<String>,
    val allowedApps: List<String>,
    val enableNotifications: Boolean = true,
    val autoStart: Boolean = false
)

data class FocusModeResult(
    val success: Boolean,
    val message: String,
    val duration: Int,
    val blockedApps: Int,
    val allowedApps: Int
)

data class FocusModeSession(
    val startTime: Date,
    val endTime: Date,
    val blockedApps: List<String>,
    val allowedApps: List<String>
)

data class SchedulingResult(
    val success: Boolean,
    val message: String,
    val scheduledSessions: Int
)

data class FocusModeStatus(
    val isActive: Boolean,
    val currentSession: FocusModeSession?,
    val remainingTime: Long,
    val blockedApps: List<String>,
    val allowedApps: List<String>
)

data class FocusModeRecommendation(
    val title: String,
    val description: String,
    val duration: Int,
    val priority: Int,
    val reason: String
)

data class FocusModeStatistics(
    val totalSessions: Int,
    val totalTime: Long,
    val averageSessionLength: Long,
    val mostBlockedApps: List<String>,
    val productivityGains: ProductivityGains,
    val focusScore: Int
)

data class FocusModeTemplate(
    val name: String,
    val description: String,
    val duration: Int,
    val blockedApps: List<String>,
    val allowedApps: List<String>,
    val priority: Int
)

data class FocusModeSessionRecord(
    val timestamp: Date,
    val settings: FocusModeSettings,
    val result: FocusModeResult,
    val duration: Int
)

data class ProductivityGains(
    val focusTimeRatio: Int,
    val productivityIncrease: Int,
    val distractionReduction: Int
)

class FocusModeScheduler {
    fun scheduleSessions(sessions: List<FocusModeSession>): Boolean {
        // Schedule focus mode sessions
        return true
    }
}

class AppBlockingManager {
    fun blockApps(apps: List<String>): Boolean {
        // Block specified apps
        return true
    }
    
    fun unblockAllApps(): Boolean {
        // Unblock all apps
        return true
    }
}

class FocusModeNotificationManager(private val context: Context) {
    fun setupFocusModeNotifications(settings: FocusModeSettings) {
        // Set up focus mode notifications
    }
    
    fun clearFocusModeNotifications() {
        // Clear focus mode notifications
    }
}
