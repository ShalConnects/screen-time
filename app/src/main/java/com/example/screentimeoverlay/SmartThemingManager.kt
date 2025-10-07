package com.example.screentimeoverlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.WindowManager
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.animation.ValueAnimator
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import kotlin.math.abs

/**
 * Smart theming system that adapts overlay appearance based on background content
 * and usage status for optimal visibility and user experience
 */
class SmartThemingManager(private val context: Context) {
    
    enum class ThemeMode {
        ADAPTIVE,    // Automatically adapts to background
        LIGHT,       // Light theme for dark backgrounds
        DARK,        // Dark theme for light backgrounds
        HIGH_CONTRAST // High contrast for accessibility
    }
    
    enum class UsageStatus {
        GOOD,        // Under 50% of goal
        WARNING,     // 50-80% of goal
        CRITICAL,    // 80-100% of goal
        EXCEEDED     // Over 100% of goal
    }
    
    private var currentThemeMode = ThemeMode.ADAPTIVE
    private var currentUsageStatus = UsageStatus.GOOD
    private var backgroundBrightness = 0.5f // 0 = dark, 1 = bright
    
    // Theme configurations
    private data class ThemeConfig(
        val backgroundRes: Int,
        val progressBarRes: Int,
        val textColor: Int,
        val secondaryTextColor: Int,
        val accentColor: Int,
        val borderColor: Int,
        val shadowColor: Int
    )
    
    private val themeConfigs = mapOf(
        ThemeMode.ADAPTIVE to ThemeConfig(
            R.drawable.glass_background_adaptive,
            R.drawable.progress_bar_animated,
            Color.WHITE,
            Color.parseColor("#E0E0E0"),
            Color.parseColor("#6366F1"),
            Color.parseColor("#30FFFFFF"),
            Color.parseColor("#80000000")
        ),
        ThemeMode.LIGHT to ThemeConfig(
            R.drawable.glass_background,
            R.drawable.progress_bar_animated,
            Color.WHITE,
            Color.parseColor("#E0E0E0"),
            Color.parseColor("#6366F1"),
            Color.parseColor("#40FFFFFF"),
            Color.parseColor("#80000000")
        ),
        ThemeMode.DARK to ThemeConfig(
            R.drawable.glass_background_dark,
            R.drawable.progress_bar_animated,
            Color.parseColor("#1A1A1A"),
            Color.parseColor("#666666"),
            Color.parseColor("#8B5CF6"),
            Color.parseColor("#20FFFFFF"),
            Color.parseColor("#40000000")
        ),
        ThemeMode.HIGH_CONTRAST to ThemeConfig(
            R.drawable.glass_background,
            R.drawable.progress_bar_animated,
            Color.WHITE,
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#00FF00"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#FF000000")
        )
    )
    
    private val statusConfigs = mapOf(
        UsageStatus.GOOD to ThemeConfig(
            R.drawable.glass_background_success,
            R.drawable.progress_bar_success,
            Color.WHITE,
            Color.parseColor("#E0E0E0"),
            Color.parseColor("#10B981"),
            Color.parseColor("#4010B981"),
            Color.parseColor("#80000000")
        ),
        UsageStatus.WARNING to ThemeConfig(
            R.drawable.glass_background_warning,
            R.drawable.progress_bar_warning,
            Color.WHITE,
            Color.parseColor("#E0E0E0"),
            Color.parseColor("#F59E0B"),
            Color.parseColor("#40F59E0B"),
            Color.parseColor("#80000000")
        ),
        UsageStatus.CRITICAL to ThemeConfig(
            R.drawable.glass_background_warning,
            R.drawable.progress_bar_warning,
            Color.WHITE,
            Color.parseColor("#E0E0E0"),
            Color.parseColor("#F59E0B"),
            Color.parseColor("#40F59E0B"),
            Color.parseColor("#80000000")
        ),
        UsageStatus.EXCEEDED to ThemeConfig(
            R.drawable.glass_background_error,
            R.drawable.progress_bar_error,
            Color.WHITE,
            Color.parseColor("#E0E0E0"),
            Color.parseColor("#EF4444"),
            Color.parseColor("#40EF4444"),
            Color.parseColor("#80000000")
        )
    )
    
    /**
     * Analyze background content to determine optimal theme
     */
    fun analyzeBackground(windowManager: WindowManager, overlayView: View): ThemeMode {
        // This is a simplified implementation
        // In a real implementation, you would:
        // 1. Capture screenshots of the background
        // 2. Analyze color distribution and brightness
        // 3. Detect UI elements that might interfere
        
        // For now, we'll use a simple heuristic based on system UI
        val displayMetrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        
        // Simple brightness estimation (this would be more sophisticated in reality)
        backgroundBrightness = 0.5f // Placeholder
        
        return when {
            backgroundBrightness < 0.3f -> ThemeMode.LIGHT
            backgroundBrightness > 0.7f -> ThemeMode.DARK
            else -> ThemeMode.ADAPTIVE
        }
    }
    
