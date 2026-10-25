package com.antoniusindra.sipatokkong.ui.volunteer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.antoniusindra.sipatokkong.core.util.formatDisplayDate
import com.antoniusindra.sipatokkong.data.model.ChildResponse
import com.antoniusindra.sipatokkong.data.model.WorkDuration
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenBg
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenText
import com.antoniusindra.sipatokkong.ui.theme.StatusTealBg
import com.antoniusindra.sipatokkong.ui.theme.StatusTealText
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmissionDetailScreen(
    submissionId: String,
    onBack: () -> Unit,
    viewModel: SubmissionDetailViewModel = viewModel(factory = SubmissionDetailViewModel.Factory)
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(submissionId) {
        androidx.core.app.NotificationManagerCompat.from(context).cancel(
            com.antoniusindra.sipatokkong.core.push.PushPayload.trayIdForEntity(submissionId)
        )
        viewModel.load(submissionId)
    }

    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            Toast.makeText(context, "Feedback berhasil disimpan", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Detail Setoran Tugas", fontWeight = FontWeight.Bold) },
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                ui.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = BrandTeal
                    )
                }

                ui.loadError != null -> {
                    ErrorRetry(
                        message = ui.loadError.orEmpty(),
                        onRetry = { viewModel.load(submissionId) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                ui.submission != null -> {
                    val sub = ui.submission!!
                    val childName = sub.tugas?.anak?.nama ?: "Anak tidak dikenal"
                    val taskTitle = sub.tugas?.judul ?: "Tugas tidak dikenal"
                    val parsedDate = runCatching { LocalDate.parse(sub.occurrenceDate) }.getOrNull()
                    val dateLabel =
                        formatDisplayDate(parsedDate, LocalDate.now()) ?: sub.occurrenceDate

                    val childResponse =
                        ChildResponse.entries.firstOrNull { it.dbValue == sub.kemandirian }
                    val workDuration = WorkDuration.entries.firstOrNull { it.dbValue == sub.durasi }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Info Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = childName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                                Text(
                                    text = taskTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandTeal
                                )
                                Text(
                                    text = "Tanggal pengerjaan: $dateLabel",
                                    fontSize = 14.sp,
                                    color = MutedText
                                )
                            }
                        }

                        // Response metrics
                        Text(
                            "Respons & Durasi Pengerjaan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            childResponse?.let {
                                Box(
                                    modifier = Modifier
                                        .background(StatusGreenBg, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${it.emoji} ${it.label}",
                                        fontSize = 14.sp,
                                        color = StatusGreenText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            workDuration?.let {
                                Box(
                                    modifier = Modifier
                                        .background(StatusTealBg, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "⏱ ${it.label}",
                                        fontSize = 14.sp,
                                        color = StatusTealText,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Parent's notes
                        Text("Catatan Orang Tua", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                        ) {
                            Text(
                                text = sub.catatan?.takeIf { it.isNotBlank() }
                                    ?: "(Tidak ada catatan tambahan)",
                                modifier = Modifier.padding(14.dp),
                                fontSize = 15.sp,
                                color = if (sub.catatan.isNullOrBlank()) MutedText else Color.Black
                            )
                        }

                        // Submitted Proof
                        ui.documentation?.let { doc ->
                            Text("Bukti Pengerjaan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF23323A)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (ui.documentationUrl != null && doc.mediaKind != "video") {
                                    AsyncImage(
                                        model = ui.documentationUrl,
                                        contentDescription = "Bukti pengerjaan",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayCircle,
                                            contentDescription = "Video bukti",
                                            tint = Color.White,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = "Bukti berupa video",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                            doc.keterangan?.let { ket ->
                                if (ket.isNotBlank()) {
                                    Text(
                                        text = ket,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedText
                                    )
                                }
                            }
                        }

                        // Volunteer Feedback Section
                        Text(
                            "Masukan Relawan (Feedback)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        OutlinedTextField(
                            value = ui.feedbackInput,
                            onValueChange = { viewModel.onFeedbackChange(it) },
                            placeholder = { Text("Tuliskan evaluasi, saran, atau pujian untuk orang tua di sini...") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !ui.isSubmitting
                        )

                        if (ui.submitError != null) {
                            Text(
                                text = ui.submitError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = { viewModel.submitFeedback() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                            enabled = !ui.isSubmitting
                        ) {
                            if (ui.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (sub.feedbackRelawan.isNullOrBlank()) "Kirim Feedback" else "Perbarui Feedback",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
