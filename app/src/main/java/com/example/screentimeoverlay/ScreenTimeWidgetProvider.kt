package com.example.screentimeoverlay

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.*

/**
 * App Widget Provider for Screen Time widgets
 * Supports both home screen and lock screen widgets
 */
class ScreenTimeWidgetProvider : AppWidgetProvider() {
    
    companion object {
        private const val TAG = "ScreenTimeWidgetProvider"
        private const val ACTION_WIDGET_UPDATE = "com.example.screentimeoverlay.WIDGET_UPDATE"
        private const val ACTION_WIDGET_CLICK = "com.example.screentimeoverlay.WIDGET_CLICK"
    }
    
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "Updating ${appWidgetIds.size} widgets")
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        when (intent.action) {
            ACTION_WIDGET_UPDATE -> {
                Log.d(TAG, "Widget update requested")
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    android.content.ComponentName(context, ScreenTimeWidgetProvider::class.java)
                )
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
            ACTION_WIDGET_CLICK -> {
                Log.d(TAG, "Widget clicked")
                handleWidgetClick(context)
            }
        }
    }
    
    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val widgetData = getWidgetData(context)
        val views = RemoteViews(context.packageName, R.layout.widget_screen_time)
        
        // Update widget content
        views.setTextViewText(R.id.widget_time, widgetData.currentTime)
        views.setTextViewText(R.id.widget_today_time, widgetData.todayTime)
        views.setTextViewText(R.id.widget_daily_goal, widgetData.dailyGoal)
        views.setTextViewText(R.id.widget_progress, "${widgetData.progressPercentage}%")
        views.setTextViewText(R.id.widget_top_app, widgetData.topApp)
        views.setTextViewText(R.id.widget_session_count, "${widgetData.sessionCount} sessions")
        
        // Set progress bar
        views.setProgressBar(R.id.widget_progress_bar, 100, widgetData.progressPercentage, false)
        
        // Set click intent
        val clickIntent = Intent(context, MainActivity::class.java)
        val clickPendingIntent = android.app.PendingIntent.getActivity(
            context, 0, clickIntent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, clickPendingIntent)
        
        // Update widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
        Log.d(TAG, "Widget $appWidgetId updated")
    }
    
    private fun getWidgetData(context: Context): WidgetData {
        val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val screenTimeData = getCurrentScreenTimeData(context)
        
        return WidgetData(
            currentTime = currentTime,
            todayTime = formatTime(screenTimeData.todayTime),
            dailyGoal = formatTime(screenTimeData.dailyGoal),
            progressPercentage = screenTimeData.progressPercentage,
            isOverLimit = screenTimeData.isOverLimit,
            topApp = screenTimeData.topApp,
            sessionCount = screenTimeData.sessionCount
        )
    }
    
    private fun getCurrentScreenTimeData(context: Context): WidgetScreenTimeData {
        // This would integrate with your actual screen time tracking
        // For now, return mock data
        return WidgetScreenTimeData(
            todayTime = 2 * 60 * 60 * 1000, // 2 hours
            dailyGoal = 8 * 60 * 60 * 1000, // 8 hours
            progressPercentage = 25,
            isOverLimit = false,
            topApp = "Chrome",
            sessionCount = 15
        )
    }
    
    private fun formatTime(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)
    }
    
    private fun handleWidgetClick(context: Context) {
        // Open the main app when widget is clicked
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "First widget created")
        
        // Start widget update service
        val intent = Intent(context, LockScreenWidgetService::class.java)
        intent.putExtra("action", "create_widget")
        context.startService(intent)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Last widget removed")
        
        // Stop widget update service
        val intent = Intent(context, LockScreenWidgetService::class.java)
        intent.putExtra("action", "remove_widget")
        context.startService(intent)
    }
}

/**
 * Data class for widget data
 */
data class WidgetData(
    val currentTime: String,
    val todayTime: String,
    val dailyGoal: String,
    val progressPercentage: Int,
    val isOverLimit: Boolean,
    val topApp: String,
    val sessionCount: Int
)

/**
 * Data class for widget-specific screen time data
 */
data class WidgetScreenTimeData(
    val todayTime: Long,
    val dailyGoal: Long,
    val progressPercentage: Int,
    val isOverLimit: Boolean,
    val topApp: String,
    val sessionCount: Int
)
