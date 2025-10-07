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
 * AI-powered break time optimizer that suggests optimal break times
 * based on usage patterns, focus levels, and productivity research
 */
class BreakTimeOptimizer(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("break_optimizer", Context.MODE_PRIVATE)
    private val breakHistory = mutableListOf<BreakRecord>()
    private val productivityResearch = ProductivityResearch()
    
    /**
     * Get optimal break times for the current day
     */
    fun getOptimalBreakTimes(currentTime: Date, usageData: UsageData): List<BreakRecommendation> {
        val recommendations = mutableListOf<BreakRecommendation>()
        val timeOfDay = getTimeOfDay(currentTime)
        val sessionLength = calculateCurrentSessionLength(usageData)
        val focusLevel = calculateCurrentFocusLevel(usageData)
        val stressLevel = calculateStressLevel(usageData)
        
        // Immediate break recommendations
        if (shouldTakeImmediateBreak(sessionLength, focusLevel, stressLevel)) {
            recommendations.add(createImmediateBreakRecommendation(currentTime, sessionLength, focusLevel))
        }
        
        // Scheduled break recommendations
        val scheduledBreaks = getScheduledBreakRecommendations(currentTime, timeOfDay, usageData)
        recommendations.addAll(scheduledBreaks)
        
        // Micro-break recommendations
        val microBreaks = getMicroBreakRecommendations(currentTime, focusLevel)
        recommendations.addAll(microBreaks)
        
        return recommendations.sortedBy { it.priority }
    }
    
    /**
     * Get break recommendations based on usage patterns
     */
    fun getBreakRecommendations(usagePatterns: UsagePatterns): List<BreakRecommendation> {
        val recommendations = mutableListOf<BreakRecommendation>()
        
        // Analyze peak usage hours for break opportunities
        val peakHours = usagePatterns.peakHours
        peakHours.forEach { hour ->
            val breakTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 30) // Suggest break at 30 minutes past peak hour
            }.time
            
            recommendations.add(
                BreakRecommendation(
                    time = formatTime(breakTime),
                    duration = "15 minutes",
                    type = BreakType.SHORT_BREAK,
                    reason = "Optimal break time based on your usage patterns",
                    priority = 2,
                    confidence = 85,
                    benefits = listOf("Prevents burnout", "Maintains focus", "Reduces eye strain")
                )
            )
        }
        
        return recommendations
    }
    
    /**
     * Record a break taken by the user
     */
    fun recordBreak(breakType: BreakType, duration: Long, effectiveness: Int) {
        val breakRecord = BreakRecord(
            timestamp = Date(),
            type = breakType,
            duration = duration,
            effectiveness = effectiveness,
            context = getBreakContext()
        )
        
        breakHistory.add(breakRecord)
        saveBreakRecord(breakRecord)
        
        // Update break effectiveness model
        updateBreakEffectivenessModel(breakRecord)
    }
    
    /**
     * Get personalized break suggestions based on user's break history
     */
    fun getPersonalizedBreakSuggestions(): List<BreakRecommendation> {
        val effectiveBreaks = breakHistory.filter { it.effectiveness > 70 }
        val recommendations = mutableListOf<BreakRecommendation>()
        
        // Find most effective break times
        val effectiveTimes = effectiveBreaks.groupBy { getTimeOfDay(it.timestamp) }
            .maxByOrNull { it.value.size }
        
        effectiveTimes?.let { (timeOfDay, breaks) ->
            val averageDuration = breaks.map { it.duration }.average()
            val mostEffectiveType = breaks.groupBy { it.type }
                .maxByOrNull { it.value.size }?.key
            
            mostEffectiveType?.let { type ->
                recommendations.add(
                    BreakRecommendation(
                        time = "Based on your history",
                        duration = "${(averageDuration / 60000).toInt()} minutes",
                        type = type,
                        reason = "This break type has been most effective for you",
                        priority = 1,
                        confidence = 90,
                        benefits = listOf("Proven effective for you", "Matches your preferences")
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private fun shouldTakeImmediateBreak(sessionLength: Long, focusLevel: Int, stressLevel: Int): Boolean {
        val sessionMinutes = sessionLength / TimeUnit.MINUTES.toMillis(1)
        
        return when {
            sessionMinutes > 90 -> true // Long session
            focusLevel < 40 -> true // Low focus
            stressLevel > 70 -> true // High stress
            sessionMinutes > 60 && focusLevel < 60 -> true // Medium session with low focus
            else -> false
        }
    }
    
    private fun createImmediateBreakRecommendation(currentTime: Date, sessionLength: Long, focusLevel: Int): BreakRecommendation {
        val breakType = determineOptimalBreakType(sessionLength, focusLevel)
        val duration = determineOptimalBreakDuration(sessionLength, focusLevel)
        
        return BreakRecommendation(
            time = "Now",
            duration = duration,
            type = breakType,
            reason = generateBreakReason(sessionLength, focusLevel),
            priority = 1,
            confidence = 95,
            benefits = listOf("Prevents burnout", "Restores focus", "Reduces stress")
        )
    }
    
    private fun getScheduledBreakRecommendations(currentTime: Date, timeOfDay: TimeOfDay, usageData: UsageData): List<BreakRecommendation> {
        val recommendations = mutableListOf<BreakRecommendation>()
        val calendar = Calendar.getInstance()
        calendar.time = currentTime
        
        // Morning break (10:30 AM)
        if (timeOfDay == TimeOfDay.MORNING) {
            val morningBreakTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 30)
            }.time
            
            if (morningBreakTime.after(currentTime)) {
                recommendations.add(
                    BreakRecommendation(
                        time = formatTime(morningBreakTime),
                        duration = "15 minutes",
                        type = BreakType.SHORT_BREAK,
                        reason = "Morning break to maintain energy",
                        priority = 2,
                        confidence = 80,
                        benefits = listOf("Maintains morning energy", "Prevents afternoon crash")
                    )
                )
            }
        }
        
        // Afternoon break (2:30 PM)
        if (timeOfDay == TimeOfDay.AFTERNOON) {
            val afternoonBreakTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 14)
                set(Calendar.MINUTE, 30)
            }.time
            
            if (afternoonBreakTime.after(currentTime)) {
                recommendations.add(
                    BreakRecommendation(
                        time = formatTime(afternoonBreakTime),
                        duration = "20 minutes",
                        type = BreakType.MEDIUM_BREAK,
                        reason = "Afternoon break to combat fatigue",
                        priority = 2,
                        confidence = 85,
                        benefits = listOf("Combats afternoon fatigue", "Boosts productivity")
                    )
                )
            }
        }
        
        return recommendations
    }
    
    private fun getMicroBreakRecommendations(currentTime: Date, focusLevel: Int): List<BreakRecommendation> {
        val recommendations = mutableListOf<BreakRecommendation>()
        
        if (focusLevel < 60) {
            recommendations.add(
                BreakRecommendation(
                    time = "Every 25 minutes",
                    duration = "2-3 minutes",
                    type = BreakType.MICRO_BREAK,
                    reason = "Micro-breaks to maintain focus",
                    priority = 3,
                    confidence = 75,
                    benefits = listOf("Maintains focus", "Reduces eye strain", "Prevents fatigue")
                )
            )
        }
        
        return recommendations
    }
    
    private fun determineOptimalBreakType(sessionLength: Long, focusLevel: Int): BreakType {
        val sessionMinutes = sessionLength / TimeUnit.MINUTES.toMillis(1)
        
        return when {
            sessionMinutes > 120 -> BreakType.LONG_BREAK
            sessionMinutes > 60 -> BreakType.MEDIUM_BREAK
            focusLevel < 40 -> BreakType.SHORT_BREAK
            else -> BreakType.MICRO_BREAK
        }
    }
    
    private fun determineOptimalBreakDuration(sessionLength: Long, focusLevel: Int): String {
        val sessionMinutes = sessionLength / TimeUnit.MINUTES.toMillis(1)
        
        return when {
            sessionMinutes > 120 -> "30 minutes"
            sessionMinutes > 60 -> "15 minutes"
            focusLevel < 40 -> "10 minutes"
            else -> "5 minutes"
        }
    }
    
    private fun generateBreakReason(sessionLength: Long, focusLevel: Int): String {
        val sessionMinutes = sessionLength / TimeUnit.MINUTES.toMillis(1)
        
        return when {
            sessionMinutes > 90 -> "You've been focused for a long time. A break will help maintain productivity."
            focusLevel < 40 -> "Your focus level is low. A break will help restore concentration."
            sessionMinutes > 60 -> "You've been working for over an hour. Time for a refreshing break."
            else -> "A quick break will help maintain your focus and energy."
        }
    }
    
    private fun calculateCurrentSessionLength(usageData: UsageData): Long {
        // This would integrate with the session tracking system
        return 0L
    }
    
    private fun calculateCurrentFocusLevel(usageData: UsageData): Int {
        return usageData.focusScore
    }
    
    private fun calculateStressLevel(usageData: UsageData): Int {
        // Calculate stress level based on usage patterns
        val sessionCount = usageData.sessionCount
        val totalTime = usageData.totalTime
        
        return when {
            sessionCount > 20 -> 80 // High session count indicates stress
            totalTime > TimeUnit.HOURS.toMillis(6) -> 70 // Long usage time
            sessionCount > 15 -> 60 // Medium session count
            else -> 40 // Low stress
        }
    }
    
    private fun getTimeOfDay(date: Date): TimeOfDay {
        val calendar = Calendar.getInstance()
        calendar.time = date
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in 6..11 -> TimeOfDay.MORNING
            in 12..17 -> TimeOfDay.AFTERNOON
            in 18..22 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }
    
    private fun getBreakContext(): BreakContext {
        // This would capture the context when the break was taken
        return BreakContext(
            appCategory = "Unknown",
            sessionLength = 0L,
            focusLevel = 0,
            stressLevel = 0
        )
    }
    
    private fun updateBreakEffectivenessModel(breakRecord: BreakRecord) {
        // Update the model based on break effectiveness
        val breakType = breakRecord.type
        val effectiveness = breakRecord.effectiveness
        
        // Store effectiveness data for future recommendations
        val effectivenessKey = "break_effectiveness_${breakType.name}"
        val currentEffectiveness = prefs.getInt(effectivenessKey, 50)
        val newEffectiveness = (currentEffectiveness + effectiveness) / 2
        
        prefs.edit().putInt(effectivenessKey, newEffectiveness).apply()
    }
    
    private fun saveBreakRecord(breakRecord: BreakRecord) {
        val recordJson = JSONObject().apply {
            put("timestamp", breakRecord.timestamp.time)
            put("type", breakRecord.type.name)
            put("duration", breakRecord.duration)
            put("effectiveness", breakRecord.effectiveness)
            put("context", JSONObject().apply {
                put("appCategory", breakRecord.context.appCategory)
                put("sessionLength", breakRecord.context.sessionLength)
                put("focusLevel", breakRecord.context.focusLevel)
                put("stressLevel", breakRecord.context.stressLevel)
            })
        }
        
        val recordsArray = try {
            JSONArray(prefs.getString("break_records", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        
        recordsArray.put(recordJson)
        prefs.edit().putString("break_records", recordsArray.toString()).apply()
    }
    
    private fun formatTime(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
    
    /**
     * Update the model with new usage data
     */
    fun updateModel(usageData: UsageData) {
        // Update break optimization model based on usage data
        val sessionLength = calculateCurrentSessionLength(usageData)
        val focusLevel = calculateCurrentFocusLevel(usageData)
        
        // Store data for future break recommendations
        val breakData = JSONObject().apply {
            put("timestamp", usageData.timestamp.time)
            put("sessionLength", sessionLength)
            put("focusLevel", focusLevel)
            put("totalTime", usageData.totalTime)
        }
        
        val dataArray = try {
            JSONArray(prefs.getString("break_optimization_data", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        
        dataArray.put(breakData)
        prefs.edit().putString("break_optimization_data", dataArray.toString()).apply()
    }
    
    companion object {
        private const val TAG = "BreakTimeOptimizer"
    }
}

// Data classes

data class BreakRecord(
    val timestamp: Date,
    val type: BreakType,
    val duration: Long,
    val effectiveness: Int,
    val context: BreakContext
)

data class BreakContext(
    val appCategory: String,
    val sessionLength: Long,
    val focusLevel: Int,
    val stressLevel: Int
)

class ProductivityResearch {
    fun getOptimalBreakIntervals(): Map<BreakType, Int> {
        return mapOf(
            BreakType.MICRO_BREAK to 25, // Every 25 minutes
            BreakType.SHORT_BREAK to 60, // Every hour
            BreakType.MEDIUM_BREAK to 120, // Every 2 hours
            BreakType.LONG_BREAK to 240 // Every 4 hours
        )
    }
    
    fun getBreakDurationRecommendations(): Map<BreakType, Int> {
        return mapOf(
            BreakType.MICRO_BREAK to 2, // 2 minutes
            BreakType.SHORT_BREAK to 15, // 15 minutes
            BreakType.MEDIUM_BREAK to 30, // 30 minutes
            BreakType.LONG_BREAK to 60 // 60 minutes
        )
    }
}
