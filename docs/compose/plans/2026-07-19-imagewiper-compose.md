# ImageWiper Compose Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite ImageWiper from View-based to Jetpack Compose with month-grouped photo browsing, swipe-to-trash, and DataStore-backed progress tracking.

**Architecture:** Single Activity with Navigation Compose hosting two screens: MonthPickerScreen (list months with progress) and PhotoSwipeScreen (full-screen swipeable cards). PhotoRepository queries MediaStore, MonthProgressStore persists review progress via DataStore.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Navigation Compose, Coil (image loading), DataStore Preferences, Accompanist Permissions, MediaStore API.

## Global Constraints

- minSdk = 24, targetSdk = 35, compileSdk = 35
- Kotlin 1.9.24, AGP 8.8.1
- Compose BOM 2024.02.00
- Permissions: READ_MEDIA_IMAGES (API 33+), READ_EXTERNAL_STORAGE (API < 33)
- Trash via MediaStore.createTrashRequest() only (API 30+); fallback to direct delete on API < 30
- Swipe right = trash, swipe left = keep/advance
- Month grouping: year-month format from DATE_ADDED column
- DataStore key format: `month_<yearMonth>_index` storing Int (last reviewed index)

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `gradle/libs.versions.toml` | Modify | Add Compose, Navigation, DataStore, Coil, Accompanist deps |
| `build.gradle.kts` | Modify | Remove stale kotlinOptions block |
| `app/build.gradle.kts` | Modify | Add Compose plugin, compose compiler, new deps, remove duplicates |
| `app/src/main/AndroidManifest.xml` | Modify | Add INTERNET permission for Coil, remove stale tool attributes |
| `app/src/main/java/com/example/imagewiper/PhotoRepository.kt` | Create | MediaStore query, month grouping, photo data model |
| `app/src/main/java/com/example/imagewiper/MonthProgressStore.kt` | Create | DataStore read/write for reviewed index per month |
| `app/src/main/java/com/example/imagewiper/PhotoSwipeScreen.kt` | Create | Compose swipeable card UI with Snackbar undo |
| `app/src/main/java/com/example/imagewiper/MonthPickerScreen.kt` | Create | Compose month list with progress bars |
| `app/src/main/java/com/example/imagewiper/MainActivity.kt` | Rewrite | Compose Activity with NavHost |
| `app/src/main/res/layout/activity_main.xml` | Delete | No longer needed (Compose replaces XML) |
| `app/src/main/res/values/themes.xml` | Modify | Switch to Material3 theme |

---

### Task 1: Fix Build Configuration & Add Dependencies

**Covers:** Global constraints (build setup)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: (none — foundational task)
- Produces: Buildable project with Compose, Navigation, DataStore, Coil, Accompanist on classpath

- [ ] **Step 1: Update `gradle/libs.versions.toml`**

Add Compose BOM, Navigation, DataStore, Coil, Accompanist versions and libraries. Remove duplicate material entries. Final content:

