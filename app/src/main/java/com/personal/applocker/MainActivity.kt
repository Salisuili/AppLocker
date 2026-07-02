package com.personal.applocker

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var calculatorDisplay: TextView
    private lateinit var vaultButton: Button
    private var calculatorInput = ""
    private var failedAttempts = 0

    companion object {
        const val PREFS_NAME = "app_locker_prefs"
        const val PIN_KEY = "user_pin"
        const val VAULT_DIR = "AppLockerVault"
        const val INTRUDER_DIR = "IntruderPhotos"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            setupApp()
        } else {
            Toast.makeText(this, "Permissions required for app to work", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.calculator_layout)

            prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            calculatorDisplay = findViewById(R.id.calculator_display)
            vaultButton = findViewById(R.id.vault_button)

            setupCalculatorButtons()

            // Check if PIN is set
            if (prefs.getString(PIN_KEY, null) == null) {
                showCreatePinDialog()
            }

            requestPermissions()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun setupApp() {
        try {
            checkUsageStatsPermission()
            checkOverlayPermission()
        } catch (e: Exception) {
            // Continue even if setup fails
        }
    }

    private fun checkUsageStatsPermission() {
        try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
            if (mode != AppOpsManager.MODE_ALLOWED) {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("To lock apps, please enable Usage Access for this app")
                    .setPositiveButton("Enable") { _, _ ->
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    private fun checkOverlayPermission() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    AlertDialog.Builder(this)
                        .setTitle("Permission Required")
                        .setMessage("Allow display over other apps for lock screen")
                        .setPositiveButton("Enable") { _, _ ->
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    private fun setupCalculatorButtons() {
        val buttonIds = listOf(
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9,
            R.id.btn_add, R.id.btn_subtract, R.id.btn_multiply, R.id.btn_divide,
            R.id.btn_equal, R.id.btn_clear, R.id.btn_decimal
        )

        buttonIds.forEach { id ->
            try {
                findViewById<Button>(id).setOnClickListener { view ->
                    handleCalculatorInput((view as Button).text.toString())
                }
            } catch (e: Exception) {
                // Skip if button not found
            }
        }

        vaultButton.setOnClickListener {
            checkPinAndOpenVault()
        }
    }

    private fun handleCalculatorInput(input: String) {
        when (input) {
            "C" -> {
                calculatorInput = ""
                failedAttempts = 0
                updateDisplay()
            }
            "=" -> {
                try {
                    if (calculatorInput == "2005") {
                        openVault()
                    } else {
                        val result = evaluateExpression(calculatorInput)
                        calculatorInput = result
                        updateDisplay()
                    }
                } catch (e: Exception) {
                    calculatorInput = "Error"
                    updateDisplay()
                }
            }
            else -> {
                calculatorInput += input
                updateDisplay()
            }
        }
    }

    private fun evaluateExpression(expression: String): String {
        return try {
            // Replace symbols for calculation
            val expr = expression
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
            
            // Simple calculator - only handles basic operations
            val parts = expr.split(Regex("(?<=[+\\-*/])|(?=[+\\-*/])"))
            if (parts.size == 1) {
                return parts[0]
            }
            
            var result = 0.0
            var currentOp = "+"
            
            for (part in parts) {
                when (part) {
                    "+" -> currentOp = "+"
                    "-" -> currentOp = "-"
                    "*" -> currentOp = "*"
                    "/" -> currentOp = "/"
                    else -> {
                        val num = part.toDoubleOrNull() ?: 0.0
                        when (currentOp) {
                            "+" -> result += num
                            "-" -> result -= num
                            "*" -> result *= num
                            "/" -> result /= if (num != 0.0) num else 1.0
                        }
                    }
                }
            }
            
            if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                String.format("%.2f", result)
            }
        } catch (e: Exception) {
            "Error"
        }
    }

    private fun updateDisplay() {
        calculatorDisplay.text = if (calculatorInput.isEmpty()) "0" else calculatorInput
    }

    private fun checkPinAndOpenVault() {
        try {
            val savedPin = prefs.getString(PIN_KEY, null)
            if (savedPin == null) {
                showCreatePinDialog()
                return
            }
            showPinDialog { enteredPin ->
                if (enteredPin == savedPin) {
                    openVault()
                    failedAttempts = 0
                } else {
                    failedAttempts++
                    Toast.makeText(this, "Wrong PIN! Attempt $failedAttempts", Toast.LENGTH_SHORT).show()
                    if (failedAttempts >= 3) {
                        lockAppTemporarily()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreatePinDialog() {
        try {
            val dialogView = layoutInflater.inflate(R.layout.pin_dialog, null)
            val pinInput = dialogView.findViewById<EditText>(R.id.pin_input)
            val confirmPinInput = dialogView.findViewById<EditText>(R.id.confirm_pin_input)

            AlertDialog.Builder(this)
                .setTitle("Create PIN")
                .setView(dialogView)
                .setPositiveButton("Save") { dialog, _ ->
                    val pin = pinInput.text.toString()
                    val confirmPin = confirmPinInput.text.toString()
                    if (pin.length == 4 && pin == confirmPin) {
                        prefs.edit().putString(PIN_KEY, pin).apply()
                        Toast.makeText(this, "PIN created successfully!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this, "PINs don't match or not 4 digits", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error creating PIN dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPinDialog(onResult: (String) -> Unit) {
        try {
            val dialogView = layoutInflater.inflate(R.layout.pin_dialog, null)
            val pinInput = dialogView.findViewById<EditText>(R.id.pin_input)
            dialogView.findViewById<EditText>(R.id.confirm_pin_input).visibility = View.GONE

            AlertDialog.Builder(this)
                .setTitle("Enter PIN")
                .setView(dialogView)
                .setPositiveButton("OK") { _, _ ->
                    onResult(pinInput.text.toString())
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error showing PIN dialog", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVault() {
        try {
            val intent = Intent(this, VaultActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open vault: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lockAppTemporarily() {
        vaultButton.isEnabled = false
        Toast.makeText(this, "App locked for 30 seconds!", Toast.LENGTH_LONG).show()
        vaultButton.postDelayed({
            vaultButton.isEnabled = true
            failedAttempts = 0
        }, 30000)
    }
}