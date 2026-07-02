package com.personal.applocker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VaultActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: Button
    private lateinit var restoreButton: Button
    private lateinit var selectAppsButton: Button
    private val vaultFiles = mutableListOf<File>()
    private lateinit var adapter: VaultAdapter

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            addToVault(uri)
        }
        loadVaultFiles()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.vault_layout)

            recyclerView = findViewById(R.id.vault_recycler)
            addButton = findViewById(R.id.add_photos_button)
            restoreButton = findViewById(R.id.restore_button)
            selectAppsButton = findViewById(R.id.select_apps_button)

            recyclerView.layoutManager = GridLayoutManager(this, 3)
            adapter = VaultAdapter(vaultFiles) { file ->
                showPhotoOptions(file)
            }
            recyclerView.adapter = adapter

            addButton.setOnClickListener {
                try {
                    pickImageLauncher.launch("image/*")
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            restoreButton.setOnClickListener {
                restoreAllPhotos()
            }

            selectAppsButton.setOnClickListener {
                try {
                    startActivity(Intent(this, AppSelectionActivity::class.java))
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open app selection", Toast.LENGTH_SHORT).show()
                }
            }

            loadVaultFiles()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadVaultFiles() {
        try {
            val vaultDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                MainActivity.VAULT_DIR
            )
            if (!vaultDir.exists()) vaultDir.mkdirs()

            vaultFiles.clear()
            vaultDir.listFiles()?.let { files ->
                vaultFiles.addAll(files.filter { 
                    it.extension.lowercase() == "jpg" || it.extension.lowercase() == "png" 
                })
            }
            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot load photos: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToVault(imageUri: Uri) {
        try {
            val vaultDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                MainActivity.VAULT_DIR
            )
            if (!vaultDir.exists()) vaultDir.mkdirs()

            val fileName = "vault_${System.currentTimeMillis()}.jpg"
            val destFile = File(vaultDir, fileName)

            contentResolver.openInputStream(imageUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            Toast.makeText(this, "Photo added to vault!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to add photo: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPhotoOptions(file: File) {
        try {
            val options = arrayOf("View", "Restore to Gallery", "Delete")
            AlertDialog.Builder(this)
                .setTitle("Photo Options")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> viewPhoto(file)
                        1 -> restorePhoto(file)
                        2 -> deletePhoto(file)
                    }
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun viewPhoto(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(
                androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                ),
                "image/*"
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot view photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restorePhoto(file: File) {
        try {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "restored_${file.name}")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
            uri?.let {
                contentResolver.openOutputStream(it)?.use { output ->
                    FileInputStream(file).use { input ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, "Photo restored to gallery!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deletePhoto(file: File) {
        try {
            file.delete()
            loadVaultFiles()
            Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot delete photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restoreAllPhotos() {
        try {
            var count = 0
            vaultFiles.forEach { file ->
                restorePhoto(file)
                count++
            }
            Toast.makeText(this, "$count photos restored to gallery!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show()
        }
    }
}

class VaultAdapter(
    private val files: List<File>,
    private val onClick: (File) -> Unit
) : RecyclerView.Adapter<VaultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.vault_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.vault_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val file = files[position]
            holder.imageView.setImageURI(
                androidx.core.content.FileProvider.getUriForFile(
                    holder.itemView.context,
                    "${holder.itemView.context.packageName}.fileprovider",
                    file
                )
            )
            holder.itemView.setOnClickListener { onClick(file) }
        } catch (e: Exception) {
            // Skip images that can't be loaded
        }
    }

    override fun getItemCount() = files.size
}