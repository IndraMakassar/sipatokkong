package com.antoniusindra.sipatokkong.ui.volunteer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import com.antoniusindra.sipatokkong.core.recurrence.labelId
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.TaskPriority
import java.time.DayOfWeek
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

    var instruksiValue by remember { mutableStateOf(TextFieldValue(ui.instruksi)) }

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
            // Without this the child list could only fail once and stay failed: the form stayed
            // fully interactive but unsubmittable, and retryLoadChildren() had no caller at all.
            if (!ui.isLoadingChildren && ui.children.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Daftar anak kosong. Pastikan admin sudah menugaskan anak kepada Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::retryLoadChildren) { Text("Muat Ulang") }
                }
            }

            // ---- Judul ----
            OutlinedTextField(
                value = ui.judul,
                onValueChange = viewModel::onJudulChange,
                label = { Text("Judul Program Ajar*") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Instruksi ----
            SectionLabel("Instruksi / Deskripsi Tugas")
            FormattingToolbar(
                onAction = { action ->
                    val text = instruksiValue.text
                    val selection = instruksiValue.selection
                    val selectedText = text.substring(selection.start, selection.end)

                    val (newText, newSelection) = when (action) {
                        "bold" -> {
                            val replacement = "**$selectedText**"
                            val transformed = text.substring(0, selection.start) + replacement + text.substring(selection.end)
                            val cursorOffset = if (selectedText.isEmpty()) selection.start + 2 else selection.start + replacement.length
                            transformed to TextRange(cursorOffset)
                        }
                        "header" -> insertBlockTag(text, selection, "### ")
                        "list" -> insertBlockTag(text, selection, "- ")
                        else -> text to selection
                    }

                    instruksiValue = TextFieldValue(newText, newSelection)
                    viewModel.onInstruksiChange(newText)
                }
            )
            OutlinedTextField(
                value = instruksiValue,
                onValueChange = {
                    instruksiValue = it
                    viewModel.onInstruksiChange(it.text)
                },
                placeholder = { Text("Gunakan toolbar di atas atau ketik manual (###, **, -)") },
                supportingText = { Text("Membantu orang tua memahami langkah-langkah terapi.") },
                minLines = 5,
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

            // ---- Pola pengulangan ----
            SectionLabel("Pola Pengulangan")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceType.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { type ->
                            FilterChip(
                                selected = ui.recurrence == type,
                                onClick = { viewModel.onRecurrenceChange(type) },
                                label = { Text(type.label) },
                            )
                        }
                    }
                }
            }

            when (ui.recurrence) {
                RecurrenceType.WEEKLY -> {
                    SectionLabel("Hari dalam Seminggu*")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.entries.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { day ->
                                    FilterChip(
                                        selected = day in ui.daysOfWeek,
                                        onClick = { viewModel.onDayOfWeekToggle(day) },
                                        label = { Text(day.labelId.take(3)) },
                                    )
                                }
                            }
                        }
                    }
                }

                RecurrenceType.MONTHLY -> OutlinedTextField(
                    value = ui.dayOfMonth,
                    onValueChange = viewModel::onDayOfMonthChange,
                    label = { Text("Tanggal dalam Bulan (1-31)*") },
                    supportingText = {
                        Text("Bulan yang lebih pendek otomatis memakai hari terakhirnya.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                RecurrenceType.INTERVAL -> OutlinedTextField(
                    value = ui.intervalDays,
                    onValueChange = viewModel::onIntervalChange,
                    label = { Text("Ulangi Tiap Berapa Hari*") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> Unit
            }

            if (ui.isRecurring) {
                SectionLabel("Tanggal Mulai*")
                DeadlineField(
                    deadline = ui.startsOn,
                    onPick = viewModel::onStartsOnChange,
                    label = "Tanggal Mulai",
                )
                SectionLabel("Tanggal Selesai*")
                DeadlineField(
                    deadline = ui.endsOn,
                    onPick = viewModel::onEndsOnChange,
                    label = "Tanggal Selesai",
                )
            } else {
                // ---- Deadline (hanya untuk tugas sekali jalan) ----
                SectionLabel("Deadline (opsional)")
                DeadlineField(
                    deadline = ui.deadline,
                    onPick = viewModel::onDeadlineChange,
                )
            }

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
private fun FormattingToolbar(
    onAction: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolbarButton(Icons.Default.Title, "Header") { onAction("header") }
        ToolbarButton(Icons.Default.FormatBold, "Bold") { onAction("bold") }
        ToolbarButton(Icons.AutoMirrored.Filled.FormatListBulleted, "List") { onAction("list") }
    }
}

private fun insertBlockTag(text: String, selection: TextRange, tag: String): Pair<String, TextRange> {
    val before = text.substring(0, selection.start)
    val after = text.substring(selection.end)

    // Ensure it starts on a new line if not already
    val needsLeadingNewline = before.isNotEmpty() && !before.endsWith("\n")
    val actualTag = if (needsLeadingNewline) "\n$tag" else tag

    val transformed = before + actualTag + after
    return transformed to TextRange(selection.start + actualTag.length)
}

@Composable
private fun ToolbarButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
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
    label: String = "Deadline",
) {
    var showPicker by remember { mutableStateOf(false) }
    val display = deadline?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "Pilih tanggal"

    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
    )

    if (showPicker) {
        // Seeded with the current value so re-opening starts on the chosen date.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = deadline
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
        )
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
