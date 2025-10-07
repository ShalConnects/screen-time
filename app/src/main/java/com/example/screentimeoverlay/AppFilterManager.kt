package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

class AppFilterManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_filters", Context.MODE_PRIVATE)
    private val packageManager: PackageManager = context.packageManager
    
    // Default categories for common app types
    private val defaultCategories = mapOf(
        "reading" to listOf(
            "com.amazon.kindle",
            "com.google.android.apps.books",
            "com.adobe.reader",
            "com.fbreader.fbreader",
            "com.overdrive.mobile.android.libby",
            "com.audible.application"
        ),
        "productivity" to listOf(
            "com.microsoft.office.excel",
            "com.microsoft.office.word",
            "com.microsoft.office.powerpoint",
            "com.google.android.apps.docs",
            "com.google.android.apps.sheets",
            "com.google.android.apps.slides",
            "com.notion.id"
        ),
        "education" to listOf(
            "com.duolingo",
            "com.khan.academy",
            "com.coursera.android",
            "com.udemy.android",
            "com.edx.mobile"
        ),
        "health" to listOf(
            "com.myfitnesspal.android",
            "com.nike.ntc",
            "com.strava",
            "com.underarmour.mapmyrun",
            "com.fitbit.FitbitMobile"
        ),
        "social" to listOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.twitter.android",
            "com.snapchat.android",
            "com.whatsapp",
            "com.telegram.messenger"
        ),
        "entertainment" to listOf(
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "com.hulu.plus",
            "com.amazon.avod.thirdpartyclient",
            "com.spotify.music",
            "com.youtube.android"
        )
    )
    
    /**
     * Get the current filter mode
     */
    fun getFilterMode(): FilterMode {
        val modeString = prefs.getString("filter_mode", FilterMode.NONE.name) ?: FilterMode.NONE.name
        return try {
            FilterMode.valueOf(modeString)
        } catch (e: Exception) {
            FilterMode.NONE
        }
    }
    
    /**
     * Set the filter mode
     */
    fun setFilterMode(mode: FilterMode) {
        prefs.edit().putString("filter_mode", mode.name).apply()
    }
    
    /**
     * Add package to whitelist
     */
    fun addToWhitelist(packageName: String) {
        val whitelist = getWhitelist().toMutableSet()
        whitelist.add(packageName)
        saveWhitelist(whitelist)
    }
    
    /**
     * Remove package from whitelist
     */
    fun removeFromWhitelist(packageName: String) {
        val whitelist = getWhitelist().toMutableSet()
        whitelist.remove(packageName)
        saveWhitelist(whitelist)
    }
    
    /**
     * Add package to blacklist
     */
    fun addToBlacklist(packageName: String) {
        val blacklist = getBlacklist().toMutableSet()
        blacklist.add(packageName)
        saveBlacklist(blacklist)
    }
    
    /**
     * Remove package from blacklist
     */
    fun removeFromBlacklist(packageName: String) {
        val blacklist = getBlacklist().toMutableSet()
        blacklist.remove(packageName)
        saveBlacklist(blacklist)
    }
    
    /**
     * Get whitelist
     */
    fun getWhitelist(): Set<String> {
        val jsonString = prefs.getString("whitelist", "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * Get blacklist
     */
    fun getBlacklist(): Set<String> {
        val jsonString = prefs.getString("blacklist", "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * Check if an app should be tracked based on current filter settings
     */
    fun shouldTrackApp(packageName: String): Boolean {
        val filterMode = getFilterMode()
        
        return when (filterMode) {
            FilterMode.NONE -> true
            FilterMode.WHITELIST_ONLY -> {
                val whitelist = getWhitelist()
                whitelist.isEmpty() || whitelist.contains(packageName)
            }
            FilterMode.BLACKLIST_EXCLUDE -> {
                val blacklist = getBlacklist()
                !blacklist.contains(packageName)
            }
            FilterMode.CATEGORY_BASED -> {
                val category = getAppCategory(packageName)
                !isExcludedCategory(category)
            }
        }
    }
    
    /**
     * Get app category based on package name
     */
    fun getAppCategory(packageName: String): String {
        for ((category, packages) in defaultCategories) {
            if (packages.any { packageName.startsWith(it) }) {
                return category
            }
        }
        return "other"
    }
    
    /**
     * Get all installed apps with their categories
     */
    fun getAllInstalledApps(): List<AppInfo> {
        val installedApps = mutableListOf<AppInfo>()
        val packages = packageManager.getInstalledPackages(0)
        
        for (packageInfo in packages) {
            if (packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                val category = getAppCategory(packageInfo.packageName)
                val isWhitelisted = getWhitelist().contains(packageInfo.packageName)
                val isBlacklisted = getBlacklist().contains(packageInfo.packageName)
                
                installedApps.add(
                    AppInfo(
                        packageName = packageInfo.packageName,
                        appName = appName,
                        category = category,
                        isWhitelisted = isWhitelisted,
                        isBlacklisted = isBlacklisted
                    )
                )
            }
        }
        
        return installedApps.sortedBy { it.appName }
    }
    
    /**
     * Add category to excluded categories
     */
    fun addExcludedCategory(category: String) {
        val excluded = getExcludedCategories().toMutableSet()
        excluded.add(category)
        saveExcludedCategories(excluded)
    }
    
    /**
     * Remove category from excluded categories
     */
    fun removeExcludedCategory(category: String) {
        val excluded = getExcludedCategories().toMutableSet()
        excluded.remove(category)
        saveExcludedCategories(excluded)
    }
    
    /**
     * Get excluded categories
     */
    fun getExcludedCategories(): Set<String> {
        val jsonString = prefs.getString("excluded_categories", "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * Check if category is excluded
     */
    private fun isExcludedCategory(category: String): Boolean {
        return getExcludedCategories().contains(category)
    }
    
    private fun saveWhitelist(whitelist: Set<String>) {
        val jsonArray = JSONArray()
        whitelist.forEach { jsonArray.put(it) }
        prefs.edit().putString("whitelist", jsonArray.toString()).apply()
    }
    
    private fun saveBlacklist(blacklist: Set<String>) {
        val jsonArray = JSONArray()
        blacklist.forEach { jsonArray.put(it) }
        prefs.edit().putString("blacklist", jsonArray.toString()).apply()
    }
    
    private fun saveExcludedCategories(categories: Set<String>) {
        val jsonArray = JSONArray()
        categories.forEach { jsonArray.put(it) }
        prefs.edit().putString("excluded_categories", jsonArray.toString()).apply()
    }
    
    /**
     * Get available categories
     */
    fun getAvailableCategories(): List<String> {
        return defaultCategories.keys.toList()
    }
    
    /**
     * Reset all filters
     */
    fun resetFilters() {
        prefs.edit().clear().apply()
    }
}

enum class FilterMode {
    NONE,           // Track all apps
    WHITELIST_ONLY, // Only track whitelisted apps
    BLACKLIST_EXCLUDE, // Track all except blacklisted
    CATEGORY_BASED  // Track based on category exclusions
}

