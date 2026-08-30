package com.antoniusindra.sipatokkong.ui.volunteer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.core.recurrence.RecurrenceType
import com.antoniusindra.sipatokkong.core.recurrence.labelId
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.TaskPriority
import com.antoniusindra.sipatokkong.data.model.TeachingAid
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.antoniusindra.sipatokkong.ui.components.MediaPickerBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onBack: () -> Unit,
    /** When set, the form opens prefilled from that template. Everything stays editable. */
    templateId: String? = null,
    /** When set, the form opens with this child already chosen. Still editable. */
    anakId: String? = null,
    viewModel: CreateTaskViewModel = viewModel(factory = CreateTaskViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var instruksiValue by remember { mutableStateOf(TextFieldValue(ui.instruksi)) }

    LaunchedEffect(templateId) {
        if (templateId != null) viewModel.applyTemplate(templateId)
    }

    LaunchedEffect(anakId) {
        if (anakId != null) viewModel.preselectChild(anakId)
    }

    // The template fills `instruksi` in the ViewModel, but the text field's own TextFieldValue is
    // seeded once and owns the cursor, so it has to be told separately or the box stays empty.
    LaunchedEffect(ui.instruksi) {
        if (ui.instruksi != instruksiValue.text) {
            instruksiValue = TextFieldValue(ui.instruksi)
        }
    }

    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            // A template failure still leaves a created program, so say what actually happened
            // rather than the cheerful default -- and give it longer to be read.
            val message = ui.templateWarning ?: "Program ajar berhasil dibuat!"
            val length = if (ui.templateWarning != null) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            Toast.makeText(context, message, length).show()
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
                hasMedia = ui.hasMedia,
                previewUrl = ui.mediaUri ?: ui.templateMediaPreviewUrl,
                mediaKind = ui.mediaKind,
                mediaName = ui.templateMediaLabel
                    ?.let { "Dari template \"$it\" — ketuk untuk ganti" }
                    ?: ui.mediaName,
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

            // ---- Alat bantu ----
            SectionLabel("Alat Bantu (opsional)")
            AlatBantuDropdown(
                query = ui.aidQuery,
                selected = ui.aidSelected,
                matches = ui.aidMatches,
                addable = ui.addableAid,
                onToggle = viewModel::onAlatBantuToggle,
                onQueryChange = viewModel::onAidQueryChange,
                onAddTyped = viewModel::onAddTypedAid,
            )

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
                    label = "Tanggal Mulai",
                    onPick = viewModel::onStartsOnChange,
                )
                SectionLabel("Tanggal Selesai*")
                DeadlineField(
                    deadline = ui.endsOn,
                    label = "Tanggal Selesai",
                    onPick = viewModel::onEndsOnChange,
                )
            } else {
                // ---- Deadline (hanya untuk tugas sekali jalan) ----
                SectionLabel("Deadline (opsional)")
                DeadlineField(
                    deadline = ui.deadline,
                    onPick = viewModel::onDeadlineChange,
                )
            }

            // ---- Template ----
            // A card, not the bare checkbox row this used to be. It was the only section of this
            // form without a SectionLabel and without any container, so on a three-screen scroll it
            // read as a footnote to the submit button rather than a feature -- which is exactly how
            // it kept getting missed.
            SectionLabel("Template")
            if (ui.hasTemplateForThisJudul) {
                // Replaced, not silently removed: UI that vanishes without explanation is worse
                // than UI that explains itself.
                Text(
                    "Sudah ada template untuk judul ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onSaveAsTemplateChange(!ui.saveAsTemplate) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = ui.saveAsTemplate,
                                onCheckedChange = viewModel::onSaveAsTemplateChange,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "Simpan juga sebagai template",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Supaya bisa dipakai lagi tanpa mengetik ulang.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText,
                                )
                            }
                        }
                        if (ui.saveAsTemplate) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = ui.templateName,
                                onValueChange = viewModel::onTemplateNameChange,
                                // Optional now, not required: it arrives prefilled from the judul,
                                // and a blank one falls back to the judul rather than blocking the
                                // save the way it used to.
                                label = { Text("Nama Template (opsional)") },
                                placeholder = { Text(ui.judul.ifBlank { "mis. Latihan Vokal A-I-U" }) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = {
                                    // Templates are shared across the whole foundation, and so is
                                    // their media. Say so here rather than in a doc nobody opens.
                                    Text("Template dan foto/videonya bisa dilihat semua relawan.")
                                },
                            )
                        }
                    }
                }
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

