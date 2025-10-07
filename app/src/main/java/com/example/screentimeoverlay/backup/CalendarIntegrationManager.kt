package com.example.screentimeoverlay

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

/**
 * Calendar integration manager for syncing with calendar work hours
 * and providing context-aware screen time management
 */
class CalendarIntegrationManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("calendar_integration", Context.MODE_PRIVATE)
    private val calendarEvents = mutableListOf<CalendarEvent>()
    private val workHoursAnalyzer = WorkHoursAnalyzer()
    
    /**
     * Sync with calendar and get work hours
     */
    fun syncWithCalendar(): CalendarSyncResult {
        val events = fetchCalendarEvents()
        val workHours = analyzeWorkHours(events)
        val screenTimeContext = analyzeScreenTimeContext(events)
        
        return CalendarSyncResult(
            events = events,
            workHours = workHours,
            screenTimeContext = screenTimeContext,
            syncStatus = SyncStatus.SUCCESS
        )
    }
    
    /**
     * Get work hours for a specific date
     */
    fun getWorkHours(date: Date): WorkHours {
        val events = getEventsForDate(date)
        val workEvents = events.filter { it.isWorkEvent }
        
        if (workEvents.isEmpty()) {
            return WorkHours(
                date = date,
                startTime = null,
                endTime = null,
                totalHours = 0,
                isWorkDay = false,
                workEvents = emptyList()
            )
        }
        
        val startTime = workEvents.minByOrNull { it.startTime }?.startTime
        val endTime = workEvents.maxByOrNull { it.endTime }?.endTime
        val totalHours = if (startTime != null && endTime != null) {
            ((endTime.time - startTime.time) / TimeUnit.HOURS.toMillis(1)).toInt()
        } else 0
        
        return WorkHours(
            date = date,
            startTime = startTime,
            endTime = endTime,
            totalHours = totalHours,
            isWorkDay = workEvents.isNotEmpty(),
            workEvents = workEvents
        )
    }
    
    /**
     * Get screen time recommendations based on calendar
     */
    fun getScreenTimeRecommendations(date: Date): List<ScreenTimeRecommendation> {
        val workHours = getWorkHours(date)
        val events = getEventsForDate(date)
        val recommendations = mutableListOf<ScreenTimeRecommendation>()
        
        if (workHours.isWorkDay) {
            // Work day recommendations
            recommendations.addAll(getWorkDayRecommendations(workHours, events))
        } else {
            // Non-work day recommendations
            recommendations.addAll(getNonWorkDayRecommendations(events))
        }
        
        return recommendations
    }
    
    /**
     * Check if current time is during work hours
     */
    fun isWorkTime(currentTime: Date): Boolean {
        val workHours = getWorkHours(currentTime)
        if (!workHours.isWorkDay) return false
        
        val startTime = workHours.startTime ?: return false
        val endTime = workHours.endTime ?: return false
        
        return currentTime.after(startTime) && currentTime.before(endTime)
    }
    
    /**
     * Get focus mode schedule based on calendar
     */
    fun getFocusModeSchedule(date: Date): List<FocusModePeriod> {
        val events = getEventsForDate(date)
        val focusPeriods = mutableListOf<FocusModePeriod>()
        
        events.forEach { event ->
            if (event.requiresFocus) {
                focusPeriods.add(
                    FocusModePeriod(
                        startTime = event.startTime,
                        endTime = event.endTime,
                        title = event.title,
                        description = "Focus mode during ${event.title}",
                        priority = event.priority,
                        allowedApps = event.allowedApps,
                        blockedApps = event.blockedApps
                    )
                )
            }
        }
        
        return focusPeriods.sortedBy { it.startTime }
    }
    
    /**
     * Get break recommendations based on calendar
     */
    fun getBreakRecommendations(date: Date): List<BreakRecommendation> {
        val events = getEventsForDate(date)
        val workHours = getWorkHours(date)
        val recommendations = mutableListOf<BreakRecommendation>()
        
        if (workHours.isWorkDay) {
            // Find gaps between meetings for breaks
            val gaps = findMeetingGaps(events)
            gaps.forEach { gap ->
                recommendations.add(
                    BreakRecommendation(
                        time = formatTime(gap.startTime),
                        duration = "${gap.durationMinutes} minutes",
                        type = BreakType.SHORT_BREAK,
                        reason = "Break between meetings",
                        priority = 2,
                        confidence = 90,
                        benefits = listOf("Prevents meeting fatigue", "Maintains focus")
                    )
                )
            }
        }
        
        return recommendations
    }
    
    /**
     * Get productivity insights based on calendar and screen time
     */
    fun getProductivityInsights(date: Date, screenTimeData: UsageData): ProductivityInsights {
        val workHours = getWorkHours(date)
        val events = getEventsForDate(date)
        val screenTimeDuringWork = calculateScreenTimeDuringWork(screenTimeData, workHours)
        val meetingTime = calculateMeetingTime(events)
        val productiveTime = calculateProductiveTime(screenTimeData, events)
        
        return ProductivityInsights(
            date = date,
            productivityScore = calculateProductivityScore(screenTimeDuringWork, meetingTime, productiveTime),
            focusScore = 75,
            distractionScore = 25,
            recommendations = generateProductivityRecommendations(screenTimeDuringWork, meetingTime, productiveTime),
            trends = UsageTrends(
                usageTrend = TrendDirection.STABLE,
                focusTrend = TrendDirection.IMPROVING,
                productivityTrend = TrendDirection.IMPROVING
            )
        )
    }
    
    private fun fetchCalendarEvents(): List<CalendarEvent> {
        // This would integrate with the device's calendar
        // For now, return mock data
        return listOf(
            CalendarEvent(
                id = "1",
                title = "Team Meeting",
                startTime = Date(),
                endTime = Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)),
                isWorkEvent = true,
                requiresFocus = true,
                priority = EventPriority.HIGH,
                allowedApps = listOf("Zoom", "Teams", "Slack"),
                blockedApps = listOf("Instagram", "TikTok", "Games")
            )
        )
    }
    
    private fun analyzeWorkHours(events: List<CalendarEvent>): WorkHoursAnalysis {
        val workEvents = events.filter { it.isWorkEvent }
        val totalWorkHours = workEvents.sumOf { 
            (it.endTime.time - it.startTime.time) / TimeUnit.HOURS.toMillis(1).toDouble()
        }
        
        return WorkHoursAnalysis(
            totalWorkHours = totalWorkHours.toInt(),
            averageWorkHours = if (workEvents.isNotEmpty()) totalWorkHours / workEvents.size else 0.0,
            peakWorkHours = findPeakWorkHours(workEvents),
            workPatterns = analyzeWorkPatterns(workEvents)
        )
    }
    
    private fun analyzeScreenTimeContext(events: List<CalendarEvent>): ScreenTimeContext {
        val workEvents = events.filter { it.isWorkEvent }
        val focusEvents = events.filter { it.requiresFocus }
        
        return ScreenTimeContext(
            hasWorkEvents = workEvents.isNotEmpty(),
            hasFocusEvents = focusEvents.isNotEmpty(),
            workEventCount = workEvents.size,
            focusEventCount = focusEvents.size,
            contextRecommendations = generateContextRecommendations(workEvents, focusEvents)
        )
    }
    
    private fun getEventsForDate(date: Date): List<CalendarEvent> {
        val calendar = Calendar.getInstance()
        calendar.time = date
        val startOfDay = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val endOfDay = Calendar.getInstance().apply {
            time = startOfDay
            add(Calendar.DAY_OF_YEAR, 1)
        }.time
        
        return calendarEvents.filter { event ->
            event.startTime.after(startOfDay) && event.startTime.before(endOfDay)
        }
    }
    
    private fun getWorkDayRecommendations(workHours: WorkHours, events: List<CalendarEvent>): List<ScreenTimeRecommendation> {
        val recommendations = mutableListOf<ScreenTimeRecommendation>()
        
        if (workHours.totalHours > 8) {
            recommendations.add(
                ScreenTimeRecommendation(
                    type = RecommendationType.TIME_LIMIT,
                    title = "Long Work Day",
                    description = "You have a long work day. Consider limiting non-work screen time.",
                    priority = 1,
                    action = "Reduce entertainment apps during work hours"
                )
            )
        }
        
        val focusEvents = events.filter { it.requiresFocus }
        if (focusEvents.isNotEmpty()) {
            recommendations.add(
                ScreenTimeRecommendation(
                    type = RecommendationType.FOCUS_MODE,
                    title = "Focus Events Scheduled",
                    description = "You have focus-required events. Enable focus mode during these times.",
                    priority = 1,
                    action = "Enable focus mode for ${focusEvents.size} events"
                )
            )
        }
        
        return recommendations
    }
    
    private fun getNonWorkDayRecommendations(events: List<CalendarEvent>): List<ScreenTimeRecommendation> {
        val recommendations = mutableListOf<ScreenTimeRecommendation>()
        
        if (events.isEmpty()) {
            recommendations.add(
                ScreenTimeRecommendation(
                    type = RecommendationType.BREAK_SUGGESTION,
                    title = "Free Day",
                    description = "You have a free day. Enjoy some screen time but maintain balance.",
                    priority = 3,
                    action = "Set a reasonable daily limit"
                )
            )
        }
        
        return recommendations
    }
    
    private fun findMeetingGaps(events: List<CalendarEvent>): List<MeetingGap> {
        val gaps = mutableListOf<MeetingGap>()
        val sortedEvents = events.sortedBy { it.startTime }
        
        for (i in 0 until sortedEvents.size - 1) {
            val currentEvent = sortedEvents[i]
            val nextEvent = sortedEvents[i + 1]
            val gapDuration = nextEvent.startTime.time - currentEvent.endTime.time
            
            if (gapDuration > TimeUnit.MINUTES.toMillis(15)) {
                gaps.add(
                    MeetingGap(
                        startTime = currentEvent.endTime,
                        endTime = nextEvent.startTime,
                        durationMinutes = (gapDuration / TimeUnit.MINUTES.toMillis(1)).toInt()
                    )
                )
            }
        }
        
        return gaps
    }
    
    private fun calculateScreenTimeDuringWork(screenTimeData: UsageData, workHours: WorkHours): Long {
        if (!workHours.isWorkDay) return 0
        
        val startTime = workHours.startTime ?: return 0
        val endTime = workHours.endTime ?: return 0
        
        // This would calculate screen time during work hours
        // For now, return a mock value
        return screenTimeData.totalTime / 2
    }
    
    private fun calculateMeetingTime(events: List<CalendarEvent>): Long {
        return events.filter { it.isWorkEvent }.sumOf { 
            it.endTime.time - it.startTime.time 
        }
    }
    
    private fun calculateProductiveTime(screenTimeData: UsageData, events: List<CalendarEvent>): Long {
        // This would calculate productive screen time
        return screenTimeData.totalTime / 3
    }
    
    private fun calculateProductivityScore(screenTimeDuringWork: Long, meetingTime: Long, productiveTime: Long): Int {
        val totalWorkTime = screenTimeDuringWork + meetingTime
        if (totalWorkTime == 0L) return 0
        
        val productivityRatio = productiveTime.toDouble() / totalWorkTime
        return (productivityRatio * 100).toInt().coerceIn(0, 100)
    }
    
    private fun generateProductivityRecommendations(screenTimeDuringWork: Long, meetingTime: Long, productiveTime: Long): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (screenTimeDuringWork > meetingTime * 2) {
            recommendations.add("Consider reducing screen time during work hours")
        }
        
        if (productiveTime < screenTimeDuringWork / 2) {
            recommendations.add("Focus on productive apps during work hours")
        }
        
        return recommendations
    }
    
    private fun findPeakWorkHours(workEvents: List<CalendarEvent>): List<Int> {
        val hourlyWork = mutableMapOf<Int, Long>()
        
        workEvents.forEach { event ->
            val calendar = Calendar.getInstance()
            calendar.time = event.startTime
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val duration = event.endTime.time - event.startTime.time
            hourlyWork[hour] = (hourlyWork[hour] ?: 0) + duration
        }
        
        val maxWork = hourlyWork.values.maxOrNull() ?: 0
        return hourlyWork.filter { it.value >= maxWork * 0.8 }.keys.sorted()
    }
    
    private fun analyzeWorkPatterns(workEvents: List<CalendarEvent>): WorkPatterns {
        val patterns = mutableListOf<String>()
        
        if (workEvents.any { it.startTime.hours < 9 }) {
            patterns.add("Early morning work")
        }
        
        if (workEvents.any { it.startTime.hours > 17 }) {
            patterns.add("Evening work")
        }
        
        if (workEvents.size > 5) {
            patterns.add("High meeting frequency")
        }
        
        return WorkPatterns(patterns = patterns)
    }
    
    private fun generateContextRecommendations(workEvents: List<CalendarEvent>, focusEvents: List<CalendarEvent>): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (workEvents.isNotEmpty()) {
            recommendations.add("Work events detected - consider focus mode")
        }
        
        if (focusEvents.isNotEmpty()) {
            recommendations.add("Focus events detected - minimize distractions")
        }
        
        return recommendations
    }
    
    private fun formatTime(date: Date): String {
        val calendar = Calendar.getInstance()
        calendar.time = date
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
    
    companion object {
        private const val TAG = "CalendarIntegrationManager"
    }
}

