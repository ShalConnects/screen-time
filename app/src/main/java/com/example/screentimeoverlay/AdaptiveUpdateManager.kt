package com.example.screentimeoverlay

import android.content.Context
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Manages adaptive update intervals based on device state and usage patterns
 */
class AdaptiveUpdateManager(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val updateExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    // Update intervals
    private var baseInterval = 60_000L // 1 minute
    private var screenOffInterval = 300_000L // 5 minutes
    private var lowBatteryInterval = 180_000L // 3 minutes
    private var idleInterval = 600_000L // 10 minutes
    
    // State tracking
    private var isScreenOn = true
    private var isLowBattery = false
    private var isIdle = false
    private var lastActivityTime = System.currentTimeMillis()
    private var consecutiveIdleChecks = 0
    
    // Usage pattern analysis
    private val activityHistory = mutableListOf<Long>()
    private val updateHistory = mutableListOf<Long>()
    private var averageActivityInterval = 0L
    private var lastSignificantUpdate = 0L
    
    companion object {
        private const val TAG = "AdaptiveUpdateManager"
        private const val IDLE_THRESHOLD_MS = 300_000L // 5 minutes
        private const val MAX_IDLE_CHECKS = 3
        private const val SIGNIFICANT_UPDATE_THRESHOLD = 30_000L // 30 seconds
    }
    
    /**
     * Initialize adaptive update manager
     */
    fun initialize() {
        Log.d(TAG, "Initializing adaptive update manager")
        startScreenStateMonitoring()
        startIdleDetection()
    }
    
    /**
     * Get current optimal update interval
     */
    fun getOptimalUpdateInterval(): Long {
        return when {
            !isScreenOn -> screenOffInterval
            isLowBattery -> lowBatteryInterval
            isIdle -> idleInterval
            else -> calculateDynamicInterval()
        }
    }
    
    /**
     * Check if update should be skipped based on current conditions
     */
    fun shouldSkipUpdate(): Boolean {
        val currentTime = System.currentTimeMillis()
        
        return when {
            !isScreenOn && isIdle -> true // Skip when screen off and idle
            isLowBattery && !isScreenOn -> kotlin.random.Random.nextFloat() < 0.5f // 50% skip chance
            isIdle && (currentTime - lastSignificantUpdate) < SIGNIFICANT_UPDATE_THRESHOLD -> true
            else -> false
        }
    }
    
    /**
     * Record activity for pattern analysis
     */
    fun recordActivity() {
        val currentTime = System.currentTimeMillis()
        lastActivityTime = currentTime
        activityHistory.add(currentTime)
        
        // Keep only recent activity history (last 24 hours)
        val cutoffTime = currentTime - TimeUnit.HOURS.toMillis(24)
        activityHistory.removeAll { it < cutoffTime }
        
        // Reset idle state if activity detected
        if (isIdle) {
            isIdle = false
            consecutiveIdleChecks = 0
            Log.d(TAG, "Activity detected - exiting idle mode")
        }
    }
    
    /**
     * Record update for pattern analysis
     */
    fun recordUpdate(hasSignificantChange: Boolean) {
        val currentTime = System.currentTimeMillis()
        updateHistory.add(currentTime)
        
        if (hasSignificantChange) {
            lastSignificantUpdate = currentTime
        }
        
        // Keep only recent update history
        val cutoffTime = currentTime - TimeUnit.HOURS.toMillis(24)
        updateHistory.removeAll { it < cutoffTime }
    }
    
    /**
     * Update battery status
     */
    fun updateBatteryStatus(batteryLevel: Int, isCharging: Boolean) {
        isLowBattery = batteryLevel < 20 && !isCharging
    }
    
    /**
     * Start monitoring screen state
     */
    private fun startScreenStateMonitoring() {
        updateExecutor.scheduleAtFixedRate({
            val isScreenCurrentlyOn = powerManager.isInteractive
            if (isScreenCurrentlyOn != isScreenOn) {
                isScreenOn = isScreenCurrentlyOn
                Log.d(TAG, "Screen state changed: ${if (isScreenOn) "ON" else "OFF"}")
                
                if (isScreenOn) {
                    recordActivity()
                }
            }
        }, 0, 5, TimeUnit.SECONDS)
    }
    
    /**
     * Start idle detection
     */
    private fun startIdleDetection() {
        updateExecutor.scheduleAtFixedRate({
            checkIdleState()
        }, 0, 30, TimeUnit.SECONDS)
    }
    
    /**
     * Check if device is idle
     */
    private fun checkIdleState() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastActivity = currentTime - lastActivityTime
        
        if (timeSinceLastActivity > IDLE_THRESHOLD_MS) {
            consecutiveIdleChecks++
            if (consecutiveIdleChecks >= MAX_IDLE_CHECKS && !isIdle) {
                isIdle = true
                Log.d(TAG, "Device entered idle state")
            }
        } else {
            consecutiveIdleChecks = 0
            if (isIdle) {
                isIdle = false
                Log.d(TAG, "Device exited idle state")
            }
        }
    }
    
    /**
     * Calculate dynamic update interval based on usage patterns
     */
    private fun calculateDynamicInterval(): Long {
        val currentTime = System.currentTimeMillis()
        
        // Analyze recent activity patterns
        val recentActivity = activityHistory.filter { 
            currentTime - it < TimeUnit.HOURS.toMillis(2) 
        }
        
        val recentUpdates = updateHistory.filter { 
            currentTime - it < TimeUnit.HOURS.toMillis(2) 
        }
        
        // Calculate average activity interval
        if (recentActivity.size > 1) {
            val intervals = recentActivity.zipWithNext { a, b -> b - a }
            averageActivityInterval = intervals.average().toLong()
        }
        
        // Adjust interval based on patterns
        return when {
            recentActivity.isEmpty() -> baseInterval * 2 // No recent activity, slow down
            averageActivityInterval > TimeUnit.MINUTES.toMillis(10) -> (baseInterval * 1.5).toLong() // Low activity
            recentUpdates.size > 10 -> baseInterval / 2 // High update frequency, speed up
            else -> baseInterval
        }
    }
    
    /**
     * Get current state information
     */
    fun getCurrentState(): AdaptiveState {
        return AdaptiveState(
            isScreenOn = isScreenOn,
            isLowBattery = isLowBattery,
            isIdle = isIdle,
            currentInterval = getOptimalUpdateInterval(),
            averageActivityInterval = averageActivityInterval,
            activityCount = activityHistory.size,
            updateCount = updateHistory.size
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        updateExecutor.shutdown()
        activityHistory.clear()
        updateHistory.clear()
        Log.d(TAG, "Adaptive update manager cleaned up")
    }
}

/**
 * Data class for adaptive state information
 */
data class AdaptiveState(
    val isScreenOn: Boolean,
    val isLowBattery: Boolean,
    val isIdle: Boolean,
    val currentInterval: Long,
    val averageActivityInterval: Long,
    val activityCount: Int,
    val updateCount: Int
)
