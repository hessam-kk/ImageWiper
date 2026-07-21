package com.example.photosweep

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MonthPickerScreen(
    months: List<MonthGroup>,
    onMonthSelected: (yearMonth: String, startIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val completedMonths by MonthProgressStore.getCompletedMonths(context).collectAsState(initial = emptySet())

    var pendingMonth by remember { mutableStateOf<MonthGroup?>(null) }

    pendingMonth?.let { month ->
        val reviewedIndex = MonthProgressStore.getReviewedIndexOnce(context, month.yearMonth)
        val total = month.photos.size

        AlertDialog(
            onDismissRequest = { pendingMonth = null },
            title = { Text(month.yearMonth) },
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
                    onMonthSelected(month.yearMonth, 0)
                }) {
                    Text("From beginning")
                }
            },
            dismissButton = {
                if (reviewedIndex > 0 && reviewedIndex < total) {
                    TextButton(onClick = {
                        pendingMonth = null
                        onMonthSelected(month.yearMonth, reviewedIndex)
                    }) {
                        Text("Resume from #$reviewedIndex")
                    }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "PhotoSweep",
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
                    onClick = { pendingMonth = month }
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
