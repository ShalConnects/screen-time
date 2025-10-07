package com.example.screentimeoverlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
// Samsung Edge Lighting imports - using reflection to avoid compile-time dependency
// import com.samsung.android.edge.EdgeLightingManager
// import com.samsung.android.edge.EdgeLightingInfo
// import com.samsung.android.edge.EdgeLightingColor
import java.util.Date

/**
 * Service for managing Samsung Edge Lighting notifications
 * Provides visual feedback for screen time alerts and goals
 */
class EdgeLightingService : Service() {
    
    // Using Any? to avoid compile-time dependency on Samsung classes
    private var edgeLightingManager: Any? = null
    private var isServiceRunning = false
    private var isSamsungEdgeLightingAvailable = false
    
    companion object {
        private const val TAG = "EdgeLightingService"
        private const val EDGE_LIGHTING_DURATION = 3000L // 3 seconds
    }
    
    override fun onCreate() {
        super.onCreate()
        initializeEdgeLighting()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "show_goal_achievement" -> showGoalAchievementLighting()
            "show_break_reminder" -> showBreakReminderLighting()
            "show_time_limit_warning" -> showTimeLimitWarningLighting()
            "show_daily_summary" -> showDailySummaryLighting()
            "test_edge_lighting" -> testEdgeLighting()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun initializeEdgeLighting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isSamsungDevice()) {
            try {
                // Use reflection to access Samsung Edge Lighting classes
                val edgeLightingManagerClass = Class.forName("com.samsung.android.edge.EdgeLightingManager")
                val getInstanceMethod = edgeLightingManagerClass.getMethod("getInstance", Context::class.java)
                edgeLightingManager = getInstanceMethod.invoke(null, this)
                isSamsungEdgeLightingAvailable = true
                isServiceRunning = true
                Log.d(TAG, "Edge Lighting initialized successfully using reflection")
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "Samsung Edge Lighting classes not available - using fallback notifications")
                isSamsungEdgeLightingAvailable = false
                isServiceRunning = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Edge Lighting", e)
                isSamsungEdgeLightingAvailable = false
                isServiceRunning = false
            }
        } else {
            Log.w(TAG, "Edge Lighting not supported on this device")
            isSamsungEdgeLightingAvailable = false
            isServiceRunning = false
        }
    }
    
    private fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
    
    /**
     * Show edge lighting for goal achievement
     */
    fun showGoalAchievementLighting() {
        if (!isServiceRunning) {
            showFallbackNotification("Goal Achieved! 🎉", Color.GREEN)
            return
        }
        
        if (isSamsungEdgeLightingAvailable) {
            try {
                val edgeLightingInfo = createEdgeLightingInfo(Color.GREEN, EDGE_LIGHTING_DURATION, 3, "EFFECT_BREATHING")
                showEdgeLighting("Goal Achieved! 🎉", edgeLightingInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show Samsung edge lighting, using fallback", e)
                showFallbackNotification("Goal Achieved! 🎉", Color.GREEN)
            }
        } else {
            showFallbackNotification("Goal Achieved! 🎉", Color.GREEN)
        }
    }
    
    /**
     * Show edge lighting for break reminders
     */
    fun showBreakReminderLighting() {
        if (!isServiceRunning) {
            showFallbackNotification("Time for a break! ☕", Color.BLUE)
            return
        }
        
        if (isSamsungEdgeLightingAvailable) {
            try {
                val edgeLightingInfo = createEdgeLightingInfo(Color.BLUE, EDGE_LIGHTING_DURATION, 2, "EFFECT_FLASHING")
                showEdgeLighting("Time for a break! ☕", edgeLightingInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show Samsung edge lighting, using fallback", e)
                showFallbackNotification("Time for a break! ☕", Color.BLUE)
            }
        } else {
            showFallbackNotification("Time for a break! ☕", Color.BLUE)
        }
    }
    
    /**
     * Show edge lighting for time limit warnings
     */
    fun showTimeLimitWarningLighting() {
        if (!isServiceRunning) {
            showFallbackNotification("Time limit reached! ⏰", Color.RED)
            return
        }
        
        if (isSamsungEdgeLightingAvailable) {
            try {
                val edgeLightingInfo = createEdgeLightingInfo(Color.RED, EDGE_LIGHTING_DURATION, 4, "EFFECT_PULSING")
                showEdgeLighting("Time limit reached! ⏰", edgeLightingInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show Samsung edge lighting, using fallback", e)
                showFallbackNotification("Time limit reached! ⏰", Color.RED)
            }
        } else {
            showFallbackNotification("Time limit reached! ⏰", Color.RED)
        }
    }
    
    /**
     * Show edge lighting for daily summary
     */
    fun showDailySummaryLighting() {
        if (!isServiceRunning) {
            showFallbackNotification("Daily summary ready 📊", Color.CYAN)
            return
        }
        
        if (isSamsungEdgeLightingAvailable) {
            try {
                val edgeLightingInfo = createEdgeLightingInfo(Color.CYAN, EDGE_LIGHTING_DURATION, 1, "EFFECT_RAINBOW")
                showEdgeLighting("Daily summary ready 📊", edgeLightingInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show Samsung edge lighting, using fallback", e)
                showFallbackNotification("Daily summary ready 📊", Color.CYAN)
            }
        } else {
            showFallbackNotification("Daily summary ready 📊", Color.CYAN)
        }
    }
    
    /**
     * Test edge lighting functionality
     */
    fun testEdgeLighting() {
        if (!isServiceRunning) {
            Log.w(TAG, "Edge Lighting not available")
            showFallbackNotification("Edge Lighting Test ✨", Color.MAGENTA)
            return
        }
        
        if (isSamsungEdgeLightingAvailable) {
            try {
                val edgeLightingInfo = createEdgeLightingInfo(Color.MAGENTA, 2000L, 1, "EFFECT_BREATHING")
                showEdgeLighting("Edge Lighting Test ✨", edgeLightingInfo)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show Samsung edge lighting test, using fallback", e)
                showFallbackNotification("Edge Lighting Test ✨", Color.MAGENTA)
            }
        } else {
            showFallbackNotification("Edge Lighting Test ✨", Color.MAGENTA)
        }
    }
    
    private fun showEdgeLighting(message: String, edgeLightingInfo: Any) {
        try {
            val showMethod = edgeLightingManager?.javaClass?.getMethod(
                "showEdgeLighting", 
                String::class.java, 
                String::class.java, 
                edgeLightingInfo.javaClass
            )
            showMethod?.invoke(edgeLightingManager, packageName, message, edgeLightingInfo)
            Log.d(TAG, "Edge lighting shown: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show edge lighting", e)
        }
    }
    
    /**
     * Create EdgeLightingInfo using reflection
     */
    private fun createEdgeLightingInfo(color: Int, duration: Long, repeatCount: Int, effect: String): Any {
        return try {
            val edgeLightingColorClass = Class.forName("com.samsung.android.edge.EdgeLightingColor")
            val edgeLightingInfoClass = Class.forName("com.samsung.android.edge.EdgeLightingInfo")
            val builderClass = Class.forName("com.samsung.android.edge.EdgeLightingInfo\$Builder")
            
            val colorConstructor = edgeLightingColorClass.getConstructor(Int::class.java)
            val colorInstance = colorConstructor.newInstance(color)
            
            val builderConstructor = builderClass.getConstructor()
            val builder = builderConstructor.newInstance()
            
            val setColorMethod = builderClass.getMethod("setColor", edgeLightingColorClass)
            val setDurationMethod = builderClass.getMethod("setDuration", Long::class.java)
            val setRepeatCountMethod = builderClass.getMethod("setRepeatCount", Int::class.java)
            val setEffectMethod = builderClass.getMethod("setEffect", String::class.java)
            val buildMethod = builderClass.getMethod("build")
            
            setColorMethod.invoke(builder, colorInstance)
            setDurationMethod.invoke(builder, duration)
            setRepeatCountMethod.invoke(builder, repeatCount)
            setEffectMethod.invoke(builder, effect)
            buildMethod.invoke(builder)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EdgeLightingInfo using reflection", e)
            throw e
        }
    }
    
    /**
     * Show fallback notification when Samsung Edge Lighting is not available
     */
    private fun showFallbackNotification(message: String, color: Int) {
        try {
            // Create a simple notification as fallback
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val channelId = "edge_lighting_fallback"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Edge Lighting Fallback",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = android.app.Notification.Builder(this)
                .setContentTitle("Screen Time Alert")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setColor(color)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            Log.d(TAG, "Fallback notification shown: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show fallback notification", e)
        }
    }
    
    /**
     * Check if edge lighting is supported and available
     */
    fun isEdgeLightingSupported(): Boolean {
        return isServiceRunning && isSamsungEdgeLightingAvailable && edgeLightingManager != null
    }
    
    /**
     * Get edge lighting status
     */
    fun getEdgeLightingStatus(): EdgeLightingStatus {
        return EdgeLightingStatus(
            isSupported = isEdgeLightingSupported(),
            isEnabled = isServiceRunning,
            deviceManufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.SDK_INT
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        edgeLightingManager = null
    }
}

/**
 * Data class for edge lighting status
 */
data class EdgeLightingStatus(
    val isSupported: Boolean,
    val isEnabled: Boolean,
    val deviceManufacturer: String,
    val androidVersion: Int
)
