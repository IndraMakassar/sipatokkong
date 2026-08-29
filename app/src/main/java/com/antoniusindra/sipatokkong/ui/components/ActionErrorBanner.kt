package com.antoniusindra.sipatokkong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antoniusindra.sipatokkong.ui.theme.StatusRedBg
import com.antoniusindra.sipatokkong.ui.theme.StatusRedText

/**
 * A failed *action*, reported above a list that stays exactly where it was.
 *
 * The counterpart to [ErrorRetry], which replaces the content because a failed *load* leaves
 * nothing to show. Mixing the two is a bug this repo has already shipped once: a failed "Hentikan
 * Program" wiped the whole list and left a full-screen red message, so one failing button cost the
 * volunteer everything they were looking at.
 */
@Composable
fun ActionErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = StatusRedBg,
        contentColor = StatusRedText,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("Tutup", color = StatusRedText) }
        }
    }
}
