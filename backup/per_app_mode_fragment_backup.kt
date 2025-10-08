// Backup of Per-App Mode handling from MonitorFragment.kt
// This file contains the backed up per-app mode functionality from MonitorFragment

// 1. Switch reference (line 32)
val perAppSwitch = view.findViewById<Switch>(R.id.perAppSwitch)

// 2. Switch listener (lines 77-83)
perAppSwitch.setOnCheckedChangeListener { _, isChecked ->
    val intent = Intent(requireContext(), OverlayService::class.java)
    intent.putExtra("action", "toggle_per_app_mode")
    intent.putExtra("enabled", isChecked)
    requireContext().startService(intent)
    Toast.makeText(requireContext(), "Per-app mode: $isChecked", Toast.LENGTH_SHORT).show()
}
