package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.media.MediaBucket
import com.antoniusindra.sipatokkong.core.media.MediaFileName
import com.antoniusindra.sipatokkong.core.media.MediaRef
import com.antoniusindra.sipatokkong.core.media.MediaSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The rules behind saving media to the phone's gallery.
 *
 * These are pure functions on purpose: the suite has no Robolectric, so anything reaching
 * `MediaStore` or `Environment` cannot be covered at all. Everything worth getting right — the
 * file name, the MIME type, the size wording, and which downloads warrant a warning — is kept
 * here instead of inside the saver.
 */
class MediaDownloadRulesTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 26)

    private fun name(path: String, label: String = "demo", unique: String = "abc123") =
        MediaFileName.of(label = label, storagePath = path, date = today, unique = unique)

    // ---- extension handling ----

    @Test
    fun `the extension comes from the storage path`() {
        assertEquals("mp4", MediaFileName.extensionOf("anak/2026/08/uuid.mp4"))
        assertEquals("webp", MediaFileName.extensionOf("anak/2026/08/uuid.webp"))
    }

    @Test
    fun `extensions are matched case-insensitively`() {
        assertEquals("jpg", MediaFileName.extensionOf("anak/2026/08/uuid.JPG"))
    }

    @Test
    fun `an unknown extension falls back to jpg rather than passing through`() {
        // The name decides the MIME row in MediaStore; a bogus extension makes the gallery refuse
        // to open a file that is otherwise perfectly fine.
        assertEquals("jpg", MediaFileName.extensionOf("anak/2026/08/uuid.exe"))
        assertEquals("jpg", MediaFileName.extensionOf("anak/2026/08/uuid"))
    }

    @Test
    fun `mime type follows the extension`() {
        assertEquals("video/mp4", MediaFileName.mimeTypeOf("a/b.mp4"))
        assertEquals("image/webp", MediaFileName.mimeTypeOf("a/b.webp"))
        assertEquals("image/jpeg", MediaFileName.mimeTypeOf("a/b.jpg"))
    }

    @Test
    fun `video is decided by extension, not by a caller flag`() {
        assertTrue(MediaFileName.isVideo("a/b.mp4"))
        assertFalse(MediaFileName.isVideo("a/b.jpg"))
    }

    // ---- file naming ----

    @Test
    fun `the file name carries the label, the date and the extension`() {
        assertEquals("Sipatokkong_demo_2026-08-26_abc123.mp4", name("anak/2026/08/uuid.mp4"))
    }

    @Test
    fun `the child id in the storage path never reaches the gallery`() {
        // Storage paths start with the child's uuid; that has no business in a shared photo album.
        val result = name("3fa85f64-5717-4562-b3fc-2c963f66afa6/2026/08/uuid.jpg")
        assertFalse(result.contains("3fa85f64"))
    }

    @Test
    fun `unsafe characters are stripped from the label and the discriminator`() {
        val result = name(path = "a/b.jpg", label = "bukti/../etc", unique = "a b*c")
        assertFalse(result.contains("/"))
        assertFalse(result.contains(" "))
        assertFalse(result.contains("*"))
    }

    @Test
    fun `a blank label still produces a usable name`() {
        assertEquals("Sipatokkong_media_2026-08-26_abc123.jpg", name("a/b.jpg", label = "   "))
    }

    @Test
    fun `two different objects downloaded the same day get different names`() {
        val first = name("a/b/uuid-one.jpg", unique = "uuidone")
        val second = name("a/b/uuid-two.jpg", unique = "uuidtwo")
        assertTrue("names collided: $first", first != second)
    }

    // ---- size wording ----

    @Test
    fun `sizes under a megabyte are shown in KB`() {
        assertEquals("195 KB", MediaSize.format(200_000))
    }

    @Test
    fun `sizes of a megabyte or more are shown in MB`() {
        assertEquals("11,4 MB", MediaSize.format(12_000_000))
    }

    @Test
    fun `the decimal separator does not follow the device locale`() {
        // A comma in Indonesian. Relying on the default locale printed "11.4 MB" on some phones.
        assertTrue(MediaSize.format(12_000_000).contains(","))
    }

    @Test
    fun `tiny and unknown sizes do not crash the dialog`() {
        assertEquals("0 B", MediaSize.format(0))
        assertEquals("tidak diketahui", MediaSize.format(-1))
    }

    // ---- confirmation rule ----

    @Test
    fun `a video is confirmed before downloading`() {
        // tugas-demo allows 50 MB and data frugality is a hard requirement for these families.
        assertTrue(MediaRef(MediaBucket.DEMO, "anak/2026/08/uuid.mp4").needsConfirmation)
    }

    @Test
    fun `a photo downloads without a dialog`() {
        assertFalse(MediaRef(MediaBucket.PRESENSI, "anak/2026/08/uuid.jpg").needsConfirmation)
        assertFalse(MediaRef(MediaBucket.DOKUMENTASI, "anak/2026/08/uuid.webp").needsConfirmation)
    }

    @Test
    fun `each bucket names its saved files distinctly`() {
        val labels = MediaBucket.entries.map { it.fileLabel }
        assertEquals(labels.size, labels.distinct().size)
    }
}
