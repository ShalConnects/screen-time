package com.example.screentimeoverlay

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Advanced memory management system with caching and resource cleanup
 */
class MemoryManager(private val context: Context) {
    
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    
    // Memory monitoring
    private var totalMemoryMB = 0L
    private var availableMemoryMB = 0L
    private var usedMemoryMB = 0L
    private var memoryUsagePercent = 0.0
    
    // Caching system
    private val appNameCache = ConcurrentHashMap<String, String>()
    private val usageStatsCache = ConcurrentHashMap<String, Long>()
    private val sessionCache = ConcurrentHashMap<String, Any>()
    
    // Memory optimization
    private val memoryThreshold = 0.8 // 80% memory usage threshold
    private val cacheSizeLimit = 50
    private var lastGarbageCollection = 0L
    private val gcInterval = 300_000L // 5 minutes
    
    // Background cleanup
    private val cleanupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    
    companion object {
        private const val TAG = "MemoryManager"
        private const val MAX_CACHE_AGE_MS = 600_000L // 10 minutes
        private const val CLEANUP_INTERVAL_MS = 120_000L // 2 minutes
    }
    
    /**
     * Initialize memory management
     */
    fun initialize() {
        Log.d(TAG, "Initializing memory manager")
        updateMemoryInfo()
        startMemoryMonitoring()
        startCacheCleanup()
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
     * Cache usage statistics
     */
    fun cacheUsageStats(packageName: String, usageTime: Long) {
        usageStatsCache[packageName] = usageTime
        checkCacheSize()
    }
    
    /**
     * Get cached usage statistics
     */
    fun getCachedUsageStats(packageName: String): Long? {
        return usageStatsCache[packageName]
    }
    
    /**
     * Cache session data
     */
    fun cacheSessionData(sessionId: String, data: Any) {
        sessionCache[sessionId] = data
        checkCacheSize()
    }
    
    /**
     * Get cached session data
     */
    fun getCachedSessionData(sessionId: String): Any? {
        return sessionCache[sessionId]
    }
    
    /**
     * Check if memory usage is high
     */
    fun isHighMemoryUsage(): Boolean {
        updateMemoryInfo()
        return memoryUsagePercent > memoryThreshold
    }
    
    /**
     * Get current memory information
     */
    fun getMemoryInfo(): MemoryInfo {
        updateMemoryInfo()
        return MemoryInfo(
            totalMemoryMB = totalMemoryMB,
            availableMemoryMB = availableMemoryMB,
            usedMemoryMB = usedMemoryMB,
            memoryUsagePercent = memoryUsagePercent,
            cacheSize = appNameCache.size + usageStatsCache.size + sessionCache.size
        )
    }
    
    /**
     * Force memory cleanup
     */
    fun forceCleanup() {
        Log.d(TAG, "Forcing memory cleanup")
        
        // Clear old cache entries
        cleanupExpiredCache()
        
        // Reduce cache size if needed
        if (isHighMemoryUsage()) {
            reduceCacheSize()
        }
        
        // Trigger garbage collection
        triggerGarbageCollection()
    }
    
    /**
     * Update memory information
     */
    private fun updateMemoryInfo() {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
        availableMemoryMB = memoryInfo.availMem / (1024 * 1024)
        usedMemoryMB = totalMemoryMB - availableMemoryMB
        memoryUsagePercent = usedMemoryMB.toDouble() / totalMemoryMB.toDouble()
    }
    
    /**
     * Start memory monitoring
     */
    private fun startMemoryMonitoring() {
        cleanupExecutor.scheduleAtFixedRate({
            updateMemoryInfo()
            
            if (isHighMemoryUsage()) {
                Log.w(TAG, "High memory usage detected: ${(memoryUsagePercent * 100).toInt()}%")
                forceCleanup()
            }
        }, 0, 30, TimeUnit.SECONDS)
    }
    
    /**
     * Start cache cleanup
     */
    private fun startCacheCleanup() {
        cleanupExecutor.scheduleAtFixedRate({
            cleanupExpiredCache()
            checkCacheSize()
        }, 0, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }
    
    /**
     * Check cache size and clean if necessary
     */
    private fun checkCacheSize() {
        val totalCacheSize = appNameCache.size + usageStatsCache.size + sessionCache.size
        
        if (totalCacheSize > cacheSizeLimit) {
            Log.d(TAG, "Cache size limit exceeded: $totalCacheSize")
            reduceCacheSize()
        }
    }
    
    /**
     * Clean up expired cache entries
     */
    private fun cleanupExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val cutoffTime = currentTime - MAX_CACHE_AGE_MS
        
        // Clean up usage stats cache (older entries)
        val expiredKeys = usageStatsCache.keys.filter { 
            // Simple cleanup based on key patterns
            it.contains("old_") 
        }
        expiredKeys.forEach { usageStatsCache.remove(it) }
        
        // Clean up session cache
        val expiredSessions = sessionCache.keys.filter { 
            it.startsWith("session_") && it.contains("_old")
        }
        expiredSessions.forEach { sessionCache.remove(it) }
        
        Log.d(TAG, "Cleaned up ${expiredKeys.size + expiredSessions.size} expired cache entries")
    }
    
    /**
     * Reduce cache size by removing least recently used entries
     */
    private fun reduceCacheSize() {
        val targetSize = cacheSizeLimit / 2
        
        // Reduce app name cache
        if (appNameCache.size > targetSize) {
            val keysToRemove = appNameCache.keys.take(appNameCache.size - targetSize)
            keysToRemove.forEach { appNameCache.remove(it) }
        }
        
        // Reduce usage stats cache
        if (usageStatsCache.size > targetSize) {
            val keysToRemove = usageStatsCache.keys.take(usageStatsCache.size - targetSize)
            keysToRemove.forEach { usageStatsCache.remove(it) }
        }
        
        // Reduce session cache
        if (sessionCache.size > targetSize) {
            val keysToRemove = sessionCache.keys.take(sessionCache.size - targetSize)
            keysToRemove.forEach { sessionCache.remove(it) }
        }
        
        Log.d(TAG, "Reduced cache size to target")
    }
    
    /**
     * Trigger garbage collection if needed
     */
    private fun triggerGarbageCollection() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastGarbageCollection > gcInterval) {
            System.gc()
            lastGarbageCollection = currentTime
            Log.d(TAG, "Garbage collection triggered")
        }
    }
    
    /**
     * Get memory usage statistics
     */
    fun getMemoryStats(): MemoryStats {
        updateMemoryInfo()
        
        return MemoryStats(
            totalMemoryMB = totalMemoryMB,
            availableMemoryMB = availableMemoryMB,
            usedMemoryMB = usedMemoryMB,
            memoryUsagePercent = memoryUsagePercent,
            appNameCacheSize = appNameCache.size,
            usageStatsCacheSize = usageStatsCache.size,
            sessionCacheSize = sessionCache.size,
            isHighMemoryUsage = isHighMemoryUsage()
        )
    }
    
    /**
     * Clear all caches
     */
    fun clearAllCaches() {
        appNameCache.clear()
        usageStatsCache.clear()
        sessionCache.clear()
        Log.d(TAG, "All caches cleared")
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cleanupExecutor.shutdown()
        clearAllCaches()
        Log.d(TAG, "Memory manager cleaned up")
    }
}

/**
 * Data class for memory information
 */
data class MemoryInfo(
    val totalMemoryMB: Long,
    val availableMemoryMB: Long,
    val usedMemoryMB: Long,
    val memoryUsagePercent: Double,
    val cacheSize: Int
)

/**
 * Data class for memory statistics
 */
data class MemoryStats(
    val totalMemoryMB: Long,
    val availableMemoryMB: Long,
    val usedMemoryMB: Long,
    val memoryUsagePercent: Double,
    val appNameCacheSize: Int,
    val usageStatsCacheSize: Int,
    val sessionCacheSize: Int,
    val isHighMemoryUsage: Boolean
)
