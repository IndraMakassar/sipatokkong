package com.antoniusindra.sipatokkong.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.time.LocalDate
import java.util.UUID

/**
 * Uploads a file for one child and returns the stored path.
 *
 * Path convention: `{anakId}/{yyyy}/{MM}/{uuid}.{ext}`. The child id **must** be the first segment:
 * every storage policy in `0001_initial_schema.sql` authorizes through
 * `public.storage_child_id(name)`, which reads `(storage.foldername(name))[1]`. Get the segment
 * order wrong and the upload is rejected, so this lives in one place rather than being copied per
 * bucket.
 */
internal suspend fun SupabaseClient.uploadChildMedia(
    bucket: String,
    anakId: String,
    bytes: ByteArray,
    ext: String,
): String {
    val now = LocalDate.now()
    val month = now.monthValue.toString().padStart(2, '0')
    val path = "$anakId/${now.year}/$month/${UUID.randomUUID()}.$ext"
    storage.from(bucket).upload(path, bytes) { upsert = false }
    return path
}
