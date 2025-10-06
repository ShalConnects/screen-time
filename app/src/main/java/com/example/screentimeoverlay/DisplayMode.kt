package com.example.screentimeoverlay

enum class DisplayMode {
    COMPACT,      // Minimal time-only display
    PROGRESS,     // Time + progress bar
    DETAILED,     // Full detailed view with apps
    EXPANDED      // Expanded view with top apps
}

enum class PositionMode {
    AUTO,         // Smart positioning
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER_LEFT,
    CENTER_RIGHT
}
