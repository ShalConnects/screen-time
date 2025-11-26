package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lightweight helper to detect local-day rollover without requiring timers.
 * Usage: call [isNewDayAndUpdate] before reading/writing daily metrics.
 */
class DateChangeGuard(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("daily_metrics_guard", Context.MODE_PRIVATE)

    private val zoneId: ZoneId = ZoneId.systemDefault()

    /** Returns today's local date as ISO string (yyyy-MM-dd). */
    private fun todayKey(): String = LocalDate.now(zoneId).toString()

    /**
     * Checks whether the stored day differs from today's local day. If yes, updates it.
     * Returns true exactly once after each midnight boundary (local time).
     */
    fun isNewDayAndUpdate(): Boolean {
        val key = todayKey()
        val last = prefs.getString("last_day", null)
        if (last == key) return false
        prefs.edit().putString("last_day", key).apply()
        return true
    }

    /** Resets the stored day to today without signaling change next call. */
    fun initializeToToday() {
        prefs.edit().putString("last_day", todayKey()).apply()
    }
}


