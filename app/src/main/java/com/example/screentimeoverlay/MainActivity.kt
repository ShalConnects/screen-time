package com.example.screentimeoverlay

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.CheckBox
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Date
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val requestOverlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasAllPermissions()) startForegroundOverlayService() else promptMissingPermissions()
        }

    private val requestUsageAccess =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasAllPermissions()) startForegroundOverlayService() else promptMissingPermissions()
        }

    // Advanced feature managers
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var appFilterManager: AppFilterManager
    private lateinit var exportManager: ExportManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize managers
        batteryOptimizationManager = BatteryOptimizationManager(this)
        appFilterManager = AppFilterManager(this)
        exportManager = ExportManager(this)

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val touchPassthroughSwitch = findViewById<Switch>(R.id.touchPassthroughSwitch)
        val perAppSwitch = findViewById<Switch>(R.id.perAppSwitch)
        val goalHoursEdit = findViewById<EditText>(R.id.goalHoursEdit)
        val goalMinutesEdit = findViewById<EditText>(R.id.goalMinutesEdit)
        val setGoalButton = findViewById<Button>(R.id.setGoalButton)
        
        // Display mode controls
        val compactModeButton = findViewById<Button>(R.id.compactModeButton)
        val progressModeButton = findViewById<Button>(R.id.progressModeButton)
        val detailedModeButton = findViewById<Button>(R.id.detailedModeButton)
        
        // Position controls
        val autoPositionButton = findViewById<Button>(R.id.autoPositionButton)
        val topRightPositionButton = findViewById<Button>(R.id.topRightPositionButton)
        val bottomRightPositionButton = findViewById<Button>(R.id.bottomRightPositionButton)
        
        // Advanced feature controls
        val batteryOptimizationButton = findViewById<Button>(R.id.batteryOptimizationButton)
        val appFilterButton = findViewById<Button>(R.id.appFilterButton)
        val exportWeeklyButton = findViewById<Button>(R.id.exportWeeklyButton)
        val exportDailyButton = findViewById<Button>(R.id.exportDailyButton)
        val accessibilityButton = findViewById<Button>(R.id.accessibilityButton)

        startButton.setOnClickListener {
            if (hasAllPermissions()) {
                startForegroundOverlayService()
            } else {
                requestPermissions()
            }
        }

        stopButton.setOnClickListener {
            stopOverlayService()
        }

        touchPassthroughSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Communicate with the overlay service
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("action", "toggle_touch_passthrough")
            intent.putExtra("enabled", isChecked)
            startService(intent)
            Toast.makeText(this, "Touch passthrough: $isChecked", Toast.LENGTH_SHORT).show()
        }

        perAppSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Communicate with the overlay service
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("action", "toggle_per_app_mode")
            intent.putExtra("enabled", isChecked)
            startService(intent)
            Toast.makeText(this, "Per-app mode: $isChecked", Toast.LENGTH_SHORT).show()
        }

        setGoalButton.setOnClickListener {
            val hours = goalHoursEdit.text.toString().toIntOrNull() ?: 8
            val minutes = goalMinutesEdit.text.toString().toIntOrNull() ?: 0
            
            // Send goal to overlay service
            val intent = Intent(this, OverlayService::class.java)
            intent.putExtra("action", "set_daily_goal")
            intent.putExtra("hours", hours)
            intent.putExtra("minutes", minutes)
            startService(intent)
            
            Toast.makeText(this, "Daily goal set: ${hours}h ${minutes}m", Toast.LENGTH_SHORT).show()
        }
        
        // Advanced feature button handlers
        batteryOptimizationButton?.setOnClickListener {
            handleBatteryOptimization()
        }
        
        appFilterButton?.setOnClickListener {
            openAppFilterSettings()
        }
        
        exportWeeklyButton?.setOnClickListener {
            exportWeeklySummary()
        }
        
        exportDailyButton?.setOnClickListener {
            exportDailySummary()
        }
        
        accessibilityButton?.setOnClickListener {
            openAccessibilitySettings()
        }
        
        // Display mode button handlers
        compactModeButton?.setOnClickListener {
            switchDisplayMode("COMPACT")
        }
        
        progressModeButton?.setOnClickListener {
            switchDisplayMode("PROGRESS")
        }
        
        detailedModeButton?.setOnClickListener {
            switchDisplayMode("DETAILED")
        }
        
        // Position button handlers
        autoPositionButton?.setOnClickListener {
            setPositionMode("AUTO")
        }
        
        topRightPositionButton?.setOnClickListener {
            setPositionMode("TOP_RIGHT")
        }
        
        bottomRightPositionButton?.setOnClickListener {
            setPositionMode("BOTTOM_RIGHT")
        }
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode == AppOpsManager.MODE_ALLOWED) return true

        // Fallback: try to query and see if we get non-empty results
        return try {
            val usage = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val end = System.currentTimeMillis()
            val start = end - 60_000
            val list = usage.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end)
            list != null && list.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasAllPermissions(): Boolean = hasOverlayPermission() && hasUsageStatsPermission()

    private fun promptMissingPermissions() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
        } else if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Usage access permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        if (!hasOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            requestOverlayPermission.launch(intent)
        } else if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            requestUsageAccess.launch(intent)
            Toast.makeText(this, "Please enable usage access for this app", Toast.LENGTH_LONG).show()
        }
    }

    private fun startForegroundOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
    }
    
    // Advanced feature methods
    
    private fun handleBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (batteryOptimizationManager.isBatteryOptimizationDisabled()) {
                Toast.makeText(this, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
            } else {
                batteryOptimizationManager.requestDisableBatteryOptimization(this)
            }
        } else {
            Toast.makeText(this, "Battery optimization not available on this Android version", Toast.LENGTH_SHORT).show()
        }
        
        // Show OEM-specific instructions
        val oemInstructions = batteryOptimizationManager.getOEMInstructions()
        val autoStartInstructions = batteryOptimizationManager.requestAutoStartWhitelist()
        
        Toast.makeText(this, "Battery: $oemInstructions\nAuto-start: $autoStartInstructions", Toast.LENGTH_LONG).show()
    }
    
    private fun openAppFilterSettings() {
        // Create a simple dialog for app filtering
        val filterModes = FilterMode.values()
        val modeNames = filterModes.map { it.name.replace("_", " ").lowercase().replaceFirstChar { char -> char.uppercase() } }
        
        val currentMode = appFilterManager.getFilterMode()
        val currentIndex = filterModes.indexOf(currentMode)
        
        // For now, just show current settings and allow basic changes
        val message = buildString {
            appendLine("Current Filter Mode: ${currentMode.name}")
            appendLine("Whitelist: ${appFilterManager.getWhitelist().size} apps")
            appendLine("Blacklist: ${appFilterManager.getBlacklist().size} apps")
            appendLine("Excluded Categories: ${appFilterManager.getExcludedCategories().joinToString()}")
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun exportWeeklySummary() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        
        val weekStart = calendar.time
        val shareIntent = exportManager.shareWeeklySummary(weekStart)
        startActivity(Intent.createChooser(shareIntent, "Share Weekly Summary"))
    }
    
    private fun exportDailySummary() {
        val today = Date()
        val shareIntent = exportManager.shareDailySummary(today)
        startActivity(Intent.createChooser(shareIntent, "Share Daily Summary"))
    }
    
    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Please enable Screen Time Overlay in accessibility services", Toast.LENGTH_LONG).show()
    }
    
    private fun switchDisplayMode(mode: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("action", "switch_display_mode")
        intent.putExtra("mode", mode)
        startService(intent)
        Toast.makeText(this, "Display mode: $mode", Toast.LENGTH_SHORT).show()
    }
    
    private fun setPositionMode(position: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("action", "set_position_mode")
        intent.putExtra("position", position)
        startService(intent)
        Toast.makeText(this, "Position: $position", Toast.LENGTH_SHORT).show()
    }
}


