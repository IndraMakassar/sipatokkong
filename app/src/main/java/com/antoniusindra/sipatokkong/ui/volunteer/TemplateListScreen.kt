package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.data.model.MediaKind
import com.antoniusindra.sipatokkong.data.model.TaskTemplate
import com.antoniusindra.sipatokkong.ui.components.ActionErrorBanner
import com.antoniusindra.sipatokkong.ui.components.EmptyState
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.IconBoxBg
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.StatusTealBg
import com.antoniusindra.sipatokkong.ui.theme.StatusTealText

/**
 * Program-ajar templates, shared across every volunteer.
 *
 * Picking one opens the ordinary create-program form with the exercise already filled in. It does
 * not create anything by itself: the child and the dates are still the volunteer's to choose, and
 * every prefilled field stays editable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    onBack: () -> Unit,
    onUseTemplate: (String) -> Unit,
    onCreateProgram: () -> Unit,
    viewModel: TemplateListViewModel = viewModel(factory = TemplateListViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<TaskTemplate?>(null) }

    // Driven from here rather than init{}, so returning from the form picks up a template that was
    // just saved. Same reasoning as ProgramListScreen and FeedbackListScreen.
    LaunchedEffect(Unit) { viewModel.refresh() }

    confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Hapus template?") },
            text = {
                Text(
                    "\"${target.nama}\" akan hilang dari daftar semua relawan. " +
                        "Program ajar yang sudah dibuat dari template ini tidak terpengaruh."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    confirmDelete = null
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Batal") }
            },
        )
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Template Program Ajar", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp)
        ) {
            // A failed delete is reported here; the list stays exactly where it was.
            ui.actionError?.let { message ->
                ActionErrorBanner(message = message, onDismiss = viewModel::dismissActionError)
                Spacer(Modifier.height(12.dp))
            }

            when {
                ui.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorRetry(message = ui.error!!, onRetry = viewModel::refresh)
                }

                ui.templates.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        title = "Belum ada template",
                        subtitle = "Centang \"Simpan juga sebagai template\" saat membuat program " +
                            "ajar, atau pilih \"Jadikan Template\" pada program yang sudah ada.",
                        // This is the only text in the app explaining how a template is made, and
                        // without a button it was a dead end: read the sentence, then back out and
                        // find the form yourself. ProgramListScreen's empty state already does this.
                        action = {
                            Button(
                                onClick = onCreateProgram,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandTeal,
                                    contentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Buat Program Ajar", fontWeight = FontWeight.Bold)
                            }
                        },
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ui.templates, key = { it.id }) { template ->
                        TemplateRow(
                            template = template,
                            isMine = ui.isMine(template),
                            isDeleting = ui.deletingId == template.id,
                            onUse = { onUseTemplate(template.id) },
                            onDelete = { confirmDelete = template },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemplateRow(
    template: TaskTemplate,
    isMine: Boolean,
    isDeleting: Boolean,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onUse,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(IconBoxBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                // The media is the point of a template, so say which kind it carries.
                Icon(
                    if (template.mediaKind == MediaKind.VIDEO.dbValue) Icons.Filled.Movie
                    else Icons.Filled.Photo,
                    contentDescription = null,
                    tint = BrandTeal,
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(template.nama, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    template.judul,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    maxLines = 1,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    // Chips that wrap sit flush at 0dp without this -- a bug already shipped and
                    // fixed once in this repo. See the Change Log in project_status.md.
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TemplateChip(template.describeRecurrence())
                    if (isMine) TemplateChip("Template saya")
                }
                if (template.alatBantuLabels.isNotEmpty()) {
                    Text(
                        template.alatBantuLabels.joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        maxLines = 1,
                    )
                }
            }

            // Delete is offered only to the author, matching the `template_delete` policy -- a
            // button that always fails is worse than no button.
            if (isMine) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Hapus template", tint = MutedText)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateChip(label: String) {
    Box(
        modifier = Modifier
            .background(StatusTealBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = StatusTealText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
