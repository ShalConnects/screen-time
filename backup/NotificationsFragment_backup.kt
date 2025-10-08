package com.example.screentimeoverlay

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.Fragment

class NotificationsFragment : Fragment(R.layout.fragment_notifications) {

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val notificationSettingsButton = view.findViewById<Button>(R.id.notificationSettingsButton)
        val testNotificationButton = view.findViewById<Button>(R.id.testNotificationButton)
        val reminderSettingsButton = view.findViewById<Button>(R.id.reminderSettingsButton)
        val breakSettingsButton = view.findViewById<Button>(R.id.breakSettingsButton)

        // Get reference to MainActivity to access its methods
        val mainActivity = requireActivity() as MainActivity

        notificationSettingsButton.setOnClickListener {
            mainActivity.showNotificationSettings()
        }

        testNotificationButton.setOnClickListener {
            mainActivity.testSmartNotifications()
        }

        reminderSettingsButton.setOnClickListener {
            mainActivity.showReminderSettings()
        }

        breakSettingsButton.setOnClickListener {
            mainActivity.showBreakSettings()
        }
    }
}
