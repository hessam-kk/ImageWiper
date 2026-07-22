package com.example.photosweep

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "month_progress")

object MonthProgressStore {

    private fun indexKey(yearMonth: String) = intPreferencesKey("month_${yearMonth}_index")
    private val completedKey = stringSetPreferencesKey("completed_months")
    private val totalDeletedKey = intPreferencesKey("total_deleted")
    private val totalSavedBytesKey = longPreferencesKey("total_saved_bytes")

    fun getReviewedIndex(context: Context, yearMonth: String): Flow<Int> {
        return context.dataStore.data.map { prefs ->
            prefs[indexKey(yearMonth)] ?: 0
        }
    }

    fun getReviewedIndexOnce(context: Context, yearMonth: String): Int {
        var result = 0
        val job = CoroutineScope(Dispatchers.IO).launch {
            context.dataStore.data.first().let { prefs ->
                result = prefs[indexKey(yearMonth)] ?: 0
            }
        }
        runBlocking { job.join() }
        return result
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

    suspend fun resetMonth(context: Context, yearMonth: String) {
        context.dataStore.edit { prefs ->
            prefs.remove(indexKey(yearMonth))
            val current = prefs[completedKey] ?: emptySet()
            prefs[completedKey] = current - yearMonth
        }
    }

    fun getTotalDeleted(context: Context): Flow<Int> {
        return context.dataStore.data.map { prefs ->
            prefs[totalDeletedKey] ?: 0
        }
    }

    fun getTotalSavedBytes(context: Context): Flow<Long> {
        return context.dataStore.data.map { prefs ->
            prefs[totalSavedBytesKey] ?: 0L
        }
    }

    suspend fun recordTrash(context: Context, fileSize: Long) {
        context.dataStore.edit { prefs ->
            prefs[totalDeletedKey] = (prefs[totalDeletedKey] ?: 0) + 1
            prefs[totalSavedBytesKey] = (prefs[totalSavedBytesKey] ?: 0L) + fileSize
        }
    }

    suspend fun recordRestore(context: Context, fileSize: Long) {
        context.dataStore.edit { prefs ->
            prefs[totalDeletedKey] = ((prefs[totalDeletedKey] ?: 0) - 1).coerceAtLeast(0)
            prefs[totalSavedBytesKey] = ((prefs[totalSavedBytesKey] ?: 0L) - fileSize).coerceAtLeast(0L)
        }
    }
}
