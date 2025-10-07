package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.*

/**
 * Coordinates all personalization features including custom goals, app categories, time zones, and profiles
 */
class PersonalizationManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("personalization", Context.MODE_PRIVATE)
    
    // Feature managers
    val customGoalsManager = CustomGoalsManager(context)
    val appCategoryManager = AppCategoryManager(context)
    val timeZoneManager = TimeZoneManager(context)
    val profileManager = ProfileManager(context)
    
    /**
     * Initialize personalization features
     */
    fun initialize() {
        // Initialize default profiles if none exist
        if (profileManager.getAllProfiles().isEmpty()) {
            initializeDefaultProfiles()
        }
        
        // Set up default timezone if not set
        if (timeZoneManager.getCurrentTimezone() == "UTC") {
            timeZoneManager.setAutoDetectEnabled(true)
        }
        
        // Enable custom goals by default
        if (!customGoalsManager.isCustomGoalsEnabled()) {
            customGoalsManager.setCustomGoalsEnabled(true)
        }
    }
    
    /**
     * Get current personalization settings
     */
    fun getCurrentSettings(): PersonalizationSettings {
        val currentProfile = profileManager.getCurrentProfile()
        val profileInfo = profileManager.getProfileInfo(currentProfile)
        
        return PersonalizationSettings(
            currentProfile = currentProfile,
            profileInfo = profileInfo,
            customGoals = customGoalsManager.getCurrentDayGoal(),
            timezone = timeZoneManager.getCurrentTimezone(),
            timezoneInfo = timeZoneManager.getTimezoneInfo(timeZoneManager.getCurrentTimezone()),
            appCategories = appCategoryManager.getAllCategories(),
            isCustomGoalsEnabled = customGoalsManager.isCustomGoalsEnabled(),
            isAutoDetectTimezone = timeZoneManager.isAutoDetectEnabled()
        )
    }
    
    /**
     * Switch to a different profile
     */
    fun switchProfile(profileName: String): Boolean {
        if (!profileManager.profileExists(profileName)) {
            return false
        }
        
        profileManager.setCurrentProfile(profileName)
        return true
    }
    
    /**
     * Create a new profile with custom settings
     */
    fun createCustomProfile(
        name: String,
        description: String,
        goals: ProfileGoals,
        notifications: ProfileNotifications,
        appFilters: ProfileAppFilters
    ): Boolean {
        if (!profileManager.createProfile(name, description)) {
            return false
        }
        
        profileManager.setProfileGoals(name, goals)
        profileManager.setProfileNotifications(name, notifications)
        profileManager.setProfileAppFilters(name, appFilters)
        
        return true
    }
    
    /**
     * Update current profile settings
     */
    fun updateCurrentProfileSettings(
        goals: ProfileGoals? = null,
        notifications: ProfileNotifications? = null,
        appFilters: ProfileAppFilters? = null
    ) {
        val currentProfile = profileManager.getCurrentProfile()
        
        goals?.let { profileManager.setProfileGoals(currentProfile, it) }
        notifications?.let { profileManager.setProfileNotifications(currentProfile, it) }
        appFilters?.let { profileManager.setProfileAppFilters(currentProfile, it) }
    }
    
    /**
     * Set custom goals for weekdays and weekends
     */
    fun setCustomGoals(weekdayHours: Int, weekdayMinutes: Int, weekendHours: Int, weekendMinutes: Int) {
        customGoalsManager.setWeekdayGoal(weekdayHours, weekdayMinutes)
        customGoalsManager.setWeekendGoal(weekendHours, weekendMinutes)
    }
    
    /**
     * Set custom goal for specific day
     */
    fun setDayGoal(dayOfWeek: Int, hours: Int, minutes: Int) {
        customGoalsManager.setDayGoal(dayOfWeek, hours, minutes)
    }
    
    /**
     * Get goal for current day
     */
    fun getCurrentDayGoal(): DayGoal {
        return customGoalsManager.getCurrentDayGoal()
    }
    
    /**
     * Get goal progress for current day
     */
    fun getCurrentGoalProgress(currentUsageMs: Long): GoalProgress {
        return customGoalsManager.getGoalProgress(currentUsageMs)
    }
    
    /**
     * Set timezone
     */
    fun setTimezone(timezone: String) {
        timeZoneManager.setTimezone(timezone)
    }
    
    /**
     * Enable/disable auto-detect timezone
     */
    fun setAutoDetectTimezone(enabled: Boolean) {
        timeZoneManager.setAutoDetectEnabled(enabled)
    }
    
    /**
     * Get current time in user's timezone
     */
    fun getCurrentTimeInUserTimezone(): Date {
        return timeZoneManager.getCurrentTimeInUserTimezone()
    }
    
    /**
     * Create custom app category
     */
    fun createCustomCategory(categoryName: String, description: String = "") {
        appCategoryManager.createCustomCategory(categoryName, description)
    }
    
    /**
     * Set app category
     */
    fun setAppCategory(packageName: String, category: String) {
        appCategoryManager.setCustomCategory(packageName, category)
    }
    
    /**
     * Get app category
     */
    fun getAppCategory(packageName: String): String {
        return appCategoryManager.getAppCategory(packageName)
    }
    
    /**
     * Get all app categories
     */
    fun getAllAppCategories(): List<String> {
        return appCategoryManager.getAllCategories()
    }
    
    /**
     * Get apps in category
     */
    fun getAppsInCategory(category: String): List<AppCategoryInfo> {
        return appCategoryManager.getAppsInCategory(category)
    }
    
    /**
     * Get all profiles
     */
    fun getAllProfiles(): Map<String, ProfileInfo> {
        return profileManager.getAllProfiles()
    }
    
    /**
     * Get current profile info
     */
    fun getCurrentProfileInfo(): ProfileInfo? {
        return profileManager.getProfileInfo(profileManager.getCurrentProfile())
    }
    
    /**
     * Get profile goals
     */
    fun getCurrentProfileGoals(): ProfileGoals {
        return profileManager.getProfileGoals(profileManager.getCurrentProfile())
    }
    
    /**
     * Get profile notifications
     */
    fun getCurrentProfileNotifications(): ProfileNotifications {
        return profileManager.getProfileNotifications(profileManager.getCurrentProfile())
    }
    
    /**
     * Get profile app filters
     */
    fun getCurrentProfileAppFilters(): ProfileAppFilters {
        return profileManager.getProfileAppFilters(profileManager.getCurrentProfile())
    }
    
    /**
     * Get timezone suggestions
     */
    fun getTimezoneSuggestions(): List<String> {
        return timeZoneManager.getTimezoneSuggestions()
    }
    
    /**
     * Get all timezones
     */
    fun getAllTimezones(): Map<String, String> {
        return timeZoneManager.getAllTimezones()
    }
    
    /**
     * Get timezone info
     */
    fun getTimezoneInfo(timezone: String): TimezoneInfo {
        return timeZoneManager.getTimezoneInfo(timezone)
    }
    
    /**
     * Get category usage statistics
     */
    fun getCategoryUsageStats(): Map<String, CategoryStats> {
        return appCategoryManager.getCategoryUsageStats()
    }
    
    /**
     * Get weekly goals
     */
    fun getWeeklyGoals(): Map<Int, DayGoal> {
        return customGoalsManager.getAllWeeklyGoals()
    }
    
    /**
     * Get personalization summary
     */
    fun getPersonalizationSummary(): PersonalizationSummary {
        val currentProfile = profileManager.getCurrentProfile()
        val profileInfo = profileManager.getProfileInfo(currentProfile)
        val currentGoal = customGoalsManager.getCurrentDayGoal()
        val timezone = timeZoneManager.getCurrentTimezone()
        val categories = appCategoryManager.getAllCategories()
        
        return PersonalizationSummary(
            currentProfile = currentProfile,
            profileDescription = profileInfo?.description ?: "",
            currentGoal = currentGoal,
            timezone = timezone,
            timezoneDisplayName = timeZoneManager.getTimezoneDisplayName(timezone),
            totalCategories = categories.size,
            customCategories = appCategoryManager.getCustomCategoryNames().size,
            isCustomGoalsEnabled = customGoalsManager.isCustomGoalsEnabled(),
            isAutoDetectTimezone = timeZoneManager.isAutoDetectEnabled()
        )
    }
    
    /**
     * Export personalization settings
     */
    fun exportPersonalizationSettings(): String {
        val export = JSONObject()
        
        // Export profiles
        val profilesJson = JSONObject()
        profileManager.getAllProfiles().forEach { (name, profile) ->
            profilesJson.put(name, profile.toJson())
        }
        export.put("profiles", profilesJson)
        export.put("current_profile", profileManager.getCurrentProfile())
        
        // Export custom goals
        export.put("custom_goals_enabled", customGoalsManager.isCustomGoalsEnabled())
        export.put("weekday_goal", customGoalsManager.getWeekdayGoal().getTotalMinutes())
        export.put("weekend_goal", customGoalsManager.getWeekendGoal().getTotalMinutes())
        
        // Export timezone
        export.put("timezone", timeZoneManager.getCurrentTimezone())
        export.put("auto_detect_timezone", timeZoneManager.isAutoDetectEnabled())
        
        // Export app categories
        export.put("app_categories", appCategoryManager.exportCategoryConfig())
        
        return export.toString()
    }
    
    /**
     * Import personalization settings
     */
    fun importPersonalizationSettings(settingsJson: String): Boolean {
        return try {
            val settings = JSONObject(settingsJson)
            
            // Import profiles
            if (settings.has("profiles")) {
                val profilesJson = settings.getJSONObject("profiles")
                for (key in profilesJson.keys()) {
                    val profileJson = profilesJson.getJSONObject(key)
                    val profile = ProfileInfo.fromJson(profileJson)
                    val profiles = profileManager.getAllProfiles().toMutableMap()
                    profiles[key] = profile
                    // Save profiles (this would need a method to save all profiles)
                }
            }
            
            // Import current profile
            if (settings.has("current_profile")) {
                profileManager.setCurrentProfile(settings.getString("current_profile"))
            }
            
            // Import custom goals
            if (settings.has("custom_goals_enabled")) {
                customGoalsManager.setCustomGoalsEnabled(settings.getBoolean("custom_goals_enabled"))
            }
            
            // Import timezone
            if (settings.has("timezone")) {
                timeZoneManager.setTimezone(settings.getString("timezone"))
            }
            
            // Import app categories
            if (settings.has("app_categories")) {
                appCategoryManager.importCategoryConfig(settings.getString("app_categories"))
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Reset all personalization settings
     */
    fun resetAllPersonalization() {
        customGoalsManager.resetToDefaults()
        appCategoryManager.resetCustomCategories()
        timeZoneManager.resetTimezoneSettings()
        profileManager.resetAllProfiles()
        prefs.edit().clear().apply()
    }
    
    /**
     * Initialize default profiles
     */
    private fun initializeDefaultProfiles() {
        // This will create the default profiles defined in ProfileManager
        profileManager.createProfile(ProfileManager.WORK_PROFILE_NAME, "Optimized for work productivity")
        profileManager.createProfile(ProfileManager.PERSONAL_PROFILE_NAME, "Relaxed settings for personal use")
        profileManager.createProfile(ProfileManager.STUDY_PROFILE_NAME, "Focused settings for studying")
    }
    
    /**
     * Get personalization recommendations
     */
    fun getPersonalizationRecommendations(): List<PersonalizationRecommendation> {
        val recommendations = mutableListOf<PersonalizationRecommendation>()
        
        // Check if user has custom goals
        if (!customGoalsManager.isCustomGoalsEnabled()) {
            recommendations.add(
                PersonalizationRecommendation(
                    type = "custom_goals",
                    title = "Set Custom Goals",
                    description = "Set different goals for weekdays and weekends",
                    priority = "high"
                )
            )
        }
        
        // Check if user has multiple profiles
        if (profileManager.getAllProfiles().size <= 1) {
            recommendations.add(
                PersonalizationRecommendation(
                    type = "multiple_profiles",
                    title = "Create Multiple Profiles",
                    description = "Create separate profiles for work and personal use",
                    priority = "medium"
                )
            )
        }
        
        // Check if user has custom categories
        if (appCategoryManager.getCustomCategoryNames().isEmpty()) {
            recommendations.add(
                PersonalizationRecommendation(
                    type = "custom_categories",
                    title = "Organize Apps by Category",
                    description = "Create custom categories to better organize your apps",
                    priority = "low"
                )
            )
        }
        
        return recommendations
    }
}

data class PersonalizationSettings(
    val currentProfile: String,
    val profileInfo: ProfileInfo?,
    val customGoals: DayGoal,
    val timezone: String,
    val timezoneInfo: TimezoneInfo,
    val appCategories: List<String>,
    val isCustomGoalsEnabled: Boolean,
    val isAutoDetectTimezone: Boolean
)

data class PersonalizationSummary(
    val currentProfile: String,
    val profileDescription: String,
    val currentGoal: DayGoal,
    val timezone: String,
    val timezoneDisplayName: String,
    val totalCategories: Int,
    val customCategories: Int,
    val isCustomGoalsEnabled: Boolean,
    val isAutoDetectTimezone: Boolean
)

data class PersonalizationRecommendation(
    val type: String,
    val title: String,
    val description: String,
    val priority: String
)
