package com.example.screentimeoverlay

import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.Fragment

class PersonalFragment : Fragment(R.layout.fragment_personal) {

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val customGoalsButton = view.findViewById<Button>(R.id.customGoalsButton)
        val appCategoriesButton = view.findViewById<Button>(R.id.appCategoriesButton)
        val timezoneButton = view.findViewById<Button>(R.id.timezoneButton)
        val profilesButton = view.findViewById<Button>(R.id.profilesButton)
        val personalizationButton = view.findViewById<Button>(R.id.personalizationButton)

        // Get reference to MainActivity to access its methods
        val mainActivity = requireActivity() as MainActivity

        customGoalsButton.setOnClickListener {
            mainActivity.showCustomGoalsSettings()
        }

        appCategoriesButton.setOnClickListener {
            mainActivity.showAppCategoriesSettings()
        }

        timezoneButton.setOnClickListener {
            mainActivity.showTimezoneSettings()
        }

        profilesButton.setOnClickListener {
            mainActivity.showProfilesSettings()
        }

        personalizationButton.setOnClickListener {
            mainActivity.showPersonalizationSummary()
        }
    }
}
