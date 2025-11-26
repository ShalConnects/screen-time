package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences

/**
 * On-device counters for playful daily metrics: taps, scroll distance, words typed.
 * Uses local-day keys and [DateChangeGuard] to reset at midnight.
 */
class MetricsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("metrics_store", Context.MODE_PRIVATE)
    private val dateGuard = DateChangeGuard(context)

    private fun key(base: String): String = base + "_" + getTodayKey()
    private fun getTodayKey(): String {
        // DateChangeGuard stores ISO yyyy-MM-dd internally; refresh if new day
        if (dateGuard.isNewDayAndUpdate()) {
            // Nothing to clear proactively; keys are date-scoped.
        }
        // Read current stored day for continuity; if absent, initialize
        val current = prefs.getString("current_day", null)
        val today = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
        if (current != today) prefs.edit().putString("current_day", today).apply()
        return today
    }

    fun addTaps(count: Int) {
        if (count <= 0) return
        val k = key("taps")
        val next = prefs.getInt(k, 0) + count
        prefs.edit().putInt(k, next).apply()
    }

    fun addScrollPx(deltaPx: Int) {
        if (deltaPx == 0) return
        val k = key("scroll_px")
        val next = prefs.getInt(k, 0) + deltaPx
        prefs.edit().putInt(k, next).apply()
    }

    fun addWordsTyped(count: Int) {
        if (count <= 0) return
        val k = key("words")
        val next = prefs.getInt(k, 0) + count
        prefs.edit().putInt(k, next).apply()
    }

    data class TodayMetrics(
        val taps: Int,
        val scrollPx: Int,
        val words: Int
    )

    fun getTodayMetrics(): TodayMetrics {
        val taps = prefs.getInt(key("taps"), 0)
        val px = prefs.getInt(key("scroll_px"), 0)
        val words = prefs.getInt(key("words"), 0)
        return TodayMetrics(taps, px, words)
    }
}


