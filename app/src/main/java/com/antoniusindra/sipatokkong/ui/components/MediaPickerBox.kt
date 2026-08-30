package com.antoniusindra.sipatokkong.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.antoniusindra.sipatokkong.data.model.MediaKind

// Lifted out of CreateTaskScreen once a third screen needed it. Three of the four media surfaces in
// this app now share it -- the volunteer's program ajar, their presensi, and the parent's task
// proof -- and a component owned by one screen but used from another is a wire nobody can see.

/**
 * @param hasMedia whether demo media is attached at all, from either source. Kept separate from
 *   [previewUrl] because a template's signed URL arrives asynchronously: without this the box would
 *   fall back to "Ketuk untuk pilih" for a second and read as if the media had been lost.
 * @param previewUrl what to render as the thumbnail -- a local content Uri for a picked file, a
 *   signed URL for media inherited from a template.
 */
@Composable
internal fun MediaPickerBox(
    hasMedia: Boolean,
    previewUrl: String?,
    mediaKind: MediaKind?,
    mediaName: String?,
    onPick: () -> Unit,
    // Worded by the caller: the presensi bucket accepts photos only, so the task form's
    // "foto/video ... MP4" prompt would be a lie there.
    emptyPrompt: String = "Ketuk untuk pilih foto/video",
    formatHint: String = "JPG, WebP, atau MP4",
) {
    val showsImage = hasMedia && mediaKind == MediaKind.IMAGE && previewUrl != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (showsImage) 200.dp else 120.dp)
            .clickable { onPick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            showsImage -> {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = "Pratinjau media",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(mediaName ?: "Ketuk untuk ganti", style = MaterialTheme.typography.labelMedium)
            }

            hasMedia -> {
                Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(mediaName ?: "Media terpilih")
                Text("Ketuk untuk ganti", style = MaterialTheme.typography.labelMedium)
            }

            else -> {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(emptyPrompt)
                Text(formatHint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