// Data classes
data class CalendarSyncResult(
    val events: List<CalendarEvent>,
    val workHours: WorkHoursAnalysis,
    val screenTimeContext: ScreenTimeContext,
    val syncStatus: SyncStatus
)

data class CalendarEvent(
    val id: String,
    val title: String,
    val startTime: Date,
    val endTime: Date,
    val isWorkEvent: Boolean,
    val requiresFocus: Boolean,
    val priority: EventPriority,
    val allowedApps: List<String>,
    val blockedApps: List<String>
)

data class WorkHours(
    val date: Date,
    val startTime: Date?,
    val endTime: Date?,
    val totalHours: Int,
    val isWorkDay: Boolean,
    val workEvents: List<CalendarEvent>
)

data class WorkHoursAnalysis(
    val totalWorkHours: Int,
    val averageWorkHours: Double,
    val peakWorkHours: List<Int>,
    val workPatterns: WorkPatterns
)

data class ScreenTimeContext(
    val hasWorkEvents: Boolean,
    val hasFocusEvents: Boolean,
    val workEventCount: Int,
    val focusEventCount: Int,
    val contextRecommendations: List<String>
)

data class ScreenTimeRecommendation(
    val type: RecommendationType,
    val title: String,
    val description: String,
    val priority: Int,
    val action: String
)

data class FocusModePeriod(
    val startTime: Date,
    val endTime: Date,
    val title: String,
    val description: String,
    val priority: EventPriority,
    val allowedApps: List<String>,
    val blockedApps: List<String>
)

data class MeetingGap(
    val startTime: Date,
    val endTime: Date,
    val durationMinutes: Int
)


data class WorkPatterns(
    val patterns: List<String>
)

enum class SyncStatus {
    SUCCESS,
    FAILED,
    NO_PERMISSION
}

enum class EventPriority {
    LOW,
    MEDIUM,
    HIGH
}

