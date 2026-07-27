package com.antoniusindra.sipatokkong.ui.volunteer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onBack: () -> Unit,
    viewModel: CreateTaskViewModel = viewModel(factory = CreateTaskViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            Toast.makeText(context, "Program ajar berhasil dibuat!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) viewModel.onMediaPicked(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buat Program Ajar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- Pilih Anak ----
            SectionLabel("Pilih Anak*")
            ChildDropdown(
                children = ui.children,
                isLoading = ui.isLoadingChildren,
                selectedName = ui.selectedChildName,
                onSelect = viewModel::onAnakSelected,
            )

            // ---- Judul ----
            OutlinedTextField(
                value = ui.judul,
                onValueChange = viewModel::onJudulChange,
                label = { Text("Judul Program Ajar*") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Instruksi ----
            OutlinedTextField(
                value = ui.instruksi,
                onValueChange = viewModel::onInstruksiChange,
                label = { Text("Instruksi / Deskripsi Tugas") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Upload media (wajib) ----
            SectionLabel("Foto/Video Demonstrasi*")
            MediaPickerBox(
                mediaUri = ui.mediaUri,
                mediaKind = ui.mediaKind,
                mediaName = ui.mediaName,
                onPick = {
                    mediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
            )

            // ---- Prioritas ----
            SectionLabel("Prioritas")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.entries.forEach { p ->
                    FilterChip(
                        selected = ui.priority == p,
                        onClick = { viewModel.onPriorityChange(p) },
                        label = { Text(p.label) },
                    )
                }
            }

            // ---- Deadline ----
            SectionLabel("Deadline (opsional)")
            DeadlineField(
                deadline = ui.deadline,
                onPick = viewModel::onDeadlineChange,
            )

            if (ui.error != null) {
                Text(
                    ui.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::submit,
                enabled = !ui.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (ui.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Simpan Program Ajar")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChildDropdown(
    children: List<com.antoniusindra.sipatokkong.data.model.ChildOption>,
    isLoading: Boolean,
    selectedName: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = when {
        isLoading -> "Memuat daftar anak..."
        children.isEmpty() -> "Belum ada anak yang ditugaskan"
        selectedName != null -> selectedName
        else -> "Pilih anak"
    }

    ExposedDropdownMenuBox(
        expanded = expanded && children.isNotEmpty(),
        onExpandedChange = { if (children.isNotEmpty()) expanded = it },
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text("Anak") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            children.forEach { anak ->
                DropdownMenuItem(
                    text = { Text(anak.nama) },
                    onClick = {
                        onSelect(anak.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaPickerBox(
    mediaUri: String?,
    mediaKind: MediaKind?,
    mediaName: String?,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (mediaUri != null && mediaKind == MediaKind.IMAGE) 200.dp else 120.dp)
            .clickable { onPick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            mediaUri != null && mediaKind == MediaKind.IMAGE -> {
                AsyncImage(
                    model = mediaUri,
                    contentDescription = "Pratinjau media",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text("Ketuk untuk ganti", style = MaterialTheme.typography.labelMedium)
            }

            mediaUri != null && mediaKind == MediaKind.VIDEO -> {
                Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(mediaName ?: "Video terpilih")
                Text("Ketuk untuk ganti", style = MaterialTheme.typography.labelMedium)
            }

            else -> {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text("Ketuk untuk pilih foto/video")
                Text("JPG, WebP, atau MP4", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineField(
    deadline: LocalDate?,
    onPick: (LocalDate?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val label = deadline?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "Pilih tanggal"

    OutlinedTextField(
        value = label,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text("Deadline") },
        trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
    )

    if (showPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Batal") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
