package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.antoniusindra.sipatokkong.core.media.MediaDownloadState
import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.ui.components.DownloadIconButton
import com.antoniusindra.sipatokkong.ui.components.EmptyState
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.components.MediaDownloadHost
import com.antoniusindra.sipatokkong.ui.home.StatusChip
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenBg
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenText
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))

/**
 * The volunteer's visit history, shown inside the "Presensi" tab of `VolunteerHomeScreen` — hence
 * no `Scaffold` of its own, the same arrangement `FeedbackListScreen` uses as a tab.
 *
 * Rows are deliberately text-only. Nothing in this app writes `foto_thumb_path`, so a thumbnail
 * grid would mean downloading every full-size proof photo just to render a list — the exact
 * opposite of the data-frugality rule these families need. That a photo exists is shown as a chip;
 * the photo itself is not re-fetched.
 */
@Composable
fun PresensiListScreen(
    onCatatKunjungan: () -> Unit,
    viewModel: PresensiListViewModel = viewModel(factory = PresensiListViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by viewModel.download.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    MediaDownloadHost(
        state = downloadState,
        onConfirm = viewModel.download::confirm,
        onCancel = viewModel.download::cancel,
        onDismissMessage = viewModel.download::dismissMessage,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                ErrorRetry(message = ui.error!!, onRetry = viewModel::refresh)
            }

            ui.visits.isEmpty() -> EmptyState(
                icon = Icons.Filled.EventAvailable,
                title = "Belum ada kunjungan tercatat",
                subtitle = "Setiap kunjungan rumah yang Anda catat akan muncul di sini.",
                action = { CatatKunjunganButton(onClick = onCatatKunjungan) },
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.height(4.dp))
                        CatatKunjunganButton(onClick = onCatatKunjungan)
                        Text(
                            "${ui.thisMonthCount} kunjungan bulan ini, ${ui.visits.size} total.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText,
                        )
                    }
                }

                items(ui.visits, key = { it.id }) { visit ->
                    VisitCard(
                        visit = visit,
                        childName = ui.childNameOf(visit),
                        isExpanded = ui.expandedId == visit.id,
                        photoUrl = ui.photoUrls[visit.id],
                        isLoadingPhoto = ui.loadingPhotoId == visit.id,
                        download = downloadState,
                        onToggle = { viewModel.toggleExpanded(visit) },
                        onDownload = { viewModel.photoRef(visit)?.let(viewModel.download::request) },
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CatatKunjunganButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandTeal,
            contentColor = Color.White,
        ),
    ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Catat Kunjungan", fontWeight = FontWeight.Bold)
    }
}

/**
 * One visit. Tapping expands it in place to show the proof photo — the same "expand, don't
 * navigate" idiom `ProgramListScreen` uses.
 *
 * The photo is fetched only once the card is opened. A thumbnail grid would download every
 * full-size proof photo just to render the list, and nothing in this app writes
 * `foto_thumb_path`, so there is no cheap version to show instead.
 */
@Composable
private fun VisitCard(
    visit: Presensi,
    childName: String,
    isExpanded: Boolean,
    photoUrl: String?,
    isLoadingPhoto: Boolean,
    download: MediaDownloadState,
    onToggle: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        onClick = onToggle,
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
                    childName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (visit.fotoPath != null) {
                    StatusChip(
                        label = "Ada foto",
                        bg = StatusGreenBg,
                        textColor = StatusGreenText,
                    )
                }
            }

            Text(
                visit.parsedDate?.format(ShortDate) ?: visit.tanggal,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )

            visit.catatan?.let { note ->
                Text(note, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }

            if (isExpanded && visit.fotoPath != null) {
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF23323A)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isLoadingPhoto -> CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )

                        photoUrl != null -> AsyncImage(
                            model = photoUrl,
                            contentDescription = "Foto bukti kunjungan",
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> Text(
                            "Pratinjau gagal dimuat, tapi foto tetap bisa diunduh.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    DownloadIconButton(
                        state = download,
                        onDownload = onDownload,
                        modifier = Modifier.align(Alignment.TopEnd),
                        contentDescription = "Unduh foto ke galeri",
                    )
                }
            }
        }
    }
}
