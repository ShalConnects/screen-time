package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages multiple user profiles for work/personal use with separate settings
 */
class ProfileManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("user_profiles", Context.MODE_PRIVATE)
    
    companion object {
        const val DEFAULT_PROFILE_NAME = "Default"
        const val WORK_PROFILE_NAME = "Work"
        const val PERSONAL_PROFILE_NAME = "Personal"
        const val STUDY_PROFILE_NAME = "Study"
    }
    
    /**
     * Get current active profile
     */
    fun getCurrentProfile(): String {
        return prefs.getString("current_profile", DEFAULT_PROFILE_NAME) ?: DEFAULT_PROFILE_NAME
    }
    
    /**
     * Set current active profile
     */
    fun setCurrentProfile(profileName: String) {
        if (profileExists(profileName)) {
            prefs.edit().putString("current_profile", profileName).apply()
        }
    }
    
    /**
     * Create a new profile
     */
    fun createProfile(profileName: String, description: String = ""): Boolean {
        if (profileExists(profileName)) {
            return false
        }
        
        val profiles = getAllProfiles().toMutableMap()
        profiles[profileName] = ProfileInfo(
            name = profileName,
            description = description,
            createdAt = System.currentTimeMillis(),
            isDefault = false,
            settings = getDefaultProfileSettings()
        )
        
        saveAllProfiles(profiles)
        return true
    }
    
    /**
     * Delete a profile
     */
    fun deleteProfile(profileName: String): Boolean {
        if (profileName == DEFAULT_PROFILE_NAME || !profileExists(profileName)) {
            return false
        }
        
        val profiles = getAllProfiles().toMutableMap()
        profiles.remove(profileName)
        saveAllProfiles(profiles)
        
        // If deleted profile was current, switch to default
        if (getCurrentProfile() == profileName) {
            setCurrentProfile(DEFAULT_PROFILE_NAME)
        }
        
        return true
    }
    
    /**
     * Check if profile exists
     */
    fun profileExists(profileName: String): Boolean {
        return getAllProfiles().containsKey(profileName)
    }
    
    /**
     * Get all profiles
     */
    fun getAllProfiles(): Map<String, ProfileInfo> {
        val jsonString = prefs.getString("all_profiles", "{}")
        return try {
            val json = JSONObject(jsonString ?: "{}")
            val profiles = mutableMapOf<String, ProfileInfo>()
            json.keys().forEach { key ->
                val profileJson = json.getJSONObject(key)
                profiles[key] = ProfileInfo.fromJson(profileJson)
            }
            profiles
        } catch (e: Exception) {
            // Return default profiles if parsing fails
            getDefaultProfiles()
        }
    }
    
    /**
     * Get profile info
     */
    fun getProfileInfo(profileName: String): ProfileInfo? {
        return getAllProfiles()[profileName]
    }
    
    /**
     * Update profile info
     */
    fun updateProfileInfo(profileName: String, profileInfo: ProfileInfo) {
        if (profileExists(profileName)) {
            val profiles = getAllProfiles().toMutableMap()
            profiles[profileName] = profileInfo
            saveAllProfiles(profiles)
        }
    }
    
    /**
     * Get profile setting
     */
    fun getProfileSetting(profileName: String, key: String, defaultValue: Any): Any {
        val profile = getProfileInfo(profileName) ?: return defaultValue
        return profile.settings[key] ?: defaultValue
    }
    
    /**
     * Set profile setting
     */
    fun setProfileSetting(profileName: String, key: String, value: Any) {
        if (profileExists(profileName)) {
            val profile = getProfileInfo(profileName) ?: return
            val updatedSettings = profile.settings.toMutableMap()
            updatedSettings[key] = value
            
            val updatedProfile = profile.copy(settings = updatedSettings)
            updateProfileInfo(profileName, updatedProfile)
        }
    }
    
    /**
     * Get current profile setting
     */
    fun getCurrentProfileSetting(key: String, defaultValue: Any): Any {
        return getProfileSetting(getCurrentProfile(), key, defaultValue)
    }
    
    /**
     * Set current profile setting
     */
    fun setCurrentProfileSetting(key: String, value: Any) {
        setProfileSetting(getCurrentProfile(), key, value)
    }
    
    /**
     * Get profile goals
     */
    fun getProfileGoals(profileName: String): ProfileGoals {
        val profile = getProfileInfo(profileName) ?: return getDefaultProfileGoals()
        
        return ProfileGoals(
            dailyGoalHours = profile.settings["daily_goal_hours"] as? Int ?: 8,
            dailyGoalMinutes = profile.settings["daily_goal_minutes"] as? Int ?: 0,
            weeklyGoalHours = profile.settings["weekly_goal_hours"] as? Int ?: 40,
            weeklyGoalMinutes = profile.settings["weekly_goal_minutes"] as? Int ?: 0,
            breakInterval = profile.settings["break_interval"] as? Int ?: 25,
            maxSessionLength = profile.settings["max_session_length"] as? Int ?: 120
        )
    }
    
    /**
     * Set profile goals
     */
    fun setProfileGoals(profileName: String, goals: ProfileGoals) {
        setProfileSetting(profileName, "daily_goal_hours", goals.dailyGoalHours)
        setProfileSetting(profileName, "daily_goal_minutes", goals.dailyGoalMinutes)
        setProfileSetting(profileName, "weekly_goal_hours", goals.weeklyGoalHours)
        setProfileSetting(profileName, "weekly_goal_minutes", goals.weeklyGoalMinutes)
        setProfileSetting(profileName, "break_interval", goals.breakInterval)
        setProfileSetting(profileName, "max_session_length", goals.maxSessionLength)
    }
    
    /**
     * Get profile notifications
     */
    fun getProfileNotifications(profileName: String): ProfileNotifications {
        val profile = getProfileInfo(profileName) ?: return getDefaultProfileNotifications()
        
        return ProfileNotifications(
            remindersEnabled = profile.settings["reminders_enabled"] as? Boolean ?: true,
            breakSuggestionsEnabled = profile.settings["break_suggestions_enabled"] as? Boolean ?: true,
            goalCelebrationsEnabled = profile.settings["goal_celebrations_enabled"] as? Boolean ?: true,
            quietHoursEnabled = profile.settings["quiet_hours_enabled"] as? Boolean ?: false,
            quietHoursStart = profile.settings["quiet_hours_start"] as? Int ?: 22,
            quietHoursEnd = profile.settings["quiet_hours_end"] as? Int ?: 7
        )
    }
    
    /**
     * Set profile notifications
     */
    fun setProfileNotifications(profileName: String, notifications: ProfileNotifications) {
        setProfileSetting(profileName, "reminders_enabled", notifications.remindersEnabled)
        setProfileSetting(profileName, "break_suggestions_enabled", notifications.breakSuggestionsEnabled)
        setProfileSetting(profileName, "goal_celebrations_enabled", notifications.goalCelebrationsEnabled)
        setProfileSetting(profileName, "quiet_hours_enabled", notifications.quietHoursEnabled)
        setProfileSetting(profileName, "quiet_hours_start", notifications.quietHoursStart)
        setProfileSetting(profileName, "quiet_hours_end", notifications.quietHoursEnd)
    }
    
    /**
     * Get profile app filters
     */
    fun getProfileAppFilters(profileName: String): ProfileAppFilters {
        val profile = getProfileInfo(profileName) ?: return getDefaultProfileAppFilters()
        
        return ProfileAppFilters(
            whitelist = profile.settings["whitelist"] as? List<String> ?: emptyList(),
            blacklist = profile.settings["blacklist"] as? List<String> ?: emptyList(),
            excludedCategories = profile.settings["excluded_categories"] as? List<String> ?: emptyList(),
            filterMode = profile.settings["filter_mode"] as? String ?: "NONE"
        )
    }
    
    /**
     * Set profile app filters
     */
    fun setProfileAppFilters(profileName: String, filters: ProfileAppFilters) {
        setProfileSetting(profileName, "whitelist", filters.whitelist)
        setProfileSetting(profileName, "blacklist", filters.blacklist)
        setProfileSetting(profileName, "excluded_categories", filters.excludedCategories)
        setProfileSetting(profileName, "filter_mode", filters.filterMode)
    }
    
    /**
     * Duplicate a profile
     */
    fun duplicateProfile(originalName: String, newName: String): Boolean {
        if (!profileExists(originalName) || profileExists(newName)) {
            return false
        }
        
        val originalProfile = getProfileInfo(originalName) ?: return false
        val duplicatedProfile = originalProfile.copy(
            name = newName,
            description = "${originalProfile.description} (Copy)",
            createdAt = System.currentTimeMillis()
        )
        
        val profiles = getAllProfiles().toMutableMap()
        profiles[newName] = duplicatedProfile
        saveAllProfiles(profiles)
        
        return true
    }
    
    /**
     * Export profile
     */
    fun exportProfile(profileName: String): String? {
        val profile = getProfileInfo(profileName) ?: return null
        return profile.toJson().toString()
    }
    
    /**
     * Import profile
     */
    fun importProfile(profileJson: String): Boolean {
        return try {
            val profile = ProfileInfo.fromJson(JSONObject(profileJson))
            val profiles = getAllProfiles().toMutableMap()
            profiles[profile.name] = profile
            saveAllProfiles(profiles)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get default profiles
     */
    private fun getDefaultProfiles(): Map<String, ProfileInfo> {
        val profiles = mutableMapOf<String, ProfileInfo>()
        
        // Default profile
        profiles[DEFAULT_PROFILE_NAME] = ProfileInfo(
            name = DEFAULT_PROFILE_NAME,
            description = "Default profile with balanced settings",
            createdAt = System.currentTimeMillis(),
            isDefault = true,
            settings = getDefaultProfileSettings()
        )
        
        // Work profile
        profiles[WORK_PROFILE_NAME] = ProfileInfo(
            name = WORK_PROFILE_NAME,
            description = "Optimized for work productivity",
            createdAt = System.currentTimeMillis(),
            isDefault = false,
            settings = getWorkProfileSettings()
        )
        
        // Personal profile
        profiles[PERSONAL_PROFILE_NAME] = ProfileInfo(
            name = PERSONAL_PROFILE_NAME,
            description = "Relaxed settings for personal use",
            createdAt = System.currentTimeMillis(),
            isDefault = false,
            settings = getPersonalProfileSettings()
        )
        
        // Study profile
        profiles[STUDY_PROFILE_NAME] = ProfileInfo(
            name = STUDY_PROFILE_NAME,
            description = "Focused settings for studying",
            createdAt = System.currentTimeMillis(),
            isDefault = false,
            settings = getStudyProfileSettings()
        )
        
        return profiles
    }
    
    /**
     * Get default profile settings
     */
    private fun getDefaultProfileSettings(): Map<String, Any> {
        return mapOf(
            "daily_goal_hours" to 8,
            "daily_goal_minutes" to 0,
            "weekly_goal_hours" to 40,
            "weekly_goal_minutes" to 0,
            "break_interval" to 25,
            "max_session_length" to 120,
            "reminders_enabled" to true,
            "break_suggestions_enabled" to true,
            "goal_celebrations_enabled" to true,
            "quiet_hours_enabled" to false,
            "quiet_hours_start" to 22,
            "quiet_hours_end" to 7,
            "whitelist" to emptyList<String>(),
            "blacklist" to emptyList<String>(),
            "excluded_categories" to emptyList<String>(),
            "filter_mode" to "NONE"
        )
    }
    
    /**
     * Get work profile settings
     */
    private fun getWorkProfileSettings(): Map<String, Any> {
        return mapOf(
            "daily_goal_hours" to 9,
            "daily_goal_minutes" to 0,
            "weekly_goal_hours" to 45,
            "weekly_goal_minutes" to 0,
            "break_interval" to 25,
            "max_session_length" to 90,
            "reminders_enabled" to true,
            "break_suggestions_enabled" to true,
            "goal_celebrations_enabled" to true,
            "quiet_hours_enabled" to true,
            "quiet_hours_start" to 18,
            "quiet_hours_end" to 8,
            "whitelist" to listOf("com.microsoft.office", "com.google.android.apps.docs", "com.slack"),
            "blacklist" to listOf("com.facebook.katana", "com.instagram.android", "com.twitter.android"),
            "excluded_categories" to listOf("Gaming", "Entertainment"),
            "filter_mode" to "BLACKLIST_EXCLUDE"
        )
    }
    
    /**
     * Get personal profile settings
     */
    private fun getPersonalProfileSettings(): Map<String, Any> {
        return mapOf(
            "daily_goal_hours" to 6,
            "daily_goal_minutes" to 0,
            "weekly_goal_hours" to 30,
            "weekly_goal_minutes" to 0,
            "break_interval" to 30,
            "max_session_length" to 150,
            "reminders_enabled" to false,
            "break_suggestions_enabled" to false,
            "goal_celebrations_enabled" to true,
            "quiet_hours_enabled" to false,
            "quiet_hours_start" to 23,
            "quiet_hours_end" to 9,
            "whitelist" to emptyList<String>(),
            "blacklist" to emptyList<String>(),
            "excluded_categories" to emptyList<String>(),
            "filter_mode" to "NONE"
        )
    }
    
    /**
     * Get study profile settings
     */
    private fun getStudyProfileSettings(): Map<String, Any> {
        return mapOf(
            "daily_goal_hours" to 7,
            "daily_goal_minutes" to 0,
            "weekly_goal_hours" to 35,
            "weekly_goal_minutes" to 0,
            "break_interval" to 20,
            "max_session_length" to 60,
            "reminders_enabled" to true,
            "break_suggestions_enabled" to true,
            "goal_celebrations_enabled" to true,
            "quiet_hours_enabled" to true,
            "quiet_hours_start" to 22,
            "quiet_hours_end" to 7,
            "whitelist" to listOf("com.duolingo", "com.khan.academy", "com.coursera.android"),
            "blacklist" to listOf("com.facebook.katana", "com.instagram.android", "com.snapchat.android"),
            "excluded_categories" to listOf("Gaming", "Entertainment", "Social"),
            "filter_mode" to "BLACKLIST_EXCLUDE"
        )
    }
    
    /**
     * Get default profile goals
     */
    private fun getDefaultProfileGoals(): ProfileGoals {
        return ProfileGoals(8, 0, 40, 0, 25, 120)
    }
    
    /**
     * Get default profile notifications
     */
    private fun getDefaultProfileNotifications(): ProfileNotifications {
        return ProfileNotifications(true, true, true, false, 22, 7)
    }
    
    /**
     * Get default profile app filters
     */
    private fun getDefaultProfileAppFilters(): ProfileAppFilters {
        return ProfileAppFilters(emptyList(), emptyList(), emptyList(), "NONE")
    }
    
    /**
     * Save all profiles
     */
    private fun saveAllProfiles(profiles: Map<String, ProfileInfo>) {
        val json = JSONObject()
        profiles.forEach { (name, profile) ->
            json.put(name, profile.toJson())
        }
        prefs.edit().putString("all_profiles", json.toString()).apply()
    }
    
    /**
     * Reset all profiles to defaults
     */
    fun resetAllProfiles() {
        prefs.edit().clear().apply()
    }
}

data class ProfileInfo(
    val name: String,
    val description: String,
    val createdAt: Long,
    val isDefault: Boolean,
    val settings: Map<String, Any>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("name", name)
        json.put("description", description)
        json.put("createdAt", createdAt)
        json.put("isDefault", isDefault)
        
        val settingsJson = JSONObject()
        settings.forEach { (key, value) ->
            when (value) {
                is String -> settingsJson.put(key, value)
                is Int -> settingsJson.put(key, value)
                is Boolean -> settingsJson.put(key, value)
                is List<*> -> {
                    val array = JSONArray()
                    value.forEach { item -> array.put(item) }
                    settingsJson.put(key, array)
                }
            }
        }
        json.put("settings", settingsJson)
        
        return json
    }
    
    companion object {
        fun fromJson(json: JSONObject): ProfileInfo {
            val settingsJson = json.getJSONObject("settings")
            val settings = mutableMapOf<String, Any>()
            
            settingsJson.keys().forEach { key ->
                val value = settingsJson.get(key)
                when (value) {
                    is String -> settings[key] = value
                    is Int -> settings[key] = value
                    is Boolean -> settings[key] = value
                    is JSONArray -> {
                        val list = mutableListOf<String>()
                        for (i in 0 until value.length()) {
                            list.add(value.getString(i))
                        }
                        settings[key] = list
                    }
                }
            }
            
            return ProfileInfo(
                name = json.getString("name"),
                description = json.getString("description"),
                createdAt = json.getLong("createdAt"),
                isDefault = json.getBoolean("isDefault"),
                settings = settings
            )
        }
    }
}

data class ProfileGoals(
    val dailyGoalHours: Int,
    val dailyGoalMinutes: Int,
    val weeklyGoalHours: Int,
    val weeklyGoalMinutes: Int,
    val breakInterval: Int,
    val maxSessionLength: Int
)

data class ProfileNotifications(
    val remindersEnabled: Boolean,
    val breakSuggestionsEnabled: Boolean,
    val goalCelebrationsEnabled: Boolean,
    val quietHoursEnabled: Boolean,
    val quietHoursStart: Int,
    val quietHoursEnd: Int
)

data class ProfileAppFilters(
    val whitelist: List<String>,
    val blacklist: List<String>,
    val excludedCategories: List<String>,
    val filterMode: String
)
