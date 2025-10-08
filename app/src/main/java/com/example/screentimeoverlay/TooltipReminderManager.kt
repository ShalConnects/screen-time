package com.example.screentimeoverlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Manages tooltip reminder display beside the overlay
 */
class TooltipReminderManager(private val context: Context) {
    
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var tooltipView: View? = null
    private var isTooltipShowing = false
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null
    
    companion object {
        private const val AUTO_DISMISS_DELAY = 5000L // 5 seconds
        private const val TOOLTIP_OFFSET_X = 20 // Offset from overlay in pixels
    }
    
    private var isOverlayOnLeft = true // Track overlay position
    
    /**
     * Show tooltip beside overlay
     * @param message The message to display
     * @param overlayParams The overlay's window params to position tooltip beside it
     */
    fun showTooltip(message: String, overlayParams: WindowManager.LayoutParams) {
        // Dismiss any existing tooltip first
        dismissTooltip()
        
        try {
            // Inflate tooltip layout
            val inflater = LayoutInflater.from(context)
            tooltipView = inflater.inflate(R.layout.tooltip_reminder, null)
            
            // Set message
            val messageText = tooltipView?.findViewById<TextView>(R.id.tooltipMessageText)
            messageText?.text = message
            
            // Determine overlay position (left or right side of screen)
            val displayMetrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels
            val centerX = screenWidth / 2
            isOverlayOnLeft = overlayParams.x < centerX
            
            // Create window params for tooltip
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            
            // Position tooltip based on overlay location
            if (isOverlayOnLeft) {
                // Overlay on left side - show tooltip to the right with left-pointing tail
                tooltipView?.setBackgroundResource(R.drawable.tooltip_background_left)
                params.gravity = Gravity.TOP or Gravity.START
                params.x = overlayParams.x + overlayParams.width + TOOLTIP_OFFSET_X
                params.y = overlayParams.y
            } else {
                // Overlay on right side - show tooltip to the left with right-pointing tail
                tooltipView?.setBackgroundResource(R.drawable.tooltip_background_right)
                params.gravity = Gravity.TOP or Gravity.END
                params.x = screenWidth - overlayParams.x + TOOLTIP_OFFSET_X
                params.y = overlayParams.y
            }
            
            // Add tooltip to window with initial animation state
            tooltipView?.alpha = 0f
            tooltipView?.scaleX = 0.8f
            tooltipView?.scaleY = 0.8f
            windowManager.addView(tooltipView, params)
            isTooltipShowing = true
            
            // Animate tooltip in
            tooltipView?.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(300)
                ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                ?.start()
            
            // Auto-dismiss after 6 seconds with improved timing
            autoDismissRunnable = Runnable {
                dismissTooltip()
            }
            handler.postDelayed(autoDismissRunnable!!, 6000)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Update tooltip position to follow overlay
     */
    fun updateTooltipPosition(overlayParams: WindowManager.LayoutParams) {
        if (!isTooltipShowing || tooltipView == null) return
        
        try {
            val params = tooltipView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            // Get screen dimensions
            val displayMetrics = android.util.DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenWidth = displayMetrics.widthPixels
            val centerX = screenWidth / 2
            
            // Update position based on overlay location
            if (isOverlayOnLeft) {
                // Overlay on left side - keep tooltip to the right
                params.gravity = Gravity.TOP or Gravity.START
                params.x = overlayParams.x + overlayParams.width + TOOLTIP_OFFSET_X
                params.y = overlayParams.y
            } else {
                // Overlay on right side - keep tooltip to the left
                params.gravity = Gravity.TOP or Gravity.END
                params.x = screenWidth - overlayParams.x + TOOLTIP_OFFSET_X
                params.y = overlayParams.y
            }
            
            windowManager.updateViewLayout(tooltipView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Dismiss tooltip with animation
     */
    fun dismissTooltip() {
        if (!isTooltipShowing) return
        
        try {
            // Cancel auto-dismiss
            autoDismissRunnable?.let { handler.removeCallbacks(it) }
            autoDismissRunnable = null
            
            // Animate tooltip out before removing
            tooltipView?.animate()
                ?.alpha(0f)
                ?.scaleX(0.9f)
                ?.scaleY(0.9f)
                ?.setDuration(250)
                ?.setInterpolator(android.view.animation.AccelerateInterpolator())
                ?.withEndAction {
                    // Remove tooltip from window after animation
                    tooltipView?.let {
                        try {
                            windowManager.removeView(it)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    tooltipView = null
                    isTooltipShowing = false
                }
                ?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Check if tooltip is currently showing
     */
    fun isShowing(): Boolean = isTooltipShowing
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        dismissTooltip()
    }
}

