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
 * AI-powered usage predictor that analyzes patterns to predict future usage
 */
class UsagePredictor(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("usage_predictor", Context.MODE_PRIVATE)
    private val historicalData = mutableListOf<HistoricalUsageData>()
    private val predictionModel = PredictionModel()
    
    /**
     * Predict daily usage based on current patterns
     */
    fun predictDailyUsage(currentTime: Date, currentUsage: UsageData): UsagePredictions {
        val timeOfDay = getTimeOfDay(currentTime)
        val historicalPatterns = getHistoricalPatterns()
        val currentRate = calculateCurrentUsageRate(currentUsage)
        
        val predictedTotal = predictTotalDailyUsage(currentRate, timeOfDay, historicalPatterns)
        val goalExceeded = isGoalExceeded(predictedTotal)
        val confidence = calculatePredictionConfidence(historicalPatterns, currentUsage)
        
        return UsagePredictions(
            predictedTotalTime = predictedTotal,
            isExceedingGoal = goalExceeded,
            excessTime = if (goalExceeded) predictedTotal - getDailyGoal() else 0,
            confidence = confidence,
            hourlyPredictions = generateHourlyPredictions(currentTime, historicalPatterns),
            riskFactors = identifyRiskFactors(currentUsage, historicalPatterns)
        )
    }
    
    /**
     * Predict usage for a specific period
     */
    fun predictPeriodUsage(startDate: Date, endDate: Date, usageData: List<UsageData>): UsagePredictions {
        val periodLength = ((endDate.time - startDate.time) / TimeUnit.DAYS.toMillis(1)).toInt()
        val historicalPatterns = getHistoricalPatterns()
        
        val predictedTotal = predictPeriodTotal(usageData, periodLength, historicalPatterns.toMap())
        val confidence = calculatePeriodConfidence(usageData, historicalPatterns.toMap())
        
        return UsagePredictions(
            predictedTotalTime = predictedTotal,
            isExceedingGoal = isGoalExceeded(predictedTotal),
            excessTime = if (isGoalExceeded(predictedTotal)) predictedTotal - getDailyGoal() else 0,
            confidence = confidence,
            hourlyPredictions = emptyList(),
            riskFactors = identifyPeriodRiskFactors(usageData, historicalPatterns.toMap())
        )
    }
    
    /**
     * Get usage predictions for the next few hours
     */
    fun getUsagePredictions(): UsagePredictions {
        val currentTime = Date()
        val currentUsage = getCurrentUsageData()
        return predictDailyUsage(currentTime, currentUsage)
    }
    
    /**
     * Update the prediction model with new data
     */
    fun updateModel(usageData: UsageData) {
        val historicalData = HistoricalUsageData(
            date = usageData.timestamp,
            totalTime = usageData.totalTime,
            sessionCount = usageData.sessionCount,
            focusScore = usageData.focusScore,
            hourlyBreakdown = generateHourlyBreakdown(usageData)
        )
        
        this.historicalData.add(historicalData)
        saveHistoricalData(historicalData)
        
        // Retrain model with new data
        retrainModel()
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
    
    private fun getHistoricalPatterns(): HistoricalPatterns {
        val recentData = historicalData.takeLast(30) // Last 30 days
        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val sameDayData = recentData.filter { 
            Calendar.getInstance().apply { time = it.date }.get(Calendar.DAY_OF_WEEK) == dayOfWeek 
        }
        
        return HistoricalPatterns(
            averageDailyUsage = recentData.map { it.totalTime }.average().toLong(),
            averageSessionCount = recentData.map { it.sessionCount }.average(),
            averageFocusScore = recentData.map { it.focusScore }.average(),
            sameDayPattern = if (sameDayData.isNotEmpty()) {
                SameDayPattern(
                    averageUsage = sameDayData.map { it.totalTime }.average().toLong(),
                    peakHours = findPeakHours(sameDayData),
                    typicalSessionLength = calculateTypicalSessionLength(sameDayData)
                )
            } else null,
            trendDirection = calculateTrendDirection(recentData)
        )
    }
    
    private fun calculateCurrentUsageRate(usageData: UsageData): Double {
        val currentTime = System.currentTimeMillis()
        val dayStart = getDayStart(currentTime)
        val elapsedTime = currentTime - dayStart
        val elapsedHours = elapsedTime / TimeUnit.HOURS.toMillis(1).toDouble()
        
        return if (elapsedHours > 0) usageData.totalTime / elapsedHours else 0.0
    }
    
    private fun predictTotalDailyUsage(currentRate: Double, timeOfDay: TimeOfDay, patterns: HistoricalPatterns): Long {
        val remainingHours = getRemainingHours(timeOfDay)
        val projectedRemaining = (currentRate * remainingHours).toLong()
        val currentUsage = getCurrentUsageData().totalTime
        
        // Apply pattern adjustments
        val patternAdjustment = calculatePatternAdjustment(patterns, timeOfDay)
        val adjustedProjection = (projectedRemaining * patternAdjustment).toLong()
        
        return currentUsage + adjustedProjection
    }
    
    private fun isGoalExceeded(predictedTotal: Long): Boolean {
        val dailyGoal = getDailyGoal()
        return predictedTotal > dailyGoal
    }
    
    private fun getDailyGoal(): Long {
        return prefs.getLong("daily_goal_ms", TimeUnit.HOURS.toMillis(8))
    }
    
    private fun calculatePredictionConfidence(patterns: HistoricalPatterns, currentUsage: UsageData): Int {
        val dataPoints = historicalData.size
        val consistency = calculateConsistency(patterns)
        val recency = calculateRecency(currentUsage)
        
        return ((dataPoints * 0.3) + (consistency * 0.4) + (recency * 0.3)).toInt().coerceIn(0, 100)
    }
    
    private fun generateHourlyPredictions(currentTime: Date, patterns: HistoricalPatterns): List<HourlyPrediction> {
        val predictions = mutableListOf<HourlyPrediction>()
        val calendar = Calendar.getInstance()
        calendar.time = currentTime
        
        for (hour in calendar.get(Calendar.HOUR_OF_DAY) until 24) {
            val predictedUsage = predictHourlyUsage(hour, patterns)
            predictions.add(
                HourlyPrediction(
                    hour = hour,
                    predictedUsage = predictedUsage,
                    confidence = calculateHourlyConfidence(hour, patterns)
                )
            )
        }
        
        return predictions
    }
    
    private fun identifyRiskFactors(currentUsage: UsageData, patterns: HistoricalPatterns): List<String> {
        val riskFactors = mutableListOf<String>()
        
        if (currentUsage.focusScore < 50) {
            riskFactors.add("Low focus score indicates potential distraction")
        }
        
        if (currentUsage.sessionCount > patterns.averageSessionCount * 1.5) {
            riskFactors.add("High session count suggests frequent app switching")
        }
        
        if (patterns.trendDirection == TrendDirection.INCREASING) {
            riskFactors.add("Usage trend is increasing")
        }
        
        return riskFactors
    }
    
    private fun predictHourlyUsage(hour: Int, patterns: HistoricalPatterns): Long {
        val sameDayPattern = patterns.sameDayPattern
        return if (sameDayPattern != null && sameDayPattern.peakHours.contains(hour)) {
            (patterns.averageDailyUsage * 0.15).toLong() // Peak hours get 15% of daily usage
        } else {
            (patterns.averageDailyUsage * 0.05).toLong() // Regular hours get 5% of daily usage
        }
    }
    
    private fun calculateHourlyConfidence(hour: Int, patterns: HistoricalPatterns): Int {
        val sameDayPattern = patterns.sameDayPattern
        return if (sameDayPattern != null && sameDayPattern.peakHours.contains(hour)) {
            85 // Higher confidence for peak hours
        } else {
            70 // Lower confidence for off-peak hours
        }
    }
    
    private fun getRemainingHours(timeOfDay: TimeOfDay): Double {
        return when (timeOfDay) {
            TimeOfDay.MORNING -> 16.0
            TimeOfDay.AFTERNOON -> 8.0
            TimeOfDay.EVENING -> 4.0
            TimeOfDay.NIGHT -> 2.0
        }
    }
    
    private fun calculatePatternAdjustment(patterns: HistoricalPatterns, timeOfDay: TimeOfDay): Double {
        val baseAdjustment = when (timeOfDay) {
            TimeOfDay.MORNING -> 1.0
            TimeOfDay.AFTERNOON -> 1.2
            TimeOfDay.EVENING -> 0.8
            TimeOfDay.NIGHT -> 0.5
        }
        
        val trendAdjustment = when (patterns.trendDirection) {
            TrendDirection.INCREASING -> 1.1
            TrendDirection.DECLINING -> 0.9
            else -> 1.0
        }
        
        return baseAdjustment * trendAdjustment
    }
    
    private fun findPeakHours(sameDayData: List<HistoricalUsageData>): List<Int> {
        val hourlyUsage = mutableMapOf<Int, Long>()
        
        sameDayData.forEach { data ->
            data.hourlyBreakdown.forEach { (hour, usage) ->
                hourlyUsage[hour] = (hourlyUsage[hour] ?: 0) + usage
            }
        }
        
        val averageUsage = hourlyUsage.values.average()
        return hourlyUsage.filter { it.value > averageUsage * 1.5 }.keys.sorted()
    }
    
    private fun calculateTypicalSessionLength(sameDayData: List<HistoricalUsageData>): Long {
        val totalSessions = sameDayData.sumOf { it.sessionCount }
        val totalTime = sameDayData.sumOf { it.totalTime }
        return if (totalSessions > 0) totalTime / totalSessions else 0
    }
    
    private fun calculateTrendDirection(recentData: List<HistoricalUsageData>): TrendDirection {
        if (recentData.size < 7) return TrendDirection.STABLE
        
        val firstWeek = recentData.take(7).map { it.totalTime }.average()
        val lastWeek = recentData.takeLast(7).map { it.totalTime }.average()
        
        return when {
            lastWeek > firstWeek * 1.1 -> TrendDirection.INCREASING
            lastWeek < firstWeek * 0.9 -> TrendDirection.DECLINING
            else -> TrendDirection.STABLE
        }
    }
    
    private fun calculateConsistency(patterns: HistoricalPatterns): Int {
        // Calculate consistency based on variance in historical data
        val variance = historicalData.map { it.totalTime }.let { data ->
            val mean = data.average()
            data.map { (it - mean).pow(2) }.average()
        }
        
        return (100 - (variance / patterns.averageDailyUsage * 100)).toInt().coerceIn(0, 100)
    }
    
    private fun calculateRecency(currentUsage: UsageData): Int {
        // Higher score for more recent and complete data
        val currentTime = System.currentTimeMillis()
        val dayStart = getDayStart(currentTime)
        val elapsedTime = currentTime - dayStart
        val dayProgress = elapsedTime / TimeUnit.HOURS.toMillis(1).toDouble()
        
        return (dayProgress * 100).toInt().coerceIn(0, 100)
    }
    
    private fun getDayStart(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun generateHourlyBreakdown(usageData: UsageData): Map<Int, Long> {
        // This would be implemented based on actual usage tracking
        return emptyMap()
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
    
    private fun saveHistoricalData(data: HistoricalUsageData) {
        val dataJson = JSONObject().apply {
            put("date", data.date.time)
            put("totalTime", data.totalTime)
            put("sessionCount", data.sessionCount)
            put("focusScore", data.focusScore)
            put("hourlyBreakdown", JSONObject(data.hourlyBreakdown))
        }
        
        val dataArray = try {
            JSONArray(prefs.getString("historical_data", "[]") ?: "[]")
        } catch (e: Exception) {
            JSONArray()
        }
        
        dataArray.put(dataJson)
        prefs.edit().putString("historical_data", dataArray.toString()).apply()
    }
    
    private fun retrainModel() {
        // Retrain the prediction model with new data
        predictionModel.updateWeights(historicalData)
    }
    
    private fun predictPeriodTotal(usageData: List<UsageData>, periodLength: Int, historicalPatterns: Map<String, Any>): Long {
        // Simple prediction based on average usage
        val averageDailyUsage = if (usageData.isNotEmpty()) {
            usageData.map { it.totalTime }.average().toLong()
        } else {
            0L
        }
        return averageDailyUsage * periodLength
    }
    
    private fun calculatePeriodConfidence(usageData: List<UsageData>, historicalPatterns: Map<String, Any>): Int {
        // Calculate confidence based on data availability and consistency
        return if (usageData.size >= 7) 85 else if (usageData.size >= 3) 70 else 50
    }
    
    private fun identifyPeriodRiskFactors(usageData: List<UsageData>, historicalPatterns: Map<String, Any>): List<String> {
        val riskFactors = mutableListOf<String>()
        
        if (usageData.isEmpty()) {
            riskFactors.add("Insufficient data for accurate prediction")
        }
        
        val totalTime = usageData.sumOf { it.totalTime }
        if (totalTime > TimeUnit.HOURS.toMillis(8)) {
            riskFactors.add("High daily usage detected")
        }
        
        return riskFactors
    }
    
    companion object {
        private const val TAG = "UsagePredictor"
    }
}

// Extension function to convert HistoricalPatterns to Map
fun HistoricalPatterns.toMap(): Map<String, Any> {
    return mapOf(
        "averageDailyUsage" to averageDailyUsage,
        "averageSessionCount" to averageSessionCount,
        "averageFocusScore" to averageFocusScore,
        "sameDayPattern" to (sameDayPattern?.let { 
            mapOf(
                "averageUsage" to it.averageUsage,
                "peakHours" to it.peakHours,
                "typicalSessionLength" to it.typicalSessionLength
            )
        } ?: emptyMap<String, Any>()),
        "trendDirection" to trendDirection
    )
}

// Data classes


data class HistoricalUsageData(
    val date: Date,
    val totalTime: Long,
    val sessionCount: Int,
    val focusScore: Int,
    val hourlyBreakdown: Map<Int, Long>
)

data class HistoricalPatterns(
    val averageDailyUsage: Long,
    val averageSessionCount: Double,
    val averageFocusScore: Double,
    val sameDayPattern: SameDayPattern?,
    val trendDirection: TrendDirection
)

data class SameDayPattern(
    val averageUsage: Long,
    val peakHours: List<Int>,
    val typicalSessionLength: Long
)

class PredictionModel {
    private var weights = mutableMapOf<String, Double>()
    
    fun updateWeights(data: List<HistoricalUsageData>) {
        // Update model weights based on historical data
        // This is a simplified implementation
        weights["time_weight"] = 0.4
        weights["session_weight"] = 0.3
        weights["focus_weight"] = 0.3
    }
}
