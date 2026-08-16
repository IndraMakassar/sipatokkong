package com.antoniusindra.sipatokkong.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.IconBoxBg
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.ProgressGradBottom
import com.antoniusindra.sipatokkong.ui.theme.ProgressGradTop

/**
 * The signed-in user's own profile: avatar, name, contact details, edit, and sign out.
 *
 * Role-neutral apart from the two parameters below — it was written for parents but the only
 * parent-specific parts were a hardcoded label and one field, so volunteers reuse it rather than
 * getting a second near-identical screen.
 *
 * Has no Scaffold on purpose: it is always embedded in a tab of a role's home screen.
 *
 * @param roleLabel shown under the name.
 * @param showFamilyStatus `familyStatus` is only collected for parents; for anyone else the row
 *   would always read "-", so it is left out entirely.
 */
@Composable
fun ProfileScreen(
    profile: Profile,
    onLogout: () -> Unit,
    roleLabel: String = "Orang Tua",
    showFamilyStatus: Boolean = true,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(profile) {
        viewModel.initProfile(profile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Profile Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val gradient = Brush.linearGradient(listOf(ProgressGradTop, ProgressGradBottom))
                        onDrawBehind {
                            drawRect(gradient)
                            drawCircle(
                                color = Color.White.copy(alpha = 0.10f),
                                radius = size.minDimension * 0.45f,
                                center = Offset(size.width * 0.95f, size.height * 0.2f),
                            )
                        }
                    }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.isEditing) {
                    OutlinedTextField(
                        value = uiState.editedName,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Nama Lengkap", color = Color.White.copy(alpha = 0.7f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                } else {
                    Text(
                        text = uiState.profile?.fullName ?: profile.fullName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = roleLabel,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // Contact Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Informasi Kontak",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!uiState.isEditing) {
                        TextButton(
                            onClick = { viewModel.startEditing() },
                            colors = ButtonDefaults.textButtonColors(contentColor = BrandTeal)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ubah", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

                if (uiState.isEditing) {
                    ProfileEditField(
                        label = "Nomor Telepon",
                        value = uiState.editedPhone,
                        onValueChange = viewModel::onPhoneChange,
                        icon = Icons.Filled.Phone
                    )
                    ProfileEditField(
                        label = "Alamat",
                        value = uiState.editedAddress,
                        onValueChange = viewModel::onAddressChange,
                        icon = Icons.Filled.LocationOn,
                        singleLine = false
                    )
                } else {
                    ProfileInfoRow(
                        icon = Icons.Filled.Phone,
                        label = "Telepon",
                        value = uiState.profile?.phone ?: profile.phone ?: "-"
                    )
                    ProfileInfoRow(
                        icon = Icons.Filled.LocationOn,
                        label = "Alamat",
                        value = uiState.profile?.address ?: profile.address ?: "-"
                    )
                    if (showFamilyStatus) {
                        ProfileInfoRow(
                            icon = Icons.Filled.Person,
                            label = "Status Keluarga",
                            value = uiState.profile?.familyStatus ?: profile.familyStatus ?: "-"
                        )
                    }
                }
            }
        }

        // Actions
        if (uiState.isEditing) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.cancelEditing() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !uiState.isLoading
                ) {
                    Text("Batal")
                }
                Button(
                    onClick = { viewModel.saveProfile() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    enabled = !uiState.isLoading
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Keluar dari Akun", fontWeight = FontWeight.Bold)
            }
        }

        if (uiState.error != null) {
            Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ProfileInfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(IconBoxBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = BrandTeal, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 12.sp, color = MutedText, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
        }
    }
}

@Composable
private fun ProfileEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = { Icon(icon, contentDescription = null, tint = BrandTeal) },
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(12.dp)
    )
}
