package com.antoniusindra.sipatokkong.core.media

import java.time.LocalDate
import java.util.Locale

/**
 * Names for files saved into the phone's gallery.
 *
 * Kept free of Android types so the rules are unit-testable — this suite has no Robolectric, so
 * anything touching `MediaStore` or `Environment` could not be covered at all.
 */
object MediaFileName {

    /** Extensions the three buckets accept. Anything else falls back to [DEFAULT_EXT]. */
    private val ALLOWED = setOf("jpg", "jpeg", "webp", "mp4")

    private const val DEFAULT_EXT = "jpg"

    private const val PREFIX = "Sipatokkong"

    private val UNSAFE = Regex("[^A-Za-z0-9_-]")

    /**
     * A file name for one stored object.
     *
     * @param label what the media is, e.g. "demo", "bukti", "presensi".
     * @param storagePath the Supabase path, e.g. `anak-id/2026/08/uuid.mp4` — only its extension
     *        is used; the rest names a child and has no business in the phone's gallery.
     * @param date the day the file is saved.
     * @param unique short discriminator (a slice of the object's UUID). Two downloads of the same
     *        object must not collide: on API 29+ MediaStore appends "(1)" itself, but the legacy
     *        API 27–28 path writes a plain file and would silently overwrite.
     */
    fun of(label: String, storagePath: String, date: LocalDate, unique: String): String {
        val safeLabel = UNSAFE.replace(label.trim(), "").ifBlank { "media" }
        val safeUnique = UNSAFE.replace(unique.trim(), "").take(8).ifBlank { "0" }
        return "${PREFIX}_${safeLabel}_${date}_$safeUnique.${extensionOf(storagePath)}"
    }

    /**
     * The extension of a storage path, lowercased.
     *
     * Unknown or missing extensions become [DEFAULT_EXT] rather than being passed through: the
     * name ends up in a MediaStore row whose MIME type is derived from it, and a bogus extension
     * makes the gallery refuse to open a perfectly good file.
     */
    fun extensionOf(storagePath: String): String {
        val ext = storagePath.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (ext in ALLOWED) ext else DEFAULT_EXT
    }

    /** MIME type for the saved file, matching what the buckets allow. */
    fun mimeTypeOf(storagePath: String): String = when (extensionOf(storagePath)) {
        "mp4" -> "video/mp4"
        "webp" -> "image/webp"
        else -> "image/jpeg"
    }

    /** True when the object is a video, decided by extension rather than a caller-supplied flag. */
    fun isVideo(storagePath: String): Boolean = extensionOf(storagePath) == "mp4"
}

/** Human-readable byte sizes for the download confirmation dialog. */
object MediaSize {

    private const val KB = 1024.0
    private const val MB = KB * KB

    /**
     * Formats bytes the way the confirmation dialog shows them.
     *
     * Indonesian decimal separator is a comma, so the value is formatted with an explicit locale —
     * relying on the device default would print "11.4 MB" on some phones and "11,4 MB" on others.
     */
    fun format(bytes: Long): String = when {
        bytes < 0 -> "tidak diketahui"
        bytes < KB -> "$bytes B"
        bytes < MB -> "${Math.round(bytes / KB)} KB"
        else -> String.format(Locale.forLanguageTag("id-ID"), "%.1f MB", bytes / MB)
    }
}
