package com.antoniusindra.sipatokkong.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsNone
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniusindra.sipatokkong.ui.components.EmptyState
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme

// ---------------------------------------------------------------------------
// Local palette — kept in sync with the parent dashboard (teal), scoped here so
// it doesn't depend on the global theme.
// ---------------------------------------------------------------------------
private val UnreadBg = Color(0xFFE8F1F2)

private val ReminderBoxBg = Color(0xFFCDE7E0)   // task reminders
private val ReminderTint = Color(0xFF2E6E7E)
private val TaskBoxBg = Color(0xFFD6E4F5)        // new task
private val TaskTint = Color(0xFF3A6FB0)
private val ReplyBoxBg = Color(0xFFF8DDB8)       // feedback reply
private val ReplyTint = Color(0xFF8A5A1E)

/** Parent-relevant notification kinds (subset of Postgres enum `notif_type`). */
enum class ParentNotifType { TUGAS_BARU, TUGAS_REMINDER, FEEDBACK_BALASAN }

/** UI model — later mapped from `public.notifications` rows for the logged-in parent. */
data class ParentNotification(
    val type: ParentNotifType,
    val title: String,
    val body: String,
    val timeLabel: String,
    val isRead: Boolean,
)

// Sample data — placeholder until the `notifications` table is wired up.
private val sampleNotifications = listOf(
    ParentNotification(
        type = ParentNotifType.TUGAS_BARU,
        title = "Tugas rumah baru",
        body = "Relawan menambahkan \"Latihan Sensori\" untuk Almair minggu ini.",
        timeLabel = "2 jam lalu",
        isRead = false,
    ),
    ParentNotification(
        type = ParentNotifType.FEEDBACK_BALASAN,
        title = "Balasan feedback",
        body = "Relawan membalas catatan Anda: \"Bagus, lanjutkan latihan memegang sendoknya ya, Bu.\"",
        timeLabel = "5 jam lalu",
        isRead = false,
    ),
    ParentNotification(
        type = ParentNotifType.TUGAS_REMINDER,
        title = "Pengingat tugas",
        body = "Jangan lupa lanjutkan \"Latihan Bicara\" hari ini. Progres 2/3.",
        timeLabel = "Kemarin",
        isRead = true,
    ),
    ParentNotification(
        type = ParentNotifType.TUGAS_REMINDER,
        title = "Pengingat mingguan",
        body = "Minggu baru sudah dimulai. Cek tugas terapi Almair minggu ini.",
        timeLabel = "3 hari lalu",
        isRead = true,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentNotificationScreen(
    onBack: () -> Unit,
    notifications: List<ParentNotification> = sampleNotifications,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Notifikasi", fontWeight = FontWeight.Bold) },
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
        if (notifications.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.NotificationsNone,
                title = "Belum ada notifikasi",
                subtitle = "Pengingat tugas & balasan feedback akan muncul di sini.",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(notifications) { notif -> NotificationCard(notif) }
            }
        }
    }
}

@Composable
private fun NotificationCard(notif: ParentNotification) {
    val (icon, boxBg, tint) = iconFor(notif.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (notif.isRead) Color.White else UnreadBg,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(boxBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notif.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                if (!notif.isRead) {
                    Box(modifier = Modifier.size(9.dp).background(BrandTeal, CircleShape))
                }
            }
            Text(
                notif.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
            Text(
                notif.timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MutedText,
            )
        }
    }
}


private fun iconFor(type: ParentNotifType): Triple<ImageVector, Color, Color> = when (type) {
    ParentNotifType.TUGAS_BARU -> Triple(Icons.AutoMirrored.Filled.Assignment, TaskBoxBg, TaskTint)
    ParentNotifType.TUGAS_REMINDER -> Triple(Icons.Filled.Alarm, ReminderBoxBg, ReminderTint)
    ParentNotifType.FEEDBACK_BALASAN -> Triple(Icons.AutoMirrored.Filled.Chat, ReplyBoxBg, ReplyTint)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ParentNotificationScreenPreview() {
    SipatokkongTheme(dynamicColor = false) {
        ParentNotificationScreen(onBack = {})
    }
}
