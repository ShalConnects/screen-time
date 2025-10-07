package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Manages historical data for weekly and monthly views
 */
class HistoricalDataManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("historical_data", Context.MODE_PRIVATE)
    private val sessionTracker: SessionTracker = SessionTracker(context)
    private val productivityScorer: ProductivityScorer = ProductivityScorer(context)
    private val usagePatternAnalyzer: UsagePatternAnalyzer = UsagePatternAnalyzer(context)
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val weekFormat = SimpleDateFormat("yyyy-'W'ww", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
    
    /**
     * Get weekly summary data
     */
    fun getWeeklySummary(weekStartDate: Date): WeeklySummary {
        val calendar = Calendar.getInstance()
        calendar.time = weekStartDate
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val weekStart = calendar.time
        val weekEnd = Calendar.getInstance().apply {
            time = weekStart
            add(Calendar.DAY_OF_WEEK, 6)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.time
        
        val sessions = sessionTracker.getSessionsForDateRange(weekStart, weekEnd)
        val dailySummaries = mutableListOf<DailySummary>()
        
        // Process each day of the week
        val dayCalendar = Calendar.getInstance()
        dayCalendar.time = weekStart
        
        for (i in 0..6) {
            val daySessions = sessions.filter { session ->
                val sessionCalendar = Calendar.getInstance()
                sessionCalendar.timeInMillis = session.startTime
                sessionCalendar.get(Calendar.DAY_OF_YEAR) == dayCalendar.get(Calendar.DAY_OF_YEAR) &&
                sessionCalendar.get(Calendar.YEAR) == dayCalendar.get(Calendar.YEAR)
            }
            
            val dailySummary = createDailySummary(dayCalendar.time, daySessions)
            dailySummaries.add(dailySummary)
            dayCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Calculate weekly totals and insights
        val totalTime = dailySummaries.sumOf { it.totalTime }
        val totalSessions = dailySummaries.sumOf { it.sessionCount }
        val averageDailyTime = if (dailySummaries.isNotEmpty()) totalTime / dailySummaries.size else 0L
        val mostActiveDay = dailySummaries.maxByOrNull { it.totalTime }?.date ?: weekStart
        val leastActiveDay = dailySummaries.minByOrNull { it.totalTime }?.date ?: weekStart
        
        // Calculate productivity metrics
        val allAppUsages = sessions.map { session ->
            AppUsage(session.packageName, session.appName, session.totalTime)
        }
        val productivityScore = productivityScorer.calculateDailyProductivityScore(allAppUsages)
        
        // Calculate focus metrics
        val longSessions = sessions.count { it.totalTime >= TimeUnit.MINUTES.toMillis(15) }
        val focusScore = if (totalSessions > 0) (longSessions * 100) / totalSessions else 0
        
        return WeeklySummary(
            weekStart = weekStart,
            weekEnd = weekEnd,
            dailySummaries = dailySummaries,
            totalTime = totalTime,
            totalSessions = totalSessions,
            averageDailyTime = averageDailyTime,
            mostActiveDay = mostActiveDay,
            leastActiveDay = leastActiveDay,
            productivityScore = productivityScore,
            focusScore = focusScore,
            topApps = getTopAppsForWeek(sessions)
        )
    }
    
    /**
     * Get monthly summary data
     */
    fun getMonthlySummary(monthStartDate: Date): MonthlySummary {
        val calendar = Calendar.getInstance()
        calendar.time = monthStartDate
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val monthStart = calendar.time
        val monthEnd = Calendar.getInstance().apply {
            time = monthStart
            add(Calendar.MONTH, 1)
            add(Calendar.DAY_OF_MONTH, -1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.time
        
        val sessions = sessionTracker.getSessionsForDateRange(monthStart, monthEnd)
        val weeklySummaries = mutableListOf<WeeklySummary>()
        
        // Process each week of the month
        val weekCalendar = Calendar.getInstance()
        weekCalendar.time = monthStart
        weekCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        while (weekCalendar.time.before(monthEnd) || weekCalendar.time == monthEnd) {
            val weekStart = weekCalendar.time
            val weekEnd = Calendar.getInstance().apply {
                time = weekStart
                add(Calendar.DAY_OF_WEEK, 6)
            }.time
            
            val weekSessions = sessions.filter { session ->
                session.startTime >= weekStart.time && session.startTime <= weekEnd.time
            }
            
            if (weekSessions.isNotEmpty()) {
                val weeklySummary = createWeeklySummaryFromSessions(weekStart, weekEnd, weekSessions)
                weeklySummaries.add(weeklySummary)
            }
            
            weekCalendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        
        // Calculate monthly totals and insights
        val totalTime = weeklySummaries.sumOf { it.totalTime }
        val totalSessions = weeklySummaries.sumOf { it.totalSessions }
        val averageWeeklyTime = if (weeklySummaries.isNotEmpty()) totalTime / weeklySummaries.size else 0L
        
        // Calculate trends
        val timeTrend = calculateTimeTrend(weeklySummaries)
        val productivityTrend = calculateProductivityTrend(weeklySummaries)
        
        // Get top apps for the month
        val topApps = getTopAppsForMonth(sessions)
        
        return MonthlySummary(
            monthStart = monthStart,
            monthEnd = monthEnd,
            weeklySummaries = weeklySummaries,
            totalTime = totalTime,
            totalSessions = totalSessions,
            averageWeeklyTime = averageWeeklyTime,
            timeTrend = timeTrend,
            productivityTrend = productivityTrend,
            topApps = topApps
        )
    }
    
    /**
     * Get historical chart data for visualization
     */
    fun getChartData(dateRange: DateRange, chartType: ChartType): ChartData {
        val sessions = sessionTracker.getSessionsForDateRange(dateRange.startDate, dateRange.endDate)
        
        return when (chartType) {
            ChartType.DAILY_USAGE -> createDailyUsageChart(dateRange, sessions)
            ChartType.HOURLY_PATTERNS -> createHourlyPatternsChart(sessions)
            ChartType.PRODUCTIVITY_TRENDS -> createProductivityTrendsChart(dateRange, sessions)
            ChartType.APP_CATEGORIES -> createAppCategoriesChart(sessions)
            ChartType.FOCUS_SCORES -> createFocusScoresChart(dateRange, sessions)
        }
    }
    
    /**
     * Create daily usage chart data
     */
    private fun createDailyUsageChart(dateRange: DateRange, sessions: List<AppSession>): ChartData {
        val calendar = Calendar.getInstance()
        calendar.time = dateRange.startDate
        
        val dataPoints = mutableListOf<ChartDataPoint>()
        
        while (calendar.time.before(dateRange.endDate) || calendar.time == dateRange.endDate) {
            val daySessions = sessions.filter { session ->
                val sessionCalendar = Calendar.getInstance()
                sessionCalendar.timeInMillis = session.startTime
                sessionCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                sessionCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
            }
            
            val totalTime = daySessions.sumOf { it.totalTime }
            val sessionCount = daySessions.size
            
            dataPoints.add(ChartDataPoint(
                label = dateFormat.format(calendar.time),
                value = totalTime,
                secondaryValue = sessionCount.toLong(),
                date = calendar.time
            ))
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        return ChartData(
            type = ChartType.DAILY_USAGE,
            title = "Daily Usage",
            dataPoints = dataPoints,
            xAxisLabel = "Date",
            yAxisLabel = "Time (hours)",
            secondaryYAxisLabel = "Sessions"
        )
    }
    
    /**
     * Create hourly patterns chart data
     */
    private fun createHourlyPatternsChart(sessions: List<AppSession>): ChartData {
        val hourlyUsage = mutableMapOf<Int, Long>()
        
        sessions.forEach { session ->
            val sessionCalendar = Calendar.getInstance()
            sessionCalendar.timeInMillis = session.startTime
            val hour = sessionCalendar.get(Calendar.HOUR_OF_DAY)
            hourlyUsage[hour] = (hourlyUsage[hour] ?: 0) + session.totalTime
        }
        
        val dataPoints = (0..23).map { hour ->
            ChartDataPoint(
                label = String.format("%02d:00", hour),
                value = hourlyUsage[hour] ?: 0,
                secondaryValue = 0,
                date = null
            )
        }
        
        return ChartData(
            type = ChartType.HOURLY_PATTERNS,
            title = "Hourly Usage Patterns",
            dataPoints = dataPoints,
            xAxisLabel = "Hour",
            yAxisLabel = "Time (minutes)",
            secondaryYAxisLabel = null
        )
    }
    
    /**
     * Create productivity trends chart data
     */
    private fun createProductivityTrendsChart(dateRange: DateRange, sessions: List<AppSession>): ChartData {
        val calendar = Calendar.getInstance()
        calendar.time = dateRange.startDate
        
        val dataPoints = mutableListOf<ChartDataPoint>()
        
        while (calendar.time.before(dateRange.endDate) || calendar.time == dateRange.endDate) {
            val daySessions = sessions.filter { session ->
                val sessionCalendar = Calendar.getInstance()
                sessionCalendar.timeInMillis = session.startTime
                sessionCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                sessionCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
            }
            
            val appUsages = daySessions.map { session ->
                AppUsage(session.packageName, session.appName, session.totalTime)
            }
            
            val productivityScore = productivityScorer.calculateDailyProductivityScore(appUsages)
            
            dataPoints.add(ChartDataPoint(
                label = dateFormat.format(calendar.time),
                value = productivityScore.overallScore.toLong(),
                secondaryValue = productivityScore.productiveTime,
                date = calendar.time
            ))
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        return ChartData(
            type = ChartType.PRODUCTIVITY_TRENDS,
            title = "Productivity Trends",
            dataPoints = dataPoints,
            xAxisLabel = "Date",
            yAxisLabel = "Productivity Score",
            secondaryYAxisLabel = "Productive Time (ms)"
        )
    }
    
    /**
     * Create app categories chart data
     */
    private fun createAppCategoriesChart(sessions: List<AppSession>): ChartData {
        val categoryUsage = mutableMapOf<String, Long>()
        
        sessions.forEach { session ->
            val category = productivityScorer.getProductivityCategory(
                productivityScorer.getProductivityScore(session.packageName)
            ).displayName
            
            categoryUsage[category] = (categoryUsage[category] ?: 0) + session.totalTime
        }
        
        val dataPoints = categoryUsage.entries.map { (category, time) ->
            ChartDataPoint(
                label = category,
                value = time,
                secondaryValue = 0,
                date = null
            )
        }.sortedByDescending { it.value }
        
        return ChartData(
            type = ChartType.APP_CATEGORIES,
            title = "Usage by Category",
            dataPoints = dataPoints,
            xAxisLabel = "Category",
            yAxisLabel = "Time (minutes)",
            secondaryYAxisLabel = null
        )
    }
    
    /**
     * Create focus scores chart data
     */
    private fun createFocusScoresChart(dateRange: DateRange, sessions: List<AppSession>): ChartData {
        val calendar = Calendar.getInstance()
        calendar.time = dateRange.startDate
        
        val dataPoints = mutableListOf<ChartDataPoint>()
        
        while (calendar.time.before(dateRange.endDate) || calendar.time == dateRange.endDate) {
            val daySessions = sessions.filter { session ->
                val sessionCalendar = Calendar.getInstance()
                sessionCalendar.timeInMillis = session.startTime
                sessionCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                sessionCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
            }
            
            val totalSessions = daySessions.size
            val longSessions = daySessions.count { it.totalTime >= TimeUnit.MINUTES.toMillis(15) }
            val focusScore = if (totalSessions > 0) (longSessions * 100) / totalSessions else 0
            
            dataPoints.add(ChartDataPoint(
                label = dateFormat.format(calendar.time),
                value = focusScore.toLong(),
                secondaryValue = longSessions.toLong(),
                date = calendar.time
            ))
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        return ChartData(
            type = ChartType.FOCUS_SCORES,
            title = "Focus Scores",
            dataPoints = dataPoints,
            xAxisLabel = "Date",
            yAxisLabel = "Focus Score (%)",
            secondaryYAxisLabel = "Long Sessions"
        )
    }
    
    /**
     * Helper methods
     */
    private fun createDailySummary(date: Date, sessions: List<AppSession>): DailySummary {
        val totalTime = sessions.sumOf { it.totalTime }
        val sessionCount = sessions.size
        val averageSessionTime = if (sessionCount > 0) totalTime / sessionCount else 0L
        
        val appUsages = sessions.map { session ->
            AppUsage(session.packageName, session.appName, session.totalTime)
        }
        val productivityScore = productivityScorer.calculateDailyProductivityScore(appUsages)
        
        val longSessions = sessions.count { it.totalTime >= TimeUnit.MINUTES.toMillis(15) }
        val focusScore = if (sessionCount > 0) (longSessions * 100) / sessionCount else 0
        
        return DailySummary(
            date = date,
            totalTime = totalTime,
            sessionCount = sessionCount,
            averageSessionTime = averageSessionTime,
            productivityScore = productivityScore,
            focusScore = focusScore,
            topApps = getTopAppsForDay(sessions)
        )
    }
    
    private fun createWeeklySummaryFromSessions(weekStart: Date, weekEnd: Date, sessions: List<AppSession>): WeeklySummary {
        val dailySummaries = mutableListOf<DailySummary>()
        val calendar = Calendar.getInstance()
        calendar.time = weekStart
        
        for (i in 0..6) {
            val daySessions = sessions.filter { session ->
                val sessionCalendar = Calendar.getInstance()
                sessionCalendar.timeInMillis = session.startTime
                sessionCalendar.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR) &&
                sessionCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)
            }
            
            val dailySummary = createDailySummary(calendar.time, daySessions)
            dailySummaries.add(dailySummary)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        val totalTime = dailySummaries.sumOf { it.totalTime }
        val totalSessions = dailySummaries.sumOf { it.sessionCount }
        val averageDailyTime = if (dailySummaries.isNotEmpty()) totalTime / dailySummaries.size else 0L
        
        val appUsages = sessions.map { session ->
            AppUsage(session.packageName, session.appName, session.totalTime)
        }
        val productivityScore = productivityScorer.calculateDailyProductivityScore(appUsages)
        
        val longSessions = sessions.count { it.totalTime >= TimeUnit.MINUTES.toMillis(15) }
        val focusScore = if (totalSessions > 0) (longSessions * 100) / totalSessions else 0
        
        return WeeklySummary(
            weekStart = weekStart,
            weekEnd = weekEnd,
            dailySummaries = dailySummaries,
            totalTime = totalTime,
            totalSessions = totalSessions,
            averageDailyTime = averageDailyTime,
            mostActiveDay = dailySummaries.maxByOrNull { it.totalTime }?.date ?: weekStart,
            leastActiveDay = dailySummaries.minByOrNull { it.totalTime }?.date ?: weekStart,
            productivityScore = productivityScore,
            focusScore = focusScore,
            topApps = getTopAppsForWeek(sessions)
        )
    }
    
    private fun getTopAppsForDay(sessions: List<AppSession>): List<AppUsage> {
        val appUsageMap = mutableMapOf<String, AppUsage>()
        
        sessions.forEach { session ->
            val existing = appUsageMap[session.packageName]
            if (existing != null) {
                appUsageMap[session.packageName] = existing.copy(
                    timeInForeground = existing.timeInForeground + session.totalTime
                )
            } else {
                appUsageMap[session.packageName] = AppUsage(
                    session.packageName,
                    session.appName,
                    session.totalTime
                )
            }
        }
        
        return appUsageMap.values.sortedByDescending { it.timeInForeground }.take(5)
    }
    
    private fun getTopAppsForWeek(sessions: List<AppSession>): List<AppUsage> {
        return getTopAppsForDay(sessions)
    }
    
    private fun getTopAppsForMonth(sessions: List<AppSession>): List<AppUsage> {
        return getTopAppsForDay(sessions)
    }
    
    private fun calculateTimeTrend(weeklySummaries: List<WeeklySummary>): TrendDirection {
        if (weeklySummaries.size < 2) return TrendDirection.STABLE
        
        val firstWeek = weeklySummaries.first().totalTime
        val lastWeek = weeklySummaries.last().totalTime
        
        val change = ((lastWeek - firstWeek) * 100) / firstWeek
        
        return when {
            change > 10 -> TrendDirection.INCREASING
            change < -10 -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }
    }
    
    private fun calculateProductivityTrend(weeklySummaries: List<WeeklySummary>): TrendDirection {
        if (weeklySummaries.size < 2) return TrendDirection.STABLE
        
        val firstWeek = weeklySummaries.first().productivityScore.overallScore
        val lastWeek = weeklySummaries.last().productivityScore.overallScore
        
        val change = lastWeek - firstWeek
        
        return when {
            change > 5 -> TrendDirection.INCREASING
            change < -5 -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }
    }
    
    companion object {
        private const val TAG = "HistoricalDataManager"
    }
}

data class WeeklySummary(
    val weekStart: Date,
    val weekEnd: Date,
    val dailySummaries: List<DailySummary>,
    val totalTime: Long,
    val totalSessions: Int,
    val averageDailyTime: Long,
    val mostActiveDay: Date,
    val leastActiveDay: Date,
    val productivityScore: ProductivityScore,
    val focusScore: Int,
    val topApps: List<AppUsage>
)

data class MonthlySummary(
    val monthStart: Date,
    val monthEnd: Date,
    val weeklySummaries: List<WeeklySummary>,
    val totalTime: Long,
    val totalSessions: Int,
    val averageWeeklyTime: Long,
    val timeTrend: TrendDirection,
    val productivityTrend: TrendDirection,
    val topApps: List<AppUsage>
)

data class DailySummary(
    val date: Date,
    val totalTime: Long,
    val sessionCount: Int,
    val averageSessionTime: Long,
    val productivityScore: ProductivityScore,
    val focusScore: Int,
    val topApps: List<AppUsage>
)

data class ChartData(
    val type: ChartType,
    val title: String,
    val dataPoints: List<ChartDataPoint>,
    val xAxisLabel: String,
    val yAxisLabel: String,
    val secondaryYAxisLabel: String?
)

data class ChartDataPoint(
    val label: String,
    val value: Long,
    val secondaryValue: Long,
    val date: Date?
)

enum class ChartType {
    DAILY_USAGE,
    HOURLY_PATTERNS,
    PRODUCTIVITY_TRENDS,
    APP_CATEGORIES,
    FOCUS_SCORES
}

