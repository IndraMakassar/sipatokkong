package com.antoniusindra.sipatokkong.ui.admin

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole

/**
 * Admin list of accounts for a single role, with search. Tapping a row opens the edit screen.
 * One screen serves both orangtua and relawan, mirroring how [CreateAccountScreen] is parameterized.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAccountsScreen(
    role: UserRole,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: ManageAccountsViewModel = viewModel(
        key = "manage_accounts_${role.name}",
        factory = ManageAccountsViewModel.factory(role),
    ),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    val roleName = when (role) {
        UserRole.ORANGTUA -> "Orangtua"
        UserRole.RELAWAN -> "Relawan"
        else -> "User"
    }

    // Re-runs when this screen re-enters composition after popping back from the edit screen,
    // so an edited name shows up immediately.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola Akun $roleName") },
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
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = ui.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Cari nama atau telepon") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            when {
                ui.isLoading -> CenteredBox { CircularProgressIndicator() }

                ui.error != null -> CenteredBox {
                    Text(ui.error!!, color = MaterialTheme.colorScheme.error)
                }

                ui.visibleProfiles.isEmpty() -> CenteredBox {
                    Text(
                        if (ui.profiles.isEmpty()) {
                            "Belum ada akun $roleName."
                        } else {
                            "Tidak ada hasil untuk \"${ui.query}\"."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(ui.visibleProfiles, key = { it.id }) { profile ->
                        AccountRow(profile = profile, onClick = { onEdit(profile.id) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountRow(profile: Profile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = profile.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!profile.isActive) {
                    Spacer(Modifier.width(8.dp))
                    InactiveBadge()
                }
            }
            Text(
                text = profile.phone?.takeIf { it.isNotBlank() } ?: "Tanpa nomor telepon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun InactiveBadge() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "Nonaktif",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
internal fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}
