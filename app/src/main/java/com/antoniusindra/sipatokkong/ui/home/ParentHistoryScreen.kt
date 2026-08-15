package com.antoniusindra.sipatokkong.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoniusindra.sipatokkong.data.model.TaskOccurrence
import java.time.LocalDate
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.MutedText


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHistoryScreen(
    /** Completed occurrences — a recurring program appears once per date it was done. */
    history: List<TaskOccurrence>,
    today: LocalDate,
    onBack: () -> Unit,
    onOpenTask: (String, LocalDate) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Tugas", fontWeight = FontWeight.Bold) },
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
        val completed = history.filter { it.isDone }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (completed.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text("Belum ada tugas yang selesai.", color = MutedText)
                    }
                }
            } else {
                completed.forEach { occurrence ->
                    Text(
                        dateHeaderLabel(occurrence.date, today),
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedText,
                    )
                    TaskCard(
                        occurrence = occurrence,
                        today = today,
                        onClick = { onOpenTask(occurrence.task.id, occurrence.date) },
                    )
                }
            }
        }
    }
}
