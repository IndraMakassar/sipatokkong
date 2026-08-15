package com.antoniusindra.sipatokkong.ui.home

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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniusindra.sipatokkong.data.model.Profile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    profile: Profile,
    onCreateOrangtua: () -> Unit,
    onCreateRelawan: () -> Unit,
    onCreateAnak: () -> Unit,
    onManageOrangtua: () -> Unit,
    onManageRelawan: () -> Unit,
    onManageAnak: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sipatokkong Admin") },
                actions = {
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        Spacer(Modifier.size(4.dp))
                        Text("Keluar")
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Halo, ${profile.fullName}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Text(
                text = "Selamat datang di panel admin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(32.dp))

            AdminActionCard(
                title = "Tambah Akun Orangtua",
                description = "Daftarkan orangtua baru ke sistem.",
                icon = Icons.Default.PersonAdd,
                onClick = onCreateOrangtua
            )

            Spacer(modifier = Modifier.height(16.dp))

            AdminActionCard(
                title = "Tambah Akun Relawan",
                description = "Daftarkan relawan baru ke sistem.",
                icon = Icons.Default.GroupAdd,
                onClick = onCreateRelawan
            )

            Spacer(modifier = Modifier.height(16.dp))

            AdminActionCard(
                title = "Tambah Data Anak",
                description = "Daftarkan anak, tautkan ke orang tua & tugaskan relawan.",
                icon = Icons.Default.ChildCare,
                onClick = onCreateAnak
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Kelola Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(16.dp))

            AdminActionCard(
                title = "Kelola Akun Orangtua",
                description = "Lihat, ubah, atau nonaktifkan akun orangtua.",
                icon = Icons.Default.ManageAccounts,
                onClick = onManageOrangtua
            )

            Spacer(modifier = Modifier.height(16.dp))

            AdminActionCard(
                title = "Kelola Akun Relawan",
                description = "Lihat, ubah, atau nonaktifkan akun relawan.",
                icon = Icons.Default.Groups,
                onClick = onManageRelawan
            )

            Spacer(modifier = Modifier.height(16.dp))

            AdminActionCard(
                title = "Kelola Data Anak",
                description = "Ubah data anak, pindah orang tua & atur relawan.",
                icon = Icons.Default.Edit,
                onClick = onManageAnak
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
