package com.antoniusindra.sipatokkong.ui.feedback

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antoniusindra.sipatokkong.core.util.pollWhileResumed
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.core.push.PushPayload
import com.antoniusindra.sipatokkong.core.util.formatIsoDate
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackMood
import com.antoniusindra.sipatokkong.data.model.FeedbackReply
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.IconBoxBg
import com.antoniusindra.sipatokkong.ui.theme.MineBubble
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.OtherBubble
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackThreadScreen(
    feedbackId: String,
    onBack: () -> Unit,
    viewModel: FeedbackThreadViewModel = viewModel(factory = FeedbackThreadViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    // Clears the tray card for this thread on the way in.
    //
    // setAutoCancel(true) already covers arriving BY the notification. This covers the
    // other door -- reaching here from the inbox or the home screen -- where the card
    // would otherwise sit there. Now that messages accumulate into a single card, one
    // left behind would keep growing with replies the reader has already seen.
    //
    // In the composable rather than the ViewModel: the ViewModels here stay free of
    // Android framework types, and a notification is a view concern.
    val trayContext = LocalContext.current
    LaunchedEffect(feedbackId) {
        NotificationManagerCompat.from(trayContext).cancel(PushPayload.trayIdForEntity(feedbackId))
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(feedbackId, lifecycle) {
        lifecycle.pollWhileResumed { viewModel.refresh(feedbackId) }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Detail Feedback", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = BrandTeal,
                    navigationIconContentColor = BrandTeal,
                )
            )
        },
        bottomBar = {
            if (ui.feedback != null) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    // Volunteers only, and only while the note is still open: a parent's reply
                    // changes no status, and once answered the volunteer has nothing left to close.
                    if (!ui.isParent && ui.feedback!!.status != FEEDBACK_STATUS_ANSWERED) {
                        QuickReplyBar(
                            isSending = ui.isSending,
                            onPick = { viewModel.sendReply(it) },
                        )
                    }
                    ReplyBar(
                        value = ui.replyText,
                        isSending = ui.isSending,
                        onChange = viewModel::onReplyChange,
                        onSend = { viewModel.sendReply() },
                    )
                }
            }
        }
    ) { innerPadding ->
        when {
            ui.isLoading && ui.feedback == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }

            ui.loadError != null && ui.feedback == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                    ErrorRetry(ui.loadError!!, viewModel::retry)
                }

            ui.feedback != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (ui.loadError != null) {
                    Text(
                        "${ui.loadError} Percakapan akan diperbarui otomatis.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FeedbackDetailCard(ui.feedback!!, ui.who, today)

                if (ui.replies.isEmpty()) {
                    Text("Belum ada balasan.", color = MutedText, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Balasan", fontWeight = FontWeight.Bold)
                    ui.replies.forEach { reply ->
                        ReplyBubble(
                            reply = reply,
                            isMine = reply.authorId == ui.myId,
                            isParentViewer = ui.isParent,
                            today = today,
                        )
                    }
                }

                if (ui.sendError != null) {
                    Text(ui.sendError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun FeedbackDetailCard(fb: Feedback, who: String?, today: LocalDate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        // Was unset, so this card rendered flat while every other card in the app sits at 2dp.
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The card's first line: this screen was reachable from a notification, a list and a
            // deep link and said only "Detail Feedback" at the top, so a volunteer holding four
            // children had no way to tell whose note they had opened. Two lines uncapped, because
            // this is the answer to "who is this about" and truncating it withholds the answer.
            who?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    // IconBoxBg, the tile colour used everywhere else; MineBubble is the chat
                    // bubble colour and reading it here was a coincidence, not a decision.
                    modifier = Modifier.size(48.dp).background(IconBoxBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(FeedbackMood.emojiOf(fb.mood), fontSize = 24.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Kondisi Si Kecil", fontWeight = FontWeight.SemiBold, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Text(fb.mood ?: "Normal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    // The note's own date was never shown anywhere on this screen.
                    formatIsoDate(fb.createdAt, today)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MutedText)
                    }
                }
                // `status` was in hand here all along and never rendered, so a parent opening a
                // thread could not tell whether it had been answered without reading the bubbles.
                val chip = feedbackChipStyle(FeedbackTriage.stateOf(fb))
                Box(
                    modifier = Modifier
                        .background(chip.bg, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(chip.label, color = chip.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!fb.catatanPerilaku.isNullOrBlank()) {
                LabeledText("Kondisi anak setelah terapi", fb.catatanPerilaku)
            }
            if (!fb.kendala.isNullOrBlank()) {
                LabeledText("Kendala / Status Terapi", fb.kendala)
            }
            LabeledText("Catatan Tambahan", fb.isi)
        }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, color = MutedText, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ReplyBubble(
    reply: FeedbackReply,
    isMine: Boolean,
    isParentViewer: Boolean,
    today: LocalDate,
) {
    val author = when {
        isMine -> "Anda"
        isParentViewer -> "Relawan"
        else -> "Orang Tua"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        if (isMine) Spacer(Modifier.width(40.dp))
        Card(
            // `fill = false`: with a plain weight(1f) a one-word reply stretched the full width and
            // read as a block, not a bubble.
            modifier = Modifier.weight(1f, fill = false),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = if (isMine) MineBubble else OtherBubble),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        author,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = BrandTeal,
                        modifier = Modifier.weight(1f),
                    )
                    // `createdAt` was fetched from the database and then never rendered, so there
                    // was no way to tell an hour-old reply from a three-week-old one.
                    formatIsoDate(reply.createdAt, today)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MutedText)
                    }
                }
                Text(reply.isi, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!isMine) Spacer(Modifier.width(40.dp))
    }
}

/**
 * One-tap replies, shown to a volunteer above the composer.
 *
 * Each chip sends a real reply, so the existing `on_balasan_insert` trigger flips the note to
 * "Dijawab" and notifies the parent — no separate acknowledge path, no new status value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickReplyBar(
    isSending: Boolean,
    onPick: (String) -> Unit,
) {
    Surface(color = Color.White) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Balas cepat",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MutedText,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                // Without this, chips that wrap onto a second line sit flush at 0dp -- a bug this
                // repo has already shipped once and fixed. See the Change Log in project_status.md.
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QUICK_REPLIES.forEach { text ->
                    AssistChip(
                        onClick = { onPick(text) },
                        enabled = !isSending,
                        label = { Text(text) },
                        colors = AssistChipDefaults.assistChipColors(labelColor = BrandTeal),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyBar(
    value: String,
    isSending: Boolean,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text("Tulis balasan...") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onSend, enabled = !isSending && value.isNotBlank()) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim", tint = BrandTeal)
                }
            }
        }
    }
}
