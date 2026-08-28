package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.ui.volunteer.CreateTaskUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that make "save as template" one tap instead of a typing exercise.
 *
 * Both live on [CreateTaskUiState] rather than in the composable so they can be checked here. The
 * blank-name case matters most: it used to reject the **whole** submit, so a volunteer who ticked
 * the box and left the name empty could not save their program at all until they went back up the
 * form and unticked it.
 */
class CreateTaskTemplateFieldTest {

    private fun state(
        judul: String = "Latihan Vokal A-I-U",
        templateName: String = "",
        existing: List<String> = emptyList(),
    ) = CreateTaskUiState(
        judul = judul,
        templateName = templateName,
        existingTemplateJuduls = existing,
    )

    // ---------------------------------------------------------------- effectiveTemplateName

    @Test
    fun `a blank name falls back to the judul`() {
        assertEquals("Latihan Vokal A-I-U", state(templateName = "").effectiveTemplateName)
        assertEquals("Latihan Vokal A-I-U", state(templateName = "   ").effectiveTemplateName)
    }

    @Test
    fun `a typed name wins over the judul`() {
        assertEquals(
            "Vokal Mingguan",
            state(templateName = "Vokal Mingguan").effectiveTemplateName,
        )
    }

    @Test
    fun `both sides are trimmed`() {
        assertEquals("Vokal Mingguan", state(templateName = "  Vokal Mingguan  ").effectiveTemplateName)
        assertEquals("Latihan Vokal", state(judul = "  Latihan Vokal  ", templateName = "").effectiveTemplateName)
    }

    // ---------------------------------------------------------------- hasTemplateForThisJudul

    @Test
    fun `an existing template for this judul is recognised, ignoring case and spacing`() {
        assertTrue(state(existing = listOf("Latihan Vokal A-I-U")).hasTemplateForThisJudul)
        assertTrue(state(existing = listOf("latihan vokal a-i-u")).hasTemplateForThisJudul)
        assertTrue(state(existing = listOf("  Latihan Vokal A-I-U  ")).hasTemplateForThisJudul)
        assertTrue(state(judul = "  Latihan Vokal A-I-U", existing = listOf("Latihan Vokal A-I-U"))
            .hasTemplateForThisJudul)
    }

    @Test
    fun `a different judul is not a match`() {
        assertFalse(state(existing = listOf("Latihan Motorik Halus")).hasTemplateForThisJudul)
        assertFalse(state(existing = emptyList()).hasTemplateForThisJudul)
    }

    @Test
    fun `an empty judul never matches`() {
        // Without the guard, a stored template with a blank judul would hide the whole template
        // card on a brand-new form before anything had been typed.
        assertFalse(state(judul = "", existing = listOf("")).hasTemplateForThisJudul)
        assertFalse(state(judul = "   ", existing = listOf("   ")).hasTemplateForThisJudul)
    }

    // ---------------------------------------------------------------- the name can never be blank

    @Test
    fun `ticking the box can never produce a nameless template`() {
        // This is what allowed the submit-blocking validation to be deleted. Whatever the volunteer
        // does to the optional name field, a judul is already required to submit at all, so the
        // template always has something to be called.
        listOf("", "   ", "\t").forEach { typed ->
            assertTrue(
                "blank name '$typed' produced an empty template name",
                state(templateName = typed).effectiveTemplateName.isNotBlank(),
            )
        }
    }
}
