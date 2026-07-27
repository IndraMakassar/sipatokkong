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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.data.model.Feedback
import com.antoniusindra.sipatokkong.data.model.FeedbackReply

private val MutedText = Color(0xFF6B7280)
private val BrandTeal = Color(0xFF2E6E7E)
private val MineBubble = Color(0xFFE8F1F2)
private val OtherBubble = Color(0xFFF1F2F4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackThreadScreen(
    feedbackId: String,
    onBack: () -> Unit,
    viewModel: FeedbackThreadViewModel = viewModel(factory = FeedbackThreadViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(feedbackId) { viewModel.load(feedbackId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detail Feedback", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        },
        bottomBar = {
            if (ui.feedback != null) {
                ReplyBar(
                    value = ui.replyText,
                    isSending = ui.isSending,
                    onChange = viewModel::onReplyChange,
                    onSend = viewModel::sendReply,
                )
            }
        }
    ) { innerPadding ->
        when {
            ui.isLoading && ui.feedback == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }

            ui.loadError != null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                    Text(ui.loadError!!, color = MaterialTheme.colorScheme.error)
                }

            ui.feedback != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FeedbackDetailCard(ui.feedback!!)

                if (ui.replies.isEmpty()) {
                    Text("Belum ada balasan.", color = MutedText, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Balasan", fontWeight = FontWeight.Bold)
                    ui.replies.forEach { reply ->
                        ReplyBubble(
                            reply = reply,
                            isMine = reply.authorId == ui.myId,
                            isParentViewer = ui.isParent,
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
private fun FeedbackDetailCard(fb: Feedback) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!fb.catatanPerilaku.isNullOrBlank()) {
                LabeledText("Kondisi anak setelah terapi", fb.catatanPerilaku)
            }
            if (!fb.kendala.isNullOrBlank()) {
                LabeledText("Kendala di rumah", fb.kendala)
            }
            LabeledText("Catatan / feedback", fb.isi)
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
private fun ReplyBubble(reply: FeedbackReply, isMine: Boolean, isParentViewer: Boolean) {
    val author = when {
        isMine -> "Anda"
        isParentViewer -> "Relawan"
        else -> "Orang Tua"
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        if (isMine) Spacer(Modifier.width(40.dp))
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = if (isMine) MineBubble else OtherBubble),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(author, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandTeal)
                Text(reply.isi, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!isMine) Spacer(Modifier.width(40.dp))
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
