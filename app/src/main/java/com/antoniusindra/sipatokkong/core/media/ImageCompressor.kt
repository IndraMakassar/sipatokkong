package com.antoniusindra.sipatokkong.core.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shrinks a picked image before upload.
 *
 * This is the first place the declared `id.zelory:compressor` dependency is actually used, and for
 * presensi it is a hard requirement rather than an optimisation: the `presensi` bucket caps objects
 * at 6 MB and accepts only JPEG/WebP, while a modern phone camera routinely produces more than
 * that. The 200 KB target comes from the app's bandwidth budget (README) — these families pay for
 * every megabyte.
 *
 * `Compressor` works on files, so the picked `content://` stream is copied to a cache file first
 * and both temporaries are deleted afterwards.
 */
object ImageCompressor {

    /**
     * Supabase's bucket cap, enforced here so the user gets a readable error instead of a raw one.
     *
     * Shared by the presensi photo and the parent's task proof; both buckets carry the same limit.
     */
    const val MAX_UPLOAD_BYTES: Long = 6L * 1024 * 1024

    private const val TARGET_BYTES: Long = 200_000

    /** Reads [uri], re-encodes it as a small JPEG, and returns the bytes. */
    suspend fun compressToJpeg(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val source = File.createTempFile("compress_src_", ".img", context.cacheDir)
        var compressed: File? = null
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                source.outputStream().use { input.copyTo(it) }
            } ?: throw IllegalStateException("Gagal membaca file foto.")

            compressed = Compressor.compress(context, source) {
                resolution(1280, 1280)
                format(Bitmap.CompressFormat.JPEG)
                quality(80)
                size(TARGET_BYTES)
            }
            compressed.readBytes()
        } finally {
            source.delete()
            compressed?.delete()
        }
    }
}
