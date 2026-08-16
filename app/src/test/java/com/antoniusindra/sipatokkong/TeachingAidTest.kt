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
    fun `dbValues stay lowercase and underscored`() {
        // A convention for the built-ins only, so they read as identifiers rather than prose and
        // never collide with a volunteer-typed label. It is NOT a safety rule about the column:
        // aids typed by volunteers do contain spaces and capitals, and that is fine — supabase-kt
        // sends the array as JSON, so nothing is parsed as a Postgres array literal.
        val allowed = Regex("^[a-z0-9_]+$")
        TeachingAid.entries.forEach {
            assertTrue("dbValue '${it.dbValue}' menyimpang dari konvensi", allowed.matches(it.dbValue))
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

    @Test
    fun `labelsOf mixes built-ins and volunteer-typed aids`() {
        assertEquals(
            listOf("Cermin", "gunting", "Bola"),
            TeachingAid.labelsOf(listOf("cermin", "gunting", "bola")),
        )
    }

    // ---- normalizeCustom ----

    @Test
    fun `normalizeCustom rejects blank input`() {
        assertNull(TeachingAid.normalizeCustom(""))
        assertNull(TeachingAid.normalizeCustom("   "))
        assertNull(TeachingAid.normalizeCustom("\t\n "))
    }

    @Test
    fun `normalizeCustom trims the edges`() {
        assertEquals("gunting", TeachingAid.normalizeCustom("  gunting  "))
    }

    @Test
    fun `normalizeCustom collapses runs of inner whitespace`() {
        // Without this, "gunting  kecil" and "gunting kecil" are two different aids forever.
        assertEquals("gunting kecil", TeachingAid.normalizeCustom("gunting   kecil"))
        assertEquals("gunting kecil", TeachingAid.normalizeCustom("gunting\tkecil"))
    }

    @Test
    fun `normalizeCustom caps the length and leaves no trailing space`() {
        val long = "a".repeat(100)
        assertEquals(TeachingAid.MAX_CUSTOM_LENGTH, TeachingAid.normalizeCustom(long)!!.length)

        // Cutting mid-string can land on a space; the result must not end in one.
        val cutOnSpace = "b".repeat(TeachingAid.MAX_CUSTOM_LENGTH - 1) + "  tail"
        assertEquals("b".repeat(TeachingAid.MAX_CUSTOM_LENGTH - 1), TeachingAid.normalizeCustom(cutOnSpace))
    }

    // ---- resolve ----

    @Test
    fun `resolve folds a retyped built-in back onto its stored value`() {
        // The whole point: typing a name that already exists must not create a near-duplicate.
        listOf("Cermin", "CERMIN", "cermin", "  cermin  ").forEach {
            assertEquals("gagal pada '$it'", "cermin", TeachingAid.resolve(it))
        }
    }

    @Test
    fun `resolve matches a built-in by its stored value too`() {
        assertEquals("kain_tekstur", TeachingAid.resolve("kain_tekstur"))
        assertEquals("kain_tekstur", TeachingAid.resolve("Kain aneka tekstur"))
    }

    @Test
    fun `resolve keeps a genuinely new aid as typed`() {
        assertEquals("gunting", TeachingAid.resolve("  gunting  "))
    }

    @Test
    fun `resolve rejects blank input`() {
        assertNull(TeachingAid.resolve("   "))
    }

    // ---- matchBuiltIn ----

    @Test
    fun `matchBuiltIn ignores case on both label and dbValue`() {
        assertEquals(TeachingAid.BOLA, TeachingAid.matchBuiltIn("BOLA"))
        assertEquals(TeachingAid.BOLA, TeachingAid.matchBuiltIn("Bola"))
        assertNull(TeachingAid.matchBuiltIn("gunting"))
    }

    // ---- sortedForStorage ----

    @Test
    fun `sortedForStorage puts built-ins in declaration order regardless of tap order`() {
        // Declared order is KAIN, CERMIN, ..., BOLA — so BOLA must come last however it was picked.
        assertEquals(
            listOf("kain_tekstur", "cermin", "bola"),
            TeachingAid.sortedForStorage(setOf("bola", "cermin", "kain_tekstur")),
        )
    }

    @Test
    fun `sortedForStorage appends custom aids alphabetically after the built-ins`() {
        assertEquals(
            listOf("cermin", "bola", "gunting", "plastisin"),
            TeachingAid.sortedForStorage(setOf("plastisin", "bola", "gunting", "cermin")),
        )
    }

    @Test
    fun `sortedForStorage sorts custom aids ignoring case`() {
        assertEquals(
            listOf("Ember", "gunting"),
            TeachingAid.sortedForStorage(setOf("gunting", "Ember")),
        )
    }

    @Test
    fun `sortedForStorage returns empty for an empty selection`() {
        assertEquals(emptyList<String>(), TeachingAid.sortedForStorage(emptySet()))
    }

    // ---- suggestionsFrom ----

    @Test
    fun `suggestionsFrom drops built-ins since the menu always lists them`() {
        assertEquals(
            listOf("gunting"),
            TeachingAid.suggestionsFrom(listOf("cermin", "gunting", "bola")),
        )
    }

    @Test
    fun `suggestionsFrom collapses case variants into one entry`() {
        // Otherwise the same aid is offered twice and the volunteer picks whichever they see first.
        assertEquals(
            listOf("Gunting"),
            TeachingAid.suggestionsFrom(listOf("Gunting", "gunting", "GUNTING")),
        )
    }

    @Test
    fun `suggestionsFrom drops a custom value that merely spells a built-in label`() {
        // Legacy rows written before resolve() existed could hold "Bola" as free text.
        assertEquals(emptyList<String>(), TeachingAid.suggestionsFrom(listOf("Bola", "Cermin")))
    }

    @Test
    fun `suggestionsFrom sorts alphabetically ignoring case`() {
        assertEquals(
            listOf("ember", "Gunting", "plastisin"),
            TeachingAid.suggestionsFrom(listOf("plastisin", "ember", "Gunting")),
        )
    }

    @Test
    fun `suggestionsFrom survives blank and empty input`() {
        assertEquals(emptyList<String>(), TeachingAid.suggestionsFrom(emptyList()))
        assertEquals(emptyList<String>(), TeachingAid.suggestionsFrom(listOf("", "   ")))
    }
}
