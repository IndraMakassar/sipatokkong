package com.antoniusindra.sipatokkong.ui.admin

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.data.model.ProfileOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChildScreen(
    onBack: () -> Unit,
    viewModel: CreateChildViewModel = viewModel(factory = CreateChildViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            Toast.makeText(context, "Data anak berhasil disimpan!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tambah Data Anak") },
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
            // ---- Orang Tua (wajib) ----
            SectionLabel("Orang Tua*")
            OptionDropdown(
                options = ui.parents,
                isLoading = ui.isLoadingOptions,
                selectedName = ui.selectedParentName,
                placeholder = "Pilih orang tua",
                emptyText = "Belum ada akun orang tua",
                label = "Orang Tua",
                onSelect = viewModel::onOrangtuaSelected,
            )

            // ---- Nama anak (wajib) ----
            OutlinedTextField(
                value = ui.nama,
                onValueChange = viewModel::onNamaChange,
                label = { Text("Nama Anak*") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Tanggal lahir (opsional) ----
            SectionLabel("Tanggal Lahir (opsional)")
            DateField(date = ui.tanggalLahir, onPick = viewModel::onTanggalLahirChange)

            // ---- Catatan kondisi (opsional) ----
            OutlinedTextField(
                value = ui.catatan,
                onValueChange = viewModel::onCatatanChange,
                label = { Text("Catatan Kondisi (opsional)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Tugaskan relawan (opsional) ----
            SectionLabel("Tugaskan Relawan (opsional)")
            OptionDropdown(
                options = ui.relawans,
                isLoading = ui.isLoadingOptions,
                selectedName = ui.selectedRelawanName,
                placeholder = "Belum ditugaskan",
                emptyText = "Belum ada akun relawan",
                label = "Relawan",
                allowClear = true,
                onSelect = { viewModel.onRelawanSelected(it) },
                onClear = { viewModel.onRelawanSelected(null) },
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
                    Text("Simpan Data Anak")
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
private fun OptionDropdown(
    options: List<ProfileOption>,
    isLoading: Boolean,
    selectedName: String?,
    placeholder: String,
    emptyText: String,
    label: String,
    allowClear: Boolean = false,
    onSelect: (String) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = when {
        isLoading -> "Memuat..."
        options.isEmpty() -> emptyText
        selectedName != null -> selectedName
        else -> placeholder
    }

    ExposedDropdownMenuBox(
        expanded = expanded && options.isNotEmpty(),
        onExpandedChange = { if (options.isNotEmpty()) expanded = it },
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowClear && onClear != null) {
                DropdownMenuItem(
                    text = { Text("— Tidak ditugaskan —") },
                    onClick = { onClear(); expanded = false },
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.fullName) },
                    onClick = { onSelect(option.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, onPick: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val label = date?.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) ?: "Pilih tanggal"

    OutlinedTextField(
        value = label,
        onValueChange = {},
        readOnly = true,
        enabled = false,
        label = { Text("Tanggal Lahir") },
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
