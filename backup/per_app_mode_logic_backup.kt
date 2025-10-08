// Backup of Per-App Mode Logic from OverlayService.kt
// This file contains the backed up per-app mode functionality

// 1. Variable declaration (line 71)
private var showPerApp = false

// 2. Per-app mode logic in updateScreenTime() method (lines 246-251)
val newText = if (showPerApp && screenTimeData.currentApp != null) {
    val currentAppUsage = screenTimeData.topApps.find { it.packageName == screenTimeData.currentApp }
    currentAppUsage?.getFormattedTime() ?: screenTimeData.getFormattedTime()
} else {
    screenTimeData.getFormattedTime()
}

// 3. togglePerAppMode() function (lines 936-940)
fun togglePerAppMode() {
    showPerApp = !showPerApp
    // Force update on next cycle
    updateScreenTime()
}

// 4. Intent handling for per-app mode toggle (lines 1131-1136)
"toggle_per_app_mode" -> {
    val enabled = intent.getBooleanExtra("enabled", false)
    if (enabled) {
        togglePerAppMode()
    }
}
