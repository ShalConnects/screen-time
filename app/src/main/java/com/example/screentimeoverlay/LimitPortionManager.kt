package com.example.screentimeoverlay

import java.util.concurrent.TimeUnit

/**
 * Manages the 3-portion limit system with contextual reminders
 */
class LimitPortionManager {
    
    /**
     * Calculate 3 equal portions from daily limit
     */
    fun calculatePortions(dailyGoal: DailyGoal): List<LimitPortion> {
        val totalMinutes = dailyGoal.getTotalMinutes()
        val portionMinutes = totalMinutes / 3
        
        val portion1End = portionMinutes
        val portion2End = portionMinutes * 2
        val portion3End = totalMinutes
        
        return listOf(
            LimitPortion(1, 0, portion1End, portionMinutes),
            LimitPortion(2, portion1End, portion2End, portionMinutes),
            LimitPortion(3, portion2End, portion3End, portionMinutes)
        )
    }
    
    /**
     * Check if reminder should be shown for current usage
     * Returns the portion number if reminder should be shown, null otherwise
     */
    fun shouldShowReminder(
        currentUsageMs: Long,
        dailyGoal: DailyGoal,
        shownReminders: Set<Int>
    ): Int? {
        if (!dailyGoal.isEnabled) return null
        
        val currentMinutes = TimeUnit.MILLISECONDS.toMinutes(currentUsageMs).toInt()
        val portions = calculatePortions(dailyGoal)
        
        // Check each portion to see if we've reached the END of a portion (33%, 66%, 100%)
        // and haven't shown that portion's reminder yet
        portions.forEach { portion ->
            if (currentMinutes >= portion.endMinutes && !shownReminders.contains(portion.portionNumber)) {
                return portion.portionNumber
            }
        }
        
        return null
    }
    
    /**
     * Get remaining time in minutes for current portion
     */
    fun getRemainingTime(currentUsageMs: Long, dailyGoal: DailyGoal): Int {
        val currentMinutes = TimeUnit.MILLISECONDS.toMinutes(currentUsageMs).toInt()
        val portions = calculatePortions(dailyGoal)
        
        // Find which portion we're currently in
        val currentPortion = portions.find { portion ->
            currentMinutes >= portion.startMinutes && currentMinutes < portion.endMinutes
        } ?: portions.last() // If we're past all portions, use the last one
        
        // Calculate remaining time in current portion
        val remainingInPortion = currentPortion.endMinutes - currentMinutes
        return if (remainingInPortion > 0) remainingInPortion else 0
    }
    
    /**
     * Get current portion number (1, 2, or 3)
     */
    fun getCurrentPortion(currentUsageMs: Long, dailyGoal: DailyGoal): Int {
        val currentMinutes = TimeUnit.MILLISECONDS.toMinutes(currentUsageMs).toInt()
        val portions = calculatePortions(dailyGoal)
        
        // Find which portion we're currently in
        val currentPortion = portions.find { portion ->
            currentMinutes >= portion.startMinutes && currentMinutes < portion.endMinutes
        } ?: portions.last() // If we're past all portions, use the last one
        
        return currentPortion.portionNumber
    }
    
    /**
     * Get current portion progress as a fraction (e.g., 0.5 for halfway through portion)
     */
    fun getCurrentPortionProgress(currentUsageMs: Long, dailyGoal: DailyGoal): Float {
        val currentMinutes = TimeUnit.MILLISECONDS.toMinutes(currentUsageMs).toInt()
        val portions = calculatePortions(dailyGoal)
        
        // Find which portion we're currently in
        val currentPortion = portions.find { portion ->
            currentMinutes >= portion.startMinutes && currentMinutes < portion.endMinutes
        } ?: portions.last() // If we're past all portions, use the last one
        
        val progressInPortion = currentMinutes - currentPortion.startMinutes
        return progressInPortion.toFloat() / currentPortion.durationMinutes.toFloat()
    }
    
    /**
     * Format remaining time as readable string
     */
    fun formatRemainingTime(remainingMinutes: Int): String {
        if (remainingMinutes <= 0) return "Limit reached"
        
        val hours = remainingMinutes / 60
        val mins = remainingMinutes % 60
        
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m remaining"
            hours > 0 -> "${hours}h remaining"
            else -> "${mins}m remaining"
        }
    }
}

/**
 * Represents one portion of the daily limit
 */
data class LimitPortion(
    val portionNumber: Int,     // 1, 2, or 3
    val startMinutes: Int,       // When this portion begins (in minutes)
    val endMinutes: Int,         // When this portion ends (in minutes)
    val durationMinutes: Int     // Duration of this portion
)

