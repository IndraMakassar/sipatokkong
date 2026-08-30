package com.antoniusindra.sipatokkong.ui.volunteer

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.core.media.CameraUriManager
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.StatusRedBg
import com.antoniusindra.sipatokkong.ui.theme.StatusRedText
import com.antoniusindra.sipatokkong.ui.components.MediaPickerBox

/**
 * "Catat Kunjungan" — the volunteer's attendance form.
 *
 * A form rather than a one-tap check-in: a visit is only useful to the foundation alongside what
 * happened during it, so the child, the date, a session note and a proof photo are captured in one
 * pass. The photo is required and is compressed before upload — the `presensi` bucket caps objects
 * at 6 MB and accepts images only.
 *
 * Form building blocks (`SectionLabel`, `ChildDropdown`, `DeadlineField`, `MediaPickerBox`) are
 * imported from `CreateTaskScreen` rather than copied; they were made `internal` for this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePresensiScreen(
    onBack: () -> Unit,
    viewModel: CreatePresensiViewModel = viewModel(factory = CreatePresensiViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            Toast.makeText(context, "Kunjungan berhasil dicatat!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    var tempUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempUri?.let { viewModel.onFotoPicked(it) }
        }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Catat Kunjungan", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Pilih Anak ----
            SectionLabel("Pilih Anak*")
            ChildDropdown(
                children = ui.children,
                isLoading = ui.isLoadingChildren,
                selectedName = ui.selectedChildName,
                onSelect = viewModel::onAnakSelected,
            )
            if (!ui.isLoadingChildren && ui.children.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Daftar anak kosong. Pastikan admin sudah menugaskan anak kepada Anda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::retryLoadChildren) { Text("Muat Ulang") }
                }
            }

            // ---- Tanggal ----
            SectionLabel("Tanggal Kunjungan*")
            DeadlineField(
                deadline = ui.tanggal,
                label = "Tanggal Kunjungan",
            )

            // ---- Catatan ----
            SectionLabel("Catatan Sesi")
            OutlinedTextField(
                value = ui.catatan,
                onValueChange = viewModel::onCatatanChange,
                label = { Text("Apa yang dikerjakan hari ini?") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---- Foto bukti ----
            SectionLabel("Foto Bukti Kunjungan*")
            MediaPickerBox(
                hasMedia = ui.fotoUri != null,
                previewUrl = ui.fotoUri,
                // Always an image: the bucket rejects video, and everything is re-encoded to JPEG.
                mediaKind = MediaKind.IMAGE.takeIf { ui.fotoUri != null },
                mediaName = null,
                onPick = {
                    val uri = CameraUriManager.createTempUri(context)
                    tempUri = uri
                    cameraLauncher.launch(uri)
                },
                emptyPrompt = "Ketuk untuk ambil foto",
                formatHint = "Foto dikecilkan otomatis sebelum diunggah",
            )

            ui.error?.let { message ->
                Surface(
                    color = StatusRedBg,
                    contentColor = StatusRedText,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }

            Button(
                onClick = viewModel::submit,
                enabled = ui.canSubmit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandTeal,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (ui.isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Menyimpan...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Simpan Kunjungan", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
