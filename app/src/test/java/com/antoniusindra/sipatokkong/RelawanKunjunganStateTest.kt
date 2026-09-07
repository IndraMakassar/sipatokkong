package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.ui.admin.RelawanKunjunganUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RelawanKunjunganStateTest {

    @Test
    fun `initial state is loading with no visits`() {
        val ui = RelawanKunjunganUiState()
        assertEquals(true, ui.isLoading)
        assertEquals(emptyList<Presensi>(), ui.visits)
        assertNull(ui.error)
    }

    @Test
    fun `loaded state has visits and is not loading`() {
        val visits = listOf(
            Presensi(
                id = "v1",
                anakId = "a1",
                relawanId = "r1",
                tanggal = "2026-09-07",
                fotoPath = "path/to/foto.jpg"
            )
        )
        val ui = RelawanKunjunganUiState(visits = visits, isLoading = false)
        assertEquals(false, ui.isLoading)
        assertEquals(visits, ui.visits)
        assertNull(ui.error)
    }
}
