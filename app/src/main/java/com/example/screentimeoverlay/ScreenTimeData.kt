package com.example.screentimeoverlay

import android.app.usage.UsageStats
import java.util.concurrent.TimeUnit

data class ScreenTimeData(
    val totalTime: Long,
    val topApps: List<AppUsage>,
    val currentApp: String? = null
) {
    fun getFormattedTime(): String {
        val hours = TimeUnit.MILLISECONDS.toHours(totalTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalTime) % 60
        return String.format("%02d:%02d", hours, minutes)
    }
}

data class AppUsage(
    val packageName: String,
    val appName: String,
    val timeInForeground: Long
) {
    fun getFormattedTime(): String {
        val hours = TimeUnit.MILLISECONDS.toHours(timeInForeground)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeInForeground) % 60
        return String.format("%02d:%02d", hours, minutes)
    }
}

data class DailyGoal(
    val maxHours: Int,
    val maxMinutes: Int,
    val isEnabled: Boolean = true
) {
    fun getTotalMinutes(): Int = maxHours * 60 + maxMinutes
    
    fun isExceeded(totalTimeMs: Long): Boolean {
        if (!isEnabled) return false
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalTimeMs)
        return totalMinutes > getTotalMinutes()
    }
}
