package com.example.screentimeoverlay

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Performance monitoring dashboard for tracking and displaying optimization metrics
 */
class PerformanceDashboard(private val context: Context) {
    
    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    private val maxHistorySize = 100
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    companion object {
        private const val TAG = "PerformanceDashboard"
    }
    
    /**
     * Record a performance snapshot
     */
    fun recordSnapshot(metrics: PerformanceMetrics, adaptiveState: AdaptiveState, memoryStats: MemoryStats) {
        val snapshot = PerformanceSnapshot(
            timestamp = System.currentTimeMillis(),
            updateCount = metrics.updateCount,
            memoryUsageMB = metrics.memoryUsageMB,
            batteryLevel = metrics.batteryLevel,
            isCharging = metrics.isCharging,
            isLowBattery = metrics.isLowBattery,
            isHighMemoryUsage = metrics.isHighMemoryUsage,
            currentUpdateInterval = metrics.currentUpdateInterval,
            cacheSize = metrics.cacheSize,
            isScreenOn = adaptiveState.isScreenOn,
            isIdle = adaptiveState.isIdle,
            averageActivityInterval = adaptiveState.averageActivityInterval,
            totalMemoryMB = memoryStats.totalMemoryMB,
            availableMemoryMB = memoryStats.availableMemoryMB,
            memoryUsagePercent = memoryStats.memoryUsagePercent
        )
        
        performanceHistory.add(snapshot)
        
        // Keep only recent history
        if (performanceHistory.size > maxHistorySize) {
            performanceHistory.removeAt(0)
        }
        
        Log.d(TAG, "Performance snapshot recorded: ${snapshot.timestamp}")
    }
    
    /**
     * Get performance summary
     */
    fun getPerformanceSummary(): PerformanceSummary {
        if (performanceHistory.isEmpty()) {
            return PerformanceSummary(
                averageMemoryUsage = 0.0,
                averageBatteryLevel = 0.0,
                totalUpdates = 0,
                averageUpdateInterval = 0.0,
                memoryEfficiency = 0.0,
                batteryEfficiency = 0.0,
                optimizationScore = 0.0
            )
        }
        
        val recentSnapshots = performanceHistory.takeLast(10) // Last 10 snapshots
        
        val averageMemoryUsage = recentSnapshots.map { it.memoryUsageMB }.average()
        val averageBatteryLevel = recentSnapshots.map { it.batteryLevel.toDouble() }.average()
        val totalUpdates = recentSnapshots.sumOf { it.updateCount }
        val averageUpdateInterval = recentSnapshots.map { it.currentUpdateInterval.toDouble() }.average()
        
        // Calculate efficiency scores
        val memoryEfficiency = calculateMemoryEfficiency(recentSnapshots)
        val batteryEfficiency = calculateBatteryEfficiency(recentSnapshots)
        val optimizationScore = (memoryEfficiency + batteryEfficiency) / 2.0
        
        return PerformanceSummary(
            averageMemoryUsage = averageMemoryUsage,
            averageBatteryLevel = averageBatteryLevel,
            totalUpdates = totalUpdates,
            averageUpdateInterval = averageUpdateInterval,
            memoryEfficiency = memoryEfficiency,
            batteryEfficiency = batteryEfficiency,
            optimizationScore = optimizationScore
        )
    }
    
