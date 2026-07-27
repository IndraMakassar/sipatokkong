package com.antoniusindra.sipatokkong.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.data.model.Feedback

private val MutedText = Color(0xFF6B7280)

private data class StatusStyle(val label: String, val bg: Color, val text: Color)

private fun feedbackStatusStyle(status: String): StatusStyle = when (status) {
    "answered" -> StatusStyle("Dijawab", Color(0xFFBFE3CE), Color(0xFF2E6E4E))
    "in_process" -> StatusStyle("Diproses", Color(0xFFD6E4F5), Color(0xFF3A6FB0))
    else -> StatusStyle("Menunggu", Color(0xFFF8DDB8), Color(0xFF8A5A1E)) // open
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackListScreen(
    title: String,
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onCompose: (() -> Unit)? = null,
    viewModel: FeedbackListViewModel = viewModel(factory = FeedbackListViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        floatingActionButton = {
            if (onCompose != null) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Tulis Catatan") },
                )
            }
        }
    ) { innerPadding ->
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }

            ui.error != null -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }

            ui.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                Text("Belum ada catatan/feedback.", color = MutedText)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.items) { fb -> FeedbackRow(fb, onClick = { onOpenThread(fb.id) }) }
            }
        }
    }
}

@Composable
private fun FeedbackRow(fb: Feedback, onClick: () -> Unit) {
    val style = feedbackStatusStyle(fb.status)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fb.isi,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(4.dp))
                Box(
                    modifier = Modifier.background(style.bg, CircleShape).padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(style.label, color = style.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!fb.catatanPerilaku.isNullOrBlank()) {
                Text("Perilaku: ${fb.catatanPerilaku}", style = MaterialTheme.typography.bodySmall, color = MutedText, maxLines = 1)
            }
            fb.createdAt?.let {
                Text(it.take(10), style = MaterialTheme.typography.labelMedium, color = MutedText)
            }
        }
    }
}
