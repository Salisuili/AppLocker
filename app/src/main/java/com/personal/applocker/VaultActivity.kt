package com.personal.applocker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
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
            pickImageLauncher.launch("image/*")
        }

        restoreButton.setOnClickListener {
            restoreAllPhotos()
        }

        selectAppsButton.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        loadVaultFiles()
    }

    private fun loadVaultFiles() {
        val vaultDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            MainActivity.VAULT_DIR
        )
        if (!vaultDir.exists()) vaultDir.mkdirs()

        vaultFiles.clear()
        vaultDir.listFiles()?.let { files ->
            vaultFiles.addAll(files.filter { it.extension == "jpg" || it.extension == "png" })
        }
        adapter.notifyDataSetChanged()
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
            Toast.makeText(this, "Failed to add photo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPhotoOptions(file: File) {
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
    }

    private fun viewPhoto(file: File) {
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
            Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deletePhoto(file: File) {
        file.delete()
        loadVaultFiles()
        Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
    }

    private fun restoreAllPhotos() {
        vaultFiles.forEach { file ->
            restorePhoto(file)
        }
        Toast.makeText(this, "All photos restored to gallery!", Toast.LENGTH_LONG).show()
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
        val file = files[position]
        holder.imageView.setImageURI(androidx.core.content.FileProvider.getUriForFile(
            holder.itemView.context,
            "${holder.itemView.context.packageName}.fileprovider",
            file
        ))
        holder.itemView.setOnClickListener { onClick(file) }
    }

    override fun getItemCount() = files.size
}