    /**
     * Get performance trends
     */
    fun getPerformanceTrends(): PerformanceTrends {
        if (performanceHistory.size < 2) {
            return PerformanceTrends(
                memoryTrend = Trend.STABLE,
                batteryTrend = Trend.STABLE,
                updateFrequencyTrend = Trend.STABLE,
                optimizationTrend = Trend.STABLE
            )
        }
        
        val recent = performanceHistory.takeLast(5)
        val older = performanceHistory.dropLast(5).takeLast(5)
        
        if (older.isEmpty()) {
            return PerformanceTrends(
                memoryTrend = Trend.STABLE,
                batteryTrend = Trend.STABLE,
                updateFrequencyTrend = Trend.STABLE,
                optimizationTrend = Trend.STABLE
            )
        }
        
        val memoryTrend = calculateTrend(
            older.map { it.memoryUsageMB.toDouble() },
            recent.map { it.memoryUsageMB.toDouble() }
        )
        
        val batteryTrend = calculateTrend(
            older.map { it.batteryLevel.toDouble() },
            recent.map { it.batteryLevel.toDouble() }
        )
        
        val updateFrequencyTrend = calculateTrend(
            older.map { it.currentUpdateInterval.toDouble() },
            recent.map { it.currentUpdateInterval.toDouble() }
        )
        
        val optimizationTrend = when {
            memoryTrend == Trend.IMPROVING && batteryTrend == Trend.IMPROVING -> Trend.IMPROVING
            memoryTrend == Trend.DECLINING || batteryTrend == Trend.DECLINING -> Trend.DECLINING
            else -> Trend.STABLE
        }
        
        return PerformanceTrends(
            memoryTrend = memoryTrend,
            batteryTrend = batteryTrend,
            updateFrequencyTrend = updateFrequencyTrend,
            optimizationTrend = optimizationTrend
        )
    }
    
    /**
     * Get optimization recommendations
     */
    fun getOptimizationRecommendations(): List<OptimizationRecommendation> {
        val recommendations = mutableListOf<OptimizationRecommendation>()
        val summary = getPerformanceSummary()
        
        // Memory optimization recommendations
        if (summary.memoryEfficiency < 0.7) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.USAGE_WARNING,
                    priority = Priority.HIGH,
                    title = "Memory Usage High",
                    description = "Consider reducing cache size or increasing cleanup frequency",
                    action = "Force memory cleanup"
                )
            )
        }
        
        // Battery optimization recommendations
        if (summary.batteryEfficiency < 0.6) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.USAGE_WARNING,
                    priority = Priority.HIGH,
                    title = "Battery Usage High",
                    description = "Reduce update frequency or enable power saving mode",
                    action = "Enable adaptive updates"
                )
            )
        }
        
        // Update frequency recommendations
        if (summary.averageUpdateInterval < 30000) { // Less than 30 seconds
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.USAGE_WARNING,
                    priority = Priority.MEDIUM,
                    title = "High Update Frequency",
                    description = "Consider increasing update intervals for better battery life",
                    action = "Increase update interval"
                )
            )
        }
        
        // General optimization recommendations
        if (summary.optimizationScore < 0.8) {
            recommendations.add(
                OptimizationRecommendation(
                    type = RecommendationType.USAGE_WARNING,
                    priority = Priority.MEDIUM,
                    title = "Performance Optimization",
                    description = "Overall performance can be improved",
                    action = "Review optimization settings"
                )
            )
        }
        
        return recommendations.sortedBy { it.priority.ordinal }
    }
    
    /**
     * Calculate memory efficiency score
     */
    private fun calculateMemoryEfficiency(snapshots: List<PerformanceSnapshot>): Double {
        val memoryUsages = snapshots.map { it.memoryUsageMB }
        val averageMemory = memoryUsages.average()
        val maxMemory = (memoryUsages.maxOrNull() ?: 0L).toDouble()
        
        // Efficiency is higher when memory usage is lower and more stable
        val stabilityScore = 1.0 - (memoryUsages.maxOrNull()!! - memoryUsages.minOrNull()!!).toDouble() / maxMemory
        val usageScore = 1.0 - (averageMemory / maxMemory)
        
        return (stabilityScore + usageScore) / 2.0
    }
    
    /**
     * Calculate battery efficiency score
     */
    private fun calculateBatteryEfficiency(snapshots: List<PerformanceSnapshot>): Double {
        val batteryLevels = snapshots.map { it.batteryLevel.toDouble() }
        val averageBattery = batteryLevels.average()
        val isChargingCount = snapshots.count { it.isCharging }
        
        // Efficiency is higher when battery level is stable and not frequently charging
        val batteryStability = 1.0 - (batteryLevels.maxOrNull()!! - batteryLevels.minOrNull()!!) / 100.0
        val chargingEfficiency = 1.0 - (isChargingCount.toDouble() / snapshots.size)
        
        return (batteryStability + chargingEfficiency) / 2.0
    }
    
    /**
     * Calculate trend between two sets of values
     */
    private fun calculateTrend(oldValues: List<Double>, newValues: List<Double>): Trend {
        val oldAverage = oldValues.average()
        val newAverage = newValues.average()
        
        val changePercent = (newAverage - oldAverage) / oldAverage
        
        return when {
            changePercent > 0.1 -> Trend.IMPROVING
            changePercent < -0.1 -> Trend.DECLINING
            else -> Trend.STABLE
        }
    }
    
    /**
     * Clear performance history
     */
    fun clearHistory() {
        performanceHistory.clear()
        Log.d(TAG, "Performance history cleared")
    }
    
    /**
     * Get formatted performance report
     */
    fun getFormattedReport(): String {
        val summary = getPerformanceSummary()
        val trends = getPerformanceTrends()
        val recommendations = getOptimizationRecommendations()
        
        return buildString {
            appendLine("=== Performance Report ===")
            appendLine("Memory Usage: ${String.format("%.1f", summary.averageMemoryUsage)}MB")
            appendLine("Battery Level: ${String.format("%.1f", summary.averageBatteryLevel)}%")
            appendLine("Total Updates: ${summary.totalUpdates}")
            appendLine("Update Interval: ${String.format("%.0f", summary.averageUpdateInterval)}ms")
            appendLine("Memory Efficiency: ${String.format("%.1f", summary.memoryEfficiency * 100)}%")
            appendLine("Battery Efficiency: ${String.format("%.1f", summary.batteryEfficiency * 100)}%")
            appendLine("Optimization Score: ${String.format("%.1f", summary.optimizationScore * 100)}%")
            appendLine()
            appendLine("=== Trends ===")
            appendLine("Memory: ${trends.memoryTrend}")
            appendLine("Battery: ${trends.batteryTrend}")
            appendLine("Updates: ${trends.updateFrequencyTrend}")
            appendLine("Overall: ${trends.optimizationTrend}")
            appendLine()
            appendLine("=== Recommendations ===")
            recommendations.forEach { rec ->
                appendLine("${rec.priority}: ${rec.title}")
                appendLine("  ${rec.description}")
            }
        }
    }
}

