package com.antoniusindra.sipatokkong.core.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes a downloaded file into the phone's gallery.
 *
 * Two branches, and neither is optional at `minSdk = 27`:
 *
 * * **API 29+** — insert a `MediaStore` row with `RELATIVE_PATH` and `IS_PENDING`, stream into it,
 *   then publish. No permission at all.
 * * **API 27–28** — `MediaStore` has no `RELATIVE_PATH`, so the file is written to the public
 *   Pictures/Movies directory (needs `WRITE_EXTERNAL_STORAGE`) and then handed to
 *   [MediaScannerConnection]. Skipping the scan leaves a file that exists on disk but never shows
 *   up in the gallery — which users read as "the download failed".
 *
 * Returns the saved [Uri] so the caller can offer an "Buka" action; a `MediaStore` uri is already
 * shareable with other apps, so no `FileProvider` is needed.
 */
object MediaSaver {

    /** Album name under Pictures/ and Movies/, so downloads are grouped rather than scattered. */
    private const val ALBUM = "Sipatokkong"

    /** True when this device needs `WRITE_EXTERNAL_STORAGE` before anything can be saved. */
    val needsLegacyPermission: Boolean
        get() = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P

    /**
     * Copies [source] into the gallery under [displayName] and returns the resulting uri.
     * [source] is a cache file owned by the caller; this function does not delete it.
     */
    suspend fun saveToGallery(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    ): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, source, displayName, mimeType, isVideo)
        } else {
            saveLegacy(context, source, displayName, isVideo)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
    ): Uri {
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath =
            if (isVideo) "${Environment.DIRECTORY_MOVIES}/$ALBUM"
            else "${Environment.DIRECTORY_PICTURES}/$ALBUM"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Gagal menyiapkan penyimpanan di galeri.")

        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { it.copyTo(output) }
            } ?: throw IllegalStateException("Gagal menulis file ke galeri.")
        } catch (e: Exception) {
            // Leaving a pending row behind would show as a permanently invisible, undeletable item.
            resolver.delete(uri, null, null)
            throw e
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        return uri
    }

    /**
     * API 27–28. `IS_PENDING`/`RELATIVE_PATH` do not exist here, so the file goes straight into the
     * public directory and the media scanner is asked to index it.
     */
    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        source: File,
        displayName: String,
        isVideo: Boolean,
    ): Uri {
        val type = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val dir = File(Environment.getExternalStoragePublicDirectory(type), ALBUM)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Gagal membuat folder $ALBUM di penyimpanan.")
        }

        val target = uniqueFile(dir, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { input.copyTo(it) }
        }

        // Fire-and-forget: the file is already saved, indexing is what makes it visible.
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return Uri.fromFile(target)
    }

    /**
     * Adds " (1)", " (2)" … when the name is taken.
     *
     * API 29+ does this itself; the legacy path would otherwise overwrite the earlier download
     * without saying so.
     */
    private fun uniqueFile(dir: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "")
        var candidate = File(dir, displayName)
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        return candidate
    }
}
