package com.example.screentimeoverlay

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OnboardingActivitySimple : AppCompatActivity() {

    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_simple)

        preferences = getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        
        val btnContinue = findViewById<Button>(R.id.btn_continue)
        val btnSkip = findViewById<Button>(R.id.btn_skip)
        
        btnContinue.setOnClickListener {
            completeOnboarding()
        }
        
        btnSkip.setOnClickListener {
            completeOnboarding()
        }
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
