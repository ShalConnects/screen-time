// MainActivity.kt
package com.example.screentimeoverlay

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        
        startButton.setOnClickListener {
            if (checkOverlayPermission() && checkUsageStatsPermission()) {
                startOverlayService()
            } else {
                requestPermissions()
            }
        }
        
        stopButton.setOnClickListener {
            stopOverlayService()
        }
    }
    
    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }
    
    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    private fun requestPermissions() {
        if (!checkOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, 1001)
        }
        
        if (!checkUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Toast.makeText(this, "Please enable usage access for this app", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
        Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
    }
}

// OverlayService.kt
package com.example.screentimeoverlay

import android.app.Service
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import java.util.concurrent.TimeUnit

class OverlayService : Service() {
    
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: android.view.View
    private lateinit var timeTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var startTime = 0L
    
    override fun onCreate() {
        super.onCreate()
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)
        timeTextView = overlayView.findViewById(R.id.timeText)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 20
        params.y = 100
        
        windowManager.addView(overlayView, params)
        
        startTime = System.currentTimeMillis()
        startUpdating()
    }
    
    private fun startUpdating() {
        handler.post(object : Runnable {
            override fun run() {
                updateScreenTime()
                handler.postDelayed(this, 1000)
            }
        })
    }
    
    private fun updateScreenTime() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.DAYS.toMillis(1)
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        var totalTime = 0L
        usageStats?.forEach { stats ->
            totalTime += stats.totalTimeInForeground
        }
        
        val hours = TimeUnit.MILLISECONDS.toHours(totalTime)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(totalTime) % 60
        
        timeTextView.text = String.format("%02d:%02d", hours, minutes)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}

// activity_main.xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="32dp"
    android:background="#121212">
    
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Screen Time Monitor"
        android:textSize="28sp"
        android:textColor="#FFFFFF"
        android:textStyle="bold"
        android:layout_marginBottom="16dp"/>
    
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Reduce phone usage with awareness"
        android:textSize="16sp"
        android:textColor="#B0B0B0"
        android:layout_marginBottom="48dp"/>
    
    <Button
        android:id="@+id/startButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Start Overlay"
        android:textSize="18sp"
        android:padding="16dp"
        android:layout_marginBottom="16dp"
        android:backgroundTint="#4CAF50"/>
    
    <Button
        android:id="@+id/stopButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Stop Overlay"
        android:textSize="18sp"
        android:padding="16dp"
        android:backgroundTint="#F44336"/>
    
</LinearLayout>

// overlay_layout.xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.cardview.widget.CardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:elevation="8dp"
    app:cardCornerRadius="20dp"
    app:cardBackgroundColor="#00000000">
    
    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp"
        android:background="@drawable/glass_background"
        android:gravity="center">
        
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="TODAY"
            android:textSize="10sp"
            android:textColor="#E0E0E0"
            android:letterSpacing="0.1"
            android:layout_marginBottom="4dp"/>
        
        <TextView
            android:id="@+id/timeText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="00:00"
            android:textSize="20sp"
            android:textColor="#FFFFFF"
            android:textStyle="bold"
            android:fontFamily="monospace"/>
    </LinearLayout>
    
</androidx.cardview.widget.CardView>

// glass_background.xml (in res/drawable/)
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#4D1E1E1E"/>
    <corners android:radius="20dp"/>
    <stroke 
        android:width="1dp" 
        android:color="#4DFFFFFF"/>
</shape>

// AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.screentimeoverlay">
    
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
    <uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions"/>
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="Screen Time"
        android:theme="@style/Theme.AppCompat.DayNight.DarkActionBar">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        
        <service
            android:name=".OverlayService"
            android:exported="false"/>
        
    </application>
    
</manifest>

// build.gradle (app level)
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.cardview:cardview:1.0.0'
    implementation 'com.google.android.material:material:1.9.0'
}