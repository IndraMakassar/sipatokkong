package com.antoniusindra.sipatokkong.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.ui.components.EmptyState
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.home.StatusChip
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenBg
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenText
import com.antoniusindra.sipatokkong.ui.theme.StatusOrangeBg
import com.antoniusindra.sipatokkong.ui.theme.StatusOrangeText
import com.antoniusindra.sipatokkong.ui.theme.StatusRedBg
import com.antoniusindra.sipatokkong.ui.theme.StatusRedText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))

/**
 * Admin attendance recap: how many home visits each volunteer logged over a date range.
 *
 * Brand colours are imported directly rather than read from `MaterialTheme.colorScheme`. Dynamic
 * colour is on app-wide, so on Android 12+ the scheme comes from the user's wallpaper — which is
 * why the older admin screens render in arbitrary hues. This one does not repeat that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RekapAbsensiScreen(
    onBack: () -> Unit,
    onDetail: (String, LocalDate, LocalDate) -> Unit,
    viewModel: RekapAbsensiViewModel = viewModel(factory = RekapAbsensiViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Rekap Absensi Relawan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = BrandTeal,
                    navigationIconContentColor = BrandTeal,
                ),
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    DateField(date = ui.from, onPick = viewModel::onFromChange, label = "Dari")
                }
                Box(Modifier.weight(1f)) {
                    DateField(date = ui.to, onPick = viewModel::onToChange, label = "Sampai")
                }
            }

            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Cari nama relawan") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                ui.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorRetry(message = ui.error!!, onRetry = viewModel::refresh)
                }

                ui.relawan.isEmpty() -> EmptyState(
                    icon = Icons.Filled.EventAvailable,
                    title = "Belum ada relawan",
                    subtitle = "Tambahkan akun relawan terlebih dahulu untuk melihat rekap absensi.",
                )

                ui.visibleRelawan.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Search,
                    title = "Tidak ada hasil",
                    subtitle = "Tidak ada relawan bernama \"${ui.query}\".",
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { SummaryCard(ui = ui) }

                    items(ui.visibleRelawan, key = { it.id }) { row ->
                        RelawanRekapCard(row, onClick = { onDetail(row.id, ui.from, ui.to) })
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(ui: RekapAbsensiUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${ui.from.format(ShortDate)} — ${ui.to.format(ShortDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
            Text(
                "${ui.totalKunjungan} kunjungan",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BrandTeal,
            )
            Text(
                if (ui.relawanTanpaKunjungan == 0) {
                    "Semua relawan melakukan kunjungan pada rentang ini."
                } else {
                    "${ui.relawanTanpaKunjungan} relawan belum melakukan kunjungan."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun RelawanRekapCard(row: RekapRelawan, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.nama,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!row.isActive) {
                    StatusChip(label = "Nonaktif", bg = StatusRedBg, textColor = StatusRedText)
                } else if (row.jumlahKunjungan == 0) {
                    StatusChip(
                        label = "Belum berkunjung",
                        bg = StatusOrangeBg,
                        textColor = StatusOrangeText,
                    )
                } else {
                    StatusChip(
                        label = "${row.jumlahKunjungan}x",
                        bg = StatusGreenBg,
                        textColor = StatusGreenText,
                    )
                }
            }

            Text(
                "${row.jumlahAnak} anak dikunjungi",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )

            Text(
                row.kunjunganTerakhir?.let { "Terakhir: ${it.format(ShortDate)}" }
                    ?: "Belum ada kunjungan pada rentang ini",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RekapAbsensiCardPreview() {
    SipatokkongTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RelawanRekapCard(
                RekapRelawan(
                    id = "1",
                    nama = "Kevin Saputra",
                    isActive = true,
                    jumlahKunjungan = 7,
                    jumlahAnak = 3,
                    kunjunganTerakhir = LocalDate.of(2026, 8, 24),
                ),
                onClick = {}
            )
            RelawanRekapCard(
                RekapRelawan(
                    id = "2",
                    nama = "Sitti Aminah",
                    isActive = true,
                    jumlahKunjungan = 0,
                    jumlahAnak = 0,
                    kunjunganTerakhir = null,
                ),
                onClick = {}
            )
        }
    }
}
