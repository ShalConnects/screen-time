package com.example.screentimeoverlay

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat

class OnboardingActivityFixed : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var btnGrantPermissions: Button
    private lateinit var btnSkipOnboarding: Button
    private lateinit var overlayPermissionStatus: ImageView
    private lateinit var usagePermissionStatus: ImageView

    private val requestOverlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            updatePermissionStatus()
            if (hasAllPermissions()) {
                showCompletionDialog()
            }
        }

    private val requestUsageAccess =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
            updatePermissionStatus()
            if (hasAllPermissions()) {
                showCompletionDialog()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display (required for Android 15+ compliance)
        // Using WindowCompat for compatibility across all supported Android versions
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Configure system bars appearance
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.isAppearanceLightStatusBars = false
        windowInsetsController?.isAppearanceLightNavigationBars = false
        
        setContentView(R.layout.activity_onboarding_fixed)
        
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

        preferences = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        
        // If user has already completed onboarding and still has both permissions, skip immediately
        if (preferences.getBoolean("onboarding_completed", false) && hasAllPermissions()) {
            completeOnboarding()
            return
        }
        
        initializeViews()
        setupClickListeners()
        updatePermissionStatus()
    }

    private fun initializeViews() {
        btnGrantPermissions = findViewById(R.id.btn_grant_permissions)
        btnSkipOnboarding = findViewById(R.id.btn_skip_onboarding)
        overlayPermissionStatus = findViewById(R.id.overlay_permission_status)
        usagePermissionStatus = findViewById(R.id.usage_permission_status)
    }

    private fun setupClickListeners() {
        btnGrantPermissions.setOnClickListener {
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
            } else if (!hasUsageStatsPermission()) {
                requestUsageAccess()
            } else {
                showCompletionDialog()
            }
        }

        btnSkipOnboarding.setOnClickListener {
            showSkipConfirmationDialog()
        }

        // Add click listeners for permission cards
        findViewById<android.view.View>(R.id.overlay_permission_card).setOnClickListener {
            showOverlayExplanationConditional()
        }

        findViewById<android.view.View>(R.id.usage_permission_card).setOnClickListener {
            showPermissionExplanationDialog(
                "Usage Access Permission",
                "This permission allows Screen Time Tracker to track which apps you use and for how long.\n\n" +
                "Why we need it:\n" +
                "• Tracks time spent in each app\n" +
                "• Generates detailed analytics and reports\n" +
                "• Provides insights about your digital habits\n" +
                "• Creates productivity scores and trends\n\n" +
                "All data is stored locally on your device and never shared.",
                "Grant Usage Access"
            ) {
                requestUsageAccess()
            }
        }
    }

    private fun showPermissionExplanationDialog(
        title: String,
        message: String,
        buttonText: String,
        onGrant: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(buttonText) { _, _ ->
                onGrant()
            }
            .setNegativeButton("Cancel", null)
            .setIcon(R.drawable.ic_info)
            .show()
    }

    /**
     * Overlay dialog with conditional primary action:
     * - If already granted: show only OK (no grant CTA)
     * - If not granted: show Grant Overlay Permission CTA
     */
    private fun showOverlayExplanationConditional() {
        val message = "This permission allows Screen Time Tracker to display a floating overlay on top of other apps.\n\n" +
            "Why we need it:\n" +
            "• Shows your current screen time in real-time\n" +
            "• Helps you stay aware of your usage\n" +
            "• Works across all apps and games\n\n" +
            "The overlay is small, unobtrusive, and can be moved or hidden anytime."

        if (hasOverlayPermission()) {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setIcon(R.drawable.ic_info)
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission")
                .setMessage(message)
                .setPositiveButton("Grant Overlay Permission") { _, _ ->
                    requestOverlayPermission()
                }
                .setNegativeButton("Cancel", null)
                .setIcon(R.drawable.ic_info)
                .show()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        requestOverlayPermission.launch(intent)
    }

    private fun requestUsageAccess() {
        // UX assist: explain exactly what to do on the next screen
        Toast.makeText(
            this,
            "On the next screen, find 'Screen Time Tracker' and enable 'Allow usage access'.\nTip: use the search icon or three dots > Show system apps on some devices.",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        requestUsageAccess.launch(intent)

        // Gentle follow-up reminder after a short delay
        android.os.Handler(mainLooper).postDelayed({
            Toast.makeText(
                this,
                "Enable usage access for 'Screen Time Tracker' to continue.",
                Toast.LENGTH_SHORT
            ).show()
        }, 1500)
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

    private fun updatePermissionStatus() {
        val overlayGranted = hasOverlayPermission()
        val usageGranted = hasUsageStatsPermission()

        overlayPermissionStatus.visibility = if (overlayGranted) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }

        usagePermissionStatus.visibility = if (usageGranted) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }

        // Update button text based on permissions
        when {
            overlayGranted && usageGranted -> {
                // Both granted: hide action buttons and immediately continue
                btnGrantPermissions.visibility = android.view.View.GONE
                btnSkipOnboarding.visibility = android.view.View.GONE
                completeOnboarding()
                return
            }
            !overlayGranted -> {
                btnGrantPermissions.text = "Grant Overlay Permission"
                btnGrantPermissions.isEnabled = true
                btnGrantPermissions.visibility = android.view.View.VISIBLE
                btnSkipOnboarding.visibility = android.view.View.VISIBLE
            }
            !usageGranted -> {
                btnGrantPermissions.text = "Grant Usage Access"
                btnGrantPermissions.isEnabled = true
                btnGrantPermissions.visibility = android.view.View.VISIBLE
                btnSkipOnboarding.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun showCompletionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Setup Complete!")
            .setMessage("All permissions have been granted. You're ready to start tracking your screen time with beautiful overlays and detailed analytics.")
            .setPositiveButton("Start Using App") { _, _ ->
                completeOnboarding()
            }
            .setCancelable(false)
            .setIcon(R.drawable.ic_check_circle)
            .show()
    }

    private fun showSkipConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Skip Onboarding?")
            .setMessage("You can grant permissions later in the app settings, but some features may not work properly without them.")
            .setPositiveButton("Skip") { _, _ ->
                completeOnboarding()
            }
            .setNegativeButton("Continue Setup", null)
            .setIcon(R.drawable.ic_warning)
            .show()
    }

    private fun completeOnboarding() {
        preferences.edit()
            .putBoolean("onboarding_completed", true)
            .apply()

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
