package com.example.screentimeoverlay

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class OnboardingActivity : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences
    private lateinit var overlayPermissionCard: MaterialCardView
    private lateinit var usagePermissionCard: MaterialCardView
    private lateinit var overlayPermissionStatus: ImageView
    private lateinit var usagePermissionStatus: ImageView
    private lateinit var btnGrantPermissions: MaterialButton
    private lateinit var btnSkipOnboarding: MaterialButton

    private val requestOverlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            updatePermissionStatus()
            if (hasAllPermissions()) {
                showCompletionDialog()
            }
        }

    private val requestUsageAccess =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
        
        setContentView(R.layout.activity_onboarding)
        
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
        
        try {
            initializeViews()
            setupClickListeners()
            updatePermissionStatus()
        } catch (e: Exception) {
            // If there's an error, just complete onboarding and go to main activity
            completeOnboarding()
        }
    }

    private fun initializeViews() {
        overlayPermissionCard = findViewById(R.id.overlay_permission_card)
        usagePermissionCard = findViewById(R.id.usage_permission_card)
        overlayPermissionStatus = findViewById(R.id.overlay_permission_status)
        usagePermissionStatus = findViewById(R.id.usage_permission_status)
        btnGrantPermissions = findViewById(R.id.btn_grant_permissions)
        btnSkipOnboarding = findViewById(R.id.btn_skip_onboarding)
    }

    private fun setupClickListeners() {
        overlayPermissionCard.setOnClickListener {
            showPermissionExplanationDialog(
                "Overlay Permission",
                "This permission allows Screen Time Tracker to display a floating overlay on top of other apps.\n\n" +
                "Why we need it:\n" +
                "• Shows your current screen time in real-time\n" +
                "• Helps you stay aware of your usage\n" +
                "• Works across all apps and games\n\n" +
                "The overlay is small, unobtrusive, and can be moved or hidden anytime.",
                "Grant Overlay Permission"
            ) {
                requestOverlayPermission()
            }
        }

        usagePermissionCard.setOnClickListener {
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

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        requestOverlayPermission.launch(intent)
    }

    private fun requestUsageAccess() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        requestUsageAccess.launch(intent)
        Toast.makeText(this, "Please enable usage access for Screen Time Tracker", Toast.LENGTH_LONG).show()
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
        overlayPermissionStatus.visibility = if (hasOverlayPermission()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }

        usagePermissionStatus.visibility = if (hasUsageStatsPermission()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }

        // Update button text based on permissions
        when {
            hasAllPermissions() -> {
                btnGrantPermissions.text = "Continue to App"
                btnGrantPermissions.isEnabled = true
            }
            !hasOverlayPermission() -> {
                btnGrantPermissions.text = "Grant Overlay Permission"
                btnGrantPermissions.isEnabled = true
            }
            !hasUsageStatsPermission() -> {
                btnGrantPermissions.text = "Grant Usage Access"
                btnGrantPermissions.isEnabled = true
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
