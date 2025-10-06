package com.example.screentimeoverlay

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.abs

class SmartPositioningManager(private val context: Context) {
    
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics = DisplayMetrics()
    
    init {
        windowManager.defaultDisplay.getMetrics(displayMetrics)
    }
    
    data class Position(
        val x: Int,
        val y: Int,
        val gravity: Int
    )
    
    fun getOptimalPosition(overlayWidth: Int, overlayHeight: Int): Position {
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Get system UI dimensions (status bar, navigation bar)
        val statusBarHeight = getStatusBarHeight()
        val navigationBarHeight = getNavigationBarHeight()
        
        // Available screen area (excluding system UI)
        val availableWidth = screenWidth
        val availableHeight = screenHeight - statusBarHeight - navigationBarHeight
        
        // Define safe zones (avoiding system UI and common app UI elements)
        val safeZones = getSafeZones(availableWidth, availableHeight, overlayWidth, overlayHeight)
        
        // Find the best position that avoids UI elements
        val bestPosition = findBestPosition(safeZones, overlayWidth, overlayHeight)
        
        return bestPosition
    }
    
    private fun getSafeZones(availableWidth: Int, availableHeight: Int, overlayWidth: Int, overlayHeight: Int): List<Rect> {
        val safeZones = mutableListOf<Rect>()
        
        // Top-right corner (most common for overlays)
        safeZones.add(Rect(
            availableWidth - overlayWidth - 20,
            20,
            availableWidth - 20,
            20 + overlayHeight
        ))
        
        // Top-left corner
        safeZones.add(Rect(
            20,
            20,
            20 + overlayWidth,
            20 + overlayHeight
        ))
        
        // Bottom-right corner
        safeZones.add(Rect(
            availableWidth - overlayWidth - 20,
            availableHeight - overlayHeight - 20,
            availableWidth - 20,
            availableHeight - 20
        ))
        
        // Bottom-left corner
        safeZones.add(Rect(
            20,
            availableHeight - overlayHeight - 20,
            20 + overlayWidth,
            availableHeight - 20
        ))
        
        // Center-right
        safeZones.add(Rect(
            availableWidth - overlayWidth - 20,
            (availableHeight - overlayHeight) / 2,
            availableWidth - 20,
            (availableHeight + overlayHeight) / 2
        ))
        
        // Center-left
        safeZones.add(Rect(
            20,
            (availableHeight - overlayHeight) / 2,
            20 + overlayWidth,
            (availableHeight + overlayHeight) / 2
        ))
        
        return safeZones
    }
    
    private fun findBestPosition(safeZones: List<Rect>, overlayWidth: Int, overlayHeight: Int): Position {
        // For now, return top-right as default
        // In a real implementation, this would analyze current screen content
        val topRight = safeZones[0]
        
        return Position(
            x = topRight.left,
            y = topRight.top,
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
        )
    }
    
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    private fun getNavigationBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }
    
    fun shouldReposition(currentX: Int, currentY: Int, overlayWidth: Int, overlayHeight: Int): Boolean {
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Check if overlay is too close to screen edges
        val margin = 50
        return currentX < margin || 
               currentY < margin || 
               currentX + overlayWidth > screenWidth - margin || 
               currentY + overlayHeight > screenHeight - margin
    }
    
    fun getSnapPosition(x: Int, y: Int, overlayWidth: Int, overlayHeight: Int): Position {
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2
        
        val targetX = if (x < centerX) 20 else screenWidth - overlayWidth - 20
        val targetY = y.coerceIn(100, screenHeight - overlayHeight - 100)
        
        return Position(
            x = targetX,
            y = targetY,
            gravity = if (x < centerX) android.view.Gravity.TOP or android.view.Gravity.START 
                     else android.view.Gravity.TOP or android.view.Gravity.END
        )
    }
}
