package com.antoniusindra.sipatokkong.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryRoute(onBack: () -> Unit, onOpenSubmission: (String) -> Unit,
    viewModel: TaskPagesViewModel = viewModel(factory = TaskPagesViewModel.factory())) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }
    picking?.let { field ->
        val current = if (field == "from") ui.from else ui.to
        val picker = rememberDatePickerState(initialSelectedDateMillis = current?.let {
            LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        })
        DatePickerDialog(onDismissRequest = { picking = null }, confirmButton = {
            TextButton(onClick = {
                val selected = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                viewModel.filter(if (field == "from") selected else ui.from, if (field == "to") selected else ui.to)
                picking = null
            }) { Text("Terapkan") }
        }, dismissButton = { TextButton(onClick = { picking = null }) { Text("Batal") } }) { DatePicker(picker) }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Riwayat Tugas") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Kembali") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Tanggal tugas", style = MaterialTheme.typography.labelLarge) }
            item { Row {
                TextButton(onClick = { picking = "from" }) { Text(ui.from ?: "Dari awal") }
                TextButton(onClick = { picking = "to" }) { Text(ui.to ?: "Sampai terbaru") }
                TextButton(onClick = { viewModel.filter(null, null) }) { Text("Semua") }
            } }
            items(ui.rows, key = { it.key }) { row ->
                Text(row.occurrenceDate)
                TaskCard(row.occurrence(true), LocalDate.now()) { onOpenSubmission(row.id) }
            }
            item { TaskPageFooter(ui, viewModel::retry, viewModel::more, "Belum ada setoran dalam rentang ini.") }
        }
    }
}

@Composable
fun OldOverdueTasks(onOpenTask: (String, LocalDate) -> Unit,
    viewModel: TaskPagesViewModel = viewModel(key = "old-overdue", factory = TaskPagesViewModel.factory(true))) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tunggakan lebih lama", style = MaterialTheme.typography.titleMedium)
        ui.rows.forEach { row ->
            Text(row.occurrenceDate)
            TaskCard(row.occurrence(false), LocalDate.now()) { onOpenTask(row.task.id, LocalDate.parse(row.occurrenceDate)) }
        }
        TaskPageFooter(ui, viewModel::retry, viewModel::more, "Tidak ada tunggakan lebih dari 30 hari.")
    }
}

@Composable
private fun TaskPageFooter(ui: TaskPageState, retry: () -> Unit, more: () -> Unit, empty: String) {
    when {
        ui.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
        ui.error != null -> ErrorRetry(ui.error, retry)
        ui.hasMore -> TextButton(onClick = more) { Text("Muat lebih banyak") }
        ui.loaded && ui.rows.isEmpty() -> Text(empty)
    }
}
