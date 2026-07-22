package com.example.photosweep

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.DecimalFormat

private val AccentColor = Color(0xFFFF6B4A)
private val CardBg = Color(0xFF1E1E1E)
private val MutedGray = Color(0xFF9E9E9E)

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return DecimalFormat("#.##").format(value) + " " + units[unitIndex]
}

@Composable
fun MonthPickerScreen(
    months: List<MonthGroup>,
    onMonthSelected: (yearMonth: String, startIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val completedMonths by MonthProgressStore.getCompletedMonths(context).collectAsState(initial = emptySet())
    val totalDeleted by MonthProgressStore.getTotalDeleted(context).collectAsState(initial = 0)
    val totalSavedBytes by MonthProgressStore.getTotalSavedBytes(context).collectAsState(initial = 0L)

    var pendingMonth by remember { mutableStateOf<MonthGroup?>(null) }

    pendingMonth?.let { month ->
        val reviewedIndex = MonthProgressStore.getReviewedIndexOnce(context, month.yearMonth)
        val total = month.photos.size

        AlertDialog(
            onDismissRequest = { pendingMonth = null },
            containerColor = CardBg,
            titleContentColor = Color.White,
            textContentColor = MutedGray,
            title = { Text(month.yearMonth, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    if (reviewedIndex > 0) {
                        Text("You've reviewed $reviewedIndex of $total photos.")
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text("Where would you like to start?")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingMonth = null
                    scope.launch {
                        MonthProgressStore.resetMonth(context, month.yearMonth)
                    }
                    onMonthSelected(month.yearMonth, 0)
                }) {
                    Text("From beginning", color = AccentColor)
                }
            },
            dismissButton = {
                if (reviewedIndex > 0 && reviewedIndex < total) {
                    TextButton(onClick = {
                        pendingMonth = null
                        onMonthSelected(month.yearMonth, reviewedIndex)
                    }) {
                        Text("Resume from #$reviewedIndex", color = MutedGray)
                    }
                }
            }
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 48.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title section - full width
        item(span = { GridItemSpan(2) }) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = "PhotoSweep",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Clean up your gallery",
                    fontSize = 14.sp,
                    color = MutedGray
                )
            }
        }

        // Stats row - full width
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Delete,
                    label = "Photos deleted",
                    value = "$totalDeleted"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Storage,
                    label = "Space saved",
                    value = formatBytes(totalSavedBytes)
                )
            }
        }

        // Completed months card - full width
        item(span = { GridItemSpan(2) }) {
            CompletedCard(
                completedCount = completedMonths.size,
                totalCount = months.size,
                totalPhotos = months.sumOf { it.photos.size },
                completedPhotos = months
                    .filter { it.yearMonth in completedMonths }
                    .sumOf { it.photos.size }
            )
        }

        // Month grid header - full width
        item(span = { GridItemSpan(2) }) {
            Text(
                text = "Months",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Month grid cards
        items(months) { month ->
            MonthGridCard(
                month = month,
                context = context,
                isCompleted = month.yearMonth in completedMonths,
                onClick = { pendingMonth = month }
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MutedGray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = MutedGray
            )
        }
    }
}

@Composable
private fun CompletedCard(
    completedCount: Int,
    totalCount: Int,
    totalPhotos: Int,
    completedPhotos: Int
) {
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "$completedCount month${if (completedCount != 1) "s" else ""} completed",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$completedCount/$totalCount months completed \u2022 $completedPhotos photos reviewed",
                        fontSize = 14.sp,
                        color = MutedGray
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentColor,
                trackColor = Color(0xFF2A2A2A)
            )
        }
    }
}

@Composable
private fun MonthGridCard(
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
    val thumbnailUri = month.photos.firstOrNull()?.uri

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail background
            if (thumbnailUri != null) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF2A2A2A))
                )
            }

            // Dark gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // Content overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = month.yearMonth,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$reviewed/$total reviewed",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            // Completed indicator
            if (isCompleted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AccentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
