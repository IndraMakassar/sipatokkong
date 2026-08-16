package com.antoniusindra.sipatokkong.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antoniusindra.sipatokkong.ui.theme.MutedText

/**
 * The app's empty state: an icon, a headline, and a sentence saying what will appear here.
 *
 * Lifted out of `ParentNotificationScreen`, where this shape was written once and hardcoded to that
 * screen's copy. Most other empty states in the app are a bare line of grey text, which tells the
 * user nothing about why the screen is blank or what to do next.
 *
 * [action] is for the case where the emptiness is fixable by the user — an empty program list wants
 * a "create one" button, an empty inbox does not.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.size(12.dp))
        Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.size(20.dp))
            action()
        }
    }
}
