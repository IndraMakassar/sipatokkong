package com.antoniusindra.sipatokkong.ui.volunteer

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.core.util.ShortDateId
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskPriority
import com.antoniusindra.sipatokkong.ui.components.ActionErrorBanner
import com.antoniusindra.sipatokkong.ui.components.EmptyState
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.components.RichTextDisplay
import com.antoniusindra.sipatokkong.ui.home.StatusChip
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.IconBoxBg
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenBg
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenText
import com.antoniusindra.sipatokkong.ui.theme.StatusRedBg
import com.antoniusindra.sipatokkong.ui.theme.StatusRedText
import com.antoniusindra.sipatokkong.ui.theme.StatusTealBg
import com.antoniusindra.sipatokkong.ui.theme.StatusTealText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDate = ShortDateId

/** Volunteer's own programs, with the option to stop a recurring one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramListScreen(
    onBack: () -> Unit,
    onCreateProgram: () -> Unit,
    onEditProgram: (String) -> Unit = {},
    viewModel: ProgramListViewModel = viewModel(factory = ProgramListViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf<Task?>(null) }
    var confirmStop by remember { mutableStateOf<Task?>(null) }
    var nameTemplateFor by remember { mutableStateOf<Task?>(null) }
    var templateName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Nothing on this screen changes when a template is saved, so without this the button would
    // just stop spinning and leave the volunteer guessing whether it worked.
    val context = LocalContext.current
    LaunchedEffect(ui.templateSavedName) {
        ui.templateSavedName?.let { name ->
            Toast.makeText(context, "Template \"$name\" tersimpan.", Toast.LENGTH_SHORT).show()
            viewModel.dismissTemplateSaved()
        }
    }

    confirmCancel?.let { target ->
        AlertDialog(onDismissRequest = { confirmCancel = null }, title = { Text("Batalkan tugas?") },
            text = { Text("Tugas ini tidak akan diminta lagi. Tugas yang sudah disetor tidak dapat dibatalkan.") },
            confirmButton = { TextButton(onClick = { viewModel.cancelTask(target); confirmCancel = null }) { Text("Batalkan tugas") } },
            dismissButton = { TextButton(onClick = { confirmCancel = null }) { Text("Kembali") } })
    }
    confirmStop?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmStop = null },
            title = { Text("Hentikan program?") },
            text = {
                Text(
                    "\"${target.judul}\" tidak akan muncul lagi untuk orang tua mulai hari ini. " +
                        "Riwayat pengerjaan yang sudah ada tetap tersimpan."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.stopProgram(target.id)
                    confirmStop = null
                }) { Text("Hentikan") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = null }) { Text("Batal") }
            },
        )
    }

    nameTemplateFor?.let { target ->
        AlertDialog(
            onDismissRequest = { nameTemplateFor = null },
            title = { Text("Jadikan template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Judul, instruksi, prioritas, alat bantu, foto/video dan pola " +
                            "pengulangannya disimpan. Anak dan tanggalnya tidak — itu diisi ulang " +
                            "setiap kali template dipakai."
                    )
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it.trimStart() },
                        label = { Text("Nama Template") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // No blank-name dead end: if it is cleared, fall back to the judul on save.
                    // Templates are shared foundation-wide, and so is their media. Say so here.
                    Text(
                        "Template dan foto/videonya bisa dilihat semua relawan.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedText,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveAsTemplate(target, templateName.ifBlank { target.judul })
                        nameTemplateFor = null
                        templateName = ""
                    },
                ) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { nameTemplateFor = null; templateName = "" }) { Text("Batal") }
            },
        )
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Program Ajar Saya", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp)
        ) {
            // A failed stop is reported here, above the list, which stays exactly where it was.
            ui.actionError?.let { message ->
                ActionErrorBanner(message = message, onDismiss = viewModel::dismissActionError)
                Spacer(Modifier.height(12.dp))
            }

            when {
                ui.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorRetry(message = ui.error!!, onRetry = viewModel::refresh)
                }

                ui.programs.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    title = "Belum ada program ajar",
                    subtitle = "Program ajar yang Anda buat untuk anak binaan akan muncul di sini.",
                    action = {
                        Button(
                            onClick = onCreateProgram,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandTeal,
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Buat Program Ajar", fontWeight = FontWeight.Bold)
                        }
                    },
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    if (ui.recurringPrograms.isNotEmpty()) {
                        item { GroupLabel("Program Berulang") }
                        items(ui.recurringPrograms, key = { it.id }) { task ->
                            ProgramCard(
                                task = task,
                                childName = ui.childNameOf(task),
                                onEdit = { onEditProgram(task.id) },
                                onCancel = { confirmCancel = task },
                                replaced = ui.programs.any { it.previousVersionId == task.id },
                                today = ui.today,
                                isStopping = ui.stoppingId == task.id,
                                isSavingTemplate = ui.savingTemplateFor == task.id,
                                onStop = { confirmStop = task },
                                onSaveAsTemplate = {
                                    // Prefilled from the judul the dialog already has in hand, so
                                    // making a template is a tap rather than a typing exercise.
                                    templateName = task.judul
                                    nameTemplateFor = task
                                },
                            )
                        }
                    }

                    if (ui.oneOffTasks.isNotEmpty()) {
                        item { GroupLabel("Tugas Sekali Jalan") }
                        items(ui.oneOffTasks, key = { it.id }) { task ->
                            ProgramCard(
                                task = task,
                                childName = ui.childNameOf(task),
                                onEdit = { onEditProgram(task.id) },
                                onCancel = { confirmCancel = task },
                                replaced = ui.programs.any { it.previousVersionId == task.id },
                                today = ui.today,
                                isStopping = ui.stoppingId == task.id,
                                isSavingTemplate = ui.savingTemplateFor == task.id,
                                onStop = null,
                                onSaveAsTemplate = {
                                    templateName = task.judul
                                    nameTemplateFor = task
                                },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}


@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MutedText,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * One program.
 *
 * Tapping expands it in place rather than opening a detail screen — the volunteer only needs to
 * re-read what they wrote, and a new destination would mean a new route, ViewModel and RLS path for
 * data already loaded here.
 *
 * A stopped or finished program dims and flattens, the same treatment a completed task gets on the
 * parent's home, so the eye lands on what is still running.
 */
