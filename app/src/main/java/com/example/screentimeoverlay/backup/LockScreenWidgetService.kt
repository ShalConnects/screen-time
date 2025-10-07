package com.example.screentimeoverlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for managing lock screen widgets
 * Provides quick access to screen time stats on the lock screen
 */
class LockScreenWidgetService : Service() {
    
    companion object {
        private const val TAG = "LockScreenWidgetService"
        private const val WIDGET_UPDATE_INTERVAL = 60000L // 1 minute
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Lock Screen Widget Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "update_widget" -> updateLockScreenWidget()
            "create_widget" -> createLockScreenWidget()
            "remove_widget" -> removeLockScreenWidget()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun updateLockScreenWidget() {
        Log.d(TAG, "Updating lock screen widget")
        
        // This would integrate with your widget system
        // For now, just log the update
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        Log.d(TAG, "Widget updated at $currentTime")
    }
    
    private fun createLockScreenWidget() {
        Log.d(TAG, "Creating lock screen widget")
        
        // This would create the actual widget
        // Implementation depends on your widget framework
    }
    
    private fun removeLockScreenWidget() {
        Log.d(TAG, "Removing lock screen widget")
        
        // This would remove the widget
        // Implementation depends on your widget framework
    }
    
    /**
     * Get widget data for display
     */
    fun getWidgetData(): LockScreenWidgetData {
        val currentTime = System.currentTimeMillis()
        val screenTimeData = getCurrentScreenTimeData()
        
        return LockScreenWidgetData(
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            todayTime = formatTime(screenTimeData.todayTime),
            dailyGoal = formatTime(screenTimeData.dailyGoal),
            progressPercentage = screenTimeData.progressPercentage,
            isOverLimit = screenTimeData.isOverLimit,
            topApp = screenTimeData.topApp,
            sessionCount = screenTimeData.sessionCount
        )
    }
    
    private fun getCurrentScreenTimeData(): ScreenTimeWidgetData {
        // This would integrate with your actual screen time tracking
        // For now, return mock data
        return ScreenTimeWidgetData(
            todayTime = 2 * 60 * 60 * 1000, // 2 hours
            dailyGoal = 8 * 60 * 60 * 1000, // 8 hours
            progressPercentage = 25,
            isOverLimit = false,
            topApp = "Chrome",
            sessionCount = 15
        )
    }
    
    private fun formatTime(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)
    }
    
    /**
     * Check if lock screen widgets are supported
     */
    fun isLockScreenWidgetSupported(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
    }
    
    /**
     * Get widget status
     */
    fun getWidgetStatus(): LockScreenWidgetStatus {
        return LockScreenWidgetStatus(
            isSupported = isLockScreenWidgetSupported(),
            isEnabled = isWidgetEnabled(),
            hasPermission = hasWidgetPermission()
        )
    }
    
    private fun isWidgetEnabled(): Boolean {
        val sharedPrefs = getSharedPreferences("screen_time_prefs", Context.MODE_PRIVATE)
        return sharedPrefs.getBoolean("lock_screen_widget_enabled", false)
    }
    
    private fun hasWidgetPermission(): Boolean {
        // Check if we have permission to create widgets
        return android.provider.Settings.canDrawOverlays(this)
    }
}

/**
 * Data class for lock screen widget data
 */
data class LockScreenWidgetData(
    val currentTime: String,
    val todayTime: String,
    val dailyGoal: String,
    val progressPercentage: Int,
    val isOverLimit: Boolean,
    val topApp: String,
    val sessionCount: Int
)

/**
 * Data class for screen time widget data
 */
data class ScreenTimeWidgetData(
    val todayTime: Long,
    val dailyGoal: Long,
    val progressPercentage: Int,
    val isOverLimit: Boolean,
    val topApp: String,
    val sessionCount: Int
)

/**
 * Data class for lock screen widget status
 */
data class LockScreenWidgetStatus(
    val isSupported: Boolean,
    val isEnabled: Boolean,
    val hasPermission: Boolean
)
