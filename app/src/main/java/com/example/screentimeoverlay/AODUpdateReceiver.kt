package com.example.screentimeoverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver for AOD-related system events
 * Handles screen on/off events and user presence detection
 */
class AODUpdateReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "AODUpdateReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                handleScreenOn(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                handleScreenOff(context)
            }
            Intent.ACTION_USER_PRESENT -> {
                handleUserPresent(context)
            }
        }
    }
    
    private fun handleScreenOn(context: Context) {
        Log.d(TAG, "Screen turned on")
        
        // Notify AOD service that screen is on
        val aodIntent = Intent(context, AODService::class.java)
        aodIntent.putExtra("action", "screen_on")
        context.startService(aodIntent)
        
        // Update screen time tracking
        updateScreenTimeTracking(context, "screen_on")
    }
    
    private fun handleScreenOff(context: Context) {
        Log.d(TAG, "Screen turned off")
        
        // Notify AOD service that screen is off
        val aodIntent = Intent(context, AODService::class.java)
        aodIntent.putExtra("action", "screen_off")
        context.startService(aodIntent)
        
        // Update screen time tracking
        updateScreenTimeTracking(context, "screen_off")
        
        // Start AOD if enabled
        startAODIfEnabled(context)
    }
    
    private fun handleUserPresent(context: Context) {
        Log.d(TAG, "User present (device unlocked)")
        
        // Stop AOD when user is present
        val aodIntent = Intent(context, AODService::class.java)
        aodIntent.putExtra("action", "stop_aod")
        context.startService(aodIntent)
        
        // Update screen time tracking
        updateScreenTimeTracking(context, "user_present")
    }
    
    private fun updateScreenTimeTracking(context: Context, event: String) {
        // This would integrate with your screen time tracking system
        // For now, just log the event
        Log.d(TAG, "Screen time tracking updated: $event")
        
        // You could send this to your overlay service or usage tracking service
        val trackingIntent = Intent(context, OverlayService::class.java)
        trackingIntent.putExtra("action", "update_screen_time")
        trackingIntent.putExtra("event", event)
        trackingIntent.putExtra("timestamp", System.currentTimeMillis())
        context.startService(trackingIntent)
    }
    
    private fun startAODIfEnabled(context: Context) {
        // Check if AOD is enabled in settings
        val sharedPrefs = context.getSharedPreferences("screen_time_prefs", Context.MODE_PRIVATE)
        val isAODEnabled = sharedPrefs.getBoolean("aod_enabled", false)
        
        if (isAODEnabled) {
            val aodIntent = Intent(context, AODService::class.java)
            aodIntent.putExtra("action", "start_aod")
            context.startService(aodIntent)
            Log.d(TAG, "AOD started due to screen off")
        }
    }
}