@Composable
private fun ProgramCard(
    task: Task,
    childName: String,
    today: LocalDate,
    isStopping: Boolean,
    isSavingTemplate: Boolean,
    onStop: (() -> Unit)?,
    onSaveAsTemplate: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    replaced: Boolean,
) {
    val rule = task.toRecurrenceRule()
    val finished = rule.isFinished(today)
    val inactive = task.isStopped || finished || task.cancelledAt != null || task.status == "done" || replaced
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (inactive) 0.6f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (inactive) 0.dp else 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(IconBoxBg, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        tint = BrandTeal,
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(task.judul, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(childName, style = MaterialTheme.typography.bodyMedium, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                when {
                    task.cancelledAt != null -> StatusChip("Dibatalkan", StatusRedBg, StatusRedText)
                    replaced -> StatusChip("Versi sebelumnya", StatusRedBg, StatusRedText)
                    task.isStopped -> StatusChip("Dihentikan", StatusRedBg, StatusRedText)
                    finished || task.status == "done" -> StatusChip("Selesai", StatusTealBg, StatusTealText)
                    else -> StatusChip("Berjalan", StatusGreenBg, StatusGreenText)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = scheduleLine(task, rule.describe()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Tutup rincian" else "Lihat rincian",
                    tint = MutedText,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Text(
                        "Prioritas ${priorityLabel(task.priorityLevel)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandTeal,
                    )
                    if (!task.instruksi.isNullOrBlank()) {
                        Text(
                            "Instruksi",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MutedText,
                        )
                        RichTextDisplay(
                            task.instruksi,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Offered on every program, stopped or finished included: a template is a recipe,
                // and a programme being over says nothing about whether the exercise is worth
                // reusing. Costs no upload -- the media is copied server-side.
                TextButton(onClick = onSaveAsTemplate, enabled = !isSavingTemplate) {
                    if (isSavingTemplate) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Jadikan Template", color = BrandTeal)
                    }
                }

                if (!inactive) {
                    TextButton(onClick = onEdit, enabled = !isStopping) { Text("Edit") }
                    if (!task.isRecurring) TextButton(onClick = onCancel, enabled = !isStopping) { Text("Batalkan tugas") }
                }
                // Only a live recurring program can be stopped.
                if (onStop != null && !inactive) {
                    TextButton(onClick = onStop, enabled = !isStopping) {
                        if (isStopping) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Hentikan Program", color = StatusRedText)
                        }
                    }
                }
            }
        }
    }
}

/** "Setiap Senin • mulai 01 Sep 2026 s/d 30 Nov 2026" */
private fun scheduleLine(task: Task, description: String): String = buildString {
    append(description)
    task.startsOn?.let { start ->
        runCatching { LocalDate.parse(start) }.getOrNull()?.let {
            append(" • mulai ${it.format(ShortDate)}")
        }
    }
    task.endsOn?.let { end ->
        runCatching { LocalDate.parse(end) }.getOrNull()?.let {
            append(" s/d ${it.format(ShortDate)}")
        }
    }
}

/**
 * Priority as the volunteer chose it.
 *
 * It is collected on the create form and then never shown again anywhere in the app — this is the
 * one place the person who set it can check what they set.
 */
private fun priorityLabel(dbValue: String): String =
    TaskPriority.entries.firstOrNull { it.dbValue == dbValue }?.label ?: dbValue
