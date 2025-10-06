package com.example.screentimeoverlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

class ScreenTimeAccessibilityService : AccessibilityService() {
    
    private var lastAppPackage: String? = null
    private var lastAppStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val appUsageTracker = AppUsageTracker()
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
        
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
            packageNames = null // Monitor all packages
        }
        
        serviceInfo = info
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let { accessibilityEvent ->
            when (accessibilityEvent.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    handleWindowStateChanged(accessibilityEvent)
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Handle content changes if needed
                }
            }
        }
    }
    
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        val className = event.className?.toString()
        
        if (packageName != null && className != null) {
            // Filter out system UI and launcher apps
            if (isSystemApp(packageName) || isLauncherApp(packageName)) {
                return
            }
            
            // Track app usage
            trackAppUsage(packageName, className)
        }
    }
    
    private fun trackAppUsage(packageName: String, className: String) {
        val currentTime = System.currentTimeMillis()
        
        // If switching to a different app, record the previous app's usage
        if (lastAppPackage != null && lastAppPackage != packageName) {
            val usageTime = currentTime - lastAppStartTime
            if (usageTime > MIN_USAGE_TIME_MS) {
                appUsageTracker.recordAppUsage(lastAppPackage!!, usageTime)
                Log.d(TAG, "Recorded usage for $lastAppPackage: ${usageTime}ms")
            }
        }
        
        // Update current app
        lastAppPackage = packageName
        lastAppStartTime = currentTime
        
        // Notify the overlay service about the current app
        notifyOverlayService(packageName)
    }
    
    private fun notifyOverlayService(packageName: String) {
        val intent = Intent(ACTION_APP_CHANGED).apply {
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        }
        sendBroadcast(intent)
    }
    
    private fun isSystemApp(packageName: String): Boolean {
        val systemApps = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher",
            "com.google.android.launcher"
        )
        return systemApps.any { packageName.startsWith(it) }
    }
    
    private fun isLauncherApp(packageName: String): Boolean {
        val launcherApps = setOf(
            "com.android.launcher",
            "com.google.android.launcher",
            "com.samsung.android.launcher",
            "com.miui.home",
            "com.huawei.android.launcher"
        )
        return launcherApps.any { packageName.startsWith(it) }
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Record final app usage if any
        if (lastAppPackage != null) {
            val usageTime = System.currentTimeMillis() - lastAppStartTime
            if (usageTime > MIN_USAGE_TIME_MS) {
                appUsageTracker.recordAppUsage(lastAppPackage!!, usageTime)
            }
        }
        Log.d(TAG, "Accessibility service destroyed")
    }
    
    companion object {
        private const val TAG = "ScreenTimeAccessibility"
        private const val MIN_USAGE_TIME_MS = 1000L // Minimum 1 second to record usage
        const val ACTION_APP_CHANGED = "com.example.screentimeoverlay.APP_CHANGED"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TIMESTAMP = "timestamp"
    }
}

/**
 * Simple in-memory app usage tracker for accessibility service
 */
class AppUsageTracker {
    private val appUsageMap = mutableMapOf<String, Long>()
    
    fun recordAppUsage(packageName: String, usageTime: Long) {
        appUsageMap[packageName] = (appUsageMap[packageName] ?: 0) + usageTime
    }
    
    fun getAppUsage(packageName: String): Long {
        return appUsageMap[packageName] ?: 0
    }
    
    fun getAllAppUsage(): Map<String, Long> {
        return appUsageMap.toMap()
    }
    
    fun clearUsage() {
        appUsageMap.clear()
    }
    
    fun getTotalUsage(): Long {
        return appUsageMap.values.sum()
    }
}
