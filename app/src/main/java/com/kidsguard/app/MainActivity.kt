package com.kidsguard.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnAccessibility: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvAccessibility: TextView

    private var guardRunning = false

    companion object {
        const val PERM_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        tvStatus = findViewById(R.id.tvStatus)
        tvAccessibility = findViewById(R.id.tvAccessibility)

        btnToggle.setOnClickListener {
            if (guardRunning) stopGuard() else startGuard()
        }

        btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    // ──────────────────────────────────────────
    // Start / Stop
    // ──────────────────────────────────────────

    private fun startGuard() {
        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Accessibility Required")
                .setMessage("KidsGuard needs Accessibility permission to pause YouTube Kids.\n\nTap 'Open Settings', find KidsGuard and enable it.")
                .setPositiveButton("Open Settings") { _, _ -> openAccessibilitySettings() }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERM_REQUEST)
        } else {
            launchService()
        }
    }

    private fun launchService() {
        val intent = Intent(this, FaceWatchService::class.java)
        ContextCompat.startForegroundService(this, intent)
        guardRunning = true
        tvStatus.text = "🟢  Guard is ON — watching…"
        tvStatus.setTextColor(0xFF388E3C.toInt())
        btnToggle.text = "⏹  Stop Guard"
        btnToggle.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFFEF5350.toInt())
    }

    private fun stopGuard() {
        val intent = Intent(this, FaceWatchService::class.java)
        stopService(intent)
        guardRunning = false
        tvStatus.text = "⚫  Guard is OFF"
        tvStatus.setTextColor(0xFF666666.toInt())
        btnToggle.text = "▶  Start Guard"
        btnToggle.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF29B6F6.toInt())
    }

    // ──────────────────────────────────────────
    // Accessibility helpers
    // ──────────────────────────────────────────

    private fun isAccessibilityEnabled(): Boolean {
        val serviceName = "${packageName}/${YTKidsAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return TextUtils.SimpleStringSplitter(':').let { splitter ->
            splitter.setString(enabledServices)
            splitter.any { it.equals(serviceName, ignoreCase = true) }
        }
    }

    private fun updateAccessibilityStatus() {
        if (isAccessibilityEnabled()) {
            tvAccessibility.text = "🟢  Accessibility: Enabled ✓"
            tvAccessibility.setTextColor(0xFF388E3C.toInt())
        } else {
            tvAccessibility.text = "⚫  Accessibility: NOT enabled"
            tvAccessibility.setTextColor(0xFF999999.toInt())
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    // ──────────────────────────────────────────
    // Permission result
    // ──────────────────────────────────────────

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                launchService()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Camera Required")
                    .setMessage("KidsGuard needs camera access to detect when your child is watching.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
