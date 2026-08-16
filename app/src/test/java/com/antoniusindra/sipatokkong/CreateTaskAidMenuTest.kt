package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.TeachingAid
import com.antoniusindra.sipatokkong.ui.volunteer.CreateTaskUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the aid menu shows for a given query and selection.
 *
 * These live in [CreateTaskUiState] rather than the composable precisely so they can be checked
 * here — the rules about what stays visible under a filter are the part most likely to be wrong,
 * and eyeballing a dropdown on an emulator is a poor way to find out.
 */
class CreateTaskAidMenuTest {

    private fun state(
        selected: Set<String> = emptySet(),
        query: String = "",
        suggestions: List<String> = emptyList(),
    ) = CreateTaskUiState(alatBantu = selected, aidQuery = query, aidSuggestions = suggestions)

    // ---- aidOptions ----

    @Test
    fun `aidOptions lists every built-in when there is no history`() {
        assertEquals(TeachingAid.entries.map { it.dbValue }, state().aidOptions)
    }

    @Test
    fun `aidOptions appends past aids after the built-ins`() {
        val options = state(suggestions = listOf("gunting", "plastisin")).aidOptions
        assertEquals(TeachingAid.entries.size + 2, options.size)
        assertEquals(listOf("gunting", "plastisin"), options.takeLast(2))
    }

    @Test
    fun `aidOptions includes an aid added this session but not yet in history`() {
        // Without this, retyping an aid just added would offer to create it a second time.
        assertTrue("gunting" in state(selected = setOf("gunting")).aidOptions)
    }

    @Test
    fun `aidOptions does not list a session aid twice when history already has it`() {
        val options = state(selected = setOf("gunting"), suggestions = listOf("Gunting")).aidOptions
        assertEquals(1, options.count { it.equals("gunting", ignoreCase = true) })
    }

    // ---- aidSelected ----

    @Test
    fun `aidSelected stays visible when the query matches none of it`() {
        // The whole reason the selection is pinned: filtering must not hide what is already picked.
        val ui = state(selected = setOf("cermin", "sedotan"), query = "bol")
        assertEquals(listOf("cermin", "sedotan"), ui.aidSelected)
    }

    @Test
    fun `aidSelected is empty before anything is picked`() {
        assertEquals(emptyList<String>(), state(query = "bol").aidSelected)
    }

    // ---- aidMatches ----

    @Test
    fun `aidMatches returns every unpicked option when the query is blank`() {
        val ui = state(selected = setOf("cermin"))
        assertEquals(TeachingAid.entries.size - 1, ui.aidMatches.size)
        assertFalse("cermin" in ui.aidMatches)
    }

    @Test
    fun `aidMatches filters on the visible label, not the stored value`() {
        // "Kain aneka tekstur" is stored as kain_tekstur; searching "aneka" must still find it.
        assertTrue("kain_tekstur" in state(query = "aneka").aidMatches)
    }

    @Test
    fun `aidMatches ignores case and surrounding spaces`() {
        assertEquals(state(query = "bo").aidMatches, state(query = "  BO  ").aidMatches)
    }

    @Test
    fun `aidMatches hides options that do not match`() {
        // "bo" catches Bola and Botol & tutup; the other ten built-ins drop out.
        assertEquals(listOf("bola", "botol_tutup"), state(query = "bo").aidMatches)
    }

    @Test
    fun `aidMatches excludes what is already picked so it is not listed twice`() {
        // "bola" is pinned above; repeating it in the results would show one aid on two rows.
        assertEquals(listOf("botol_tutup"), state(selected = setOf("bola"), query = "bo").aidMatches)
    }

    @Test
    fun `aidMatches searches past aids too`() {
        assertEquals(listOf("gunting"), state(query = "gun", suggestions = listOf("gunting")).aidMatches)
    }

    // ---- addableAid ----

    @Test
    fun `addableAid is null while nothing is typed`() {
        assertNull(state().addableAid)
        assertNull(state(query = "   ").addableAid)
    }

    @Test
    fun `addableAid offers a genuinely new aid`() {
        assertEquals("gunting", state(query = "gunting").addableAid)
    }

    @Test
    fun `addableAid cleans the typed text before offering it`() {
        assertEquals("gunting kecil", state(query = "gunting   kecil ").addableAid)
    }

    @Test
    fun `addableAid stays silent for a built-in typed out in full`() {
        // Otherwise the volunteer is invited to create a duplicate of something already listed.
        listOf("Cermin", "cermin", "CERMIN", "kain aneka tekstur").forEach {
            assertNull("menawarkan duplikat untuk '$it'", state(query = it).addableAid)
        }
    }

    @Test
    fun `addableAid stays silent for a built-in typed as its stored value`() {
        assertNull(state(query = "kain_tekstur").addableAid)
    }

    @Test
    fun `addableAid stays silent for an aid already in history`() {
        assertNull(state(query = "Gunting", suggestions = listOf("gunting")).addableAid)
    }

    @Test
    fun `addableAid still offers a partial query that matches nothing exactly`() {
        // "bol" narrows to Bola and Botol, but is not either of them — adding it stays available.
        assertEquals("bol", state(query = "bol").addableAid)
    }
}
