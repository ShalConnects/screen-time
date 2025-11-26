package com.example.screentimeoverlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.example.screentimeoverlay.BuildConfig

/**
 * Optional AccessibilityService to collect playful metrics (taps/scroll/words).
 * Disabled by default and gated behind an in-app toggle.
 */
class MetricsAccessibilityService : AccessibilityService() {

    private lateinit var store: MetricsStore

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = MetricsStore(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = getSharedPreferences("ui_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("metrics_accessibility_enabled", false)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                store.addTaps(1)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Prefer API 28+ delta; otherwise or if zero, estimate using indices
                var dy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    event.scrollDeltaY
                } else 0
                if (dy == 0) {
                    val fromIdx = event.fromIndex
                    val toIdx = event.toIndex
                    val idxDelta = if (fromIdx >= 0 && toIdx >= 0) kotlin.math.abs(toIdx - fromIdx) else 0
                    // Estimate ~10px per moved item; ensure a minimal bump so we register activity
                    dy = if (idxDelta > 0) idxDelta * 10 else 10
                }
                val absDy = kotlin.math.abs(dy)
                if (absDy > 0) {
                    store.addScrollPx(absDy)
                    if (BuildConfig.DEBUG) {
                        android.util.Log.d("MetricsAccessibilityService", "Scroll event: delta=$dy, abs=$absDy, class=${event.className}")
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val added = event.addedCount
                if (!event.isPassword) {
                    // Approximate words from added characters
                    val words = (event.text?.joinToString(" ") ?: "").trim().split(Regex("\\s+")).count()
                    if (words > 0) store.addWordsTyped(words)
                }
            }
        }
    }

    override fun onInterrupt() { }
}


