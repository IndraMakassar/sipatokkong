package com.antoniusindra.sipatokkong.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.antoniusindra.sipatokkong.ui.notifications.NotifDisplay
import com.antoniusindra.sipatokkong.ui.theme.BrandOrange
import com.antoniusindra.sipatokkong.ui.theme.MutedText

/**
 * The bell that opens a notification inbox, with its unread count.
 *
 * Extracted because the parent's and the volunteer's top bars would otherwise hold character-for-
 * character the same block, and the line most likely to drift between two copies is the one nobody
 * looks at: the `contentDescription`. This app's users are exactly the population that pays for a
 * screen reader announcing the wrong thing, so it lives in one place.
 *
 * A number rather than a bare dot: "3" tells the reader whether it is worth opening. [NotifDisplay
 * .badgeLabel] returns null below one — so nothing is drawn at all rather than a permanent "0" —
 * and caps at "9+" so the circle never has to stretch.
 */
@Composable
fun NotificationBell(unread: Int, onClick: () -> Unit) {
    val badge = NotifDisplay.badgeLabel(unread)
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                badge?.let {
                    Badge(containerColor = BrandOrange, contentColor = Color.White) { Text(it) }
                }
            }
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = if (badge == null) {
                    "Notifikasi"
                } else {
                    // The count itself, not the capped label: "9+" is a layout compromise, and a
                    // screen reader has no width to run out of.
                    "Notifikasi, $unread belum dibaca"
                },
                tint = MutedText,
            )
        }
    }
}
