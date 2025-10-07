package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import java.util.*

/**
 * Manages time zone support for different regions and automatic detection
 */
class TimeZoneManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("timezone_settings", Context.MODE_PRIVATE)
    
    companion object {
        private const val DEFAULT_TIMEZONE = "UTC"
        private const val AUTO_DETECT_ENABLED = true
    }
    
    // Common time zones with their display names
    private val timeZones = mapOf(
        "UTC" to "UTC (Coordinated Universal Time)",
        "America/New_York" to "Eastern Time (ET)",
        "America/Chicago" to "Central Time (CT)",
        "America/Denver" to "Mountain Time (MT)",
        "America/Los_Angeles" to "Pacific Time (PT)",
        "Europe/London" to "London (GMT/BST)",
        "Europe/Paris" to "Paris (CET/CEST)",
        "Europe/Berlin" to "Berlin (CET/CEST)",
        "Europe/Rome" to "Rome (CET/CEST)",
        "Europe/Madrid" to "Madrid (CET/CEST)",
        "Asia/Tokyo" to "Tokyo (JST)",
        "Asia/Shanghai" to "Shanghai (CST)",
        "Asia/Seoul" to "Seoul (KST)",
        "Asia/Hong_Kong" to "Hong Kong (HKT)",
        "Asia/Singapore" to "Singapore (SGT)",
        "Asia/Kolkata" to "Mumbai/Delhi (IST)",
        "Asia/Dubai" to "Dubai (GST)",
        "Asia/Tehran" to "Tehran (IRST)",
        "Australia/Sydney" to "Sydney (AEST/AEDT)",
        "Australia/Melbourne" to "Melbourne (AEST/AEDT)",
        "Australia/Perth" to "Perth (AWST)",
        "Pacific/Auckland" to "Auckland (NZST/NZDT)",
        "America/Sao_Paulo" to "São Paulo (BRT)",
        "America/Mexico_City" to "Mexico City (CST/CDT)",
        "America/Toronto" to "Toronto (EST/EDT)",
        "America/Vancouver" to "Vancouver (PST/PDT)",
        "Africa/Cairo" to "Cairo (EET)",
        "Africa/Johannesburg" to "Johannesburg (SAST)",
        "Africa/Lagos" to "Lagos (WAT)"
    )
    
    /**
     * Get current timezone
     */
    fun getCurrentTimezone(): String {
        return prefs.getString("current_timezone", getSystemTimezone()) ?: getSystemTimezone()
    }
    
    /**
     * Set timezone
     */
    fun setTimezone(timezone: String) {
        prefs.edit().putString("current_timezone", timezone).apply()
    }
    
    /**
     * Get system timezone
     */
    fun getSystemTimezone(): String {
        return TimeZone.getDefault().id
    }
    
    /**
     * Check if auto-detect is enabled
     */
    fun isAutoDetectEnabled(): Boolean {
        return prefs.getBoolean("auto_detect_timezone", AUTO_DETECT_ENABLED)
    }
    
    /**
     * Enable/disable auto-detect
     */
    fun setAutoDetectEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_detect_timezone", enabled).apply()
        
        if (enabled) {
            // Update to system timezone
            setTimezone(getSystemTimezone())
        }
    }
    
    /**
     * Get all available timezones
     */
    fun getAllTimezones(): Map<String, String> {
        return timeZones
    }
    
    /**
     * Get timezone display name
     */
    fun getTimezoneDisplayName(timezone: String): String {
        return timeZones[timezone] ?: timezone
    }
    
    /**
     * Get current time in specified timezone
     */
    fun getCurrentTimeInTimezone(timezone: String): Date {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(timezone))
        return calendar.time
    }
    
    /**
     * Get current time in user's timezone
     */
    fun getCurrentTimeInUserTimezone(): Date {
        return getCurrentTimeInTimezone(getCurrentTimezone())
    }
    
    /**
     * Convert time from one timezone to another
     */
    fun convertTime(date: Date, fromTimezone: String, toTimezone: String): Date {
        val fromCalendar = Calendar.getInstance(TimeZone.getTimeZone(fromTimezone))
        fromCalendar.time = date
        
        val toCalendar = Calendar.getInstance(TimeZone.getTimeZone(toTimezone))
        toCalendar.set(Calendar.YEAR, fromCalendar.get(Calendar.YEAR))
        toCalendar.set(Calendar.MONTH, fromCalendar.get(Calendar.MONTH))
        toCalendar.set(Calendar.DAY_OF_MONTH, fromCalendar.get(Calendar.DAY_OF_MONTH))
        toCalendar.set(Calendar.HOUR_OF_DAY, fromCalendar.get(Calendar.HOUR_OF_DAY))
        toCalendar.set(Calendar.MINUTE, fromCalendar.get(Calendar.MINUTE))
        toCalendar.set(Calendar.SECOND, fromCalendar.get(Calendar.SECOND))
        toCalendar.set(Calendar.MILLISECOND, fromCalendar.get(Calendar.MILLISECOND))
        
        return toCalendar.time
    }
    
    /**
     * Get timezone offset in hours
     */
    fun getTimezoneOffset(timezone: String): Int {
        val tz = TimeZone.getTimeZone(timezone)
        val now = Date()
        val offsetMs = tz.getOffset(now.time)
        return offsetMs / (1000 * 60 * 60)
    }
    
    /**
     * Get timezone offset string (e.g., "+05:30", "-08:00")
     */
    fun getTimezoneOffsetString(timezone: String): String {
        val offset = getTimezoneOffset(timezone)
        val sign = if (offset >= 0) "+" else "-"
        val absOffset = Math.abs(offset)
        val hours = absOffset
        val minutes = 0 // Simplified for now
        return String.format("%s%02d:%02d", sign, hours, minutes)
    }
    
    /**
     * Check if timezone supports daylight saving time
     */
    fun hasDaylightSavingTime(timezone: String): Boolean {
        val tz = TimeZone.getTimeZone(timezone)
        return tz.useDaylightTime()
    }
    
    /**
     * Get timezone info
     */
    fun getTimezoneInfo(timezone: String): TimezoneInfo {
        val tz = TimeZone.getTimeZone(timezone)
        val now = Date()
        val offset = tz.getOffset(now.time)
        val hasDST = tz.useDaylightTime()
        
        return TimezoneInfo(
            id = timezone,
            displayName = getTimezoneDisplayName(timezone),
            offsetHours = offset / (1000 * 60 * 60),
            offsetString = getTimezoneOffsetString(timezone),
            hasDaylightSaving = hasDST,
            isSystemTimezone = timezone == getSystemTimezone(),
            isCurrentTimezone = timezone == getCurrentTimezone()
        )
    }
    
    /**
     * Get all timezone info
     */
    fun getAllTimezoneInfo(): List<TimezoneInfo> {
        return timeZones.keys.map { getTimezoneInfo(it) }
    }
    
    /**
     * Detect timezone based on location (simplified)
     */
    fun detectTimezoneByLocation(latitude: Double, longitude: Double): String {
        // This is a simplified implementation
        // In a real app, you would use a proper timezone detection service
        
        return when {
            // North America
            latitude in 25.0..70.0 && longitude in -180.0..-50.0 -> {
                when {
                    longitude < -100.0 -> "America/Denver"
                    longitude < -85.0 -> "America/Chicago"
                    else -> "America/New_York"
                }
            }
            // Europe
            latitude in 35.0..70.0 && longitude in -10.0..40.0 -> {
                when {
                    longitude < 0.0 -> "Europe/London"
                    longitude < 15.0 -> "Europe/Paris"
                    longitude < 30.0 -> "Europe/Berlin"
                    else -> "Europe/Moscow"
                }
            }
            // Asia
            latitude in 0.0..60.0 && longitude in 70.0..180.0 -> {
                when {
                    longitude < 100.0 -> "Asia/Shanghai"
                    longitude < 120.0 -> "Asia/Tokyo"
                    longitude < 140.0 -> "Asia/Seoul"
                    else -> "Pacific/Auckland"
                }
            }
            // Australia
            latitude in -50.0..-10.0 && longitude in 110.0..180.0 -> {
                when {
                    longitude < 130.0 -> "Australia/Perth"
                    longitude < 150.0 -> "Australia/Sydney"
                    else -> "Pacific/Auckland"
                }
            }
            else -> "UTC"
        }
    }
    
    /**
     * Get timezone by country code
     */
    fun getTimezoneByCountry(countryCode: String): String {
        val countryTimezones = mapOf(
            "US" to "America/New_York",
            "CA" to "America/Toronto",
            "GB" to "Europe/London",
            "DE" to "Europe/Berlin",
            "FR" to "Europe/Paris",
            "IT" to "Europe/Rome",
            "ES" to "Europe/Madrid",
            "JP" to "Asia/Tokyo",
            "CN" to "Asia/Shanghai",
            "KR" to "Asia/Seoul",
            "IN" to "Asia/Kolkata",
            "AU" to "Australia/Sydney",
            "NZ" to "Pacific/Auckland",
            "BR" to "America/Sao_Paulo",
            "MX" to "America/Mexico_City",
            "RU" to "Europe/Moscow",
            "ZA" to "Africa/Johannesburg",
            "EG" to "Africa/Cairo",
            "NG" to "Africa/Lagos"
        )
        
        return countryTimezones[countryCode] ?: "UTC"
    }
    
    /**
     * Get timezone suggestions based on current location
     */
    fun getTimezoneSuggestions(): List<String> {
        val systemTz = getSystemTimezone()
        val currentTz = getCurrentTimezone()
        
        val suggestions = mutableListOf<String>()
        
        // Add system timezone if different from current
        if (systemTz != currentTz) {
            suggestions.add(systemTz)
        }
        
        // Add current timezone
        suggestions.add(currentTz)
        
        // Add popular timezones
        val popularTimezones = listOf(
            "UTC",
            "America/New_York",
            "America/Los_Angeles",
            "Europe/London",
            "Europe/Paris",
            "Asia/Tokyo",
            "Asia/Shanghai",
            "Australia/Sydney"
        )
        
        popularTimezones.forEach { tz ->
            if (tz !in suggestions) {
                suggestions.add(tz)
            }
        }
        
        return suggestions.take(8) // Limit to 8 suggestions
    }
    
    /**
     * Reset to system timezone
     */
    fun resetToSystemTimezone() {
        setTimezone(getSystemTimezone())
    }
    
    /**
     * Reset all timezone settings
     */
    fun resetTimezoneSettings() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Export timezone settings
     */
    fun exportTimezoneSettings(): String {
        return prefs.getString("current_timezone", getSystemTimezone()) ?: getSystemTimezone()
    }
    
    /**
     * Import timezone settings
     */
    fun importTimezoneSettings(timezone: String) {
        if (timeZones.containsKey(timezone)) {
            setTimezone(timezone)
        }
    }
}

data class TimezoneInfo(
    val id: String,
    val displayName: String,
    val offsetHours: Int,
    val offsetString: String,
    val hasDaylightSaving: Boolean,
    val isSystemTimezone: Boolean,
    val isCurrentTimezone: Boolean
)