/**
 * Searchable multi-select for teaching aids, which also accepts an aid the volunteer types.
 *
 * A dropdown rather than a row of chips like Prioritas: the options would eat far too much vertical
 * space on a phone. Typing filters the list, but the already-picked aids are pinned above the
 * results and stay visible whatever the filter — otherwise searching would hide the selection the
 * volunteer is building. Picks are echoed as removable chips underneath.
 *
 * [selected], [matches] and [addable] are derived in the ViewModel so the menu's contents are unit
 * tested rather than only inspectable by eye.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AlatBantuDropdown(
    query: String,
    selected: List<String>,
    matches: List<String>,
    addable: String?,
    onToggle: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onAddTyped: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuScroll = rememberScrollState()

    // After scrolling down the full list, a new keystroke leaves a two-row menu parked mid-scroll.
    LaunchedEffect(query) { menuScroll.scrollTo(0) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = query,
            // Typing never routes through onExpandedChange, so the menu must be reopened by hand.
            onValueChange = { expanded = true; onQueryChange(it) },
            label = { Text("Alat Bantu") },
            placeholder = { Text("Cari, atau ketik alat baru") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (addable != null) onAddTyped() }),
            trailingIcon = {
                // Its own anchor: with an editable field the chevron is what toggles the menu,
                // since tapping the text itself has to place the cursor instead.
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded = expanded,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            scrollState = menuScroll,
            // Otherwise the height is whatever space the window happens to have, which with the
            // keyboard up can be less than the pinned selection alone.
            modifier = Modifier.heightIn(max = 280.dp),
        ) {
            // Above the pinned selection on purpose. Once a few aids are picked, the pinned rows
            // alone can fill the menu, and this is the row the volunteer is typing towards.
            if (addable != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "Tambah \"$addable\" sebagai alat bantu baru",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = onAddTyped,
                    // Leading slot left empty so this does not line up with the checkbox column
                    // and get read as just another aid.
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
                HorizontalDivider()
            }
            if (selected.isNotEmpty()) {
                AidMenuHeader("TERPILIH")
                selected.forEach { AidMenuRow(it, checked = true, onToggle = onToggle) }
                HorizontalDivider()
            }
            matches.forEach { AidMenuRow(it, checked = false, onToggle = onToggle) }
            if (selected.isEmpty() && matches.isEmpty() && addable == null) {
                AidMenuHeader("Tidak ada alat bantu yang cocok")
            }
        }
    }

    if (selected.isNotEmpty()) {
        // Same omission as the parent screen had: without verticalArrangement, wrapped chip rows
        // touch. Selecting a few aids makes this wrap immediately.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selected.forEach { value ->
                val label = TeachingAid.labelOf(value)
                InputChip(
                    selected = true,
                    onClick = { onToggle(value) },
                    label = { Text(label) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Hapus $label",
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
}

/**
 * A non-clickable section label inside the aid menu.
 *
 * A plain Text, not a disabled DropdownMenuItem: that would cost a 48dp row, render at 38% alpha,
 * and make TalkBack announce a dead control. Padding matches [ExposedDropdownMenuDefaults
 * .ItemContentPadding] so headers line up with the rows under them.
 */
@Composable
private fun AidMenuHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

/** One checkable aid row. The menu stays open: picking several aids in one go is the normal case. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AidMenuRow(value: String, checked: Boolean, onToggle: (String) -> Unit) {
    DropdownMenuItem(
        text = { Text(TeachingAid.labelOf(value)) },
        onClick = { onToggle(value) },
        // onCheckedChange = null: the row already handles the click, so the checkbox is decorative
        // and TalkBack announces one control instead of two overlapping ones.
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null) },
        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
    )
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChildDropdown(
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

/**
 * A date, picked from a calendar.
 *
 * Pass [onPick] to make it editable; leave it out and the field is a plain read-only display, which
 * is what the presensi form wants — a volunteer records the visit they are making now, so its date
 * is deliberately not theirs to change.
 *
 * **Editable fields are `readOnly`, not `enabled = false`.** Disabling paints the whole control in
 * the disabled palette — grey text, grey border, grey label — which on a field you are meant to tap
 * reads as "this is switched off", and that is exactly how it looked. `readOnly` keeps the normal
 * colours and still refuses typing; the tap is caught through the `interactionSource` instead of a
 * `clickable` modifier, which is the supported way to do it on a text field. The genuinely locked
 * variant keeps the grey, because there it is telling the truth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeadlineField(
    deadline: LocalDate?,
    label: String = "Deadline",
    onPick: ((LocalDate?) -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val display = deadline?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "Pilih tanggal"
    val interactionSource = remember { MutableInteractionSource() }

    if (onPick != null) {
        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Release) showPicker = true
            }
        }
    }

    OutlinedTextField(
        value = display,
        onValueChange = {},
        readOnly = true,
        enabled = onPick != null,
        label = { Text(label) },
        trailingIcon = {
            // An optional date needs a way back to "not set" — without this, one stray tap on
            // "Deadline (opsional)" makes it permanent for the life of the form.
            if (onPick != null && deadline != null) {
                IconButton(onClick = { onPick(null) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Hapus tanggal")
                }
            } else {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            }
        },
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showPicker && onPick != null) {
        // Seeded with the current value so re-opening lands on the date already chosen.
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
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Batal") } },
        ) {
            DatePicker(state = state)
        }
    }
}
