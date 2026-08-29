package com.antoniusindra.sipatokkong.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.antoniusindra.sipatokkong.core.media.MediaDownloadState
import com.antoniusindra.sipatokkong.core.media.MediaSaver

/**
 * Runs [action] once the app is allowed to write to the gallery.
 *
 * On API 29+ nothing is asked — `MediaStore` needs no permission. On API 27–28 this fires the
 * app's **first ever runtime permission request**; a denial is reported out loud rather than
 * leaving a button that silently does nothing.
 */
@Composable
fun rememberMediaSaveGate(): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pending
        pending = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(
                context,
                "Izin penyimpanan ditolak, jadi file tidak bisa disimpan ke galeri.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    return remember(launcher, context) {
        { action ->
            val granted = !MediaSaver.needsLegacyPermission ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED

            if (granted) {
                action()
            } else {
                pending = action
                launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}

/**
 * The download affordance: an icon that turns into a progress ring while working.
 *
 * The permission gate lives here rather than in each caller, so no screen can forget it.
 */
@Composable
fun DownloadIconButton(
    state: MediaDownloadState,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    contentDescription: String = "Unduh ke galeri",
) {
    val gate = rememberMediaSaveGate()

    IconButton(
        onClick = { gate(onDownload) },
        enabled = !state.isBusy,
        modifier = modifier,
    ) {
        val progress = state.progress
        when {
            progress != null -> CircularProgressIndicator(
                progress = { progress },
                color = tint,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )

            state.isPreparing -> CircularProgressIndicator(
                color = tint,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
            )

            else -> Icon(Icons.Filled.Download, contentDescription = contentDescription, tint = tint)
        }
    }
}

/**
 * The dialog and toasts that go with [DownloadIconButton]. Place once per screen.
 *
 * The size confirmation only ever appears for video: `tugas-demo` allows 50 MB and data frugality
 * is a hard requirement for these families, while a 200 KB photo does not deserve a dialog.
 */
@Composable
fun MediaDownloadHost(
    state: MediaDownloadState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val context = LocalContext.current

    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Unduh video?") },
            text = {
                Text(
                    if (pending.sizeBytes >= 0) {
                        "Video ini berukuran sekitar ${pending.sizeLabel} dan akan memakai kuota " +
                            "data Anda. Unduh sekarang?"
                    } else {
                        "Video ini akan memakai kuota data Anda. Unduh sekarang?"
                    }
                )
            },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Unduh") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Batal") } },
        )
    }

    LaunchedEffect(state.message, state.savedUri, state.error) {
        val error = state.error
        val message = state.message
        when {
            error != null -> {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                onDismissMessage()
            }

            message != null -> {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                onDismissMessage()
            }
        }
    }
}

/** Opens a saved file in whatever gallery app the phone has. */
fun openSavedMedia(context: android.content.Context, uri: String, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uri), mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
