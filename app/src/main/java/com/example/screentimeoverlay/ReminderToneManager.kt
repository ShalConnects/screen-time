package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages reminder tone selection and message generation
 */
class ReminderToneManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("reminder_tone", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_SELECTED_TONE = "selected_tone"
        private const val PREF_REMINDERS_ENABLED = "reminders_enabled"
        private const val DEFAULT_TONE = "FRIENDLY"
    }
    
    /**
     * Get currently selected tone
     */
    fun getSelectedTone(): ReminderTone {
        val toneName = prefs.getString(PREF_SELECTED_TONE, DEFAULT_TONE) ?: DEFAULT_TONE
        return try {
            ReminderTone.valueOf(toneName)
        } catch (e: IllegalArgumentException) {
            ReminderTone.FRIENDLY
        }
    }
    
    /**
     * Set selected tone
     */
    fun setSelectedTone(tone: ReminderTone) {
        prefs.edit().putString(PREF_SELECTED_TONE, tone.name).apply()
    }
    
    /**
     * Check if reminders are enabled
     */
    fun areRemindersEnabled(): Boolean {
        return prefs.getBoolean(PREF_REMINDERS_ENABLED, false)
    }
    
    /**
     * Enable/disable reminders
     */
    fun setRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_REMINDERS_ENABLED, enabled).apply()
    }
    
    /**
     * Get reminder message based on tone and portion
     */
    fun getReminderMessage(tone: ReminderTone, portionNumber: Int, remainingTime: String): String {
        val messages = when (tone) {
            ReminderTone.HUMOR -> getHumorMessages()
            ReminderTone.RUDE -> getRudeMessages()
            ReminderTone.VERY_RUDE -> getVeryRudeMessages()
            ReminderTone.MOTIVATE -> getMotivateMessages()
            ReminderTone.FRIENDLY -> getFriendlyMessages()
        }
        
        // Get message for specific portion (1-indexed to 0-indexed)
        val portionMessages = messages.getOrNull(portionNumber - 1) ?: messages[0]
        return portionMessages
    }
    
    private fun getHumorMessages(): List<String> {
        return listOf(
            "33% down already? Either you’re really efficient… or just really distracted.",
            "66%! Your phone’s starting to think it’s your soulmate.",
            "Boom! Limit reached. Your thumbs have officially earned a vacation."
        )
    }
    
    private fun getRudeMessages(): List<String> {
        return listOf(
            "33% gone already? Impressive… in a tragic way.",
            "66%? Wow. Maybe go outside and see what “sunlight” feels like.",
            "Limit hit. Congrats—you’ve officially run out of excuses."
        )
    }
    
    private fun getVeryRudeMessages(): List<String> {
        return listOf(
            "33% gone?! Is doomscrolling your full-time job now?",
            "66% wasted! Even your Wi-Fi is judging you.",
            "LIMIT ANNIHILATED! You’re not scrolling—you’re spiraling."
        )
    }
    
    private fun getMotivateMessages(): List<String> {
        return listOf(
            "You’re 1/3 in! Small steps add up—stay balanced, stay focused.",
            "You’ve used 2/3 already—great job being aware of your habits!",
            "Limit reached! Time to recharge and come back stronger."
        )
    }
    
    private fun getFriendlyMessages(): List<String> {
        return listOf(
            "You’ve reached 33%! Just a heads-up, you’re doing great so far.",
            "Two-thirds done! Don’t forget to stretch and blink!",
            "Limit reached—thank you for taking care of your screen time like a pro."
        )
    }
    
    /**
     * Get the appropriate adjective for the selected tone
     */
    fun getToneAdjective(tone: ReminderTone): String {
        return when (tone) {
            ReminderTone.HUMOR -> "funny"
            ReminderTone.RUDE -> "rude"
            ReminderTone.VERY_RUDE -> "very rude"
            ReminderTone.MOTIVATE -> "motivating"
            ReminderTone.FRIENDLY -> "gentle"
        }
    }
    
    /**
     * Get the dynamic reminder description text
     */
    fun getReminderDescriptionText(tone: ReminderTone): String {
        val adjective = getToneAdjective(tone)
        return "Get 3 $adjective reminders at 33%, 66%, and 100% of your limit"
    }
}

/**
 * Available reminder tone options
 */
enum class ReminderTone(val displayName: String) {
    HUMOR("Humor 😄"),
    RUDE("Rude 😠"),
    VERY_RUDE("Very Rude 💀"),
    MOTIVATE("Motivate 💪"),
    FRIENDLY("Friendly 😊")
}

