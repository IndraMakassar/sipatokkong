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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.data.model.UserRole

/**
 * Edit form for one account. Role-specific fields mirror [CreateAccountScreen].
 * There is no email/password field here — see [EditAccountViewModel] for why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAccountScreen(
    profileId: String,
    onBack: () -> Unit,
    viewModel: EditAccountViewModel = viewModel(
        key = "edit_account_$profileId",
        factory = EditAccountViewModel.factory(profileId),
    ),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            Toast.makeText(context, "Perubahan tersimpan!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    val role = ui.profile?.role
    val roleName = when (role) {
        UserRole.ORANGTUA -> "Orangtua"
        UserRole.RELAWAN -> "Relawan"
        else -> "Akun"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit $roleName") },
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

            ui.profile == null -> CenteredBox {
                Text(
                    ui.error ?: "Akun tidak ditemukan.",
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
                OutlinedTextField(
                    value = ui.fullName,
                    onValueChange = viewModel::onFullNameChange,
                    label = { Text("Nama Lengkap*") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = ui.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text("Nomor Telepon") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (role == UserRole.ORANGTUA) {
                    OutlinedTextField(
                        value = ui.familyStatus,
                        onValueChange = viewModel::onFamilyStatusChange,
                        label = { Text("Status Keluarga (Ayah/Ibu)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.address,
                        onValueChange = viewModel::onAddressChange,
                        label = { Text("Alamat") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = ui.quotaNotes,
                        onValueChange = viewModel::onQuotaNotesChange,
                        label = { Text("Catatan Kuota / Internet") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (role == UserRole.RELAWAN) {
                    OutlinedTextField(
                        value = ui.specialization,
                        onValueChange = viewModel::onSpecializationChange,
                        label = { Text("Spesialisasi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionLabel("Status Akun")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (ui.isActive) "Akun aktif" else "Akun nonaktif",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "Akun nonaktif tidak bisa dipilih saat menambah data anak.",
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
