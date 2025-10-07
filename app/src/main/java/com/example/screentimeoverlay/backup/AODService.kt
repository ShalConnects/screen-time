package com.example.screentimeoverlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.RequiresApi
import java.text.SimpleDateFormat
import java.util.*

/**
 * Service for managing Always-On Display (AOD) functionality
 * Shows screen time stats on the lock screen when the device is in AOD mode
 */
class AODService : Service() {
    
    private var windowManager: WindowManager? = null
    private var aodView: TextView? = null
    private var isAODActive = false
    private var isScreenOn = false
    
    companion object {
        private const val TAG = "AODService"
        private const val AOD_UPDATE_INTERVAL = 30000L // 30 seconds
    }
    
    override fun onCreate() {
        super.onCreate()
        initializeAOD()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "start_aod" -> startAOD()
            "stop_aod" -> stopAOD()
            "update_aod" -> updateAODContent()
            "screen_on" -> handleScreenOn()
            "screen_off" -> handleScreenOff()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun initializeAOD() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "AOD Service initialized")
    }
    
    private fun startAOD() {
        if (isAODActive) return
        
        try {
            createAODView()
            isAODActive = true
            Log.d(TAG, "AOD started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AOD", e)
        }
    }
    
    private fun stopAOD() {
        if (!isAODActive) return
        
        try {
            removeAODView()
            isAODActive = false
            Log.d(TAG, "AOD stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop AOD", e)
        }
    }
    
    private fun createAODView() {
        if (aodView != null) return
        
        aodView = TextView(this).apply {
            text = getAODContent()
            textSize = 14f
            setTextColor(Color.WHITE)
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
        }
        
        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 100
        }
        
        windowManager?.addView(aodView, layoutParams)
    }
    
    private fun removeAODView() {
        aodView?.let { view ->
            windowManager?.removeView(view)
            aodView = null
        }
    }
    
    private fun updateAODContent() {
        if (!isAODActive || aodView == null) return
        
        try {
            aodView?.text = getAODContent()
            Log.d(TAG, "AOD content updated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update AOD content", e)
        }
    }
    
    private fun getAODContent(): String {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val screenTimeData = getCurrentScreenTimeData()
        
        return buildString {
            appendLine("📱 Screen Time")
            appendLine("Time: $currentTime")
            appendLine("Today: ${formatTime(screenTimeData.todayTime)}")
            appendLine("Goal: ${formatTime(screenTimeData.dailyGoal)}")
            appendLine("Progress: ${screenTimeData.progressPercentage}%")
            
            if (screenTimeData.isOverLimit) {
                appendLine("⚠️ Over limit!")
            } else {
                val remaining = screenTimeData.dailyGoal - screenTimeData.todayTime
                appendLine("Remaining: ${formatTime(remaining)}")
            }
        }
    }
    
    private fun handleScreenOn() {
        isScreenOn = true
        if (isAODActive) {
            // Hide AOD when screen is on
            aodView?.visibility = android.view.View.GONE
        }
    }
    
    private fun handleScreenOff() {
        isScreenOn = false
        if (isAODActive) {
            // Show AOD when screen is off
            aodView?.visibility = android.view.View.VISIBLE
            updateAODContent()
        }
    }
    
    private fun getCurrentScreenTimeData(): ScreenTimeAODData {
        // This would integrate with your actual screen time tracking
        // For now, return mock data
        return ScreenTimeAODData(
            todayTime = 2 * 60 * 60 * 1000, // 2 hours
            dailyGoal = 8 * 60 * 60 * 1000, // 8 hours
            progressPercentage = 25,
            isOverLimit = false
        )
    }
    
    private fun formatTime(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)
    }
    
    /**
     * Check if AOD is supported on this device
     */
    fun isAODSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && 
               windowManager != null
    }
    
    /**
     * Get AOD status
     */
    fun getAODStatus(): AODStatus {
        return AODStatus(
            isSupported = isAODSupported(),
            isActive = isAODActive,
            isScreenOn = isScreenOn,
            hasPermission = hasAODPermission()
        )
    }
    
    private fun hasAODPermission(): Boolean {
        // Check if we have the necessary permissions for AOD
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || 
               android.provider.Settings.canDrawOverlays(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAOD()
    }
}

/**
 * Data class for AOD status
 */
data class AODStatus(
    val isSupported: Boolean,
    val isActive: Boolean,
    val isScreenOn: Boolean,
    val hasPermission: Boolean
)

/**
 * Data class for screen time AOD data
 */
data class ScreenTimeAODData(
    val todayTime: Long,
    val dailyGoal: Long,
    val progressPercentage: Int,
    val isOverLimit: Boolean
)
