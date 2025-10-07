package com.example.screentimeoverlay

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Performance optimization manager for battery efficiency and resource management
 */
class PerformanceOptimizer(private val context: Context) {
    
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    // Performance monitoring
    private var lastUpdateTime = 0L
    private var updateCount = 0
    private var totalCpuTime = 0L
    private var memoryUsage = 0L
    private var batteryLevel = 0
    private var isCharging = false
    
    // Adaptive update intervals
    private var baseUpdateInterval = 60_000L // 1 minute base
    private var currentUpdateInterval = baseUpdateInterval
    private var screenOffInterval = 300_000L // 5 minutes when screen off
    private var lowBatteryInterval = 180_000L // 3 minutes when low battery
    
    // Background processing optimization
    private val backgroundExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private var isScreenOn = true
    private var isLowBattery = false
    private var isHighMemoryUsage = false
    
    // Caching system
    private val appNameCache = mutableMapOf<String, String>()
    private val usageStatsCache = mutableMapOf<String, Long>()
    private var lastCacheCleanup = 0L
    private val cacheCleanupInterval = 300_000L // 5 minutes
    
    companion object {
        private const val TAG = "PerformanceOptimizer"
        private const val LOW_BATTERY_THRESHOLD = 20
        private const val HIGH_MEMORY_THRESHOLD = 0.8 // 80% of available memory
        private const val MAX_CACHE_SIZE = 100
        private const val MEMORY_CHECK_INTERVAL = 60_000L // 1 minute
    }
    
    /**
     * Initialize performance monitoring
     */
    fun initialize() {
        Log.d(TAG, "Initializing performance optimizer")
        startPerformanceMonitoring()
        startMemoryMonitoring()
        updateBatteryStatus()
    }
    
    /**
     * Get optimal update interval based on current conditions
     */
    fun getOptimalUpdateInterval(): Long {
        updateBatteryStatus()
        checkMemoryUsage()
        
        return when {
            !isScreenOn -> screenOffInterval
            isLowBattery -> lowBatteryInterval
            isHighMemoryUsage -> currentUpdateInterval * 2
            else -> currentUpdateInterval
        }
    }
    
    /**
     * Check if we should skip this update cycle for battery optimization
     */
    fun shouldSkipUpdate(): Boolean {
        updateBatteryStatus()
        
        return when {
            !isScreenOn && batteryLevel < 10 -> true // Skip when screen off and very low battery
            isLowBattery && !isCharging -> kotlin.random.Random.nextFloat() < 0.3f // 30% chance to skip when low battery
            isHighMemoryUsage -> kotlin.random.Random.nextFloat() < 0.2f // 20% chance to skip when high memory usage
            else -> false
        }
    }
    
    /**
     * Optimize background processing based on current conditions
     */
    fun optimizeBackgroundProcessing() {
        val optimalInterval = getOptimalUpdateInterval()
        
        // Adjust background processing frequency
        if (optimalInterval > baseUpdateInterval) {
            // Reduce background processing when conditions are poor
            backgroundExecutor.scheduleAtFixedRate(
                { performLightweightUpdate() },
                0,
                optimalInterval,
                TimeUnit.MILLISECONDS
            )
        }
    }
    
    /**
     * Get cached app name or fetch and cache it
     */
    fun getCachedAppName(packageName: String): String {
        return appNameCache.getOrPut(packageName) {
            try {
                val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName.substringAfterLast(".")
            }
        }
    }
    
    /**
     * Cache usage stats for optimization
     */
    fun cacheUsageStats(packageName: String, usageTime: Long) {
        usageStatsCache[packageName] = usageTime
        
        // Clean cache if it gets too large
        if (usageStatsCache.size > MAX_CACHE_SIZE) {
            cleanupCache()
        }
    }
    
    /**
     * Get cached usage stats
     */
    fun getCachedUsageStats(packageName: String): Long? {
        return usageStatsCache[packageName]
    }
    
    /**
     * Start performance monitoring
     */
    private fun startPerformanceMonitoring() {
        backgroundExecutor.scheduleAtFixedRate({
            monitorPerformance()
        }, 0, 30, TimeUnit.SECONDS)
    }
    
