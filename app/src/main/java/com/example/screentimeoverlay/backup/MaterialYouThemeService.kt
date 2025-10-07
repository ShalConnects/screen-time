package com.example.screentimeoverlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.*

/**
 * Service for managing Material You dynamic theming
 * Adapts app colors based on user's wallpaper and system theme
 */
class MaterialYouThemeService : Service() {
    
    private var isServiceRunning = false
    private var currentTheme: MaterialYouTheme? = null
    
    companion object {
        private const val TAG = "MaterialYouThemeService"
    }
    
    override fun onCreate() {
        super.onCreate()
        initializeMaterialYou()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra("action")) {
            "update_theme" -> updateMaterialYouTheme()
            "get_theme" -> getCurrentTheme()
            "apply_theme" -> applyThemeToUI()
            "reset_theme" -> resetToDefaultTheme()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun initializeMaterialYou() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                isServiceRunning = true
                currentTheme = generateMaterialYouTheme()
                Log.d(TAG, "Material You theme service initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Material You theme", e)
                isServiceRunning = false
            }
        } else {
            Log.w(TAG, "Material You not supported on this Android version")
            isServiceRunning = false
        }
    }
    
    private fun updateMaterialYouTheme() {
        if (!isServiceRunning) return
        
        try {
            val newTheme = generateMaterialYouTheme()
            currentTheme = newTheme
            Log.d(TAG, "Material You theme updated")
            
            // Notify other components about theme change
            broadcastThemeChange(newTheme)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Material You theme", e)
        }
    }
    
    private fun generateMaterialYouTheme(): MaterialYouTheme {
        // This would integrate with Android 12+ Material You APIs
        // For now, generate a theme based on current time and system settings
        
        val isDarkMode = isDarkModeEnabled()
        val accentColor = generateAccentColor()
        
        return MaterialYouTheme(
            primaryColor = accentColor,
            secondaryColor = adjustColorBrightness(accentColor, 0.8f),
            tertiaryColor = adjustColorBrightness(accentColor, 0.6f),
            surfaceColor = if (isDarkMode) Color.parseColor("#121212") else Color.parseColor("#FFFFFF"),
            onSurfaceColor = if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#000000"),
            errorColor = Color.parseColor("#F44336"),
            isDarkMode = isDarkMode,
            generatedAt = System.currentTimeMillis()
        )
    }
    
    private fun isDarkModeEnabled(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
    
    private fun generateAccentColor(): Int {
        // Generate a color based on current time for demonstration
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour % 6) {
            0 -> Color.parseColor("#FF5722") // Deep Orange
            1 -> Color.parseColor("#E91E63") // Pink
            2 -> Color.parseColor("#9C27B0") // Purple
            3 -> Color.parseColor("#3F51B5") // Indigo
            4 -> Color.parseColor("#2196F3") // Blue
            5 -> Color.parseColor("#4CAF50") // Green
            else -> Color.parseColor("#FF9800") // Orange
        }
    }
    
    private fun adjustColorBrightness(color: Int, factor: Float): Int {
        val alpha = Color.alpha(color)
        val red = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val green = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val blue = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, red, green, blue)
    }
    
    private fun broadcastThemeChange(theme: MaterialYouTheme) {
        val intent = Intent("com.example.screentimeoverlay.THEME_CHANGED")
        intent.putExtra("theme", theme)
        sendBroadcast(intent)
    }
    
    private fun getCurrentTheme() {
        currentTheme?.let { theme ->
            Log.d(TAG, "Current theme: $theme")
        } ?: Log.w(TAG, "No theme available")
    }
    
    private fun applyThemeToUI() {
        currentTheme?.let { theme ->
            // This would apply the theme to your UI components
            Log.d(TAG, "Applying theme to UI: ${theme.primaryColor}")
        }
    }
    
    private fun resetToDefaultTheme() {
        currentTheme = generateDefaultTheme()
        Log.d(TAG, "Theme reset to default")
    }
    
    private fun generateDefaultTheme(): MaterialYouTheme {
        return MaterialYouTheme(
            primaryColor = Color.parseColor("#2196F3"),
            secondaryColor = Color.parseColor("#1976D2"),
            tertiaryColor = Color.parseColor("#0D47A1"),
            surfaceColor = Color.parseColor("#FFFFFF"),
            onSurfaceColor = Color.parseColor("#000000"),
            errorColor = Color.parseColor("#F44336"),
            isDarkMode = false,
            generatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * Get current Material You theme
     */
    fun getMaterialYouTheme(): MaterialYouTheme? {
        return currentTheme
    }
    
    /**
     * Check if Material You is supported
     */
    fun isMaterialYouSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isServiceRunning
    }
    
    /**
     * Get theme status
     */
    fun getThemeStatus(): MaterialYouStatus {
        return MaterialYouStatus(
            isSupported = isMaterialYouSupported(),
            isActive = isServiceRunning,
            currentTheme = currentTheme,
            androidVersion = Build.VERSION.SDK_INT
        )
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        currentTheme = null
    }
}

/**
 * Data class for Material You theme
 */
data class MaterialYouTheme(
    val primaryColor: Int,
    val secondaryColor: Int,
    val tertiaryColor: Int,
    val surfaceColor: Int,
    val onSurfaceColor: Int,
    val errorColor: Int,
    val isDarkMode: Boolean,
    val generatedAt: Long
) : java.io.Serializable

/**
 * Data class for Material You status
 */
data class MaterialYouStatus(
    val isSupported: Boolean,
    val isActive: Boolean,
    val currentTheme: MaterialYouTheme?,
    val androidVersion: Int
)
