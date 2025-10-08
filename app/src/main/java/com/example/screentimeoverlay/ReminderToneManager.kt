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
            "One third of your limit down! Time flies when you're having fun... or procrastinating!",
            "Two thirds done! Your screen is starting to wonder if you'll ever put it down!",
            "Limit reached! Your screen says thanks for the workout! Time to rest those thumbs!"
        )
    }
    
    private fun getRudeMessages(): List<String> {
        return listOf(
            "Seriously? You're already at 33% of your limit!",
            "66% gone! Do you ever put your phone down?",
            "Limit reached! Congratulations on wasting your entire limit!"
        )
    }
    
    private fun getVeryRudeMessages(): List<String> {
        return listOf(
            "33% GONE! Are you addicted or what?!",
            "66% WASTED! Get a life already!",
            "LIMIT DESTROYED! Your productivity just called - it gave up on you!"
        )
    }
    
    private fun getMotivateMessages(): List<String> {
        return listOf(
            "You're 1/3 through your limit! Stay mindful and make every minute count!",
            "2/3 of the way! You're doing great at tracking your usage! Keep it up!",
            "Limit reached! You've completed your journey! Take a well-deserved break!"
        )
    }
    
    private fun getFriendlyMessages(): List<String> {
        return listOf(
            "Hey there! You've reached your first milestone! You're 1/3 through your daily limit.",
            "Friendly reminder! You're now at 2/3 of your limit. Time is precious!",
            "You've reached your limit! Thanks for being mindful of your screen time today!"
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