/**
 * Data class for performance snapshot
 */
data class PerformanceSnapshot(
    val timestamp: Long,
    val updateCount: Int,
    val memoryUsageMB: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isLowBattery: Boolean,
    val isHighMemoryUsage: Boolean,
    val currentUpdateInterval: Long,
    val cacheSize: Int,
    val isScreenOn: Boolean,
    val isIdle: Boolean,
    val averageActivityInterval: Long,
    val totalMemoryMB: Long,
    val availableMemoryMB: Long,
    val memoryUsagePercent: Double
)

/**
 * Data class for performance summary
 */
data class PerformanceSummary(
    val averageMemoryUsage: Double,
    val averageBatteryLevel: Double,
    val totalUpdates: Int,
    val averageUpdateInterval: Double,
    val memoryEfficiency: Double,
    val batteryEfficiency: Double,
    val optimizationScore: Double
)

/**
 * Data class for performance trends
 */
data class PerformanceTrends(
    val memoryTrend: Trend,
    val batteryTrend: Trend,
    val updateFrequencyTrend: Trend,
    val optimizationTrend: Trend
)

/**
 * Data class for optimization recommendation
 */
data class OptimizationRecommendation(
    val type: RecommendationType,
    val priority: Priority,
    val title: String,
    val description: String,
    val action: String
)

/**
 * Trend enumeration
 */
enum class Trend {
    IMPROVING, STABLE, DECLINING
}

/**
 * Recommendation type enumeration
 */

/**
 * Priority enumeration
 */
enum class Priority {
    LOW, MEDIUM, HIGH, CRITICAL
}
