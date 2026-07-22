package com.example.photosweep

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "PhotoRepo"
private const val TRASH_FOLDER = ".PhotoSweepTrash"

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long
)

data class MonthGroup(
    val yearMonth: String,
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

    private fun getTrashDir(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            TRASH_FOLDER
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getFilePath(context: Context, photo: PhotoItem): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(photo.uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                return cursor.getString(idx)
            }
        }
        return null
    }

    fun trashPhoto(context: Context, photo: PhotoItem): Boolean {
        val filePath = getFilePath(context, photo)
        val src = filePath?.let { File(it) }
        Log.d(TAG, "trashPhoto: id=${photo.id} name=${photo.displayName} path=$filePath srcExists=${src?.exists()}")

        val dest = File(getTrashDir(), "${photo.id}_${photo.displayName}")

        return try {
            if (src != null && src.exists()) {
                val fileSize = src.length()
                var moved = src.renameTo(dest)
                Log.d(TAG, "renameTo result=$moved dest=${dest.absolutePath}")
                if (!moved) {
                    Log.d(TAG, "renameTo failed, trying stream copy")
                    FileInputStream(src).use { input ->
                        FileOutputStream(dest).use { output ->
                            input.copyTo(output)
                        }
                    }
                    src.delete()
                    Log.d(TAG, "stream copy + delete done, dest exists=${dest.exists()}")
                }

                CoroutineScope(Dispatchers.IO).launch {
                    MonthProgressStore.recordTrash(context, fileSize)
                }

                val parentDir = src.parentFile
                if (parentDir != null) {
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(parentDir.absolutePath),
                        null, null
                    )
                    Log.d(TAG, "Scanned parent dir: ${parentDir.absolutePath}")
                }
            } else {
                Log.w(TAG, "Source file null or missing: $filePath")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "trashPhoto FAILED for ${photo.displayName}", e)
            false
        }
    }

    fun restorePhoto(context: Context, photo: PhotoItem): Boolean {
        val trashFile = File(getTrashDir(), "${photo.id}_${photo.displayName}")
        if (!trashFile.exists()) return false

        val fileSize = trashFile.length()
        val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dest = File(destDir, photo.displayName)

        return try {
            if (!trashFile.renameTo(dest)) {
                FileInputStream(trashFile).use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                trashFile.delete()
            }
            CoroutineScope(Dispatchers.IO).launch {
                MonthProgressStore.recordRestore(context, fileSize)
            }
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "restorePhoto failed for ${photo.displayName}", e)
            false
        }
    }
}
