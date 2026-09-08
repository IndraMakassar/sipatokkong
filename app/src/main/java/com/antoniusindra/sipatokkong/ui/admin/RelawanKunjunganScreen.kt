package com.antoniusindra.sipatokkong.ui.admin

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.antoniusindra.sipatokkong.data.model.Presensi
import com.antoniusindra.sipatokkong.ui.components.EmptyState
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDate = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id-ID"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelawanKunjunganScreen(
    relawanId: String,
    from: LocalDate,
    to: LocalDate,
    onBack: () -> Unit,
) {
    val viewModel: RelawanKunjunganViewModel = viewModel(
        factory = RelawanKunjunganViewModel.Factory(relawanId, from, to)
    )
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Detail Kunjungan", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp),
        ) {
            when {
                ui.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorRetry(message = ui.error!!, onRetry = viewModel::refresh)
                }

                ui.visits.isEmpty() -> EmptyState(
                    icon = Icons.Filled.EventAvailable,
                    title = "Belum ada kunjungan",
                    subtitle = "Relawan belum mencatat kunjungan pada rentang tanggal ini.",
                )

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(ui.visits, key = { it.id }) { visit ->
                        VisitDetailCard(
                            visit = visit,
                            onGetUrl = { viewModel.getSignedUrl(visit.fotoPath ?: "") },
                            onOpenMap = { lat, lon -> openMap(context, lat, lon) },
                            onDownload = { path ->
                                scope.launch {
                                    runCatching { viewModel.getSignedUrl(path) }
                                        .onSuccess { url -> downloadImage(context, url) }
                                        .onFailure {
                                            Toast.makeText(context, "Gagal mengunduh foto", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitDetailCard(
    visit: Presensi,
    onGetUrl: suspend () -> String,
    onOpenMap: (Double, Double) -> Unit,
    onDownload: (String) -> Unit
) {
    var photoUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(visit.fotoPath) {
        if (!visit.fotoPath.isNullOrEmpty()) {
            photoUrl = onGetUrl()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    visit.parsedDate?.format(ShortDate) ?: visit.tanggal,
                    fontWeight = FontWeight.Bold,
                    color = BrandTeal
                )
            }

            if (!visit.catatan.isNullOrBlank()) {
                Text(visit.catatan, style = MaterialTheme.typography.bodyMedium)
            }

            if (photoUrl != null) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Foto Bukti",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { onDownload(visit.fotoPath!!) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Unduh", tint = Color.White)
                    }
                }
            } else if (!visit.fotoPath.isNullOrEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }

            if (visit.latitude != null && visit.longitude != null) {
                Button(
                    onClick = { onOpenMap(visit.latitude, visit.longitude) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = BrandTeal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Lihat di Peta", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun openMap(context: Context, latitude: Double, longitude: Double) {
    val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.setPackage("com.google.android.apps.maps")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

private fun downloadImage(context: Context, url: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("Sipatokkong Presensi")
        .setDescription("Mengunduh foto bukti kunjungan")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "sipatokkong_${System.currentTimeMillis()}.jpg")
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
    Toast.makeText(context, "Mulai mengunduh...", Toast.LENGTH_SHORT).show()
}
