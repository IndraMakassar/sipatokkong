package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.ChildOption
import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.ui.volunteer.CreatePresensiUiState
import com.antoniusindra.sipatokkong.ui.volunteer.PresensiListUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The attendance form's submit rule, and the counter on the volunteer's history tab.
 *
 * Both live as derived state rather than inside `submit()`/`refresh()` because this suite has no
 * coroutine or mocking libraries — logic buried in a ViewModel method cannot be covered at all, so
 * putting it there would mean shipping the rule untested.
 */
class CreatePresensiFormTest {

    private fun form(
        anakId: String? = "anak-1",
        fotoUri: String? = "content://foto",
        isSubmitting: Boolean = false,
    ) = CreatePresensiUiState(
        children = listOf(ChildOption(id = "anak-1", nama = "Budi")),
        isLoadingChildren = false,
        selectedAnakId = anakId,
        fotoUri = fotoUri,
        isSubmitting = isSubmitting,
    )

    // ---- canSubmit ----

    @Test
    fun `a complete form can be submitted`() {
        assertTrue(form().canSubmit)
    }

    @Test
    fun `no child selected blocks submission`() {
        assertFalse(form(anakId = null).canSubmit)
    }

    @Test
    fun `a missing photo blocks submission`() {
        // The column is nullable, so nothing server-side stops a visit with no evidence.
        assertFalse(form(fotoUri = null).canSubmit)
    }

    @Test
    fun `submission is blocked while a save is already running`() {
        // Guards the double tap the unique index exists to catch, one layer earlier.
        assertFalse(form(isSubmitting = true).canSubmit)
    }

    @Test
    fun `a blank note does not block submission`() {
        assertTrue(form().copy(catatan = "   ").canSubmit)
    }

    // ---- selectedChildName ----

    @Test
    fun `the selected child resolves to its name`() {
        assertEquals("Budi", form().selectedChildName)
    }

    @Test
    fun `an unknown child id resolves to null rather than throwing`() {
        assertEquals(null, form(anakId = "tidak-ada").selectedChildName)
    }

    // ---- history counter ----

    private val today: LocalDate = LocalDate.of(2026, 8, 25)

    private fun visit(tanggal: String) = Presensi(
        id = tanggal,
        anakId = "anak-1",
        relawanId = "relawan-1",
        tanggal = tanggal,
    )

    private fun history(vararg tanggal: String) = PresensiListUiState(
        visits = tanggal.map(::visit),
        today = today,
        isLoading = false,
    )

    @Test
    fun `thisMonthCount counts only the current calendar month`() {
        val ui = history("2026-08-01", "2026-08-25", "2026-07-31")
        assertEquals(2, ui.thisMonthCount)
    }

    @Test
    fun `the same month a year earlier does not count`() {
        assertEquals(0, history("2025-08-10").thisMonthCount)
    }

    @Test
    fun `an unparseable date is skipped instead of crashing the tab`() {
        assertEquals(1, history("2026-08-10", "bukan-tanggal").thisMonthCount)
    }

    @Test
    fun `an unknown child id falls back to a readable label`() {
        val ui = history("2026-08-10")
        assertEquals("Anak tidak dikenal", ui.childNameOf(ui.visits.first()))
    }
}
