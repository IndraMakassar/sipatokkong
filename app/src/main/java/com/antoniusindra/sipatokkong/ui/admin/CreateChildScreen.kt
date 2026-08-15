package com.antoniusindra.sipatokkong.ui.admin

import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

// SectionLabel / OptionDropdown / DateField live in AdminFormComponents.kt,
// shared with the admin edit screens.

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
