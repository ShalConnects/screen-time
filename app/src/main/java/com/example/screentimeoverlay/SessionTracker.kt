package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Tracks focused usage sessions for better screen time analysis
 */
class SessionTracker(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("session_tracker", Context.MODE_PRIVATE)
    private val sessionThresholdMs = TimeUnit.MINUTES.toMillis(2) // Minimum 2 minutes for a session
    private val breakThresholdMs = TimeUnit.MINUTES.toMillis(5) // 5 minutes break between sessions
    
    private var currentSession: AppSession? = null
    private var lastAppSwitchTime = 0L
    private var sessionStartTime = 0L
    
    /**
     * Start tracking a new session
     */
    fun startSession(packageName: String, appName: String) {
        val currentTime = System.currentTimeMillis()
        
        // If we have an active session, end it first if it's been inactive too long
        currentSession?.let { session ->
            if (currentTime - session.lastActivityTime > breakThresholdMs) {
                endCurrentSession()
            }
        }
        
        // Start new session if we don't have one or if it's been long enough
        if (currentSession == null || currentTime - lastAppSwitchTime > breakThresholdMs) {
            currentSession = AppSession(
                id = UUID.randomUUID().toString(),
                packageName = packageName,
                appName = appName,
                startTime = currentTime,
                endTime = 0,
                totalTime = 0,
                isActive = true,
                lastActivityTime = currentTime
            )
            sessionStartTime = currentTime
            Log.d(TAG, "Started new session for $appName")
        } else {
            // Update existing session
            currentSession?.let { session ->
                if (session.packageName != packageName) {
                    // App switch within session - update session
                    session.packageName = packageName
                    session.appName = appName
                    session.lastActivityTime = currentTime
                    Log.d(TAG, "Updated session to $appName")
                } else {
                    // Same app, just update activity time
                    session.lastActivityTime = currentTime
                }
            }
        }
        
        lastAppSwitchTime = currentTime
    }
    
    /**
     * Update current session activity
     */
    fun updateSession() {
        val currentTime = System.currentTimeMillis()
        currentSession?.let { session ->
            if (session.isActive) {
                val timeSinceLastUpdate = currentTime - session.lastActivityTime
                if (timeSinceLastUpdate < breakThresholdMs) {
                    session.totalTime += timeSinceLastUpdate
                    session.lastActivityTime = currentTime
                } else {
                    // Session has been inactive too long, end it
                    endCurrentSession()
                }
            }
        }
    }
    
    /**
     * End current session
     */
    fun endCurrentSession() {
        currentSession?.let { session ->
            if (session.isActive) {
                session.isActive = false
                session.endTime = System.currentTimeMillis()
                
                // Only save sessions that meet minimum threshold
                if (session.totalTime >= sessionThresholdMs) {
                    saveSession(session)
                    Log.d(TAG, "Ended session for ${session.appName}: ${formatTime(session.totalTime)}")
                }
            }
        }
        currentSession = null
    }
    
    /**
     * Get current session info
     */
    fun getCurrentSession(): AppSession? = currentSession
    
    /**
     * Get sessions for a specific date
     */
    fun getSessionsForDate(date: Date): List<AppSession> {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val startOfDay = calendar.timeInMillis
        val endOfDay = startOfDay + TimeUnit.DAYS.toMillis(1)
        
        return getAllSessions().filter { session ->
            session.startTime >= startOfDay && session.startTime < endOfDay
        }
    }
    
    /**
     * Get sessions for a date range
     */
    fun getSessionsForDateRange(startDate: Date, endDate: Date): List<AppSession> {
        val startTime = startDate.time
        val endTime = endDate.time
        
        return getAllSessions().filter { session ->
            session.startTime >= startTime && session.startTime < endTime
        }
    }
    
    /**
     * Get session statistics for a date
     */
    fun getSessionStats(date: Date): SessionStats {
        val sessions = getSessionsForDate(date)
        
        val totalSessions = sessions.size
        val totalTime = sessions.sumOf { it.totalTime }
        val averageSessionTime = if (totalSessions > 0) totalTime / totalSessions else 0L
        val longestSession = sessions.maxByOrNull { it.totalTime }?.totalTime ?: 0L
        
        // Calculate focus score (sessions longer than 15 minutes are considered focused)
        val focusedSessions = sessions.count { it.totalTime >= TimeUnit.MINUTES.toMillis(15) }
        val focusScore = if (totalSessions > 0) (focusedSessions * 100) / totalSessions else 0
        
        return SessionStats(
            date = date,
            totalSessions = totalSessions,
            totalTime = totalTime,
            averageSessionTime = averageSessionTime,
            longestSession = longestSession,
            focusScore = focusScore,
            focusedSessions = focusedSessions
        )
    }
    
    /**
     * Get session statistics for a date range
     */
    fun getSessionStatsForRange(startDate: Date, endDate: Date): SessionStats {
        val sessions = getSessionsForDateRange(startDate, endDate)
        
        val totalSessions = sessions.size
        val totalTime = sessions.sumOf { it.totalTime }
        val averageSessionTime = if (totalSessions > 0) totalTime / totalSessions else 0L
        val longestSession = sessions.maxByOrNull { it.totalTime }?.totalTime ?: 0L
        
        // Calculate focus score (sessions longer than 15 minutes are considered focused)
        val focusedSessions = sessions.count { it.totalTime >= TimeUnit.MINUTES.toMillis(15) }
        val focusScore = if (totalSessions > 0) (focusedSessions * 100) / totalSessions else 0
        
        return SessionStats(
            date = startDate,
            totalSessions = totalSessions,
            totalTime = totalTime,
            averageSessionTime = averageSessionTime,
            longestSession = longestSession,
            focusScore = focusScore,
            focusedSessions = focusedSessions
        )
    }
    
    /**
     * Get session insights for better analytics
     */
    fun getSessionInsights(date: Date): SessionInsights {
        val sessions = getSessionsForDate(date)
        
        if (sessions.isEmpty()) {
            return SessionInsights(
                firstSessionTime = null,
                lastSessionTime = null,
                averageBreakTime = 0L,
                mostUsedApp = null,
                sessionPattern = "No sessions recorded"
            )
        }
        
        val sortedSessions = sessions.sortedBy { it.startTime }
        val firstSession = sortedSessions.first()
        val lastSession = sortedSessions.last()
        
        // Calculate average break time
        var totalBreakTime = 0L
        var breakCount = 0
        
        for (i in 1 until sortedSessions.size) {
            val breakTime = sortedSessions[i].startTime - sortedSessions[i-1].endTime
            if (breakTime > 0) {
                totalBreakTime += breakTime
                breakCount++
            }
        }
        
        val averageBreakTime = if (breakCount > 0) totalBreakTime / breakCount else 0L
        
        // Find most used app
        val appUsage = sessions.groupBy { it.appName }
            .mapValues { it.value.sumOf { session -> session.totalTime } }
        val mostUsedApp = appUsage.maxByOrNull { it.value }?.key
        
        // Determine session pattern
        val sessionPattern = when {
            sessions.size >= 10 -> "High activity day"
            sessions.size >= 5 -> "Moderate activity day"
            sessions.size >= 2 -> "Light activity day"
            else -> "Minimal activity day"
        }
        
        return SessionInsights(
            firstSessionTime = firstSession.startTime,
            lastSessionTime = lastSession.startTime,
            averageBreakTime = averageBreakTime,
            mostUsedApp = mostUsedApp,
            sessionPattern = sessionPattern
        )
    }
    
    /**
     * Get top apps by session count
     */
    fun getTopAppsBySessions(dateRange: DateRange): List<AppSessionSummary> {
        val sessions = getSessionsForDateRange(dateRange.startDate, dateRange.endDate)
        val appSessions = mutableMapOf<String, AppSessionSummary>()
        
        sessions.forEach { session ->
            val existing = appSessions[session.packageName]
            if (existing != null) {
                existing.sessionCount++
                existing.totalTime += session.totalTime
                existing.averageSessionTime = existing.totalTime / existing.sessionCount
            } else {
                appSessions[session.packageName] = AppSessionSummary(
                    packageName = session.packageName,
                    appName = session.appName,
                    sessionCount = 1,
                    totalTime = session.totalTime,
                    averageSessionTime = session.totalTime
                )
            }
        }
        
        return appSessions.values.sortedByDescending { it.totalTime }
    }
    
    /**
     * Save session to storage
     */
    private fun saveSession(session: AppSession) {
        val sessionsJson = prefs.getString("sessions", "[]") ?: "[]"
        val sessionsArray = try {
            JSONArray(sessionsJson)
        } catch (e: Exception) {
            JSONArray()
        }
        
        val sessionJson = JSONObject().apply {
            put("id", session.id)
            put("packageName", session.packageName)
            put("appName", session.appName)
            put("startTime", session.startTime)
            put("endTime", session.endTime)
            put("totalTime", session.totalTime)
            put("isActive", session.isActive)
        }
        
        sessionsArray.put(sessionJson)
        prefs.edit().putString("sessions", sessionsArray.toString()).apply()
    }
    
    /**
     * Get all saved sessions
     */
    private fun getAllSessions(): List<AppSession> {
        val sessionsJson = prefs.getString("sessions", "[]") ?: "[]"
        return try {
            val sessionsArray = JSONArray(sessionsJson)
            val sessions = mutableListOf<AppSession>()
            
            for (i in 0 until sessionsArray.length()) {
                val sessionJson = sessionsArray.getJSONObject(i)
                sessions.add(AppSession(
                    id = sessionJson.getString("id"),
                    packageName = sessionJson.getString("packageName"),
                    appName = sessionJson.getString("appName"),
                    startTime = sessionJson.getLong("startTime"),
                    endTime = sessionJson.getLong("endTime"),
                    totalTime = sessionJson.getLong("totalTime"),
                    isActive = sessionJson.getBoolean("isActive")
                ))
            }
            
            sessions
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sessions", e)
            emptyList()
        }
    }
    
    /**
     * Clear old sessions (older than 30 days)
     */
    fun clearOldSessions() {
        val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        val allSessions = getAllSessions()
        val recentSessions = allSessions.filter { it.startTime >= thirtyDaysAgo }
        
        val sessionsArray = JSONArray()
        recentSessions.forEach { session ->
            val sessionJson = JSONObject().apply {
                put("id", session.id)
                put("packageName", session.packageName)
                put("appName", session.appName)
                put("startTime", session.startTime)
                put("endTime", session.endTime)
                put("totalTime", session.totalTime)
                put("isActive", session.isActive)
            }
            sessionsArray.put(sessionJson)
        }
        
        prefs.edit().putString("sessions", sessionsArray.toString()).apply()
        Log.d(TAG, "Cleared old sessions, kept ${recentSessions.size} recent sessions")
    }
    
    private fun formatTime(timeMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(timeMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs) % 60
        return String.format("%02d:%02d", hours, minutes)
    }
    
    companion object {
        private const val TAG = "SessionTracker"
    }
}

data class AppSession(
    val id: String,
    var packageName: String,
    var appName: String,
    val startTime: Long,
    var endTime: Long = 0,
    var totalTime: Long,
    var isActive: Boolean,
    var lastActivityTime: Long = 0
)

data class SessionStats(
    val date: Date,
    val totalSessions: Int,
    val totalTime: Long,
    val averageSessionTime: Long,
    val longestSession: Long,
    val focusScore: Int,
    val focusedSessions: Int
)

data class AppSessionSummary(
    val packageName: String,
    val appName: String,
    var sessionCount: Int,
    var totalTime: Long,
    var averageSessionTime: Long
)

data class SessionInsights(
    val firstSessionTime: Long?,
    val lastSessionTime: Long?,
    val averageBreakTime: Long,
    val mostUsedApp: String?,
    val sessionPattern: String
)

