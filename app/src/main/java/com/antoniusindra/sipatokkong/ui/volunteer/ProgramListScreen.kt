package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.data.model.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))

/** Volunteer's own programs, with the option to stop a recurring one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramListScreen(
    onBack: () -> Unit,
    viewModel: ProgramListViewModel = viewModel(factory = ProgramListViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(Unit) { viewModel.refresh() }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Program Ajar Saya") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            when {
                ui.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                }

                ui.programs.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        "Belum ada program ajar yang dibuat.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { Spacer(Modifier.height(8.dp)) }

                    if (ui.recurringPrograms.isNotEmpty()) {
                        item { GroupLabel("Program Berulang") }
                        items(ui.recurringPrograms, key = { it.id }) { task ->
                            ProgramCard(
                                task = task,
                                childName = ui.childNameOf(task),
                                today = ui.today,
                                isStopping = ui.stoppingId == task.id,
                                onStop = { confirmStop = task },
                            )
                        }
                    }

                    if (ui.oneOffTasks.isNotEmpty()) {
                        item { GroupLabel("Tugas Sekali Jalan") }
                        items(ui.oneOffTasks, key = { it.id }) { task ->
                            ProgramCard(
                                task = task,
                                childName = ui.childNameOf(task),
                                today = ui.today,
                                isStopping = false,
                                onStop = null,
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ProgramCard(
    task: Task,
    childName: String,
    today: LocalDate,
    isStopping: Boolean,
    onStop: (() -> Unit)?,
) {
    val rule = task.toRecurrenceRule()
    val finished = rule.isFinished(today)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.judul,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (task.isStopped) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill("Dihentikan", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                } else if (finished) {
                    Spacer(Modifier.width(8.dp))
                    StatusPill("Selesai", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Text(
                text = "Anak: $childName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = buildString {
                    append(rule.describe())
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
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            // Only a live recurring program can be stopped.
            if (onStop != null && !task.isStopped && !finished) {
                TextButton(
                    onClick = onStop,
                    enabled = !isStopping,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (isStopping) {
                        CircularProgressIndicator(Modifier.height(18.dp).width(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Hentikan Program", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
