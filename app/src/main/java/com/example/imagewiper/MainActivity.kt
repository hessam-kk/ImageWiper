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


import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable


import android.view.View

class MainActivity : AppCompatActivity() {


    private lateinit var imageView: ImageView
    private lateinit var nextButton: Button
    private lateinit var rootLayout: View

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
        rootLayout =
            findViewById(R.id.backgroundView)  // Ensure this ID exists in activity_main.xml

        // Use the correct permission based on API level
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, readPermission)
            != PackageManager.PERMISSION_GRANTED
        ) {
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
        if (ContextCompat.checkSelfPermission(
                this,
                readPermission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
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
                Log.e(
                    "MainActivity",
                    "Query returned null. Check permissions or if gallery is empty."
                )
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
        } else {
            Log.e("MainActivity", "imageUris is Empty.")
        }
        applyDynamicBackground()
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
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            loadImagesFromGallery()
        }
    }


    private fun updateBackgroundWithGradientBlur(bitmap: Bitmap, containerView: View) {
        // Generate a Palette asynchronously
        Palette.from(bitmap).generate { palette ->
            // Use the dominant color as the start of the gradient (fallback to black if null)
            val dominantColor = palette?.getDominantColor(Color.BLACK) ?: Color.BLACK
            // Optionally, you can create a darker variant for the gradient end
            val darkerColor = manipulateColor(dominantColor, 0.8f)

            // Create a vertical gradient from the dominant color to the darker color (or transparent)
            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(dominantColor, darkerColor)
            )

            // Set the gradient as the background of your container (could be the root layout or card)
            containerView.background = gradientDrawable

            // Optionally apply a blur effect if running on API 31 or above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurEffect = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
                containerView.setRenderEffect(blurEffect)
            }
        }
    }

        // Utility function to darken a color
        private fun manipulateColor(color: Int, factor: Float): Int {
            val a = Color.alpha(color)
            val r = (Color.red(color) * factor).toInt().coerceAtMost(255)
            val g = (Color.green(color) * factor).toInt().coerceAtMost(255)
            val b = (Color.blue(color) * factor).toInt().coerceAtMost(255)
            return Color.argb(a, r, g, b)
        }

            private fun applyDynamicBackground() {
                val drawable = imageView.drawable
                if (drawable is BitmapDrawable) {
                    val bitmap = drawable.bitmap
                    // Pass the initialized backgroundView instead of rootLayout
                    updateBackgroundWithGradientBlur(bitmap, rootLayout)
                }
            }
        }
