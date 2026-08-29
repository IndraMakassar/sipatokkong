package com.antoniusindra.sipatokkong.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.DownloadStatus
import io.github.jan.supabase.storage.downloadAuthenticatedToAsFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Downloading side of the storage contract, mirroring [uploadChildMedia] in `StorageUpload.kt`.
 *
 * All three buckets are private, so every read goes through an authenticated download rather than
 * a public URL. Streaming to a file rather than using `downloadAuthenticated(path): ByteArray` is
 * deliberate: `tugas-demo` and `dokumentasi` allow objects up to 50 MB, and holding one of those
 * in memory risks an OOM on exactly the low-end phones this app targets.
 */
internal fun SupabaseClient.downloadChildMediaAsFlow(
    bucket: String,
    path: String,
    target: File,
): Flow<DownloadStatus> = storage.from(bucket).downloadAuthenticatedToAsFlow(path, target)

/**
 * Size of a stored object in bytes, for the confirmation dialog shown before a video download.
 *
 * One extra request, on purpose: the progress events carry `contentLength` too, but by then the
 * download has already started — which is the thing the dialog exists to prevent.
 */
internal suspend fun SupabaseClient.childMediaSize(bucket: String, path: String): Long =
    storage.from(bucket).info(path).size
