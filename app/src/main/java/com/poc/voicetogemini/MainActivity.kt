package com.poc.voicetogemini

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.poc.voicetogemini.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var geminiClient: GeminiClient

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        updatePermissionStatuses()
        if (isGranted) {
            Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geminiClient = GeminiClient(this)

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun setupUI() {
        // Load existing API key
        val savedKey = geminiClient.getSavedApiKey()
        if (savedKey.isNotEmpty()) {
            binding.etApiKey.setText(savedKey)
        }

        // Save API Key
        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            geminiClient.saveApiKey(key)
            Toast.makeText(this, "API Key saved successfully", Toast.LENGTH_SHORT).show()
        }

        // Reset Saved Position
        binding.btnResetPosition.setOnClickListener {
            VoicePolishAccessibilityService.instance?.resetSavedPosition()
            Toast.makeText(this, "Star position reset to default", Toast.LENGTH_SHORT).show()
        }

        // Permission Handlers
        binding.btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } else {
                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable 'Voice to Gemini' in Accessibility services", Toast.LENGTH_LONG).show()
        }

        binding.btnGrantAudio.setOnClickListener {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Test Polish Live
        binding.btnTestPolish.setOnClickListener {
            val text = binding.etSampleText.text?.toString()?.trim() ?: ""
            if (text.isEmpty()) {
                binding.tvTestResult.text = "Please enter text to test."
                return@setOnClickListener
            }

            val apiKey = geminiClient.getSavedApiKey()
            if (apiKey.isEmpty()) {
                binding.tvTestResult.text = "Error: Please save your Gemini API key first."
                return@setOnClickListener
            }

            binding.btnTestPolish.isEnabled = false
            binding.tvTestResult.text = "Polishing with Gemini (${GeminiClient.MODEL_PRIMARY})..."

            lifecycleScope.launch {
                val result = geminiClient.polishText(text)
                binding.btnTestPolish.isEnabled = true
                result.onSuccess { polished ->
                    binding.tvTestResult.text = "✨ Result:\n$polished"
                }.onFailure { error ->
                    binding.tvTestResult.text = "❌ Error:\n${error.message}"
                }
            }
        }
    }

    private fun updatePermissionStatuses() {
        // Overlay Status
        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (hasOverlay) {
            binding.tvOverlayStatus.text = "✓ Display Over Other Apps: Granted"
            binding.tvOverlayStatus.setTextColor(0xFF00E676.toInt())
            binding.btnGrantOverlay.visibility = View.GONE
        } else {
            binding.tvOverlayStatus.text = "✗ Display Over Other Apps: Required"
            binding.tvOverlayStatus.setTextColor(0xFFFF5252.toInt())
            binding.btnGrantOverlay.visibility = View.VISIBLE
        }

        // Accessibility Status
        val hasAccessibility = isAccessibilityServiceEnabled(this, VoicePolishAccessibilityService::class.java)
        if (hasAccessibility) {
            binding.tvAccessibilityStatus.text = "✓ Accessibility Service: Enabled"
            binding.tvAccessibilityStatus.setTextColor(0xFF00E676.toInt())
            binding.btnGrantAccessibility.visibility = View.GONE
        } else {
            binding.tvAccessibilityStatus.text = "✗ Accessibility Service: Disabled"
            binding.tvAccessibilityStatus.setTextColor(0xFFFF5252.toInt())
            binding.btnGrantAccessibility.visibility = View.VISIBLE
        }

        // Audio Permission
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            binding.tvAudioStatus.text = "✓ Microphone: Granted"
            binding.tvAudioStatus.setTextColor(0xFF00E676.toInt())
            binding.btnGrantAudio.visibility = View.GONE
        } else {
            binding.tvAudioStatus.text = "✗ Microphone: Optional"
            binding.tvAudioStatus.setTextColor(0xFF9E9EA8.toInt())
            binding.btnGrantAudio.visibility = View.VISIBLE
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${serviceClass.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any {
            it.equals(expectedComponentName, ignoreCase = true)
        }
    }
}
