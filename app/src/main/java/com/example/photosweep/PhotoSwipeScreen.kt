package com.example.photosweep

import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PhotoSwipeScreen(
    monthGroup: MonthGroup,
    startFromIndex: Int,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentIndex by remember { mutableIntStateOf(startFromIndex) }
    val photos = monthGroup.photos
    val totalPhotos = photos.size

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* user returned from settings */ }

    LaunchedEffect(currentIndex) {
        if (currentIndex < totalPhotos) {
            MonthProgressStore.setReviewedIndex(context, monthGroup.yearMonth, currentIndex)
        }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= totalPhotos && totalPhotos > 0) {
            MonthProgressStore.markMonthCompleted(context, monthGroup.yearMonth)
            onComplete()
        }
    }

    if (totalPhotos == 0) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No photos in this month", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    if (currentIndex >= totalPhotos) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Month complete!", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "${monthGroup.yearMonth} \u2014 $totalPhotos photos reviewed",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    val currentPhoto = photos[currentIndex]
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer {
                    rotationZ = offsetX / 50f
                    alpha = 1f - (kotlin.math.abs(offsetX) / 1000f).coerceIn(0f, 0.5f)
                }
                .pointerInput(currentIndex) {
                    detectDragGestures(
                        onDragEnd = {
                            val threshold = 200f
                            when {
                                offsetX > threshold -> {
                                    val photoToDelete = photos[currentIndex]
                                    currentIndex++
                                    offsetX = 0f
                                    offsetY = 0f

                                    scope.launch {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                            !Environment.isExternalStorageManager()
                                        ) {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                android.net.Uri.parse("package:${context.packageName}")
                                            )
                                            manageStorageLauncher.launch(intent)
                                            snackbarHostState.showSnackbar(
                                                message = "Grant \"All files access\" then swipe again",
                                                duration = SnackbarDuration.Short
                                            )
                                            return@launch
                                        }

                                        val moved = PhotoRepository.trashPhoto(context, photoToDelete)
                                        if (moved) {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Photo trashed",
                                                actionLabel = "Undo",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                PhotoRepository.restorePhoto(context, photoToDelete)
                                            }
                                        } else {
                                            Toast.makeText(context, "Failed to trash photo", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                offsetX < -threshold -> {
                                    currentIndex++
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                                else -> {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    )
                },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = currentPhoto.uri,
                    contentDescription = currentPhoto.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (offsetX > 100f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(16.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Red.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Trash",
                            tint = Color.White
                        )
                    }
                }
                if (offsetX < -100f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(16.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Green.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Keep",
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = "${currentIndex + 1} / $totalPhotos",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(
                            Color.Black.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
