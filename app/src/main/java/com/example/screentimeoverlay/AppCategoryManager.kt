package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * Enhanced app categorization system with custom categories and grouping
 */
class AppCategoryManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("app_categories", Context.MODE_PRIVATE)
    private val packageManager: PackageManager = context.packageManager
    
    // Default categories with comprehensive app lists
    private val defaultCategories = mapOf(
        "Social" to listOf(
            "com.facebook.katana",
            "com.instagram.android",
            "com.twitter.android",
            "com.snapchat.android",
            "com.whatsapp",
            "com.telegram.messenger",
            "com.discord",
            "com.linkedin.android",
            "com.pinterest",
            "com.tumblr",
            "com.reddit.frontpage",
            "com.viber.voip"
        ),
        "Productivity" to listOf(
            "com.microsoft.office.excel",
            "com.microsoft.office.word",
            "com.microsoft.office.powerpoint",
            "com.google.android.apps.docs",
            "com.google.android.apps.sheets",
            "com.google.android.apps.slides",
            "com.notion.id",
            "com.todoist",
            "com.evernote",
            "com.onenote",
            "com.trello",
            "com.asana",
            "com.slack",
            "com.microsoft.teams"
        ),
        "Education" to listOf(
            "com.duolingo",
            "com.khan.academy",
            "com.coursera.android",
            "com.udemy.android",
            "com.edx.mobile",
            "com.skillshare",
            "com.babbel",
            "com.rosetta",
            "com.quizlet",
            "com.anki",
            "com.wolframalpha"
        ),
        "Health & Fitness" to listOf(
            "com.myfitnesspal.android",
            "com.nike.ntc",
            "com.strava",
            "com.underarmour.mapmyrun",
            "com.fitbit.FitbitMobile",
            "com.headspace",
            "com.calm",
            "com.meditation",
            "com.yoga",
            "com.workout",
            "com.runtastic"
        ),
        "Entertainment" to listOf(
            "com.netflix.mediaclient",
            "com.hulu.plus",
            "com.disney.disneyplus",
            "com.amazon.avod.thirdpartyclient",
            "com.spotify.music",
            "com.google.android.apps.youtube",
            "com.twitch.tv",
            "com.tiktok",
            "com.roblox.client",
            "com.mojang.minecraftpe"
        ),
        "Reading" to listOf(
            "com.amazon.kindle",
            "com.google.android.apps.books",
            "com.adobe.reader",
            "com.fbreader.fbreader",
            "com.overdrive.mobile.android.libby",
            "com.audible.application",
            "com.medium",
            "com.pocket",
            "com.instapaper"
        ),
        "Gaming" to listOf(
            "com.roblox.client",
            "com.mojang.minecraftpe",
            "com.epicgames.fortnite",
            "com.supercell.clashofclans",
            "com.supercell.clashroyale",
            "com.king.candycrushsaga",
            "com.king.candycrushsoda",
            "com.pokemongo",
            "com.niantic.ingress"
        ),
        "Communication" to listOf(
            "com.whatsapp",
            "com.telegram.messenger",
            "com.skype.raider",
            "com.microsoft.teams",
            "com.slack",
            "com.discord",
            "com.viber.voip",
            "com.signal",
            "com.wire"
        ),
        "News" to listOf(
            "com.cnn.mobile.android.phone",
            "com.bbc.news",
            "com.nytimes.android",
            "com.washingtonpost.rainbow",
            "com.reddit.frontpage",
            "com.flipboard.app",
            "com.google.android.apps.magazines"
        ),
        "Shopping" to listOf(
            "com.amazon.mShop.android.shopping",
            "com.ebay.mobile",
            "com.alibaba.aliexpresshd",
            "com.wish",
            "com.target",
            "com.walmart.android",
            "com.bestbuy"
        ),
        "Finance" to listOf(
            "com.paypal.android.p2pmobile",
            "com.venmo",
            "com.cash.app",
            "com.mint",
            "com.ynab",
            "com.personalcapital",
            "com.robinhood"
        ),
        "Travel" to listOf(
            "com.ubercab",
            "com.lyft",
            "com.airbnb.android",
            "com.booking",
            "com.google.android.apps.travel",
            "com.tripadvisor",
            "com.waze"
        ),
        "Photography" to listOf(
            "com.instagram.android",
            "com.snapchat.android",
            "com.pinterest",
            "com.vsco",
            "com.adobe.lightroom",
            "com.google.android.apps.photos",
            "com.canon.photoprint"
        ),
        "Food & Dining" to listOf(
            "com.doordash.consumer",
            "com.ubereats",
            "com.grubhub",
            "com.postmates",
            "com.opentable",
            "com.yelp",
            "com.zomato"
        )
    )
    
    /**
     * Get app category based on package name
     */
    fun getAppCategory(packageName: String): String {
        // Check custom categories first
        val customCategory = getCustomCategory(packageName)
        if (customCategory != null) {
            return customCategory
        }
        
        // Check default categories
        for ((category, packages) in defaultCategories) {
            if (packages.any { packageName.startsWith(it) }) {
                return category
            }
        }
        
        return "Other"
    }
    
    /**
     * Set custom category for an app
     */
    fun setCustomCategory(packageName: String, category: String) {
        val customCategories = getCustomCategories().toMutableMap()
        customCategories[packageName] = category
        saveCustomCategories(customCategories)
    }
    
    /**
     * Remove custom category for an app
     */
    fun removeCustomCategory(packageName: String) {
        val customCategories = getCustomCategories().toMutableMap()
        customCategories.remove(packageName)
        saveCustomCategories(customCategories)
    }
    
    /**
     * Get custom category for an app
     */
    fun getCustomCategory(packageName: String): String? {
        val customCategories = getCustomCategories()
        return customCategories[packageName]
    }
    
    /**
     * Get all custom categories
     */
    fun getCustomCategories(): Map<String, String> {
        val jsonString = prefs.getString("custom_categories", "{}")
        return try {
            val json = JSONObject(jsonString ?: "{}")
            val categories = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                categories[key] = json.getString(key)
            }
            categories
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * Save custom categories
     */
    private fun saveCustomCategories(categories: Map<String, String>) {
        val json = JSONObject()
        categories.forEach { (packageName, category) ->
            json.put(packageName, category)
        }
        prefs.edit().putString("custom_categories", json.toString()).apply()
    }
    
    /**
     * Get all available categories
     */
    fun getAllCategories(): List<String> {
        val defaultCats = defaultCategories.keys.toList()
        val customCats = getCustomCategories().values.distinct()
        return (defaultCats + customCats).distinct().sorted()
    }
    
    /**
     * Get apps in a specific category
     */
    fun getAppsInCategory(category: String): List<AppCategoryInfo> {
        val apps = mutableListOf<AppCategoryInfo>()
        val packages = packageManager.getInstalledPackages(0)
        
        for (packageInfo in packages) {
            if (packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val appCategory = getAppCategory(packageInfo.packageName)
                if (appCategory == category) {
                    val appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                    apps.add(AppCategoryInfo(
                        packageName = packageInfo.packageName,
                        appName = appName,
                        category = appCategory,
                        isCustom = getCustomCategory(packageInfo.packageName) != null
                    ))
                }
            }
        }
        
        return apps.sortedBy { it.appName }
    }
    
    /**
     * Get all apps with their categories
     */
    fun getAllAppsWithCategories(): List<AppCategoryInfo> {
        val apps = mutableListOf<AppCategoryInfo>()
        val packages = packageManager.getInstalledPackages(0)
        
        for (packageInfo in packages) {
            if (packageInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                val category = getAppCategory(packageInfo.packageName)
                val isCustom = getCustomCategory(packageInfo.packageName) != null
                
                apps.add(AppCategoryInfo(
                    packageName = packageInfo.packageName,
                    appName = appName,
                    category = category,
                    isCustom = isCustom
                ))
            }
        }
        
        return apps.sortedBy { it.appName }
    }
    
    /**
     * Create a new custom category
     */
    fun createCustomCategory(categoryName: String, description: String = "") {
        val customCategoryNames = getCustomCategoryNames().toMutableSet()
        customCategoryNames.add(categoryName)
        saveCustomCategoryNames(customCategoryNames)
        
        if (description.isNotEmpty()) {
            setCustomCategoryDescription(categoryName, description)
        }
    }
    
    /**
     * Delete a custom category
     */
    fun deleteCustomCategory(categoryName: String) {
        val customCategoryNames = getCustomCategoryNames().toMutableSet()
        customCategoryNames.remove(categoryName)
        saveCustomCategoryNames(customCategoryNames)
        
        // Remove description
        prefs.edit().remove("category_desc_$categoryName").apply()
        
        // Move apps from this category to "Other"
        val customCategories = getCustomCategories().toMutableMap()
        customCategories.entries.removeAll { it.value == categoryName }
        saveCustomCategories(customCategories)
    }
    
    /**
     * Get custom category names
     */
    fun getCustomCategoryNames(): Set<String> {
        val jsonString = prefs.getString("custom_category_names", "[]")
        return try {
            val jsonArray = JSONArray(jsonString ?: "[]")
            val names = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                names.add(jsonArray.getString(i))
            }
            names
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * Save custom category names
     */
    private fun saveCustomCategoryNames(names: Set<String>) {
        val jsonArray = JSONArray()
        names.forEach { jsonArray.put(it) }
        prefs.edit().putString("custom_category_names", jsonArray.toString()).apply()
    }
    
    /**
     * Set description for a custom category
     */
    fun setCustomCategoryDescription(categoryName: String, description: String) {
        prefs.edit().putString("category_desc_$categoryName", description).apply()
    }
    
    /**
     * Get description for a custom category
     */
    fun getCustomCategoryDescription(categoryName: String): String {
        return prefs.getString("category_desc_$categoryName", "") ?: ""
    }
    
    /**
     * Get category usage statistics
     */
    fun getCategoryUsageStats(): Map<String, CategoryStats> {
        val stats = mutableMapOf<String, CategoryStats>()
        val allCategories = getAllCategories()
        
        for (category in allCategories) {
            val apps = getAppsInCategory(category)
            val customApps = apps.count { it.isCustom }
            val defaultApps = apps.count { !it.isCustom }
            
            stats[category] = CategoryStats(
                categoryName = category,
                totalApps = apps.size,
                customApps = customApps,
                defaultApps = defaultApps,
                isCustom = category in getCustomCategoryNames()
            )
        }
        
        return stats
    }
    
    /**
     * Reset all custom categories
     */
    fun resetCustomCategories() {
        prefs.edit()
            .remove("custom_categories")
            .remove("custom_category_names")
            .apply()
        
        // Remove all category descriptions
        val customNames = getCustomCategoryNames()
        customNames.forEach { name ->
            prefs.edit().remove("category_desc_$name").apply()
        }
    }
    
    /**
     * Export category configuration
     */
    fun exportCategoryConfig(): String {
        val config = JSONObject()
        config.put("custom_categories", JSONObject(getCustomCategories()))
        config.put("custom_category_names", JSONArray(getCustomCategoryNames()))
        
        val customNames = getCustomCategoryNames()
        val descriptions = JSONObject()
        customNames.forEach { name ->
            descriptions.put(name, getCustomCategoryDescription(name))
        }
        config.put("category_descriptions", descriptions)
        
        return config.toString()
    }
    
    /**
     * Import category configuration
     */
    fun importCategoryConfig(configJson: String) {
        try {
            val config = JSONObject(configJson)
            
            // Import custom categories
            if (config.has("custom_categories")) {
                val customCategories = config.getJSONObject("custom_categories")
                val categories = mutableMapOf<String, String>()
                customCategories.keys().forEach { key ->
                    categories[key] = customCategories.getString(key)
                }
                saveCustomCategories(categories)
            }
            
            // Import custom category names
            if (config.has("custom_category_names")) {
                val namesArray = config.getJSONArray("custom_category_names")
                val names = mutableSetOf<String>()
                for (i in 0 until namesArray.length()) {
                    names.add(namesArray.getString(i))
                }
                saveCustomCategoryNames(names)
            }
            
            // Import category descriptions
            if (config.has("category_descriptions")) {
                val descriptions = config.getJSONObject("category_descriptions")
                descriptions.keys().forEach { key ->
                    setCustomCategoryDescription(key, descriptions.getString(key))
                }
            }
        } catch (e: Exception) {
            // Handle import error
        }
    }
}

data class AppCategoryInfo(
    val packageName: String,
    val appName: String,
    val category: String,
    val isCustom: Boolean
)

data class CategoryStats(
    val categoryName: String,
    val totalApps: Int,
    val customApps: Int,
    val defaultApps: Int,
    val isCustom: Boolean
)
