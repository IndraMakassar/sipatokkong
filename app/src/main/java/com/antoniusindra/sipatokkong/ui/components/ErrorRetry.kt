package com.antoniusindra.sipatokkong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.MutedText

/**
 * A failed load, with a way out.
 *
 * Every error state in this app used to be a line of red text and nothing else — a dead end that
 * forced the user to back out and re-enter the screen to try again, on an app whose users are on
 * patchy mobile data. The retry button is the whole point of this component.
 */
@Composable
fun ErrorRetry(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(48.dp),
        )
        Text("Gagal memuat data", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandTeal,
                contentColor = Color.White,
            ),
        ) { Text("Coba Lagi", fontWeight = FontWeight.Bold) }
    }
}
