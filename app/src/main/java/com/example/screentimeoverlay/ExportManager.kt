package com.example.screentimeoverlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class ExportManager(private val context: Context) {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val weekFormat = SimpleDateFormat("yyyy-'W'ww", Locale.getDefault())
    
    /**
     * Export weekly summary to JSON file
     */
    fun exportWeeklySummary(weekStartDate: Date): String? {
        try {
            val weekEndDate = Calendar.getInstance().apply {
                time = weekStartDate
                add(Calendar.DAY_OF_WEEK, 6)
            }.time
            
            val weeklyData = getWeeklyData(weekStartDate, weekEndDate)
            val fileName = "screen_time_${weekFormat.format(weekStartDate)}.json"
            val file = File(getExportDirectory(), fileName)
            
            val jsonData = createWeeklyJSON(weeklyData, weekStartDate, weekEndDate)
            
            FileWriter(file).use { writer ->
                writer.write(jsonData.toString(2))
            }
            
            Log.d(TAG, "Weekly summary exported to: ${file.absolutePath}")
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting weekly summary", e)
            return null
        }
    }
    
    /**
     * Export daily summary to JSON file
     */
    fun exportDailySummary(date: Date): String? {
        try {
            val dailyData = getDailyData(date)
            val fileName = "screen_time_${dateFormat.format(date)}.json"
            val file = File(getExportDirectory(), fileName)
            
            val jsonData = createDailyJSON(dailyData, date)
            
            FileWriter(file).use { writer ->
                writer.write(jsonData.toString(2))
            }
            
            Log.d(TAG, "Daily summary exported to: ${file.absolutePath}")
            return file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting daily summary", e)
            return null
        }
    }
    
    /**
     * Share weekly summary via intent
     */
    fun shareWeeklySummary(weekStartDate: Date): Intent {
        val filePath = exportWeeklySummary(weekStartDate)
        return if (filePath != null) {
            createShareIntent(filePath, "Weekly Screen Time Summary")
        } else {
            createErrorIntent("Failed to export weekly summary")
        }
    }
    
    /**
     * Share daily summary via intent
     */
    fun shareDailySummary(date: Date): Intent {
        val filePath = exportDailySummary(date)
        return if (filePath != null) {
            createShareIntent(filePath, "Daily Screen Time Summary")
        } else {
            createErrorIntent("Failed to export daily summary")
        }
    }
    
    /**
     * Get weekly data from UsageStatsManager
     */
    private fun getWeeklyData(startDate: Date, endDate: Date): WeeklyData {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val startTime = startDate.time
        val endTime = endDate.time
        
        val usageStats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()
        
        val dailyData = mutableListOf<DailyData>()
        val appUsageMap = mutableMapOf<String, Long>()
        
        // Process each day
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        
        while (calendar.time.before(endDate) || calendar.time == endDate) {
            val dayStart = calendar.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000
            
            val dayStats = usageStats.filter { 
                it.lastTimeUsed >= dayStart && it.lastTimeUsed < dayEnd 
            }
            
            var totalTime = 0L
            val appUsages = mutableListOf<AppUsage>()
            
            dayStats.forEach { stats ->
                totalTime += stats.totalTimeInForeground
                if (stats.totalTimeInForeground > 0) {
                    val appName = getAppName(stats.packageName)
                    appUsages.add(AppUsage(stats.packageName, appName, stats.totalTimeInForeground))
                    
                    // Accumulate for weekly totals
                    appUsageMap[stats.packageName] = (appUsageMap[stats.packageName] ?: 0) + stats.totalTimeInForeground
                }
            }
            
            dailyData.add(DailyData(calendar.time, totalTime, appUsages))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Create weekly app usage list
        val weeklyAppUsages = appUsageMap.map { (packageName, totalTime) ->
            AppUsage(packageName, getAppName(packageName), totalTime)
        }.sortedByDescending { it.timeInForeground }
        
        return WeeklyData(startDate, endDate, dailyData, weeklyAppUsages)
    }
    
    /**
     * Get daily data from UsageStatsManager
     */
    private fun getDailyData(date: Date): DailyData {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val startTime = calendar.timeInMillis
        val endTime = startTime + 24 * 60 * 60 * 1000
        
        val usageStats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: emptyList()
        
        var totalTime = 0L
        val appUsages = mutableListOf<AppUsage>()
        
        usageStats.forEach { stats ->
            totalTime += stats.totalTimeInForeground
            if (stats.totalTimeInForeground > 0) {
                val appName = getAppName(stats.packageName)
                appUsages.add(AppUsage(stats.packageName, appName, stats.totalTimeInForeground))
            }
        }
        
        return DailyData(date, totalTime, appUsages)
    }
    
    /**
     * Create JSON for weekly data
     */
    private fun createWeeklyJSON(weeklyData: WeeklyData, startDate: Date, endDate: Date): JSONObject {
        val json = JSONObject()
        
        // Metadata
        json.put("type", "weekly_summary")
        json.put("start_date", dateFormat.format(startDate))
        json.put("end_date", dateFormat.format(endDate))
        json.put("exported_at", timeFormat.format(Date()))
        
        // Weekly totals
        val weeklyTotal = weeklyData.dailyData.sumOf { it.totalTime }
        json.put("total_screen_time_ms", weeklyTotal)
        json.put("total_screen_time_formatted", formatTime(weeklyTotal))
        json.put("average_daily_time_ms", weeklyTotal / 7)
        json.put("average_daily_time_formatted", formatTime(weeklyTotal / 7))
        
        // Daily breakdown
        val dailyArray = JSONArray()
        weeklyData.dailyData.forEach { daily ->
            val dayJson = JSONObject()
            dayJson.put("date", dateFormat.format(daily.date))
            dayJson.put("total_time_ms", daily.totalTime)
            dayJson.put("total_time_formatted", formatTime(daily.totalTime))
            
            val appsArray = JSONArray()
            daily.appUsages.forEach { appUsage ->
                val appJson = JSONObject()
                appJson.put("package_name", appUsage.packageName)
                appJson.put("app_name", appUsage.appName)
                appJson.put("time_ms", appUsage.timeInForeground)
                appJson.put("time_formatted", formatTime(appUsage.timeInForeground))
                appsArray.put(appJson)
            }
            dayJson.put("apps", appsArray)
            dailyArray.put(dayJson)
        }
        json.put("daily_breakdown", dailyArray)
        
        // Top apps for the week
        val topAppsArray = JSONArray()
        weeklyData.weeklyAppUsages.take(10).forEach { appUsage ->
            val appJson = JSONObject()
            appJson.put("package_name", appUsage.packageName)
            appJson.put("app_name", appUsage.appName)
            appJson.put("total_time_ms", appUsage.timeInForeground)
            appJson.put("total_time_formatted", formatTime(appUsage.timeInForeground))
            appJson.put("percentage", (appUsage.timeInForeground * 100.0 / weeklyTotal).toInt())
            topAppsArray.put(appJson)
        }
        json.put("top_apps", topAppsArray)
        
        return json
    }
    
    /**
     * Create JSON for daily data
     */
    private fun createDailyJSON(dailyData: DailyData, date: Date): JSONObject {
        val json = JSONObject()
        
        // Metadata
        json.put("type", "daily_summary")
        json.put("date", dateFormat.format(date))
        json.put("exported_at", timeFormat.format(Date()))
        
        // Daily totals
        json.put("total_screen_time_ms", dailyData.totalTime)
        json.put("total_screen_time_formatted", formatTime(dailyData.totalTime))
        
        // App breakdown
        val appsArray = JSONArray()
        dailyData.appUsages.sortedByDescending { it.timeInForeground }.forEach { appUsage ->
            val appJson = JSONObject()
            appJson.put("package_name", appUsage.packageName)
            appJson.put("app_name", appUsage.appName)
            appJson.put("time_ms", appUsage.timeInForeground)
            appJson.put("time_formatted", formatTime(appUsage.timeInForeground))
            appJson.put("percentage", (appUsage.timeInForeground * 100.0 / dailyData.totalTime).toInt())
            appsArray.put(appJson)
        }
        json.put("apps", appsArray)
        
        return json
    }
    
    /**
     * Create share intent
     */
    private fun createShareIntent(filePath: String, subject: String): Intent {
        val file = File(filePath)
        val uri = Uri.fromFile(file)
        
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "Screen Time Summary")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    /**
     * Create error intent
     */
    private fun createErrorIntent(message: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Error: $message")
        }
    }
    
    /**
     * Get app name from package name
     */
    private fun getAppName(packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
    }
    
    /**
     * Format time in milliseconds to readable format
     */
    private fun formatTime(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)
    }
    
    /**
     * Get export directory
     */
    private fun getExportDirectory(): File {
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ScreenTime")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }
    
    companion object {
        private const val TAG = "ExportManager"
    }
}

data class WeeklyData(
    val startDate: Date,
    val endDate: Date,
    val dailyData: List<DailyData>,
    val weeklyAppUsages: List<AppUsage>
)

data class DailyData(
    val date: Date,
    val totalTime: Long,
    val appUsages: List<AppUsage>
)
