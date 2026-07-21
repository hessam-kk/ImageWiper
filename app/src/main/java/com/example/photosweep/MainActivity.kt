package com.example.photosweep

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                PhotoSweepApp()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoSweepApp() {
    val context = LocalContext.current

    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val readPermissionState = rememberPermissionState(readPermission)
    val writePermissionState = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else null

    var manageStorageRequested by remember { mutableStateOf(false) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        manageStorageRequested = false
    }

    val needsRead = !readPermissionState.status.isGranted
    val needsWrite = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            writePermissionState?.status?.isGranted == false
    val needsManage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager() &&
            !manageStorageRequested

    val allGranted = !needsRead && !needsWrite && !needsManage

    if (allGranted) {
        PhotoSweepContent()
    } else {
        LaunchedEffect(Unit) {
            readPermissionState.launchPermissionRequest()
        }
        if (needsWrite && readPermissionState.status.isGranted) {
            LaunchedEffect(Unit) {
                writePermissionState?.launchPermissionRequest()
            }
        }
        if (needsManage && readPermissionState.status.isGranted && !needsWrite) {
            LaunchedEffect(Unit) {
                manageStorageRequested = true
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                manageStorageLauncher.launch(intent)
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (readPermissionState.status.shouldShowRationale) {
                Text(
                    text = "PhotoSweep needs access to your photos to help you organize them.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = "Permission required. Please grant photo access.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun PhotoSweepContent() {
    val context = LocalContext.current
    var months by remember { mutableStateOf<List<MonthGroup>>(emptyList()) }
    var selectedMonth by remember { mutableStateOf<MonthGroup?>(null) }
    var startFromIndex by remember { mutableStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        months = PhotoRepository.loadPhotos(context)
        isLoaded = true
    }

    if (!isLoaded) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading photos...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    if (months.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("No photos found on device", style = MaterialTheme.typography.headlineMedium)
        }
        return
    }

    val currentMonth = selectedMonth
    if (currentMonth != null) {
        PhotoSwipeScreen(
            monthGroup = currentMonth,
            startFromIndex = startFromIndex,
            onComplete = {
                selectedMonth = null
                months = PhotoRepository.loadPhotos(context)
            }
        )
    } else {
        MonthPickerScreen(
            months = months,
            onMonthSelected = { yearMonth, startIndex ->
                selectedMonth = months.find { it.yearMonth == yearMonth }
                startFromIndex = startIndex
            }
        )
    }
}