    /**
     * Update usage status and apply appropriate theming
     */
    fun updateUsageStatus(progress: Int, goalMinutes: Int, currentMinutes: Int) {
        val newStatus = when {
            progress < 50 -> UsageStatus.GOOD
            progress < 80 -> UsageStatus.WARNING
            progress < 100 -> UsageStatus.CRITICAL
            else -> UsageStatus.EXCEEDED
        }
        
        if (newStatus != currentUsageStatus) {
            currentUsageStatus = newStatus
            // Trigger status change animation
            animateStatusChange()
        }
    }
    
    /**
     * Apply theme to overlay components
     */
    fun applyTheme(
        mainContainer: LinearLayout,
        timeTextView: TextView,
        dateTextView: TextView?,
        progressBar: android.widget.ProgressBar,
        goalTextView: TextView?
    ) {
        val config = getCurrentConfig()
        
        // Apply background
        mainContainer.setBackgroundResource(config.backgroundRes)
        
        // Apply text colors
        timeTextView.setTextColor(config.textColor)
        timeTextView.setShadowLayer(4f, 2f, 2f, config.shadowColor)
        
        dateTextView?.setTextColor(config.secondaryTextColor)
        goalTextView?.setTextColor(config.secondaryTextColor)
        
        // Apply progress bar
        progressBar.progressDrawable = context.getDrawable(config.progressBarRes)
        
        // Add subtle animations
        animateThemeTransition(mainContainer)
    }
    
    /**
     * Add floating particle effects for ambient animation
     */
    fun addFloatingParticles(container: LinearLayout) {
        val particleContainer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Add 3-5 floating particles
        repeat(4) {
            val particle = ImageView(context).apply {
                setImageResource(R.drawable.floating_particle)
                layoutParams = LinearLayout.LayoutParams(8, 8)
                alpha = 0.3f
            }
            particleContainer.addView(particle)
            
            // Animate particle movement
            animateFloatingParticle(particle)
        }
        
        container.addView(particleContainer)
    }
    
    /**
     * Add pulse effect when approaching goals
     */
    fun addPulseEffect(view: View) {
        val pulseAnimator = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f)
        val pulseAnimatorY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f)
        
        // Set repeat properties on individual animators
        pulseAnimator.repeatCount = ObjectAnimator.INFINITE
        pulseAnimator.repeatMode = ObjectAnimator.REVERSE
        pulseAnimatorY.repeatCount = ObjectAnimator.INFINITE
        pulseAnimatorY.repeatMode = ObjectAnimator.REVERSE
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(pulseAnimator, pulseAnimatorY)
        animatorSet.duration = 1000
        animatorSet.start()
    }
    
    /**
     * Smooth transition animation between themes
     */
    private fun animateThemeTransition(view: View) {
        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.7f)
        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0.7f, 1f)
        
        val animatorSet = AnimatorSet()
        animatorSet.playSequentially(fadeOut, fadeIn)
        animatorSet.duration = 300
        animatorSet.start()
    }
    
    /**
     * Animate status change with color transition
     */
    private fun animateStatusChange() {
        // This would trigger a smooth color transition
        // Implementation depends on specific UI components
    }
    
    /**
     * Animate floating particles
     */
    private fun animateFloatingParticle(particle: ImageView) {
        val randomX = (Math.random() * 200 - 100).toFloat()
        val randomY = (Math.random() * 200 - 100).toFloat()
        
        val moveX = ObjectAnimator.ofFloat(particle, "translationX", 0f, randomX, 0f)
        val moveY = ObjectAnimator.ofFloat(particle, "translationY", 0f, randomY, 0f)
        val fade = ObjectAnimator.ofFloat(particle, "alpha", 0.3f, 0.8f, 0.3f)
        
        // Set repeat properties on individual animators
        moveX.repeatCount = ObjectAnimator.INFINITE
        moveX.repeatMode = ObjectAnimator.REVERSE
        moveY.repeatCount = ObjectAnimator.INFINITE
        moveY.repeatMode = ObjectAnimator.REVERSE
        fade.repeatCount = ObjectAnimator.INFINITE
        fade.repeatMode = ObjectAnimator.REVERSE
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(moveX, moveY, fade)
        animatorSet.duration = 3000 + (Math.random() * 2000).toLong()
        animatorSet.start()
    }
    
    /**
     * Get current theme configuration
     */
    private fun getCurrentConfig(): ThemeConfig {
        return if (currentThemeMode == ThemeMode.ADAPTIVE) {
            // Use status-based theming for adaptive mode
            statusConfigs[currentUsageStatus] ?: themeConfigs[ThemeMode.ADAPTIVE]!!
        } else {
            themeConfigs[currentThemeMode] ?: themeConfigs[ThemeMode.ADAPTIVE]!!
        }
    }
    
    /**
     * Set theme mode manually
     */
    fun setThemeMode(mode: ThemeMode) {
        currentThemeMode = mode
    }
    
    /**
     * Get current usage status
     */
    fun getCurrentUsageStatus(): UsageStatus = currentUsageStatus
    
    /**
     * Check if overlay needs high contrast for accessibility
     */
    fun needsHighContrast(): Boolean {
        return currentThemeMode == ThemeMode.HIGH_CONTRAST || 
               (currentThemeMode == ThemeMode.ADAPTIVE && backgroundBrightness < 0.2f)
    }
}
