package com.antoniusindra.sipatokkong.ui.admin

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Edit form for one child, including which volunteers are assigned to them.
 * Assignment changes are staged and applied together with the rest on save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditChildScreen(
    anakId: String,
    onBack: () -> Unit,
    viewModel: EditChildViewModel = viewModel(
        key = "edit_child_$anakId",
        factory = EditChildViewModel.factory(anakId),
    ),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            Toast.makeText(context, "Data anak berhasil diperbarui!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Data Anak") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            ui.isLoading -> CenteredBox { CircularProgressIndicator() }

            ui.child == null -> CenteredBox {
                Text(
                    ui.error ?: "Data anak tidak ditemukan.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---- Orang tua ----
                SectionLabel("Orang Tua*")
                OptionDropdown(
                    options = ui.parentOptions,
                    isLoading = false,
                    selectedName = ui.selectedParentName,
                    placeholder = "Pilih orang tua",
                    emptyText = "Belum ada akun orang tua aktif",
                    label = "Orang Tua",
                    onSelect = viewModel::onOrangtuaSelected,
                )

                // ---- Nama ----
                OutlinedTextField(
                    value = ui.nama,
                    onValueChange = viewModel::onNamaChange,
                    label = { Text("Nama Anak*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- Tanggal lahir ----
                SectionLabel("Tanggal Lahir (opsional)")
                DateField(date = ui.tanggalLahir, onPick = viewModel::onTanggalLahirChange)

                // ---- Catatan ----
                OutlinedTextField(
                    value = ui.catatan,
                    onValueChange = viewModel::onCatatanChange,
                    label = { Text("Catatan Kondisi (opsional)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                // ---- Relawan yang ditugaskan ----
                SectionLabel("Relawan Ditugaskan")
                if (ui.assignedIds.isEmpty()) {
                    Text(
                        "Belum ada relawan yang ditugaskan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.assignedIds.forEach { id ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                                ) {
                                    Text(
                                        text = ui.nameOf(id),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = { viewModel.removeRelawan(id) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Lepas penugasan ${ui.nameOf(id)}",
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OptionDropdown(
                    options = ui.assignableRelawan,
                    isLoading = false,
                    selectedName = null,
                    placeholder = "Tambah relawan",
                    emptyText = "Tidak ada relawan lain yang tersedia",
                    label = "Tambah Relawan",
                    onSelect = viewModel::addRelawan,
                )

                HorizontalDivider()

                // ---- Status ----
                SectionLabel("Status Data Anak")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (ui.isActive) "Data aktif" else "Data nonaktif",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Data nonaktif disembunyikan dari daftar relawan dan orang tua.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ui.isActive, onCheckedChange = viewModel::onActiveChange)
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
                    onClick = viewModel::save,
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
                        Text("Simpan Perubahan")
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
