package com.antoniusindra.sipatokkong.ui.task

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
    occurrenceDate: LocalDate,
    onBack: () -> Unit,
    viewModel: TaskDetailViewModel = viewModel(factory = TaskDetailViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
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
                onResponse = viewModel::onResponseChange,
                onDuration = viewModel::onDurationChange,
                onCatatan = viewModel::onCatatanChange,
            )
        }
    }
}

@Composable
private fun DetailBody(
    modifier: Modifier,
    ui: TaskDetailUiState,
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
        MediaHero(mediaUrl = ui.mediaUrl, isVideo = tugas.mediaKind == "video")

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

@Composable
private fun MediaHero(mediaUrl: String?, isVideo: Boolean) {
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
            // Private-bucket video playback is out of scope; show a play affordance.
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "Video demonstrasi",
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
        }
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
