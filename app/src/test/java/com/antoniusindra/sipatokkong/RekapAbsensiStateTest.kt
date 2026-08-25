package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.admin.RekapAbsensiUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The numbers on the admin's attendance recap.
 *
 * This is the screen the foundation uses to tell a busy volunteer from an absent one, so two rules
 * carry real weight: a volunteer who logged nothing must still appear (they are the finding, not a
 * gap in the data), and the range must include both of its endpoints — an off-by-one at either end
 * silently drops a whole day of visits from every count.
 */
class RekapAbsensiStateTest {

    private val from: LocalDate = LocalDate.of(2026, 8, 1)
    private val to: LocalDate = LocalDate.of(2026, 8, 31)

    private fun relawan(id: String, nama: String, isActive: Boolean = true) = Profile(
        id = id,
        role = UserRole.RELAWAN,
        fullName = nama,
        isActive = isActive,
    )

    private fun visit(relawanId: String, anakId: String, tanggal: String) = Presensi(
        id = "$relawanId-$anakId-$tanggal",
        anakId = anakId,
        relawanId = relawanId,
        tanggal = tanggal,
        fotoPath = "anak/2026/08/x.jpg",
    )

    private fun state(
        visits: List<Presensi> = emptyList(),
        relawan: List<Profile> = emptyList(),
        query: String = "",
    ) = RekapAbsensiUiState(
        visits = visits,
        relawan = relawan,
        from = from,
        to = to,
        query = query,
        isLoading = false,
    )

    // ---- perRelawan ----

    @Test
    fun `visits are counted per volunteer`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin")),
            visits = listOf(
                visit("r1", "a1", "2026-08-05"),
                visit("r1", "a2", "2026-08-06"),
            ),
        )
        assertEquals(2, ui.perRelawan.single().jumlahKunjungan)
    }

    @Test
    fun `a volunteer with no visits still appears with zero`() {
        // The whole point of an attendance overview: grouping the rows would erase this person.
        val ui = state(relawan = listOf(relawan("r1", "Kevin")))
        val row = ui.perRelawan.single()
        assertEquals(0, row.jumlahKunjungan)
        assertEquals(0, row.jumlahAnak)
        assertNull(row.kunjunganTerakhir)
    }

    @Test
    fun `repeat visits to the same child count once towards jumlahAnak`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin")),
            visits = listOf(
                visit("r1", "a1", "2026-08-05"),
                visit("r1", "a1", "2026-08-12"),
                visit("r1", "a2", "2026-08-13"),
            ),
        )
        val row = ui.perRelawan.single()
        assertEquals(3, row.jumlahKunjungan)
        assertEquals(2, row.jumlahAnak)
    }

    @Test
    fun `kunjunganTerakhir is the newest date, not the last row`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin")),
            visits = listOf(
                visit("r1", "a1", "2026-08-20"),
                visit("r1", "a1", "2026-08-03"),
            ),
        )
        assertEquals(LocalDate.of(2026, 8, 20), ui.perRelawan.single().kunjunganTerakhir)
    }

    @Test
    fun `another volunteer's visits do not leak into the count`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin"), relawan("r2", "Aminah")),
            visits = listOf(visit("r2", "a1", "2026-08-05")),
        )
        val byId = ui.perRelawan.associateBy { it.id }
        assertEquals(0, byId.getValue("r1").jumlahKunjungan)
        assertEquals(1, byId.getValue("r2").jumlahKunjungan)
    }

    @Test
    fun `busiest volunteer sorts first`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin"), relawan("r2", "Aminah")),
            visits = listOf(
                visit("r2", "a1", "2026-08-05"),
                visit("r2", "a2", "2026-08-06"),
                visit("r1", "a1", "2026-08-07"),
            ),
        )
        assertEquals(listOf("r2", "r1"), ui.perRelawan.map { it.id })
    }

    // ---- date range ----

    @Test
    fun `both ends of the range are inclusive`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin")),
            visits = listOf(
                visit("r1", "a1", "2026-08-01"),
                visit("r1", "a2", "2026-08-31"),
            ),
        )
        assertEquals(2, ui.perRelawan.single().jumlahKunjungan)
    }

    @Test
    fun `visits outside the range are excluded`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin")),
            visits = listOf(
                visit("r1", "a1", "2026-07-31"),
                visit("r1", "a2", "2026-09-01"),
            ),
        )
        assertEquals(0, ui.perRelawan.single().jumlahKunjungan)
    }

    @Test
    fun `an unparseable date is ignored rather than crashing the recap`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin")),
            visits = listOf(visit("r1", "a1", "bukan-tanggal")),
        )
        assertEquals(0, ui.perRelawan.single().jumlahKunjungan)
    }

    @Test
    fun `an inverted range is reported as invalid`() {
        val ui = state().copy(from = to, to = from)
        assertTrue(!ui.isRangeValid)
    }

    // ---- search + totals ----

    @Test
    fun `name search is case-insensitive and partial`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin Saputra"), relawan("r2", "Sitti Aminah")),
            query = "sitti",
        )
        assertEquals(listOf("r2"), ui.visibleRelawan.map { it.id })
    }

    @Test
    fun `an empty query shows every volunteer`() {
        val ui = state(relawan = listOf(relawan("r1", "Kevin"), relawan("r2", "Aminah")))
        assertEquals(2, ui.visibleRelawan.size)
    }

    @Test
    fun `totals count visits in range and volunteers without any`() {
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin"), relawan("r2", "Aminah")),
            visits = listOf(
                visit("r1", "a1", "2026-08-05"),
                visit("r1", "a2", "2026-08-06"),
                visit("r1", "a2", "2026-07-01"), // out of range
            ),
        )
        assertEquals(2, ui.totalKunjungan)
        assertEquals(1, ui.relawanTanpaKunjungan)
    }

    @Test
    fun `search does not change the headline totals`() {
        // The summary card answers "what happened in this range", not "what am I filtering to".
        val ui = state(
            relawan = listOf(relawan("r1", "Kevin"), relawan("r2", "Aminah")),
            visits = listOf(visit("r2", "a1", "2026-08-05")),
            query = "kevin",
        )
        assertEquals(1, ui.totalKunjungan)
    }
}
