package com.antoniusindra.sipatokkong.ui.feedback

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.antoniusindra.sipatokkong.ui.theme.BrandOrange
import com.antoniusindra.sipatokkong.ui.theme.BrandOrangeLight
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateFeedbackScreen(
    onBack: () -> Unit,
    viewModel: CreateFeedbackViewModel = viewModel(factory = CreateFeedbackViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(ui.isSuccess) {
        if (ui.isSuccess) {
            Toast.makeText(context, "Catatan terhasil disimpan!", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Catatan Perkembangan Anak", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = BrandTeal,
                    navigationIconContentColor = BrandTeal,
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section 1: Mood / Kondisi Umum
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Bagaimana kondisi Si Kecil hari ini?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MoodItem("Senang", "😊", ui.mood == "Senang") { viewModel.onMoodChange("Senang") }
                    MoodItem("Biasa", "😐", ui.mood == "Biasa") { viewModel.onMoodChange("Biasa") }
                    MoodItem("Rewel", "😢", ui.mood == "Rewel") { viewModel.onMoodChange("Rewel") }
                    MoodItem("Sakit", "🤒", ui.mood == "Sakit") { viewModel.onMoodChange("Sakit") }
                }
            }

            // Section 2: Quick Challenges
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Apakah ada kendala saat terapi?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                val challenges = listOf("Sulit Fokus", "Mudah Lelah", "Menolak Latihan", "Lancar", "Banyak Kemajuan")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    challenges.forEach { tag ->
                        val selected = ui.selectedChallenges.contains(tag)
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.toggleChallenge(tag) },
                            label = { Text(tag) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandOrangeLight,
                                selectedLabelColor = BrandOrange
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = if (selected) BrandOrange else Color.LightGray,
                                selectedBorderColor = BrandOrange,
                                borderWidth = 1.dp
                            )
                        )
                    }
                }
            }

            // Section 3: Additional Notes
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Catatan Tambahan (Opsional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = ui.isi,
                    onValueChange = viewModel::onIsiChange,
                    placeholder = { Text("Ceritakan lebih detail tentang perkembangan atau kendala anak...") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (ui.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        ui.error!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = viewModel::submit,
                enabled = !ui.isSubmitting && !ui.isLoadingChild,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal)
            ) {
                if (ui.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text("Simpan Catatan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun MoodItem(
    label: String,
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    if (isSelected) BrandOrangeLight else Color(0xFFF5F5F5),
                    CircleShape
                )
                .border(
                    width = 2.dp,
                    color = if (isSelected) BrandOrange else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 32.sp)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) BrandOrange else Color.Black
        )
    }
}
