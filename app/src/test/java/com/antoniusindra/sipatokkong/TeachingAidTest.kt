package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.TeachingAid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The aid list is an app-side constant written into `tugas.alat_bantu` as plain text, so nothing in
 * the database constrains it. These assertions are the only guard on that contract.
 */
class TeachingAidTest {

    @Test
    fun `dbValues are unique`() {
        // Two aids sharing a stored value would be indistinguishable once written, and one would
        // silently render as the other.
        val values = TeachingAid.entries.map { it.dbValue }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun `labels are unique`() {
        val labels = TeachingAid.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `every entry has a non-blank label and dbValue`() {
        TeachingAid.entries.forEach {
            assertTrue("label kosong pada $it", it.label.isNotBlank())
            assertTrue("dbValue kosong pada $it", it.dbValue.isNotBlank())
        }
    }

    @Test
    fun `dbValues avoid characters that complicate a text array`() {
        // Postgres array literals quote on commas, braces and whitespace; keeping values to
        // lowercase and underscores sidesteps that entirely.
        val allowed = Regex("^[a-z0-9_]+$")
        TeachingAid.entries.forEach {
            assertTrue("dbValue '${it.dbValue}' mengandung karakter tak aman", allowed.matches(it.dbValue))
        }
    }

    // ---- fromDb ----

    @Test
    fun `fromDb resolves a known value`() {
        assertEquals(TeachingAid.CERMIN, TeachingAid.fromDb("cermin"))
    }

    @Test
    fun `fromDb returns null for an unknown value`() {
        assertNull(TeachingAid.fromDb("teleskop"))
    }

    // ---- labelsOf ----

    @Test
    fun `labelsOf maps known values to their labels`() {
        assertEquals(
            listOf("Cermin", "Bola"),
            TeachingAid.labelsOf(listOf("cermin", "bola")),
        )
    }

    @Test
    fun `labelsOf passes an unknown value through instead of dropping it`() {
        // This is the point of the helper. If the list changes in a later release, a task saved
        // under the old list must keep showing its aid rather than quietly losing it.
        assertEquals(
            listOf("Cermin", "alat_lama_yang_dihapus", "Bola"),
            TeachingAid.labelsOf(listOf("cermin", "alat_lama_yang_dihapus", "bola")),
        )
    }

    @Test
    fun `labelsOf handles null and empty without failing`() {
        assertEquals(emptyList<String>(), TeachingAid.labelsOf(null))
        assertEquals(emptyList<String>(), TeachingAid.labelsOf(emptyList()))
    }
}
