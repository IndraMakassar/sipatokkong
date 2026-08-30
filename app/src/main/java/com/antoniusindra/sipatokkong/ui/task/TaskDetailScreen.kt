package com.antoniusindra.sipatokkong.ui.task

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.antoniusindra.sipatokkong.data.model.ChildResponse
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.WorkDuration
import com.antoniusindra.sipatokkong.core.media.MediaDownloadState
import com.antoniusindra.sipatokkong.data.model.Documentation
import com.antoniusindra.sipatokkong.ui.components.DownloadIconButton
import com.antoniusindra.sipatokkong.ui.components.MediaDownloadHost
import com.antoniusindra.sipatokkong.ui.components.RichTextDisplay
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DetailDateFormat =
    DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.forLanguageTag("id-ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    tugasId: String,
    /**
     * The day to open, or null when the caller has none -- a notification tap, which carries only
     * the task id. The ViewModel then resolves one from the task's recurrence rule.
     */
    occurrenceDate: LocalDate?,
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = viewModel(factory = TaskDetailViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by viewModel.download.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(tugasId, occurrenceDate) { viewModel.load(tugasId, occurrenceDate) }
    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            Toast.makeText(context, "Bukti terkirim, tugas ditandai selesai!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val proofPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.onProofPicked(uri) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Program Ajar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = BrandTeal,
                    navigationIconContentColor = BrandTeal,
                )
            )
        },
        bottomBar = {
            if (ui.tugas != null) {
                SubmitBar(
                    alreadyDone = ui.alreadyDone,
                    hasProof = ui.proofUri != null,
                    isSubmitting = ui.isSubmitting,
                    onPick = {
                        proofPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onSubmit = viewModel::submit,
                )
            }
        }
    ) { innerPadding ->
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }

            ui.loadError != null -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                Text(ui.loadError!!, color = MaterialTheme.colorScheme.error)
            }

            ui.tugas != null -> DetailBody(
                modifier = Modifier.padding(innerPadding),
                ui = ui,
                download = downloadState,
                onDownloadDemo = { viewModel.demoRef()?.let(viewModel.download::request) },
                onDownloadProof = { viewModel.proofRef()?.let(viewModel.download::request) },
                onResponse = viewModel::onResponseChange,
                onDuration = viewModel::onDurationChange,
                onCatatan = viewModel::onCatatanChange,
            )
        }
    }

    MediaDownloadHost(
        state = downloadState,
        onConfirm = viewModel.download::confirm,
        onCancel = viewModel.download::cancel,
        onDismissMessage = viewModel.download::dismissMessage,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailBody(
    modifier: Modifier,
    ui: TaskDetailUiState,
    download: MediaDownloadState,
    onDownloadDemo: () -> Unit,
    onDownloadProof: () -> Unit,
    onResponse: (ChildResponse) -> Unit,
    onDuration: (WorkDuration) -> Unit,
    onCatatan: (String) -> Unit,
) {
    val tugas = ui.tugas!!
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Labelled so the parent knows this is the volunteer's demonstration, not their own upload.
        Text("Media Ajar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        MediaHero(
            mediaUrl = ui.mediaUrl,
            isVideo = tugas.mediaKind == "video",
            download = download,
            onDownload = onDownloadDemo,
        )

        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tugas.judul, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Instruksi", fontWeight = FontWeight.SemiBold, color = MutedText)
                RichTextDisplay(tugas.instruksi ?: "-")

                // Read-only: the volunteer chooses the aids, the parent only needs to know what
                // to have ready. Hidden entirely when there are none, so tasks that need no aid
                // don't show an empty heading.
                val aids = tugas.alatBantuLabels
                if (aids.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Alat Bantu", fontWeight = FontWeight.SemiBold, color = MutedText)
                    // verticalArrangement matters: without it, chips that wrap onto a second
                    // line sit flush against the one above. Same values as the FlowRow in
                    // CreateFeedbackScreen so both read alike.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        aids.forEach { label ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "⏱ Prioritas ${priorityLabel(tugas.priorityLevel)}" +
                        (ui.occurrenceDate?.let { " • Jadwal ${it.format(DetailDateFormat)}" } ?: "") +
                        (if (tugas.isRecurring) " • ${tugas.toRecurrenceRule().describe()}" else ""),
                    color = BrandTeal,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (ui.alreadyDone) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BrandTeal)
                Spacer(Modifier.size(8.dp))
                Text(
                    if (tugas.isRecurring) {
                        "Jadwal ini sudah ditandai selesai. Jadwal berikutnya tetap perlu dikerjakan."
                    } else {
                        "Tugas ini sudah ditandai selesai."
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            ui.submittedProof?.let { proof ->
                SubmittedProof(
                    proof = proof,
                    proofUrl = ui.submittedProofUrl,
                    download = download,
                    onDownload = onDownloadProof,
                )
            }
        } else {
            // Independence selector — 2x2 grid, because four cards with labels as long as
            // "Dengan Bantuan Penuh" do not fit across a single phone-width row.
            Text("Seberapa mandiri anak mengerjakan ini?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChildResponse.entries.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        row.forEach { r ->
                            ResponseCard(
                                response = r,
                                selected = ui.response == r,
                                onClick = { onResponse(r) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Duration
            Text("Berapa lama dikerjakan?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkDuration.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { d ->
                            FilterChip(
                                selected = ui.duration == d,
                                onClick = { onDuration(d) },
                                label = { Text(d.label) },
                            )
                        }
                    }
                }
            }

            // Catatan
            Text("Catatan Tambahan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            OutlinedTextField(
                value = ui.catatan,
                onValueChange = onCatatan,
                placeholder = { Text("Tuliskan komentar atau observasi tambahan di sini...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // Selected proof preview
            if (ui.proofUri != null) {
                Text("Bukti terpilih", fontWeight = FontWeight.SemiBold, color = MutedText)
                if (ui.proofKind == MediaKind.IMAGE) {
                    AsyncImage(
                        model = ui.proofUri,
                        contentDescription = "Bukti",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Text("🎬 Video bukti terpilih", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (ui.submitError != null) {
                Text(ui.submitError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

/**
 * The volunteer's demo media, with a download button.
 *
 * There is still no in-app video player. Downloading is what makes a demo video watchable at all:
 * it was mandatory to upload and impossible to view, so the file now goes to the gallery and the
 * phone's own player opens it. The `PlayCircle` icon, dead until now, says so out loud.
 */
@Composable
private fun MediaHero(
    mediaUrl: String?,
    isVideo: Boolean,
    download: MediaDownloadState,
    onDownload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF23323A)),
        contentAlignment = Alignment.Center,
    ) {
        if (mediaUrl != null && !isVideo) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = "Media demonstrasi",
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (isVideo) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "Video demonstrasi",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Unduh untuk menonton video ini",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        DownloadIconButton(
            state = download,
            onDownload = onDownload,
            modifier = Modifier.align(Alignment.TopEnd),
            contentDescription = if (isVideo) "Unduh video ke galeri" else "Unduh foto ke galeri",
        )
    }
}

/**
 * The proof this parent already sent for this date.
 *
 * `dokumentasi` was write-only until now — uploaded, then gone from the UI forever. Older
 * completions can still have no `dokumentasi_id`, so this section simply does not appear for them.
 */
@Composable
private fun SubmittedProof(
    proof: Documentation,
    proofUrl: String?,
    download: MediaDownloadState,
    onDownload: () -> Unit,
) {
    Text("Bukti yang Anda kirim", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF23323A)),
        contentAlignment = Alignment.Center,
    ) {
        if (proofUrl != null && proof.mediaKind != "video") {
            AsyncImage(
                model = proofUrl,
                contentDescription = "Bukti yang dikirim",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "Video bukti",
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Unduh untuk menonton",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        DownloadIconButton(
            state = download,
            onDownload = onDownload,
            modifier = Modifier.align(Alignment.TopEnd),
            contentDescription = "Unduh bukti ke galeri",
        )
    }
    proof.keterangan?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MutedText)
    }
}

@Composable
private fun ResponseCard(
    response: ChildResponse,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE8F1F2) else Color.White,
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, BrandTeal) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(response.emoji, fontSize = 28.sp)
            Text(
                response.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) BrandTeal else MutedText,
            )
        }
    }
}

@Composable
private fun SubmitBar(
    alreadyDone: Boolean,
    hasProof: Boolean,
    isSubmitting: Boolean,
    onPick: () -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Box(Modifier.padding(16.dp)) {
            Button(
                onClick = { if (hasProof) onSubmit() else onPick() },
                enabled = !alreadyDone && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                when {
                    isSubmitting -> CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    alreadyDone -> Text("Sudah Selesai")
                    !hasProof -> {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Kirim Bukti Foto & Selesai")
                    }
                    else -> Text("Kirim & Tandai Selesai")
                }
            }
        }
    }
}

private fun priorityLabel(db: String): String = when (db) {
    "high" -> "Tinggi"
    "low" -> "Rendah"
    else -> "Sedang"
}
