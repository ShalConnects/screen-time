package com.example.screentimeoverlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi

class BatteryOptimizationManager(private val context: Context) {
    
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    
    /**
     * Check if battery optimization is disabled for this app
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Battery optimization not available on older versions
        }
    }
    
    /**
     * Request to disable battery optimization
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun requestDisableBatteryOptimization(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            activity.startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE)
        } catch (e: Exception) {
            // Fallback to general battery optimization settings
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            activity.startActivity(intent)
            Toast.makeText(context, "Please disable battery optimization for this app", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Check for OEM-specific battery optimization settings
     */
    fun checkOEMBatterySettings(): List<String> {
        val oemSettings = mutableListOf<String>()
        
        // Check for common OEM battery optimization settings
        val oemBatterySettings = mapOf(
            "xiaomi" to listOf(
                "com.miui.powerkeeper",
                "com.miui.securitycenter",
                "com.miui.system"
            ),
            "huawei" to listOf(
                "com.huawei.systemmanager",
                "com.huawei.powergenie"
            ),
            "samsung" to listOf(
                "com.samsung.android.sm",
                "com.samsung.android.smartcallprovider"
            ),
            "oneplus" to listOf(
                "com.oneplus.security",
                "com.oneplus.safecenter"
            ),
            "oppo" to listOf(
                "com.coloros.safecenter",
                "com.coloros.oppoguardelf"
            ),
            "vivo" to listOf(
                "com.vivo.permissionmanager",
                "com.vivo.safecenter"
            )
        )
        
        val manufacturer = Build.MANUFACTURER.lowercase()
        val relevantSettings = oemBatterySettings[manufacturer] ?: emptyList()
        
        relevantSettings.forEach { packageName ->
            if (isPackageInstalled(packageName)) {
                oemSettings.add(packageName)
            }
        }
        
        return oemSettings
    }
    
    /**
     * Get instructions for disabling battery optimization on specific OEMs
     */
    fun getOEMInstructions(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when (manufacturer) {
            "xiaomi" -> "Go to Settings > Apps > Manage apps > Screen Time > Battery usage > No restrictions"
            "huawei" -> "Go to Settings > Apps > Apps > Screen Time > Battery > Don't optimize"
            "samsung" -> "Go to Settings > Apps > Screen Time > Battery > Optimize battery usage > Don't optimize"
            "oneplus" -> "Go to Settings > Apps > Screen Time > Battery optimization > Don't optimize"
            "oppo" -> "Go to Settings > Apps > Screen Time > Battery > Don't optimize"
            "vivo" -> "Go to Settings > Apps > Screen Time > Battery > Don't optimize"
            else -> "Go to Settings > Apps > Screen Time > Battery > Don't optimize"
        }
    }
    
    /**
     * Check if the app is whitelisted from auto-start restrictions
     */
    fun isAutoStartWhitelisted(): Boolean {
        // This would require checking OEM-specific settings
        // For now, return true as we can't reliably check this
        return true
    }
    
    /**
     * Request auto-start whitelist for OEMs
     */
    fun requestAutoStartWhitelist(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when (manufacturer) {
            "xiaomi" -> "Go to Security > Apps > Permissions > Autostart > Enable for Screen Time"
            "huawei" -> "Go to Settings > Apps > Apps > Screen Time > Permissions > Autostart"
            "samsung" -> "Go to Settings > Apps > Screen Time > Battery > Allow background activity"
            "oneplus" -> "Go to Settings > Apps > Screen Time > Battery > Allow background activity"
            "oppo" -> "Go to Settings > Apps > Screen Time > Battery > Allow background activity"
            "vivo" -> "Go to Settings > Apps > Screen Time > Battery > Allow background activity"
            else -> "Enable background activity for Screen Time in battery settings"
        }
    }
    
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    companion object {
        const val BATTERY_OPTIMIZATION_REQUEST_CODE = 1001
    }
}