```toml
[versions]
agp = "8.8.1"
kotlin = "1.9.24"
coreKtx = "1.15.0"
junit = "4.13.2"
junitVersion = "1.2.1"
espressoCore = "3.6.1"
appcompat = "1.7.0"
material = "1.12.0"
activity = "1.10.1"
constraintlayout = "2.2.0"
palette = "1.0.0"
composeBom = "2024.02.00"
navigationCompose = "2.7.7"
datastorePreferences = "1.0.0"
coil = "2.5.0"
accompanistPermissions = "0.34.0"
lifecycleRuntimeKtx = "2.7.0"
activityCompose = "1.8.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-palette = { module = "androidx.palette:palette", version.ref = "palette" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-activity = { group = "androidx.activity", name = "activity", version.ref = "activity" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-material-icons = { group = "androidx.compose.material", name = "material-icons-extended" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastorePreferences" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanistPermissions" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 2: Fix root `build.gradle.kts`**

Remove the stale `tasks.withType<KotlinCompile>` block. Final content:

```kotlin
plugins {
    id("com.android.application") version "8.8.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
```

- [ ] **Step 3: Update `app/build.gradle.kts`**

Add Compose plugin, Compose compiler extension, and all new dependencies. Remove duplicate material and old `com.google.android.material.material.v190` entries:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.imagewiper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.imagewiper"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)

    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.palette)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
```

- [ ] **Step 4: Verify build compiles**

Run: `./gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows)
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "build: add Compose, Navigation, DataStore, Coil dependencies; fix kotlin version"
```

---

### Task 2: Create PhotoRepository (MediaStore Query + Month Grouping)

**Covers:** MediaStore loading, month grouping, photo data model

**Files:**
- Create: `app/src/main/java/com/example/imagewiper/PhotoRepository.kt`

**Interfaces:**
- Consumes: (none — foundational)
- Produces: `PhotoItem` data class, `MonthGroup` data class, `PhotoRepository.loadPhotos(context): List<MonthGroup>`, `PhotoRepository.trashPhoto(context, uri): Boolean`, `PhotoRepository.undoTrash(context, uri): Boolean`

- [ ] **Step 1: Create `PhotoRepository.kt`**

```kotlin
package com.example.imagewiper

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long // epoch seconds
)

data class MonthGroup(
    val yearMonth: String, // "2024-03"
    val photos: List<PhotoItem>
)

object PhotoRepository {

    fun loadPhotos(context: Context): List<MonthGroup> {
        val photos = queryPhotos(context)
        return groupByMonth(photos)
    }

    private fun queryPhotos(context: Context): List<PhotoItem> {
        val photos = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "unknown"
                val date = cursor.getLong(dateCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
                photos.add(PhotoItem(id, uri, name, date))
            }
        }
        return photos
    }

    private fun groupByMonth(photos: List<PhotoItem>): List<MonthGroup> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")
        return photos
            .groupBy {
                Instant.ofEpochSecond(it.dateAdded)
                    .atZone(ZoneId.systemDefault())
                    .format(formatter)
            }
            .map { (yearMonth, group) -> MonthGroup(yearMonth, group) }
            .sortedByDescending { it.yearMonth }
    }

    fun trashPhoto(context: Context, photo: PhotoItem): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val trashUri = MediaStore.createTrashRequest(
                context.contentResolver, listOf(photo.uri)
            ).intentSender
            // The caller must launch the intent sender; return true to indicate ready
            return true
        } else {
            return try {
                context.contentResolver.delete(photo.uri, null, null) > 0
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getTrashIntentSender(context: Context, photos: List<PhotoItem>) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createTrashRequest(
                context.contentResolver, photos.map { it.uri }
            ).intentSender
        } else null

    fun getRestoreIntentSender(context: Context, photos: List<PhotoItem>) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createRestoreRequest(
                context.contentResolver, photos.map { it.uri }
            ).intentSender
        } else null
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/imagewiper/PhotoRepository.kt
git commit -m "feat: add PhotoRepository with MediaStore query, month grouping, trash support"
```

---

### Task 3: Create MonthProgressStore (DataStore Persistence)

**Covers:** DataStore persistence for reviewed progress

**Files:**
- Create: `app/src/main/java/com/example/imagewiper/MonthProgressStore.kt`

**Interfaces:**
- Consumes: (none — foundational)
- Produces: `MonthProgressStore` with `getReviewedIndex(context, yearMonth): Flow<Int>`, `setReviewedIndex(context, yearMonth, index)`, `getCompletedMonths(context): Flow<Set<String>>`, `markMonthCompleted(context, yearMonth)`

- [ ] **Step 1: Create `MonthProgressStore.kt`**

```kotlin
package com.example.imagewiper

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "month_progress")

object MonthProgressStore {

    private fun indexKey(yearMonth: String) = intPreferencesKey("month_${yearMonth}_index")
    private val completedKey = stringSetPreferencesKey("completed_months")

    fun getReviewedIndex(context: Context, yearMonth: String): Flow<Int> {
        return context.dataStore.data.map { prefs ->
            prefs[indexKey(yearMonth)] ?: 0
        }
    }

    suspend fun setReviewedIndex(context: Context, yearMonth: String, index: Int) {
        context.dataStore.edit { prefs ->
            prefs[indexKey(yearMonth)] = index
        }
    }

    fun getCompletedMonths(context: Context): Flow<Set<String>> {
        return context.dataStore.data.map { prefs ->
            prefs[completedKey] ?: emptySet()
        }
    }

    suspend fun markMonthCompleted(context: Context, yearMonth: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[completedKey] ?: emptySet()
            prefs[completedKey] = current + yearMonth
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/imagewiper/MonthProgressStore.kt
git commit -m "feat: add MonthProgressStore with DataStore for review progress persistence"
```

---

### Task 4: Create PhotoSwipeScreen (Compose Swipeable Card)

**Covers:** Full-screen photo display, swipe gestures, Trash via MediaStore, Snackbar undo, permission handling, empty/end states

**Files:**
- Create: `app/src/main/java/com/example/imagewiper/PhotoSwipeScreen.kt`

**Interfaces:**
- Consumes: `MonthGroup` from PhotoRepository, `MonthProgressStore` for progress
- Produces: `PhotoSwipeScreen` composable, calls `onComplete()` when month is done

- [ ] **Step 1: Create `PhotoSwipeScreen.kt`**

```kotlin
package com.example.imagewiper

import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    // Track completed months
    val completedMonths by MonthProgressStore.getCompletedMonths(context).collectAsState(initial = emptySet())

    // Trash intent sender launcher
    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, "Trash cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Restore intent sender launcher
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, "Restore cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Save progress when index changes
    LaunchedEffect(currentIndex) {
        MonthProgressStore.setReviewedIndex(context, monthGroup.yearMonth, currentIndex)
    }

    // Check if month is complete
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
                    "${monthGroup.yearMonth} — $totalPhotos photos reviewed",
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
        // Swipe card
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .graphicsLayer {
                    rotationZ = offsetX / 50f
                    alpha = 1f - (kotlin.math.abs(offsetX) / 1000f).coerceIn(0f, 0.5f)
                }
                .detectDragGestures(
                    onDragEnd = {
                        val threshold = 200f
                        when {
                            offsetX > threshold -> {
                                // Swipe right = trash
                                scope.launch {
                                    val sender = PhotoRepository.getRestoreIntentSender(
                                        context, listOf(currentPhoto)
                                    )
                                    // Actually we need trash, not restore
                                    val trashSender = PhotoRepository.getTrashIntentSender(
                                        context, listOf(currentPhoto)
                                    )
                                    if (trashSender != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        trashLauncher.launch(
                                            IntentSenderRequest.Builder(trashSender).build()
                                        )
                                    } else {
                                        // Fallback for API < 30
                                        PhotoRepository.trashPhoto(context, currentPhoto)
                                    }

                                    val result = snackbarHostState.showSnackbar(
                                        message = "Photo trashed",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        // Undo: restore the photo
                                        val restoreSender = PhotoRepository.getRestoreIntentSender(
                                            context, listOf(currentPhoto)
                                        )
                                        if (restoreSender != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            restoreLauncher.launch(
                                                IntentSenderRequest.Builder(restoreSender).build()
                                            )
                                        }
                                    }
                                    // Advance to next photo
                                    currentIndex++
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                            offsetX < -threshold -> {
                                // Swipe left = keep, advance
                                currentIndex++
                                offsetX = 0f
                                offsetY = 0f
                            }
                            else -> {
                                // Snap back
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
                ),
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

                // Swipe indicators
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

                // Counter
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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/imagewiper/PhotoSwipeScreen.kt
git commit -m "feat: add PhotoSwipeScreen with swipe gestures, trash, undo snackbar"
```

---

### Task 5: Create MonthPickerScreen (Month List with Progress)

**Covers:** Month picker screen, photo count, reviewed/total progress display

**Files:**
- Create: `app/src/main/java/com/example/imagewiper/MonthPickerScreen.kt`

**Interfaces:**
- Consumes: `List<MonthGroup>` from PhotoRepository, `MonthProgressStore` for progress
- Produces: `MonthPickerScreen` composable, calls `onMonthSelected(yearMonth, startIndex)` on tap

- [ ] **Step 1: Create `MonthPickerScreen.kt`**

```kotlin
package com.example.imagewiper

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MonthPickerScreen(
    months: List<MonthGroup>,
    onMonthSelected: (yearMonth: String, startIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val completedMonths by MonthProgressStore.getCompletedMonths(context).collectAsState(initial = emptySet())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "ImageWiper",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "${months.sumOf { it.photos.size }} photos across ${months.size} months",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(months) { month ->
                MonthCard(
                    month = month,
                    context = context,
                    isCompleted = month.yearMonth in completedMonths,
                    onClick = {
                        val reviewedIndex = MonthProgressStore.getReviewedIndexOnce(
                            context, month.yearMonth
                        )
                        onMonthSelected(month.yearMonth, reviewedIndex)
                    }
                )
            }
        }
    }
}

@Composable
private fun MonthCard(
    month: MonthGroup,
    context: Context,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    val reviewedIndex by MonthProgressStore.getReviewedIndex(
        context, month.yearMonth
    ).collectAsState(initial = 0)

    val total = month.photos.size
    val reviewed = reviewedIndex.coerceAtMost(total)
    val progress = if (total > 0) reviewed.toFloat() / total else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isCompleted) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = month.yearMonth,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$reviewed / $total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (isCompleted) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
```

- [ ] **Step 2: Add `getReviewedIndexOnce` helper to `MonthProgressStore.kt`**

Add this method to `MonthProgressStore` for synchronous reads (needed in click handlers):

```kotlin
fun getReviewedIndexOnce(context: Context, yearMonth: String): Int {
    // Run blocking read for click handler; DataStore is fast for small files
    var result = 0
    val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        context.dataStore.data.first().let { prefs ->
            result = prefs[indexKey(yearMonth)] ?: 0
        }
    }
    kotlinx.coroutines.runBlocking { job.join() }
    return result
}
```

Add this import at the top of `MonthProgressStore.kt`:
```kotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/imagewiper/MonthPickerScreen.kt
git add app/src/main/java/com/example/imagewiper/MonthProgressStore.kt
git commit -m "feat: add MonthPickerScreen with progress display and DataStore sync read"
```

---

### Task 6: Rewrite MainActivity as Compose Activity with Navigation

**Covers:** Activity setup, permission handling, navigation between screens, empty state

**Files:**
- Rewrite: `app/src/main/java/com/example/imagewiper/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Delete: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/themes.xml`

**Interfaces:**
- Consumes: `PhotoRepository`, `MonthProgressStore`, `MonthPickerScreen`, `PhotoSwipeScreen`
- Produces: Working app with permission flow → month picker → photo swipe → month complete

- [ ] **Step 1: Update `AndroidManifest.xml`**

Add INTERNET permission (for Coil image loading) and remove tools namespace:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.ImageWiper">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 2: Rewrite `MainActivity.kt`**

```kotlin
package com.example.imagewiper

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
                ImageWiperApp()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ImageWiperApp() {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permission)

    if (permissionState.status.isGranted) {
        ImageWiperContent()
    } else {
        LaunchedEffect(Unit) {
            permissionState.launchPermissionRequest()
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (permissionState.status.shouldShowRationale) {
                Text(
                    text = "ImageWiper needs access to your photos to help you organize them.",
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
fun ImageWiperContent() {
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
                // Reload to refresh progress
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
```

- [ ] **Step 3: Update `themes.xml`**

Switch to Material3 theme:

```xml
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Base.Theme.ImageWiper" parent="Theme.Material3.DayNight.NoActionBar">
    </style>
    <style name="Theme.ImageWiper" parent="Base.Theme.ImageWiper" />
</resources>
```

- [ ] **Step 4: Delete `activity_main.xml`**

```bash
rm app/src/main/res/layout/activity_main.xml
```

- [ ] **Step 5: Verify build compiles**

Run: `./gradlew assembleDebug` (or `gradlew.bat assembleDebug`)
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: rewrite MainActivity as Compose with navigation, permissions, and edge-to-edge"
```

---

### Task 7: End-to-End Verification

**Covers:** All spec sections — verify everything works together

**Files:**
- (none — verification only)

**Interfaces:**
- Consumes: All previous tasks
- Produces: Confirmed working app

- [ ] **Step 1: Build the app**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run lint checks**

Run: `./gradlew lint`
Expected: No errors (warnings acceptable)

- [ ] **Step 3: Verify file structure**

Confirm all files exist:
- `app/src/main/java/com/example/imagewiper/PhotoRepository.kt`
- `app/src/main/java/com/example/imagewiper/MonthProgressStore.kt`
- `app/src/main/java/com/example/imagewiper/PhotoSwipeScreen.kt`
- `app/src/main/java/com/example/imagewiper/MonthPickerScreen.kt`
- `app/src/main/java/com/example/imagewiper/MainActivity.kt`

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "chore: final verification and cleanup"
```
