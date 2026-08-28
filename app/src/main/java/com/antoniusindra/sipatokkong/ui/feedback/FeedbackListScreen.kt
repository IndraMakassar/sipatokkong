package com.antoniusindra.sipatokkong.ui.feedback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.core.util.formatIsoDate
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackMood
import com.antoniusindra.sipatokkong.ui.components.EmptyState
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenBg
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenText
import com.antoniusindra.sipatokkong.ui.theme.StatusOrangeBg
import com.antoniusindra.sipatokkong.ui.theme.StatusOrangeText
import com.antoniusindra.sipatokkong.ui.theme.StatusTealBg
import com.antoniusindra.sipatokkong.ui.theme.StatusTealText


private data class StatusStyle(val label: String, val bg: Color, val text: Color)

// Driven by FeedbackTriage.stateOf, the same function behind the tabs. Before, the chip read only
// `feedback.status` while the tabs read `needsAttention`, so a positive unanswered note sat under
// "Sudah Beres" wearing an orange "Menunggu" -- the row arguing with the tab above it.
//
// (The `feedback_status` enum also declares 'in_process', but nothing writes it, so it has no chip.)
private fun feedbackChipStyle(state: FeedbackState): StatusStyle = when (state) {
    FeedbackState.DIJAWAB -> StatusStyle("Dijawab", StatusGreenBg, StatusGreenText)
    FeedbackState.PERLU_DIBALAS -> StatusStyle("Perlu Dibalas", StatusOrangeBg, StatusOrangeText)
    FeedbackState.KABAR_BAIK -> StatusStyle("Kabar Baik", StatusTealBg, StatusTealText)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackListScreen(
    title: String,
    onOpenThread: (String) -> Unit,
    onCompose: (() -> Unit)? = null,
    useScaffold: Boolean = true,
    /** Parent-voiced by default; a volunteer sees this list under a different title. */
    emptyText: String = "Belum ada catatan/feedback.",
    /**
     * Splits the list into "Perlu Ditanggapi" / "Sudah Beres". Off for parents, who only ever see
     * their own child's notes and so have nothing to triage.
     */
    enableTriage: Boolean = false,
    viewModel: FeedbackListViewModel = viewModel(factory = FeedbackListViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedBucket by rememberSaveable { mutableIntStateOf(0) }
    // Read once per composition rather than per row, so every row agrees on what "Hari Ini" means.
    val today = remember { LocalDate.now() }

    // Re-runs when this screen re-enters composition after popping back from a thread, so a reply
    // sent there is reflected here. Without it the list only ever loaded once per process.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val content = @Composable { padding: androidx.compose.foundation.layout.PaddingValues ->
        when {
            ui.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            ui.error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                ErrorRetry(message = ui.error!!, onRetry = viewModel::refresh)
            }

            ui.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = "Belum ada feedback",
                    subtitle = emptyText,
                )
            }

            !enableTriage -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.items) { fb ->
                    FeedbackRow(fb, onClick = { onOpenThread(fb.id) }, today = today)
                }
            }

            else -> {
                // Recomputed from the list already in hand -- no second query. See FeedbackTriage.
                val urgent = remember(ui.items) { FeedbackTriage.needsAttention(ui.items) }
                val calm = remember(ui.items) { FeedbackTriage.settled(ui.items) }
                val shown = if (selectedBucket == 0) urgent else calm

                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedBucket,
                        containerColor = AppBackground,
                        contentColor = BrandTeal,
                    ) {
                        Tab(
                            selected = selectedBucket == 0,
                            onClick = { selectedBucket = 0 },
                            text = { Text("Perlu Ditanggapi (${urgent.size})") },
                        )
                        Tab(
                            selected = selectedBucket == 1,
                            onClick = { selectedBucket = 1 },
                            text = { Text("Sudah Beres (${calm.size})") },
                        )
                    }

                    if (shown.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            // The `ui.items.isEmpty()` branch above already caught "no notes at
                            // all", so reaching here means the notes are simply all in the other
                            // tab -- which is worth saying, and is why neither subtitle is the
                            // generic `emptyText` any more.
                            EmptyState(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                title = if (selectedBucket == 0) {
                                    "Tidak ada yang mendesak"
                                } else {
                                    "Belum ada yang beres"
                                },
                                subtitle = if (selectedBucket == 0) {
                                    "Semua catatan yang perlu ditanggapi sudah dijawab."
                                } else {
                                    "Semua catatan masih menunggu tanggapan Anda."
                                },
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(shown) { fb ->
                                FeedbackRow(fb, onClick = { onOpenThread(fb.id) }, today = today)
                            }
                        }
                    }
                }
            }
        }
    }

    if (useScaffold) {
        Scaffold(
            containerColor = AppBackground,
            topBar = {
                TopAppBar(
                    title = { Text(title, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppBackground,
                    )
                )
            },
            floatingActionButton = {
                if (onCompose != null) {
                    ExtendedFloatingActionButton(
                        onClick = onCompose,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Tambah Catatan") },
                        containerColor = BrandTeal,
                        contentColor = Color.White
                    )
                }
            }
        ) { innerPadding ->
            content(innerPadding)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            content(androidx.compose.foundation.layout.PaddingValues(0.dp))
            if (onCompose != null) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Tambah Catatan") },
                    containerColor = BrandTeal,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FeedbackRow(fb: Feedback, onClick: () -> Unit, today: LocalDate) {
    val style = feedbackChipStyle(FeedbackTriage.stateOf(fb))
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mood Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFF5F5F5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(FeedbackMood.emojiOf(fb.mood), fontSize = 24.sp)
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // Not `fb.isi`: that is auto-filled with a constant when the parent types
                        // nothing, which made every chip-only note read the same. See FeedbackSummary.
                        FeedbackSummary.headlineOf(fb),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.padding(4.dp))
                    Box(
                        modifier = Modifier.background(style.bg, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(style.label, color = style.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Null when the chips are already the headline -- printing them twice on one
                // row is worse than printing them once.
                FeedbackSummary.subtitleOf(fb)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                formatIsoDate(fb.createdAt, today)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MutedText)
                }
            }
        }
    }
}