    /**
     * Start memory monitoring
     */
    private fun startMemoryMonitoring() {
        backgroundExecutor.scheduleAtFixedRate({
            checkMemoryUsage()
        }, 0, MEMORY_CHECK_INTERVAL, TimeUnit.MILLISECONDS)
    }
    
    /**
     * Monitor overall performance metrics
     */
    private fun monitorPerformance() {
        val currentTime = SystemClock.uptimeMillis()
        updateCount++
        
        if (lastUpdateTime > 0) {
            val timeDiff = currentTime - lastUpdateTime
            totalCpuTime += timeDiff
        }
        
        lastUpdateTime = currentTime
        
        // Log performance metrics
        if (updateCount % 10 == 0) { // Log every 10 updates
            Log.d(TAG, "Performance metrics - Updates: $updateCount, Memory: ${memoryUsage}MB, Battery: $batteryLevel%")
        }
    }
    
    /**
     * Check memory usage and adjust behavior accordingly
     */
    private fun checkMemoryUsage() {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val usedMemory = memoryInfo.totalMem - memoryInfo.availMem
        val memoryUsagePercent = usedMemory.toFloat() / memoryInfo.totalMem.toFloat()
        
        memoryUsage = usedMemory / (1024 * 1024) // Convert to MB
        isHighMemoryUsage = memoryUsagePercent > HIGH_MEMORY_THRESHOLD
        
        if (isHighMemoryUsage) {
            Log.w(TAG, "High memory usage detected: ${(memoryUsagePercent * 100).toInt()}%")
            // Trigger garbage collection
            System.gc()
        }
    }
    
    /**
     * Update battery status and charging state
     */
    private fun updateBatteryStatus() {
        batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        isCharging = batteryManager.isCharging
        isLowBattery = batteryLevel < LOW_BATTERY_THRESHOLD && !isCharging
    }
    
    /**
     * Check if screen is on
     */
    fun updateScreenState(isScreenOn: Boolean) {
        this.isScreenOn = isScreenOn
        
        if (!isScreenOn) {
            Log.d(TAG, "Screen turned off - switching to power saving mode")
            // Reduce update frequency when screen is off
            currentUpdateInterval = screenOffInterval
        } else {
            Log.d(TAG, "Screen turned on - resuming normal update frequency")
            currentUpdateInterval = baseUpdateInterval
        }
    }
    
    /**
     * Perform lightweight update for background processing
     */
    private fun performLightweightUpdate() {
        // Only perform essential updates when in power saving mode
        Log.d(TAG, "Performing lightweight background update")
    }
    
    /**
     * Clean up cache to free memory
     */
    private fun cleanupCache() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastCacheCleanup > cacheCleanupInterval) {
            // Remove oldest entries from cache
            val entriesToRemove = appNameCache.size - (MAX_CACHE_SIZE / 2)
            if (entriesToRemove > 0) {
                val keysToRemove = appNameCache.keys.take(entriesToRemove)
                keysToRemove.forEach { appNameCache.remove(it) }
            }
            
            // Clear usage stats cache
            usageStatsCache.clear()
            
            lastCacheCleanup = currentTime
            Log.d(TAG, "Cache cleaned up")
        }
    }
    
    /**
     * Get current performance metrics
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            updateCount = updateCount,
            memoryUsageMB = memoryUsage,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isLowBattery = isLowBattery,
            isHighMemoryUsage = isHighMemoryUsage,
            currentUpdateInterval = currentUpdateInterval,
            cacheSize = appNameCache.size + usageStatsCache.size
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        backgroundExecutor.shutdown()
        appNameCache.clear()
        usageStatsCache.clear()
        Log.d(TAG, "Performance optimizer cleaned up")
    }
}

/**
 * Data class for performance metrics
 */
data class PerformanceMetrics(
    val updateCount: Int,
    val memoryUsageMB: Long,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isLowBattery: Boolean,
    val isHighMemoryUsage: Boolean,
    val currentUpdateInterval: Long,
    val cacheSize: Int
)
