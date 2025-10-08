package com.example.screentimeoverlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class MonitorFragment : Fragment(R.layout.fragment_monitor) {
    
    private lateinit var toggleOverlayButton: Button
    private lateinit var mainActivity: MainActivity
    
    // Modern Goal Controls - Class level properties
    private lateinit var goalHoursSlider: SeekBar
    private lateinit var goalMinutesSlider: SeekBar
    private lateinit var goalHoursValue: TextView
    private lateinit var goalMinutesValue: TextView
    private lateinit var goalStatusText: TextView
    
    // Reminder settings
    private lateinit var reminderToneManager: ReminderToneManager
    private lateinit var reminderSettingsContainer: LinearLayout
    private lateinit var enableRemindersSwitch: Switch
    private lateinit var currentToneText: TextView
    private lateinit var reminderDescriptionText: TextView

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toggleOverlayButton = view.findViewById<Button>(R.id.toggleOverlayButton)
        val touchPassthroughSwitch = view.findViewById<Switch>(R.id.touchPassthroughSwitch)
        // Per-App Mode - HIDDEN (backed up in backup/per_app_mode_fragment_backup.kt)
        // val perAppSwitch = view.findViewById<Switch>(R.id.perAppSwitch)
        val autoHideSwitch = view.findViewById<Switch>(R.id.autoHideSwitch)
        // Modern Goal Controls
        goalHoursSlider = view.findViewById<SeekBar>(R.id.goalHoursSlider)
        goalMinutesSlider = view.findViewById<SeekBar>(R.id.goalMinutesSlider)
        goalHoursValue = view.findViewById<TextView>(R.id.goalHoursValue)
        goalMinutesValue = view.findViewById<TextView>(R.id.goalMinutesValue)
        goalStatusText = view.findViewById<TextView>(R.id.goalStatusText)
        val setGoalButton = view.findViewById<Button>(R.id.setGoalButton)
        
        // Quick Preset Buttons
        val preset4hButton = view.findViewById<Button>(R.id.preset4hButton)
        val preset6hButton = view.findViewById<Button>(R.id.preset6hButton)
        val preset8hButton = view.findViewById<Button>(R.id.preset8hButton)
        val preset10hButton = view.findViewById<Button>(R.id.preset10hButton)
        
        val autoPositionButton = view.findViewById<Button>(R.id.autoPositionButton)
        val topRightPositionButton = view.findViewById<Button>(R.id.topRightPositionButton)
        val bottomRightPositionButton = view.findViewById<Button>(R.id.bottomRightPositionButton)

        // Get reference to MainActivity to access its methods
        mainActivity = requireActivity() as MainActivity
        
        // Initialize reminder settings
        initializeReminderSettings(view)

        // Update button state based on current overlay status
        updateToggleButtonState(toggleOverlayButton, mainActivity)
        
        toggleOverlayButton.setOnClickListener {
            // Add press animation
            animateButtonPress(toggleOverlayButton) {
                if (isOverlayServiceRunning()) {
                    // Overlay is running, so stop it
                    mainActivity.stopOverlayService()
                    updateToggleButtonState(toggleOverlayButton, mainActivity)
                } else {
                    // Overlay is not running, so start it
                    if (mainActivity.hasAllPermissions()) {
                        mainActivity.startForegroundOverlayService()
                        updateToggleButtonState(toggleOverlayButton, mainActivity)
                    } else {
                        mainActivity.requestPermissions()
                    }
                }
            }
        }

        touchPassthroughSwitch.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "toggle_touch_passthrough")
            intent.putExtra("enabled", isChecked)
            requireContext().startService(intent)
            Toast.makeText(requireContext(), "Touch passthrough: $isChecked", Toast.LENGTH_SHORT).show()
        }

        // Per-App Mode - HIDDEN (backed up in backup/per_app_mode_fragment_backup.kt)
        /*
        perAppSwitch.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "toggle_per_app_mode")
            intent.putExtra("enabled", isChecked)
            requireContext().startService(intent)
            Toast.makeText(requireContext(), "Per-app mode: $isChecked", Toast.LENGTH_SHORT).show()
        }
        */

        autoHideSwitch.setOnCheckedChangeListener { _, isChecked ->
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "toggle_auto_hide")
            intent.putExtra("enabled", isChecked)
            requireContext().startService(intent)
            Toast.makeText(requireContext(), "Auto-hide mode: $isChecked", Toast.LENGTH_SHORT).show()
        }

        // Interactive Slider Controls
        goalHoursSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                goalHoursValue.text = progress.toString()
                updateGoalStatus()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        goalMinutesSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                goalMinutesValue.text = String.format("%02d", progress)
                updateGoalStatus()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Quick Preset Buttons
        preset4hButton.setOnClickListener { setPresetGoal(4, 0) }
        preset6hButton.setOnClickListener { setPresetGoal(6, 0) }
        preset8hButton.setOnClickListener { setPresetGoal(8, 0) }
        preset10hButton.setOnClickListener { setPresetGoal(10, 0) }
        
        
        // Action Buttons
        setGoalButton.setOnClickListener {
            val hours = goalHoursSlider.progress
            val minutes = goalMinutesSlider.progress
            
            val intent = Intent(requireContext(), OverlayService::class.java)
            intent.putExtra("action", "set_daily_goal")
            intent.putExtra("hours", hours)
            intent.putExtra("minutes", minutes)
            requireContext().startService(intent)
            
            Toast.makeText(requireContext(), "Daily goal set: ${hours}h ${minutes}m", Toast.LENGTH_SHORT).show()
        }
        

        autoPositionButton.setOnClickListener {
            mainActivity.setPositionMode("AUTO")
        }

        topRightPositionButton.setOnClickListener {
            mainActivity.setPositionMode("TOP_RIGHT")
        }

        bottomRightPositionButton.setOnClickListener {
            mainActivity.setPositionMode("BOTTOM_RIGHT")
        }
    }
    
    private fun initializeReminderSettings(view: android.view.View) {
        // Initialize reminder tone manager
        reminderToneManager = ReminderToneManager(requireContext())
        
        // Get UI references
        reminderSettingsContainer = view.findViewById<LinearLayout>(R.id.reminderSettingsContainer)
        enableRemindersSwitch = view.findViewById<Switch>(R.id.enableRemindersSwitch)
        currentToneText = view.findViewById<TextView>(R.id.currentToneText)
        reminderDescriptionText = view.findViewById<TextView>(R.id.reminderDescriptionText)
        
        // Tone selection buttons
        val toneHumorButton = view.findViewById<Button>(R.id.toneHumorButton)
        val toneRudeButton = view.findViewById<Button>(R.id.toneRudeButton)
        val toneVeryRudeButton = view.findViewById<Button>(R.id.toneVeryRudeButton)
        val toneMotivateButton = view.findViewById<Button>(R.id.toneMotivateButton)
        val toneFriendlyButton = view.findViewById<Button>(R.id.toneFriendlyButton)
        
        // Set up enable/disable switch
        enableRemindersSwitch.setOnCheckedChangeListener { _, isChecked ->
            reminderToneManager.setRemindersEnabled(isChecked)
            if (isChecked) {
                reminderSettingsContainer.visibility = android.view.View.VISIBLE
                updateCurrentToneDisplay()
            } else {
                reminderSettingsContainer.visibility = android.view.View.GONE
            }
        }
        
        // Load current state
        enableRemindersSwitch.isChecked = reminderToneManager.areRemindersEnabled()
        if (enableRemindersSwitch.isChecked) {
            reminderSettingsContainer.visibility = android.view.View.VISIBLE
        }
        
        // Set up tone selection buttons
        toneHumorButton.setOnClickListener { setReminderTone(ReminderTone.HUMOR) }
        toneRudeButton.setOnClickListener { setReminderTone(ReminderTone.RUDE) }
        toneVeryRudeButton.setOnClickListener { setReminderTone(ReminderTone.VERY_RUDE) }
        toneMotivateButton.setOnClickListener { setReminderTone(ReminderTone.MOTIVATE) }
        toneFriendlyButton.setOnClickListener { setReminderTone(ReminderTone.FRIENDLY) }
        
        // Initialize display
        updateCurrentToneDisplay()
        updateReminderDescriptionText()
    }
    
    private fun setReminderTone(tone: ReminderTone) {
        reminderToneManager.setSelectedTone(tone)
        updateCurrentToneDisplay()
        updateReminderDescriptionText()
        Toast.makeText(requireContext(), "Reminder tone set to ${tone.displayName}", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateCurrentToneDisplay() {
        val currentTone = reminderToneManager.getSelectedTone()
        currentToneText.text = "Selected: ${currentTone.displayName}"
    }
    
    private fun updateReminderDescriptionText() {
        val currentTone = reminderToneManager.getSelectedTone()
        reminderDescriptionText.text = reminderToneManager.getReminderDescriptionText(currentTone)
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh button state when fragment becomes visible
        updateToggleButtonState(toggleOverlayButton, mainActivity)
    }
    
    override fun onPause() {
        super.onPause()
    }
    
    /**
     * Check if the OverlayService is currently running
     */
    private fun isOverlayServiceRunning(): Boolean {
        val activityManager = requireContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        
        for (serviceInfo in runningServices) {
            if (OverlayService::class.java.name == serviceInfo.service.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * Update the toggle button state based on overlay service status with smooth animations
     */
    private fun updateToggleButtonState(button: Button, mainActivity: MainActivity) {
        val isRunning = isOverlayServiceRunning()
        val newText = if (isRunning) "STOP OVERLAY" else "START OVERLAY"
        
        // Animate text change with scale effect
        animateButtonStateChange(button, newText, isRunning)
    }
    
    /**
     * Animate button state change with smooth transitions
     */
    private fun animateButtonStateChange(button: Button, newText: String, isRunning: Boolean) {
        // Create scale down animation
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f)
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f)
        val fadeOut = ObjectAnimator.ofFloat(button, "alpha", 1f, 0.7f)
        
        val scaleDownSet = AnimatorSet()
        scaleDownSet.playTogether(scaleDownX, scaleDownY, fadeOut)
        scaleDownSet.duration = 150
        scaleDownSet.interpolator = AccelerateDecelerateInterpolator()
        
        // Create scale up animation
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1f)
        val fadeIn = ObjectAnimator.ofFloat(button, "alpha", 0.7f, 1f)
        
        val scaleUpSet = AnimatorSet()
        scaleUpSet.playTogether(scaleUpX, scaleUpY, fadeIn)
        scaleUpSet.duration = 200
        scaleUpSet.interpolator = OvershootInterpolator(1.2f)
        
        // Chain animations
        scaleDownSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Update text and background during the transition
                button.text = newText
                updateButtonBackground(button, isRunning)
                scaleUpSet.start()
            }
        })
        
        scaleDownSet.start()
    }
    
    /**
     * Update button background with glass morphism and animated icons
     */
    private fun updateButtonBackground(button: Button, isRunning: Boolean) {
        val backgroundRes = if (isRunning) R.drawable.button_glass_stop else R.drawable.button_glass_start
        val iconRes = if (isRunning) R.drawable.ic_stop_animated else R.drawable.ic_play_animated
        
        button.setBackgroundResource(backgroundRes)
        button.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0)
        
        // Animate icon transition
        animateIconTransition(button, isRunning)
    }
    
    
    /**
     * Simple icon transition without rotation animation
     */
    private fun animateIconTransition(button: Button, isRunning: Boolean) {
        // Simple scale animation for icon emphasis
        val scaleXAnimator = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.05f, 1f)
        val scaleYAnimator = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.05f, 1f)
        scaleXAnimator.duration = 300
        scaleYAnimator.duration = 300
        
        // Simple shimmer effect
        val alphaAnimator = ObjectAnimator.ofFloat(button, "alpha", 1f, 0.8f, 1f)
        alphaAnimator.duration = 200
        
        // Chain animations
        val iconAnimationSet = AnimatorSet()
        iconAnimationSet.playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
        iconAnimationSet.start()
    }
    
    /**
     * Animate button press with satisfying feedback
     */
    private fun animateButtonPress(button: Button, onComplete: () -> Unit) {
        // Scale down animation for press
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.95f)
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.95f)
        
        val pressSet = AnimatorSet()
        pressSet.playTogether(scaleDownX, scaleDownY)
        pressSet.duration = 100
        pressSet.interpolator = AccelerateDecelerateInterpolator()
        
        // Scale up animation for release
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 0.95f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.95f, 1f)
        
        val releaseSet = AnimatorSet()
        releaseSet.playTogether(scaleUpX, scaleUpY)
        releaseSet.duration = 150
        releaseSet.interpolator = OvershootInterpolator(1.1f)
        
        // Chain animations
        pressSet.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                releaseSet.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onComplete()
                    }
                })
                releaseSet.start()
            }
        })
        
        pressSet.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
    }
    
    // Modern Goal Helper Methods
    private fun updateGoalStatus() {
        val hours = goalHoursSlider.progress
        val minutes = goalMinutesSlider.progress
        goalStatusText.text = "${hours}h ${String.format("%02d", minutes)}m"
        
        // Update color based on goal
        val color = when {
            hours < 4 -> "#FF5722" // Red for very low goals
            hours < 6 -> "#FF9800" // Orange for low goals
            hours < 8 -> "#4CAF50" // Green for normal goals
            hours < 10 -> "#2196F3" // Blue for high goals
            else -> "#9C27B0" // Purple for very high goals
        }
        goalStatusText.setTextColor(android.graphics.Color.parseColor(color))
    }
    
    private fun setPresetGoal(hours: Int, minutes: Int) {
        goalHoursSlider.progress = hours
        goalMinutesSlider.progress = minutes
        updateGoalStatus()
    }
    
}
