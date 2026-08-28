package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.AssignedChild
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.ui.volunteer.VolunteerHomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * What each child card on the volunteer's home says.
 *
 * Worth pinning down because two of these are first-of-their-kind in this app: nothing has ever
 * displayed a child's age (`tanggal_lahir` was write-only until now), and the per-child programme
 * count has to agree with the total printed directly above it.
 */
class AssignedChildTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 29)

    private fun child(
        id: String = "anak-1",
        nama: String = "Kevin",
        tanggalLahir: String? = "2020-03-15",
        catatanKondisi: String? = null,
        orangtuaId: String? = "ortu-1",
    ) = AssignedChild(
        id = id,
        nama = nama,
        tanggalLahir = tanggalLahir,
        catatanKondisi = catatanKondisi,
        orangtuaId = orangtuaId,
    )

    private fun task(
        id: String,
        anakId: String = "anak-1",
        stoppedAt: String? = null,
        startsOn: String? = "2026-08-01",
        endsOn: String? = "2026-12-31",
    ) = Task(
        id = id,
        anakId = anakId,
        relawanId = "relawan-1",
        judul = "Program $id",
        mediaKind = "image",
        mediaPath = "x.jpg",
        priorityLevel = "medium",
        status = "assigned",
        recurrence = "daily",
        startsOn = startsOn,
        endsOn = endsOn,
        stoppedAt = stoppedAt,
    )

    // ---------------------------------------------------------------- age

    @Test
    fun `a child over one year old is counted in years`() {
        assertEquals("6 tahun", child(tanggalLahir = "2020-03-15").ageLabel(today))
    }

    @Test
    fun `a birthday that has not arrived yet does not round up`() {
        // Born Dec 2020; on 29 Aug 2026 they are 5, not 6. Using year subtraction breaks here.
        assertEquals("5 tahun", child(tanggalLahir = "2020-12-31").ageLabel(today))
    }

    @Test
    fun `under one year is counted in months, never zero years`() {
        // "0 tahun" says nothing about a baby, and months are a real difference in therapy terms.
        assertEquals("8 bulan", child(tanggalLahir = "2025-12-29").ageLabel(today))
        assertEquals("0 bulan", child(tanggalLahir = "2026-08-29").ageLabel(today))
        // Born 30 Sep 2025: on 29 Aug 2026 the eleventh month is one day short of complete.
        assertEquals("10 bulan", child(tanggalLahir = "2025-09-30").ageLabel(today))
    }

    @Test
    fun `a missing or unusable birth date yields no age at all`() {
        // `tanggal_lahir` is nullable and has never been rendered before, so blanks are normal and
        // a bad stored value must not take the home screen down.
        assertNull(child(tanggalLahir = null).ageLabel(today))
        assertNull(child(tanggalLahir = "").ageLabel(today))
        assertNull(child(tanggalLahir = "bukan tanggal").ageLabel(today))
        assertNull(child(tanggalLahir = "2030-01-01").ageLabel(today))  // in the future
    }

    // ---------------------------------------------------------------- avatar initial

    @Test
    fun `the avatar initial is the first letter, upper-cased and trimmed`() {
        assertEquals('K', child(nama = "Kevin").initial)
        assertEquals('S', child(nama = "  siti").initial)
    }

    @Test
    fun `a blank name gives no initial, so the avatar can fall back to an icon`() {
        assertNull(child(nama = "   ").initial)
    }

    // ---------------------------------------------------------------- per-child counts

    @Test
    fun `the per-child count agrees with the total shown above it`() {
        // The rule that is easiest to break silently: counting raw `programs` would make the cards
        // add up to more than the hero card's "N program berjalan" on the same screen.
        val programs = listOf(
            task("running"),
            task("stopped", stoppedAt = "2026-08-10T00:00:00Z"),
            task("expired", startsOn = "2026-01-01", endsOn = "2026-06-30"),
        )
        val ui = VolunteerHomeUiState(
            programs = programs,
            children = listOf(child()),
            today = today,
            isLoading = false,
        )

        assertEquals(1, ui.activeProgramCount(child()))
        assertEquals(ui.activeCount, ui.activeProgramCount(child()))
    }

    @Test
    fun `programs are counted per child, not shared between them`() {
        val ui = VolunteerHomeUiState(
            programs = listOf(task("a", anakId = "anak-1"), task("b", anakId = "anak-2"), task("c", anakId = "anak-2")),
            children = listOf(child(id = "anak-1"), child(id = "anak-2")),
            today = today,
            isLoading = false,
        )
        assertEquals(1, ui.activeProgramCount(child(id = "anak-1")))
        assertEquals(2, ui.activeProgramCount(child(id = "anak-2")))
        assertEquals(0, ui.activeProgramCount(child(id = "anak-3")))
    }

    // ---------------------------------------------------------------- subtitle

    @Test
    fun `the subtitle joins age and parent, and drops whichever is missing`() {
        val ui = VolunteerHomeUiState(
            children = listOf(child()),
            parentNames = mapOf("ortu-1" to "Ibu Siti"),
            today = today,
            isLoading = false,
        )

        assertEquals("6 tahun · Ibu Siti", ui.subtitleOf(child()))
        // No dangling separator when one half is absent -- children without a birth date are the
        // normal case, since nothing ever displayed it before.
        assertEquals("Ibu Siti", ui.subtitleOf(child(tanggalLahir = null)))
        assertEquals("6 tahun", ui.subtitleOf(child(orangtuaId = "tidak-dikenal")))
        assertEquals("", ui.subtitleOf(child(tanggalLahir = null, orangtuaId = null)))
    }

    @Test
    fun `an unresolved parent shows nothing rather than a placeholder`() {
        val ui = VolunteerHomeUiState(children = listOf(child()), today = today, isLoading = false)
        assertNull(ui.parentNameOf(child()))
        assertNull(ui.parentNameOf(child(orangtuaId = null)))
    }
}
