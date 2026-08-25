package com.antoniusindra.sipatokkong.core.media

import android.net.Uri
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.repository.childMediaSize
import com.antoniusindra.sipatokkong.data.repository.downloadChildMediaAsFlow
import io.github.jan.supabase.storage.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/** Which private bucket an object lives in, and how a saved file should be named. */
enum class MediaBucket(val bucketName: String, val fileLabel: String) {
    DEMO("tugas-demo", "demo"),
    DOKUMENTASI("dokumentasi", "bukti"),
    PRESENSI("presensi", "presensi"),
}

/**
 * One downloadable object.
 *
 * [isVideo] is decided by the stored path's extension rather than by a caller-supplied flag: the
 * two disagreed before (a `media_kind` column *and* a file extension), and the file name written
 * to the gallery has to follow the bytes, not the metadata.
 */
data class MediaRef(val bucket: MediaBucket, val path: String) {

    val isVideo: Boolean get() = MediaFileName.isVideo(path)

    /**
     * Whether to ask before downloading.
     *
     * Only videos: `tugas-demo` allows 50 MB and data frugality is a hard requirement here, while
     * a photo is a couple of hundred KB and a dialog for that is just friction.
     */
    val needsConfirmation: Boolean get() = isVideo
}

/**
 * Downloads a private object and saves it into the phone's gallery.
 *
 * The bytes land in a cache file first and are then copied into MediaStore. That is two disk
 * writes, but it keeps a 50 MB video out of memory and lets the download report real progress.
 */
object MediaDownloader {

    /** Bytes the object occupies, for the confirmation dialog. */
    suspend fun sizeOf(ref: MediaRef): Long =
        ServiceLocator.supabase.childMediaSize(ref.bucket.bucketName, ref.path)

    /**
     * Downloads [ref] and saves it to the gallery, reporting progress as 0f..1f.
     * Returns the saved uri, which the caller can hand to `Intent.ACTION_VIEW`.
     */
    suspend fun download(ref: MediaRef, onProgress: (Float) -> Unit): Uri {
        val context = ServiceLocator.appContext
        val cacheFile = withContext(Dispatchers.IO) {
            File.createTempFile("unduh_", ".${MediaFileName.extensionOf(ref.path)}", context.cacheDir)
        }

        try {
            ServiceLocator.supabase
                .downloadChildMediaAsFlow(ref.bucket.bucketName, ref.path, cacheFile)
                .collect { status ->
                    if (status is DownloadStatus.Progress && status.contentLength > 0) {
                        onProgress(
                            (status.totalBytesReceived.toFloat() / status.contentLength)
                                .coerceIn(0f, 1f)
                        )
                    }
                }

            return MediaSaver.saveToGallery(
                context = context,
                source = cacheFile,
                displayName = MediaFileName.of(
                    label = ref.bucket.fileLabel,
                    storagePath = ref.path,
                    date = LocalDate.now(),
                    unique = uniqueOf(ref.path),
                ),
                mimeType = MediaFileName.mimeTypeOf(ref.path),
                isVideo = ref.isVideo,
            )
        } finally {
            withContext(Dispatchers.IO) { cacheFile.delete() }
        }
    }

    /**
     * A short discriminator taken from the object's own file name.
     *
     * Storage paths end in a UUID (`{anakId}/{yyyy}/{MM}/{uuid}.{ext}`), so this is stable per
     * object — the same photo downloaded twice on the same day keeps the same name and the
     * "(1)" suffix makes the copy obvious, rather than producing two unrelated-looking files.
     */
    private fun uniqueOf(path: String): String =
        path.substringAfterLast('/').substringBeforeLast('.')
}
