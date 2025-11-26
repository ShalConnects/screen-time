package com.example.screentimeoverlay

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.Manifest
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import com.example.screentimeoverlay.BuildConfig
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import java.util.Date
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val requestOverlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasAllPermissions()) startForegroundOverlayService() else promptMissingPermissions()
        }

    private val requestUsageAccess =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasAllPermissions()) startForegroundOverlayService() else promptMissingPermissions()
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // No-op: we don't change behavior based on the result here
        }

    // Core feature managers (keeping for Option 2)
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    private lateinit var appFilterManager: AppFilterManager
    private lateinit var exportManager: ExportManager
    
    // Moderate feature managers (keeping for Option 2)
    private lateinit var sessionTracker: SessionTracker
    private lateinit var productivityScorer: ProductivityScorer
    private lateinit var usagePatternAnalyzer: UsagePatternAnalyzer
    private lateinit var historicalDataManager: HistoricalDataManager
    
    // Public getters for AnalyticsFragment
    fun getAppFilterManager() = appFilterManager
    fun getHistoricalDataManager() = historicalDataManager
    fun getSessionTracker() = sessionTracker
    
    // UI accumulator to prevent minute rollback and sync with overlay (same logic as OverlayService)
    private var uiBaseTotalMs: Long = 0L
    private var uiBaseTsMs: Long = 0L
    private var lastUsageStatsTotalMs: Long = 0L
    private var currentDayKey: String = ""
    
    // Onboarding
    private lateinit var preferences: SharedPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display (required for Android 15+ compliance)
        // Using WindowCompat for compatibility across all supported Android versions
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Configure system bars appearance
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.isAppearanceLightStatusBars = false
        windowInsetsController?.isAppearanceLightNavigationBars = false
        
        // Check if onboarding is needed
        preferences = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("onboarding_completed", false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        
        setContentView(R.layout.activity_main)
        
        // Handle WindowInsets for edge-to-edge display
        val contentView = findViewById<android.view.ViewGroup>(android.R.id.content)
        val rootLayout = contentView?.getChildAt(0) as? android.widget.LinearLayout
        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        // Initialize core managers
        batteryOptimizationManager = BatteryOptimizationManager(this)
        appFilterManager = AppFilterManager(this)
        exportManager = ExportManager(this)
        
        // Initialize moderate feature managers
        sessionTracker = SessionTracker(this)
        productivityScorer = ProductivityScorer(this)
        usagePatternAnalyzer = UsagePatternAnalyzer(this)
        historicalDataManager = HistoricalDataManager(this)

        // Setup ViewPager2 and TabLayout
        setupViewPager()
    }

    private fun setupViewPager() {
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.setIcon(R.drawable.ic_monitor)
                }
                1 -> {
                    tab.setIcon(R.drawable.ic_analytics)
                }
                else -> {
                    tab.setIcon(R.drawable.ic_monitor)
                }
            }
        }.attach()
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        if (mode == AppOpsManager.MODE_ALLOWED) return true

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

    fun hasAllPermissions(): Boolean = hasOverlayPermission() && hasUsageStatsPermission()

    private fun promptMissingPermissions() {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
        } else if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Usage access permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
        }
    }

    fun requestPermissions() {
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

    fun startForegroundOverlayService() {
        // CRITICAL: Verify permissions before starting service
        // This prevents crashes on MIUI and other devices
        if (!hasOverlayPermission()) {
            Toast.makeText(this, "Overlay permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
            return
        }
        
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Usage access permission is required. Please enable it in settings.", Toast.LENGTH_LONG).show()
            requestPermissions()
            return
        }
        
        requestNotificationPermissionIfNeeded()
        val intent = Intent(this, OverlayService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            // Android 14+ requires foregroundServiceType in manifest
            android.util.Log.e("MainActivity", "Failed to start foreground service", e)
            Toast.makeText(this, "Failed to start overlay. Please restart the app.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start overlay service", e)
            Toast.makeText(this, "Failed to start overlay: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun restartOnboarding() {
        preferences.edit()
            .putBoolean("onboarding_completed", false)
            .apply()
        
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }

    fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun switchDisplayMode(mode: String) {
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("action", "switch_display_mode")
        intent.putExtra("mode", mode)
        startService(intent)
        Toast.makeText(this, "Display mode: $mode", Toast.LENGTH_SHORT).show()
    }
    
    fun setPositionMode(position: String) {
        // Check if service is running
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
        val isServiceRunning = runningServices.any { 
            OverlayService::class.java.name == it.service.className 
        }
        
        if (!isServiceRunning) {
            Toast.makeText(this, "Please start the overlay first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val intent = Intent(this, OverlayService::class.java)
        intent.putExtra("action", "set_position_mode")
        intent.putExtra("position", position)
        startService(intent)
        Toast.makeText(this, "Position: $position", Toast.LENGTH_SHORT).show()
    }
    
    // Core feature methods
    fun handleBatteryOptimization() {
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_battery_settings)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val batteryStatusText = dialog.findViewById<android.widget.TextView>(R.id.batteryStatusText)
        val autoStartStatusText = dialog.findViewById<android.widget.TextView>(R.id.autoStartStatusText)
        val instructionsText = dialog.findViewById<android.widget.TextView>(R.id.instructionsText)
        val openBatterySettingsButton = dialog.findViewById<android.widget.Button>(R.id.openBatterySettingsButton)
        val openAutoStartButton = dialog.findViewById<android.widget.Button>(R.id.openAutoStartButton)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        // Collapsible section handles
        val overviewTitle = dialog.findViewById<android.widget.TextView>(R.id.overviewTitle)
        val overviewContent = dialog.findViewById<android.view.View>(R.id.overviewContent)
        // Breakdown section removed from layout
        val currentSessionTitle = dialog.findViewById<android.widget.TextView>(R.id.currentSessionTitle)
        val currentSessionContent = dialog.findViewById<android.view.View>(R.id.currentSessionContent)
        
        // Check battery optimization status
        val isBatteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            batteryOptimizationManager.isBatteryOptimizationDisabled()
        } else {
            true // Not available on older versions
        }
        
        batteryStatusText.text = if (isBatteryOptimized) "✓ Disabled" else "✗ Enabled"
        batteryStatusText.setTextColor(if (isBatteryOptimized) getColor(android.R.color.holo_green_light) else getColor(android.R.color.holo_red_light))
        
        // Check auto-start status (simplified)
        autoStartStatusText.text = "Check Settings"
        autoStartStatusText.setTextColor(getColor(R.color.text_tertiary))
        
        // Get instructions
        val oemInstructions = batteryOptimizationManager.getOEMInstructions()
        val autoStartInstructions = batteryOptimizationManager.requestAutoStartWhitelist()
        
        instructionsText.text = buildString {
            appendLine("Battery Optimization:")
            appendLine("$oemInstructions")
            appendLine("\nAuto-Start Settings:")
            appendLine("$autoStartInstructions")
        }
        
        // Button listeners
        openBatterySettingsButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!isBatteryOptimized) {
                    batteryOptimizationManager.requestDisableBatteryOptimization(this)
                } else {
                    Toast.makeText(this, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Battery optimization not available on this Android version", Toast.LENGTH_SHORT).show()
            }
        }
        
        openAutoStartButton.setOnClickListener {
            // Open auto-start settings (device-specific)
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
            Toast.makeText(this, "Look for 'Auto-start' or 'Background activity' settings", Toast.LENGTH_LONG).show()
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    fun openAppFilterSettings() {
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_app_filters)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val totalAppsText = dialog.findViewById<android.widget.TextView>(R.id.totalAppsText)
        val whitelistCountText = dialog.findViewById<android.widget.TextView>(R.id.whitelistCountText)
        val blacklistCountText = dialog.findViewById<android.widget.TextView>(R.id.blacklistCountText)
        val excludedCategoriesText = dialog.findViewById<android.widget.TextView>(R.id.excludedCategoriesText)
        val manageWhitelistButton = dialog.findViewById<android.widget.Button>(R.id.manageWhitelistButton)
        val manageBlacklistButton = dialog.findViewById<android.widget.Button>(R.id.manageBlacklistButton)
        val resetFiltersButton = dialog.findViewById<android.widget.Button>(R.id.resetFiltersButton)
        val saveButton = dialog.findViewById<android.widget.Button>(R.id.saveButton)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        // Get current filter data
        val currentMode = appFilterManager.getFilterMode()
        val whitelist = appFilterManager.getWhitelist()
        val blacklist = appFilterManager.getBlacklist()
        val excludedCategories = appFilterManager.getExcludedCategories()
        
        // Populate data
        totalAppsText.text = (whitelist.size + blacklist.size).toString()
        whitelistCountText.text = "${whitelist.size} apps"
        blacklistCountText.text = "${blacklist.size} apps"
        excludedCategoriesText.text = if (excludedCategories.isNotEmpty()) {
            excludedCategories.joinToString(", ")
        } else {
            "None"
        }
        
        // Hide/disable interactive elements for "coming soon"
        val filterModeSpinner = dialog.findViewById<android.widget.Spinner>(R.id.filterModeSpinner)
        filterModeSpinner?.visibility = android.view.View.GONE
        val categorySection = dialog.findViewById<android.widget.LinearLayout>(R.id.categorySection)
        categorySection?.visibility = android.view.View.GONE
        
        // Button listeners
        manageWhitelistButton.setOnClickListener {
            Toast.makeText(this, "Filter configuration coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        manageBlacklistButton.setOnClickListener {
            Toast.makeText(this, "Filter configuration coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        saveButton.setOnClickListener {
            Toast.makeText(this, "Filter configuration coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        resetFiltersButton.setOnClickListener {
            // Reset filters to default
            appFilterManager.resetFilters()
            Toast.makeText(this, "Filters reset to default", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    private fun openAppSelectionDialog(
        title: String,
        selectedPackages: MutableSet<String>,
        onSave: (Set<String>) -> Unit
    ) {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_app_selection)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Set dialog window size
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
        
        val dialogTitle = dialog.findViewById<android.widget.TextView>(R.id.dialogTitle)
        val searchEditText = dialog.findViewById<android.widget.EditText>(R.id.searchEditText)
        val appListContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.appListContainer)
        val selectAllButton = dialog.findViewById<android.widget.Button>(R.id.selectAllButton)
        val deselectAllButton = dialog.findViewById<android.widget.Button>(R.id.deselectAllButton)
        val saveButton = dialog.findViewById<android.widget.Button>(R.id.saveButton)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        dialogTitle.text = title
        
        // Get all installed apps
        val allApps = appFilterManager.getAllInstalledApps()
        val checkBoxMap = mutableMapOf<String, android.widget.CheckBox>()
        var displayedApps = allApps
        
        // Function to create app checkbox
        fun createAppCheckBox(app: AppInfo): android.widget.CheckBox {
            return android.widget.CheckBox(this).apply {
                text = app.appName
                isChecked = selectedPackages.contains(app.packageName)
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                setPadding(16, 12, 16, 12)
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedPackages.add(app.packageName)
                    } else {
                        selectedPackages.remove(app.packageName)
                    }
                }
            }
        }
        
        // Function to populate app list
        fun populateAppList(apps: List<AppInfo>) {
            appListContainer.removeAllViews()
            checkBoxMap.clear()
            
            if (apps.isEmpty()) {
                val noAppsText = android.widget.TextView(this).apply {
                    text = if (searchEditText.text.isNotEmpty()) {
                        "No apps found matching your search."
                    } else {
                        "No user-installed apps found.\nSystem apps are not shown."
                    }
                    textSize = 14f
                    setTextColor(getColor(R.color.text_tertiary))
                    setPadding(16, 32, 16, 32)
                    gravity = android.view.Gravity.CENTER
                }
                appListContainer.addView(noAppsText)
            } else {
                apps.forEach { app ->
                    val checkBox = createAppCheckBox(app)
                    checkBoxMap[app.packageName] = checkBox
                    appListContainer.addView(checkBox)
                }
            }
        }
        
        // Initial population
        populateAppList(allApps)
        
        // Search functionality
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.lowercase() ?: ""
                displayedApps = if (query.isEmpty()) {
                    allApps
                } else {
                    allApps.filter { 
                        it.appName.lowercase().contains(query) || 
                        it.packageName.lowercase().contains(query)
                    }
                }
                populateAppList(displayedApps)
                
                // Restore selection state after filtering
                checkBoxMap.forEach { (packageName, checkBox) ->
                    checkBox.isChecked = selectedPackages.contains(packageName)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // Select All button
        selectAllButton.setOnClickListener {
            displayedApps.forEach { app ->
                selectedPackages.add(app.packageName)
                checkBoxMap[app.packageName]?.isChecked = true
            }
        }
        
        // Deselect All button
        deselectAllButton.setOnClickListener {
            displayedApps.forEach { app ->
                selectedPackages.remove(app.packageName)
                checkBoxMap[app.packageName]?.isChecked = false
            }
        }
        
        // Save button
        saveButton.setOnClickListener {
            onSave(selectedPackages)
            dialog.dismiss()
        }
        
        // Close button
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    fun exportWeeklySummary() {
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
    
    fun exportDailySummary() {
        val today = Date()
        val shareIntent = exportManager.shareDailySummary(today)
        startActivity(Intent.createChooser(shareIntent, "Share Daily Summary"))
    }
    
    fun openAccessibilitySettings() {
        // Check if user has already seen and agreed to the prominent disclosure
        val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val hasAgreedToDisclosure = prefs.getBoolean("accessibility_disclosure_agreed", false)
        
        if (!hasAgreedToDisclosure) {
            // Show prominent disclosure dialog first (required by Google Play policy)
            showAccessibilityProminentDisclosure()
        } else {
            // User has already agreed, show opt-in dialog with toggle
            showAccessibilityOptInDialog()
        }
    }
    
    private fun showAccessibilityProminentDisclosure() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_accessibility_prominent_disclosure)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Make dialog non-dismissible with back button (policy requirement)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        
        val btnAgree = dialog.findViewById<android.widget.Button>(R.id.btnAgree)
        val btnNotNow = dialog.findViewById<android.widget.Button>(R.id.btnNotNow)
        
        val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        
        // User agrees - mark as agreed, enable in-app toggle, and go to settings
        btnAgree.setOnClickListener {
            prefs.edit().putBoolean("accessibility_disclosure_agreed", true).apply()
            // Automatically enable the in-app toggle when user agrees to disclosure
            prefs.edit().putBoolean("metrics_accessibility_enabled", true).apply()
            dialog.dismiss()
            // Show opt-in dialog for confirmation, but toggle is already enabled
            showAccessibilityOptInDialog()
        }
        
        // User declines - just dismiss (they can enable later)
        btnNotNow.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showAccessibilityOptInDialog() {
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_accessibility_opt_in)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val prefs = getSharedPreferences("ui_prefs", Context.MODE_PRIVATE)
        val enableMetricsSwitch = dialog.findViewById<android.widget.Switch>(R.id.enableMetricsSwitch)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        val openSettingsButton = dialog.findViewById<android.widget.Button>(R.id.openSettingsButton)
        
        // Load current toggle state
        val currentState = prefs.getBoolean("metrics_accessibility_enabled", false)
        enableMetricsSwitch?.isChecked = currentState
        
        // Wire up toggle to save preference
        enableMetricsSwitch?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("metrics_accessibility_enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "Metrics enabled! Tap 'Open' to enable in system settings.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Metrics disabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Close button
        closeButton?.setOnClickListener {
            dialog.dismiss()
        }
        
        // Open Settings button - navigates to system accessibility settings
        openSettingsButton?.setOnClickListener {
            dialog.dismiss()
            navigateToAccessibilitySettings()
        }
        
        dialog.show()
    }
    
    private fun navigateToAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Please enable Screen Time Overlay metrics service", Toast.LENGTH_LONG).show()
    }
    
    // Moderate feature methods
    fun showSessionStats() {
        val todayStats = sessionTracker.getSessionStats(Date())
        val currentSession = sessionTracker.getCurrentSession()
        val todaySessions = sessionTracker.getSessionsForDate(Date()).toMutableList()
        
        // Include current active session in today's sessions if it started today
        val calendar = Calendar.getInstance()
        calendar.time = Date()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 1) // 00:01 AM - align with overlay day start
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        if (currentSession != null && currentSession.isActive && currentSession.startTime >= startOfDay) {
            // Add active session to today's sessions for display (with current time included)
            val activeSessionWithCurrentTime = currentSession.copy(
                totalTime = currentSession.totalTime + (System.currentTimeMillis() - currentSession.lastActivityTime)
            )
            todaySessions.add(activeSessionWithCurrentTime)
        }
        
        // Get total time using same method as overlay (UsageStatsManager) to ensure they match
        val overlayTotalTime = getTodayTotalUsageFromUsageStats()
        
        // Debug logging
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "Session Stats - Total: ${todayStats.totalSessions}, Session Time: ${formatTime(todayStats.totalTime)}, Overlay Time: ${formatTime(overlayTotalTime)}, Sessions found: ${todaySessions.size}")
        }
        
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_session_stats)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val totalSessionsText = dialog.findViewById<android.widget.TextView>(R.id.totalSessionsText)
        val totalTimeText = dialog.findViewById<android.widget.TextView>(R.id.totalTimeText)
        val averageSessionText = dialog.findViewById<android.widget.TextView>(R.id.averageSessionText)
        val currentSessionContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.currentSessionContainer)
        val currentAppText = dialog.findViewById<android.widget.TextView>(R.id.currentAppText)
        val currentDurationText = dialog.findViewById<android.widget.TextView>(R.id.currentDurationText)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        // Collapsible section handles
        val overviewTitle = dialog.findViewById<android.widget.TextView>(R.id.overviewTitle)
        val overviewContent = dialog.findViewById<android.view.View>(R.id.overviewContent)
        
        val currentSessionTitle = dialog.findViewById<android.widget.TextView>(R.id.currentSessionTitle)
        val currentSessionContent = dialog.findViewById<android.view.View>(R.id.currentSessionContent)
        
        // Populate data with real session statistics
        totalSessionsText.text = todayStats.totalSessions.toString()
        // Use overlay's calculation (UsageStatsManager) to match overlay exactly
        // This ensures Session Stats Total time matches what's shown in overlay
        val displayTotalTime = overlayTotalTime
        totalTimeText.text = formatDurationForDisplay(displayTotalTime)
        averageSessionText.text = formatDurationForDisplay(todayStats.averageSessionTime)
        // Focus score removed
        // Focused sessions and longest session values removed with breakdown section
        
        // Show current session if available
        if (currentSession != null && currentSession.isActive) {
            currentSessionContainer.visibility = android.view.View.VISIBLE
            // Format app name with title case (like Weekly/Monthly views)
            val locale = java.util.Locale.getDefault()
            val titleCased = currentSession.appName.split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { part ->
                    part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString() }
                }
            currentAppText.text = titleCased
            // Calculate current session duration including active time
            val currentDuration = currentSession.totalTime + (System.currentTimeMillis() - currentSession.lastActivityTime)
            currentDurationText.text = formatDurationForDisplay(currentDuration)
        } else {
            currentSessionContainer.visibility = android.view.View.GONE
        }

        // Remove collapsible behavior: always show overview; current session section already controlled above
        if (overviewContent != null) overviewContent.visibility = android.view.View.VISIBLE

        // Populate additional overview data
        val mostUsedText = dialog.findViewById<android.widget.TextView>(R.id.mostUsedText)
        val overviewMetaText = dialog.findViewById<android.widget.TextView>(R.id.overviewMetaText)
        if (mostUsedText != null) {
            if (todaySessions.isNotEmpty()) {
                val byApp = todaySessions.groupBy { it.appName }.mapValues { entry -> entry.value.sumOf { it.totalTime } }
                val most = byApp.maxByOrNull { it.value }
                if (most != null) {
                    // Format app name with title case (like Weekly/Monthly views)
                    val locale = java.util.Locale.getDefault()
                    val titleCased = most.key.split(" ")
                        .filter { it.isNotBlank() }
                        .joinToString(" ") { part ->
                            part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString() }
                        }
                    mostUsedText.text = "Most used:\n$titleCased: ${formatDurationForDisplay(most.value)}"
                } else {
                    mostUsedText.text = "Most used: —"
                }
            } else {
                mostUsedText.text = "Most used: —"
            }
        }
        if (overviewMetaText != null) {
            var peakHour = "—"
            if (todaySessions.isNotEmpty()) {
                val counts = IntArray(24)
                val cal = java.util.Calendar.getInstance()
                todaySessions.forEach { s ->
                    cal.timeInMillis = s.startTime
                    val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                    if (h in 0..23) counts[h]++
                }
                val idx = counts.indices.maxByOrNull { counts[it] } ?: -1
                if (idx >= 0) peakHour = formatClockTimeForDisplay(idx)
            }
            val sincePickup = if (todaySessions.isNotEmpty()) {
                val last = todaySessions.maxByOrNull { it.endTime } ?: todaySessions.last()
                val end = if (last.endTime > 0) last.endTime else last.lastActivityTime
                val delta = System.currentTimeMillis() - end
                formatDurationForDisplay(delta)
            } else {
                "—"
            }
            overviewMetaText.text = "Peak hour: $peakHour · Since last pickup: $sincePickup"
        }
        
        // Add session insights based on real data
        addSessionInsights(dialog, todaySessions, todayStats)
        
        // Close button listener
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    private fun addSessionInsights(dialog: android.app.Dialog, sessions: List<AppSession>, stats: SessionStats) {
        // Find the container for additional insights
        val insightsContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.insightsContainer)
        if (insightsContainer != null) {
            insightsContainer.removeAllViews()
            
            // Get enhanced session insights
            val insights = sessionTracker.getSessionInsights(Date())
            
            if (sessions.isNotEmpty()) {
                // Add session timeline visualization
                addSessionTimeline(insightsContainer, sessions)
                
                val insightsText = android.widget.TextView(this).apply {
                    text = buildString {
                        appendLine("Session Insights:")
                        appendLine("• Pattern: ${insights.sessionPattern}")
                        if (insights.firstSessionTime != null) {
                            appendLine("• First session: ${formatClockTimeFromEpoch(insights.firstSessionTime)}")
                        }
                        if (insights.lastSessionTime != null) {
                            appendLine("• Last session: ${formatClockTimeFromEpoch(insights.lastSessionTime)}")
                        }
                        if (insights.averageBreakTime > 0) {
                            appendLine("• Average break: ${formatDurationForDisplay(insights.averageBreakTime)}")
                        }
                        
                    }
                    textSize = 12f
                    setTextColor(getColor(R.color.text_tertiary))
                    setPadding(0, 8, 0, 8)
                }
                insightsContainer.addView(insightsText)
            } else {
                val noDataText = android.widget.TextView(this).apply {
                    text = "No session data available for today.\nStart using apps to see session analytics."
                    textSize = 12f
                    setTextColor(getColor(R.color.text_tertiary))
                    setPadding(0, 8, 0, 8)
                    gravity = android.view.Gravity.CENTER
                }
                insightsContainer.addView(noDataText)
            }
        }
    }
    
    private fun addSessionTimeline(container: android.widget.LinearLayout, sessions: List<AppSession>) {
        val timelineTitle = android.widget.TextView(this).apply {
            text = "Session Timeline"
            textSize = 14f
            setTextColor(getColor(R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 8)
        }
        container.addView(timelineTitle)
        
        // Create a simple timeline visualization (scrollable within a fixed-height area)
        val timelineContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }
        
        val sortedSessions = sessions.sortedBy { it.startTime }
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        // Create timeline bars for each session
        sortedSessions.forEachIndexed { index, session ->
            val sessionBar = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }
            
            // Session time label
            val timeLabel = android.widget.TextView(this).apply {
                text = formatClockTimeFromEpoch(session.startTime)
                textSize = 10f
                setTextColor(getColor(R.color.text_tertiary))
                layoutParams = android.widget.LinearLayout.LayoutParams(80, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            
            // Session duration bar
            val sessionDuration = session.totalTime
            val barWidth = (sessionDuration * 200 / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(20) // Scale to fit
            
            val sessionBarView = android.view.View(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(barWidth, 20)
                setBackgroundColor(if (sessionDuration >= TimeUnit.MINUTES.toMillis(15)) {
                    getColor(android.R.color.holo_green_light) // Focused session
                } else {
                    getColor(android.R.color.holo_orange_light) // Short session
                })
            }
            
            // Session info
            val sessionInfo = android.widget.TextView(this).apply {
                // Format app name with title case (like Weekly/Monthly views)
                val locale = java.util.Locale.getDefault()
                val titleCased = session.appName.split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString() }
                    }
                text = "$titleCased (${formatDurationForDisplay(sessionDuration)})"
                textSize = 10f
                setTextColor(getColor(R.color.text_tertiary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(8, 0, 0, 0)
            }
            
            sessionBar.addView(timeLabel)
            sessionBar.addView(sessionBarView)
            sessionBar.addView(sessionInfo)
            timelineContainer.addView(sessionBar)
        }
        
        // Wrap in a NestedScrollView with fixed height so only the timeline scrolls
        val heightDp = 240
        val density = resources.displayMetrics.density
        val scrollHeightPx = (heightDp * density).toInt()
        val scroll = androidx.core.widget.NestedScrollView(this).apply {
            isFillViewport = false
            overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                scrollHeightPx
            )
            addView(timelineContainer)
        }
        container.addView(scroll)
    }
    
    fun showProductivityInsights() {
        val insights = productivityScorer.getProductivityInsights(
            DateRange(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time,
                Date()
            ),
            emptyList()
        )
        
        val message = buildString {
            appendLine("Productivity Insights:")
            appendLine("Productivity Ratio: ${insights.productivityRatio}%")
            appendLine("Distraction Ratio: ${insights.distractionRatio}%")
            appendLine("\nTop Distracting Apps:")
            insights.topDistractingApps.take(3).forEach { app ->
                appendLine("• ${app.appName}: ${formatTime(app.timeSpent)}")
            }
            appendLine("\nRecommendations:")
            insights.recommendations.take(2).forEach { rec ->
                appendLine("• $rec")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    fun showPatternAnalysis() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val weekStart = calendar.time
        val weekEnd = Date()
        
        val analysis = usagePatternAnalyzer.analyzeUsagePatterns(DateRange(weekStart, weekEnd))
        
        val message = buildString {
            appendLine("Usage Pattern Analysis:")
            appendLine("Average Daily Usage: ${formatTime(analysis.averageDailyUsage)}")
            appendLine("Peak Usage Hour: ${analysis.peakUsageHour}:00")
            appendLine("Focus Score: ${analysis.focusScore}%")
            appendLine("Consistency Score: ${analysis.consistencyScore}%")
            appendLine("\nRecommendations:")
            analysis.recommendations.take(3).forEach { rec ->
                appendLine("• $rec")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    fun showWeeklyView() {
        val weeklySummary = historicalDataManager.getWeeklySummary(Date())
        
        // Debug logging
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "Weekly View - Total time: ${formatTime(weeklySummary.totalTime)}, Sessions: ${weeklySummary.totalSessions}, Days: ${weeklySummary.dailySummaries.size}")
        }
        
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_weekly_view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val totalTimeText = dialog.findViewById<android.widget.TextView>(R.id.totalTimeText)
        val totalSessionsText = dialog.findViewById<android.widget.TextView>(R.id.totalSessionsText)
        val averageDailyText = dialog.findViewById<android.widget.TextView>(R.id.averageDailyText)
        val productivityScoreText = dialog.findViewById<android.widget.TextView>(R.id.productivityScoreText)
        val mostActiveDayText = dialog.findViewById<android.widget.TextView>(R.id.mostActiveDayText)
        val topAppsContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.topAppsContainer)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        // Populate data
        totalTimeText.text = formatDurationForDisplay(weeklySummary.totalTime)
        totalSessionsText.text = weeklySummary.totalSessions.toString()
        averageDailyText.text = formatDurationForDisplay(weeklySummary.averageDailyTime)
        productivityScoreText.text = "${weeklySummary.productivityScore.overallScore}%"
        
        // Format most active day
        val calendar = Calendar.getInstance()
        calendar.time = weeklySummary.mostActiveDay
        val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        mostActiveDayText.text = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        
        // Add top apps dynamically
        topAppsContainer.removeAllViews()
        weeklySummary.topApps.take(3).forEach { app ->
            val appView = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 8, 0, 8)
            }
            
            val appNameText = android.widget.TextView(this).apply {
                val locale = java.util.Locale.getDefault()
                val titleCased = app.appName.split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString() }
                    }
                text = titleCased
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val appTimeText = android.widget.TextView(this).apply {
                text = formatDurationForDisplay(app.timeInForeground)
                textSize = 12f
                setTextColor(getColor(R.color.text_tertiary))
                gravity = android.view.Gravity.END
            }
            
            appView.addView(appNameText)
            appView.addView(appTimeText)
            topAppsContainer.addView(appView)
        }
        
        // Close button listener
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    fun showMonthlyView() {
        val monthlySummary = historicalDataManager.getMonthlySummary(Date())
        
        // Debug logging
        if (BuildConfig.DEBUG) {
            android.util.Log.d("MainActivity", "Monthly View - Total time: ${formatTime(monthlySummary.totalTime)}, Sessions: ${monthlySummary.totalSessions}, Weeks: ${monthlySummary.weeklySummaries.size}")
        }
        
        // Create custom dialog
        val dialog = android.app.Dialog(this)
        dialog.setContentView(R.layout.dialog_monthly_view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Get dialog views
        val totalTimeText = dialog.findViewById<android.widget.TextView>(R.id.totalTimeText)
        val totalSessionsText = dialog.findViewById<android.widget.TextView>(R.id.totalSessionsText)
        val averageWeeklyText = dialog.findViewById<android.widget.TextView>(R.id.averageWeeklyText)
        val weeksTrackedText = dialog.findViewById<android.widget.TextView>(R.id.weeksTrackedText)
        val timeTrendText = dialog.findViewById<android.widget.TextView>(R.id.timeTrendText)
        val productivityTrendText = dialog.findViewById<android.widget.TextView>(R.id.productivityTrendText)
        val topAppsContainer = dialog.findViewById<android.widget.LinearLayout>(R.id.topAppsContainer)
        val closeButton = dialog.findViewById<android.widget.Button>(R.id.closeButton)
        
        // Populate data
        totalTimeText.text = formatDurationForDisplay(monthlySummary.totalTime)
        totalSessionsText.text = monthlySummary.totalSessions.toString()
        averageWeeklyText.text = formatDurationForDisplay(monthlySummary.averageWeeklyTime)
        weeksTrackedText.text = monthlySummary.weeklySummaries.size.toString()
        
        // Format trends with better display
        val timeTrendDisplay = when (monthlySummary.timeTrend) {
            com.example.screentimeoverlay.TrendDirection.INCREASING -> "↗ Increasing"
            com.example.screentimeoverlay.TrendDirection.DECREASING -> "↘ Decreasing"
            com.example.screentimeoverlay.TrendDirection.IMPROVING -> "↗ Improving"
            com.example.screentimeoverlay.TrendDirection.DECLINING -> "↘ Declining"
            com.example.screentimeoverlay.TrendDirection.STABLE -> "→ Stable"
        }
        timeTrendText.text = timeTrendDisplay
        
        val productivityTrendDisplay = when (monthlySummary.productivityTrend) {
            com.example.screentimeoverlay.TrendDirection.INCREASING -> "↗ Improving"
            com.example.screentimeoverlay.TrendDirection.DECREASING -> "↘ Declining"
            com.example.screentimeoverlay.TrendDirection.IMPROVING -> "↗ Improving"
            com.example.screentimeoverlay.TrendDirection.DECLINING -> "↘ Declining"
            com.example.screentimeoverlay.TrendDirection.STABLE -> "→ Stable"
        }
        productivityTrendText.text = productivityTrendDisplay
        
        // Add top apps dynamically
        topAppsContainer.removeAllViews()
        monthlySummary.topApps.take(3).forEach { app ->
            val appView = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 8, 0, 8)
            }
            
            val appNameText = android.widget.TextView(this).apply {
                val locale = java.util.Locale.getDefault()
                val titleCased = app.appName.split(" ")
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString() }
                    }
                text = titleCased
                textSize = 14f
                setTextColor(getColor(R.color.text_primary))
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val appTimeText = android.widget.TextView(this).apply {
                text = formatDurationForDisplay(app.timeInForeground)
                textSize = 12f
                setTextColor(getColor(R.color.text_tertiary))
                gravity = android.view.Gravity.END
            }
            
            appView.addView(appNameText)
            appView.addView(appTimeText)
            topAppsContainer.addView(appView)
        }
        
        // Close button listener
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Show dialog
        dialog.show()
    }
    
    fun showBreakTimeRecommendations() {
        val recommendations = usagePatternAnalyzer.getOptimalBreakTimes()
        
        val message = buildString {
            appendLine("Break Time Recommendations:")
            recommendations.forEach { rec ->
                appendLine("• ${rec.time} (${rec.duration})")
                appendLine("  ${rec.reason}")
            }
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Get today's total usage time from UsageStatsManager using same logic as overlay
     * This ensures Session Stats Total time matches overlay exactly
     */
    fun getTodayTotalUsageFromUsageStats(): Long {
        return try {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val endTime = System.currentTimeMillis()
            
            // Calculate start time as midnight (00:01 AM) of current day - same as overlay
            val calendar = Calendar.getInstance()
            // Ensure we're working with today's date, not yesterday's
            calendar.timeInMillis = endTime
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 1) // 00:01 AM
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            
            // Ensure startTime is not in the future (shouldn't happen, but safety check)
            val actualStartTime = if (startTime > endTime) {
                // If startTime is in future, we're at midnight - use 00:01 AM of today
                calendar.timeInMillis = endTime
                calendar.add(Calendar.DAY_OF_YEAR, 0) // Ensure we're on the same day
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 1)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            } else {
                startTime
            }
            
            val usageStats = usageStatsManager.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                actualStartTime,
                endTime
            ) ?: return 0L
            
            // Filter out stats that don't have activity within today's window
            // UsageStatsManager may return stats from previous day that have lastTimeUsed in range
            // but we only want stats that actually have usage within today's window
            val filteredStats = usageStats.filter { stats ->
                stats.lastTimeUsed >= actualStartTime && stats.lastTimeUsed <= endTime
            }
            
            // Check if day changed - reset accumulator at midnight (same as overlay)
            val todayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            if (currentDayKey.isNotEmpty() && currentDayKey != todayKey) {
                // New day detected - reset accumulator
                uiBaseTotalMs = 0L
                uiBaseTsMs = System.currentTimeMillis()
                lastUsageStatsTotalMs = 0L
                currentDayKey = todayKey
            } else if (currentDayKey.isEmpty()) {
                // First run - initialize
                currentDayKey = todayKey
            }
            
            // Apply app filtering (same as overlay)
            var totalTime = 0L
            filteredStats.forEach { stats ->
                if (appFilterManager.shouldTrackApp(stats.packageName)) {
                    totalTime += stats.totalTimeInForeground
                }
            }
            
            // Prevent UI minute rollback: compute a clamped UI total that never decreases within the same day (same as overlay)
            val nowTs = System.currentTimeMillis()
            val usageStatsTotal = totalTime
            val uiTotalMs: Long
            if (usageStatsTotal > lastUsageStatsTotalMs) {
                // UsageStats has increased - it's current, use it directly
                lastUsageStatsTotalMs = usageStatsTotal
                uiBaseTotalMs = usageStatsTotal
                uiBaseTsMs = nowTs
                uiTotalMs = usageStatsTotal
            } else if (usageStatsTotal == lastUsageStatsTotalMs) {
                // UsageStats hasn't changed - check if we need to add elapsed time (UsageStats might be lagging)
                val elapsedSinceBase = nowTs - uiBaseTsMs
                // Only add time if UsageStats is lagging (more than 30 seconds since last update)
                if (elapsedSinceBase > 30000L) {
                    val uiAdvancedMs = (elapsedSinceBase / 60000L) * 60000L
                    uiTotalMs = kotlin.math.max(usageStatsTotal, uiBaseTotalMs + uiAdvancedMs)
                } else {
                    // UsageStats is current (updated recently), use it directly
                    uiTotalMs = usageStatsTotal
                }
            } else {
                // UsageStats decreased (shouldn't happen, but handle it) - use accumulator to prevent rollback
                val elapsedSinceBase = nowTs - uiBaseTsMs
                val uiAdvancedMs = if (elapsedSinceBase > 0) (elapsedSinceBase / 60000L) * 60000L else 0L
                uiTotalMs = kotlin.math.max(usageStatsTotal, uiBaseTotalMs + uiAdvancedMs)
            }
            
            return uiTotalMs
        } catch (_: Throwable) {
            0L
        }
    }
    
    fun formatTime(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        return String.format("%02d:%02d", hours, minutes)
    }
    
    private fun formatDurationForDisplay(timeMs: Long): String {
        val hours = timeMs / (1000 * 60 * 60)
        val minutes = (timeMs % (1000 * 60 * 60)) / (1000 * 60)
        val hUnit = getString(R.string.unit_hour_short)
        val mUnit = getString(R.string.unit_minute_short)
        return when {
            hours > 0 && minutes > 0 -> "$hours$hUnit $minutes$mUnit"
            hours > 0 -> "$hours$hUnit"
            else -> "$minutes$mUnit"
        }
    }

    private fun formatClockTimeForDisplay(hourOfDay: Int): String {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val fmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        return fmt.format(java.util.Date(cal.timeInMillis))
    }

    private fun formatClockTimeFromEpoch(epochMs: Long): String {
        val fmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
        return fmt.format(java.util.Date(epochMs))
    }
    
    
    
}