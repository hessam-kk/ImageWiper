package com.example.imagewiper // Replace with your actual package name

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.imagewiper.R // Add this import
import android.os.Build
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity() {


    private lateinit var imageView: ImageView
    private lateinit var nextButton: Button

    private var imageUris: List<Uri> = listOf()
    private var currentIndex = 0

    companion object {
        private const val REQUEST_CODE_READ_EXTERNAL = 100
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        nextButton = findViewById(R.id.nextButton)

        // Use the correct permission based on API level
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, readPermission)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(readPermission),
                REQUEST_CODE_READ_EXTERNAL
            )
        } else {
            loadImagesFromGallery()
        }

        nextButton.setOnClickListener {
            processCurrentImage()
            showNextImage()
        }
    }


    private fun loadImagesFromGallery() {
        // Determine the correct permission for your Android version
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        // Check if permission is granted (this should normally pass since we checked in onCreate)
        if (ContextCompat.checkSelfPermission(this, readPermission) != PackageManager.PERMISSION_GRANTED) {
            Log.e("MainActivity", "Permission not granted: $readPermission")
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val uris = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            if (cursor == null) {
                Log.e("MainActivity", "Query returned null. Check permissions or if gallery is empty.")
                Toast.makeText(this, "Could not access gallery", Toast.LENGTH_SHORT).show()
                return
            }

            cursor.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val name = c.getString(nameColumn)
                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    uris.add(contentUri)
                    Log.d("MainActivity", "Added image: $name, URI: $contentUri")
                }
            }

            imageUris = uris
            currentIndex = 0

            if (imageUris.isNotEmpty()) {
                Log.d("MainActivity", "Loading first image from ${imageUris.size} images")
                imageView.setImageURI(imageUris[currentIndex])
            } else {
                Log.e("MainActivity", "No images found in the gallery.")
                Toast.makeText(this, "No images found in gallery", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading images: ${e.message}")
            Toast.makeText(this, "Error loading images", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showNextImage() {
        if (imageUris.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % imageUris.size
            imageView.setImageURI(imageUris[currentIndex])
        }
        else {
            Log.e("MainActivity", "imageUris is Empty.")
        }
    }

    private fun processCurrentImage() {
        Log.i("MainActivity", "Processing current image.")

//        println("Processing image: ${imageUris[currentIndex]}")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_EXTERNAL &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadImagesFromGallery()
        }
    }
}