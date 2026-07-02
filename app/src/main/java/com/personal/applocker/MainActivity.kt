package com.personal.applocker

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var calculatorDisplay: TextView
    private lateinit var vaultButton: Button
    private var calculatorInput = ""
    private var failedAttempts = 0
    private var isCalculating = false
    private lateinit var cameraExecutor: ExecutorService

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
        setContentView(R.layout.calculator_layout)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cameraExecutor = Executors.newSingleThreadExecutor()

        calculatorDisplay = findViewById(R.id.calculator_display)
        vaultButton = findViewById(R.id.vault_button)

        setupCalculatorButtons()

        // Check if PIN is set
        if (prefs.getString(PIN_KEY, null) == null) {
            showCreatePinDialog()
        }

        requestPermissions()
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
        checkUsageStatsPermission()
        checkOverlayPermission()
        startAppLockService()
    }

    private fun checkUsageStatsPermission() {
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
    }

    private fun checkOverlayPermission() {
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
    }

    private fun startAppLockService() {
        val intent = Intent(this, AppLockService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
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
            findViewById<Button>(id).setOnClickListener { view ->
                handleCalculatorInput((view as Button).text.toString())
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
                    val result = evaluateExpression(calculatorInput)
                    if (result == "2005") { // Secret PIN
                        openVault()
                    } else {
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
            // Simple calculator evaluation
            val parts = expression.split(Regex("(?<=[+\\-×÷])|(?=[+\\-×÷])"))
            var result = 0.0
            var currentOp = "+"
            
            for (part in parts) {
                when {
                    part == "+" -> currentOp = "+"
                    part == "−" -> currentOp = "-"
                    part == "×" -> currentOp = "*"
                    part == "÷" -> currentOp = "/"
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
                result.toString()
            }
        } catch (e: Exception) {
            "Error"
        }
    }

    private fun updateDisplay() {
        calculatorDisplay.text = if (calculatorInput.isEmpty()) "0" else calculatorInput
    }

    private fun checkPinAndOpenVault() {
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
                captureIntruderPhoto()
                Toast.makeText(this, "Wrong PIN! Attempt $failedAttempts", Toast.LENGTH_SHORT).show()
                if (failedAttempts >= 3) {
                    lockAppTemporarily()
                }
            }
        }
    }

    private fun showCreatePinDialog() {
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
    }

    private fun showPinDialog(onResult: (String) -> Unit) {
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
    }

    private fun openVault() {
        val intent = Intent(this, VaultActivity::class.java)
        startActivity(intent)
    }

    private fun captureIntruderPhoto() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                
                val imageCapture = ImageCapture.Builder()
                    .setTargetRotation(windowManager.defaultDisplay.rotation)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        this as LifecycleOwner, cameraSelector, preview, imageCapture
                    )

                    val photoFile = File(
                        getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "intruder_${System.currentTimeMillis()}.jpg"
                    )

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(this),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                saveIntruderPhoto(photoFile)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                // Failed to capture
                            }
                        }
                    )
                } catch (e: Exception) {
                    // Camera error
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            // Setup error
        }
    }

    private fun saveIntruderPhoto(file: File) {
        val intruderDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            INTRUDER_DIR
        )
        if (!intruderDir.exists()) intruderDir.mkdirs()

        val destFile = File(intruderDir, file.name)
        file.copyTo(destFile, overwrite = true)

        // Save to MediaStore for gallery visibility
        val values = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$INTRUDER_DIR")
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun lockAppTemporarily() {
        vaultButton.isEnabled = false
        Toast.makeText(this, "App locked for 30 seconds!", Toast.LENGTH_LONG).show()
        vaultButton.postDelayed({
            vaultButton.isEnabled = true
            failedAttempts = 0
        }, 30000)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}