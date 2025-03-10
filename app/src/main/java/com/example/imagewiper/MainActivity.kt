package com.example.imagewiper

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.palette.graphics.Palette

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var undoButton: Button
    private lateinit var rootLayout: View
    private lateinit var gestureDetector: GestureDetectorCompat

    private var imageUris: List<Uri> = listOf()
    private var currentIndex = 0

    private var lastDeletedUri: Uri? = null
    private var lastDeletedIndex: Int = -1

    companion object {
        private const val REQUEST_CODE_READ_EXTERNAL = 100
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        undoButton = findViewById(R.id.undoButton)
        // Ensure the XML layout contains a view with id "backgroundView"
        rootLayout = findViewById(R.id.backgroundView)

        // Initialize the gesture detector for swipe events
        gestureDetector = GestureDetectorCompat(this, SwipeGestureListener())

        // Set the touch listener on the imageView
        imageView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

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

        undoButton.setOnClickListener {
            if (lastDeletedUri != null) {
                // Undo deletion: reinsert the deleted image into the list
                val mutableList = imageUris.toMutableList()
                if (lastDeletedIndex < 0 || lastDeletedIndex > mutableList.size) {
                    // If index is invalid, append the image at the end
                    mutableList.add(lastDeletedUri!!)
                    currentIndex = mutableList.size - 1
                } else {
                    mutableList.add(lastDeletedIndex, lastDeletedUri!!)
                    currentIndex = lastDeletedIndex
                }
                imageUris = mutableList.toList()
                imageView.setImageURI(lastDeletedUri)
                Toast.makeText(this, "Image restored", Toast.LENGTH_SHORT).show()
                // Clear the last deleted info and hide the undo button
                lastDeletedUri = null
                lastDeletedIndex = -1
                undoButton.visibility = View.VISIBLE
                showPreviousImage()

            } else {
                Toast.makeText(this, "Nothing to delete", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPreviousImage() {
        if (imageUris.isNotEmpty()) {
            currentIndex = if (currentIndex - 1 < 0) imageUris.size - 1 else currentIndex - 1
            imageView.setImageURI(imageUris[currentIndex])
        } else {
            Log.e("MainActivity", "No images found in gallery.")
        }
        applyDynamicBackground()
    }

    private fun loadImagesFromGallery() {
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

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


    private fun applyDynamicBackground() {
        val drawable = imageView.drawable
        if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            updateBackgroundWithGradientBlur(bitmap, rootLayout)
        }
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

    // Inner class for detecting swipe gestures
    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        @Suppress("NOTHING_TO_OVERRIDE", "ACCIDENTAL_OVERRIDE")
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent?,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null || e2 == null) return false
            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y
            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        onSwipeRight()
                    } else {
                        onSwipeLeft()
                    }
                    return true
                }
            }
            return false
        }
    }


    private fun onSwipeLeft() {
        imageView.animate()
            .translationX(-imageView.width.toFloat())
            .setDuration(300)
            .withEndAction {
                if (imageUris.isNotEmpty()) {
                    // Save the current image info for undo
                    lastDeletedUri = imageUris[currentIndex]
                    lastDeletedIndex = currentIndex

                    // Remove the image from the list (simulate deletion)
                    val mutableList = imageUris.toMutableList()
                    mutableList.removeAt(currentIndex)
                    imageUris = mutableList.toList()

                    // Show the next image, if available; otherwise clear the ImageView
                    if (imageUris.isNotEmpty()) {
                        // Adjust currentIndex if necessary
                        currentIndex %= imageUris.size
                        imageView.setImageURI(imageUris[currentIndex])
                    } else {
                        imageView.setImageDrawable(null)
                    }

                    // Update the undo button text and show it
//                    undoButton.text = "Undo Deleted Image"
//                    undoButton.visibility = View.VISIBLE
                }
                // Reset translation for the next interaction
                imageView.translationX = 0f
            }
            .start()

        // Placeholder: Add functionality for left swipe here
        Toast.makeText(this, "Image Deleted", Toast.LENGTH_SHORT).show()
    }


    private fun onSwipeRight() {
        // Animate the image sliding to the right
        imageView.animate()
            .translationX(imageView.width.toFloat())
            .setDuration(300)
            .withEndAction {
                // After animation completes, reset translation and load the previous image
                imageView.translationX = 0f
                showNextImage()
            }
            .start()
        // Placeholder: Add functionality for right swipe here
        Toast.makeText(this, "Keeping Image", Toast.LENGTH_SHORT).show()
    }

//    private fun deleteCurrentImage() {
//        if (imageUris.isNotEmpty()) {
//            val currentUri = imageUris[currentIndex]
//            try {
//                val rowsDeleted = contentResolver.delete(currentUri, null, null)
//                if (rowsDeleted > 0) {
//                    Toast.makeText(this, "Image deleted", Toast.LENGTH_SHORT).show()
//                    // Remove the deleted image from your list
//                    val mutableList = imageUris.toMutableList()
//                    mutableList.removeAt(currentIndex)
//                    imageUris = mutableList.toList()
//                    // Adjust the index if needed
//                    if (currentIndex >= imageUris.size && imageUris.isNotEmpty()) {
//                        currentIndex = 0
//                    }
//                } else {
//                    Toast.makeText(this, "Unable to delete image", Toast.LENGTH_SHORT).show()
//                }
//            } catch (e: Exception) {
//                Log.e("MainActivity", "Error deleting image: ${e.message}")
//                Toast.makeText(this, "Error deleting image", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }

    // Top-level functions for background update remain unchanged
    private fun updateBackgroundWithGradientBlur(bitmap: Bitmap, containerView: View) {
        Palette.from(bitmap).generate { palette ->
            val dominantColor = palette?.getDominantColor(Color.BLACK) ?: Color.BLACK
            val darkerColor = manipulateColor(dominantColor, 0.8f)
            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(dominantColor, darkerColor)
            )
            containerView.background = gradientDrawable

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurEffect = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
                containerView.setRenderEffect(blurEffect)
            }
        }
    }

    private fun manipulateColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceAtMost(255)
        val g = (Color.green(color) * factor).toInt().coerceAtMost(255)
        val b = (Color.blue(color) * factor).toInt().coerceAtMost(255)
        return Color.argb(a, r, g, b)
    }
}