package com.personal.applocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.io.File

class LockScreenActivity : Activity() {

    private lateinit var pinInput: EditText
    private lateinit var unlockButton: Button
    private var failedAttempts = 0
    private var lockedPackage = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lock_screen_layout)

        lockedPackage = intent.getStringExtra("locked_package") ?: ""

        pinInput = findViewById(R.id.lock_pin_input)
        unlockButton = findViewById(R.id.unlock_button)

        unlockButton.setOnClickListener {
            checkPin()
        }
    }

    private fun checkPin() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val savedPin = prefs.getString(MainActivity.PIN_KEY, "")

        if (pinInput.text.toString() == savedPin) {
            finish()
        } else {
            failedAttempts++
            captureIntruderPhoto()
            Toast.makeText(this, "Wrong PIN!", Toast.LENGTH_SHORT).show()
            
            if (failedAttempts >= 3) {
                Toast.makeText(this, "Too many attempts! Returning to home.", Toast.LENGTH_LONG).show()
                goToHome()
            }
        }
    }

    private fun captureIntruderPhoto() {
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder().build()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        this, cameraSelector, imageCapture
                    )

                    val photoFile = File(
                        getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                        "lock_intruder_${System.currentTimeMillis()}.jpg"
                    )

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(this),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                val intruderDir = File(
                                    Environment.getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_PICTURES
                                    ),
                                    MainActivity.INTRUDER_DIR
                                )
                                if (!intruderDir.exists()) intruderDir.mkdirs()
                                photoFile.copyTo(
                                    File(intruderDir, photoFile.name),
                                    overwrite = true
                                )
                            }
                            override fun onError(exception: ImageCaptureException) {}
                        }
                    )
                } catch (e: Exception) {}
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {}
    }

    private fun goToHome() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Block back button
        goToHome()
    }
}