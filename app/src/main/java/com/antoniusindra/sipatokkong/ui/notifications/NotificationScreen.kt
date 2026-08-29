package com.antoniusindra.sipatokkong.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.antoniusindra.sipatokkong.core.util.formatIsoDate
import com.antoniusindra.sipatokkong.ui.components.ActionErrorBanner
import com.antoniusindra.sipatokkong.ui.components.EmptyState
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.IconBoxBg
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme
import java.time.LocalDate

// ---------------------------------------------------------------------------
// Local palette — kept in sync with the parent dashboard (teal), scoped here so
// it doesn't depend on the global theme.
// ---------------------------------------------------------------------------
private val UnreadBg = Color(0xFFE8F1F2)

private val TaskBoxBg = Color(0xFFD6E4F5)        // new task
private val TaskTint = Color(0xFF3A6FB0)
private val ReplyBoxBg = Color(0xFFF8DDB8)       // feedback reply
private val ReplyTint = Color(0xFF8A5A1E)
private val NoteBoxBg = Color(0xFFBFE3CE)        // a parent's note, as a volunteer receives it
private val NoteTint = Color(0xFF2E6E4E)

/**
 * The notification inbox, for whichever role is signed in.
 *
 * One screen for both, because nothing here needs to know the role: `notif_select` is
 * `recipient_id = auth.uid()`, so the repository already returns exactly the rows this user owns,
 * and the card is drawn from `type` and `entity_table` on the row itself. Tapping a row marks it
 * read; a feedback notification also opens its thread, while a task notification does not navigate —
 * see [NotifDisplay.targetOf] for why there is no honest destination for one.
 *
 * The one thing that does differ by role is the empty-state line; it is a parameter, not a branch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    onBack: () -> Unit,
    onOpenFeedbackThread: (String) -> Unit = {},
    /**
     * The line under "Belum ada notifikasi". Parent-voiced by default, because the parent is the
     * only role that receives more than one kind: `on_tugas_insert` and `on_balasan_insert` both
     * write to `orangtua_id`, so promising a volunteer "pengingat tugas" here would describe a row
     * the database will never write them.
     */
    emptyText: String = "Pengingat tugas & balasan feedback akan muncul di sini.",
    viewModel: NotificationViewModel = viewModel(factory = NotificationViewModel.Factory),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    // Read once per composition rather than per row, so every row agrees on what "Hari Ini" means.
    val today = remember { LocalDate.now() }
    var confirmMarkAll by remember { mutableStateOf(false) }

    // Re-runs when this screen re-enters composition, so a notification written while it was open
    // elsewhere shows up on return. Without it the list would only ever load once per process.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val onCardClick = { notif: NotificationItem ->
        // Fire-and-forget: the mark is optimistic, so navigation must not wait on the round-trip.
        viewModel.markRead(notif.id)
        when (val target = notif.target) {
            is NotifTarget.FeedbackThread -> onOpenFeedbackThread(target.feedbackId)
            NotifTarget.None -> Unit
        }
    }

    Scaffold(
        // AppBackground, not MaterialTheme.colorScheme.background: `dynamicColor` is still true in
        // Theme.kt, so the theme value resolves to the user's wallpaper palette on Android 12+ and
        // this screen would not match the rest of the app. Same workaround as ParentHomeScreen.
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Notifikasi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    // Hidden, not disabled, when there is nothing to mark — the same rule as
                    // NotifDisplay.badgeLabel(0) returning null: a control that can only no-op is a
                    // permanent mark on the screen that means nothing. unreadCount is 0 while
                    // loading, after a failed load and on an empty inbox, so this one condition
                    // covers every branch of the body below.
                    if (ui.unreadCount > 0) {
                        TextButton(onClick = { confirmMarkAll = true }) {
                            // No contentDescription: the Text beside it is the accessible label,
                            // and setting both makes TalkBack announce the action twice.
                            Icon(Icons.Filled.DoneAll, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Tandai semua", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = BrandTeal,
                    navigationIconContentColor = BrandTeal,
                    // Without this the action inherits from MaterialTheme.colorScheme, and
                    // dynamicColor is still true — so it would be painted from the user's wallpaper
                    // on Android 12+. The same bug the containerColor comment above describes; it
                    // was simply unreachable while this bar had no actions.
                    actionIconContentColor = BrandTeal,
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // An action error, above a list that stays exactly where it was. A failed mark-as-read
            // must not clear the inbox — see ActionErrorBanner's KDoc.
            ui.actionError?.let { message ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ActionErrorBanner(message = message, onDismiss = viewModel::dismissActionError)
                }
            }

            when {
                ui.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                ui.error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    ErrorRetry(message = ui.error!!, onRetry = viewModel::refresh)
                }

                ui.items.isEmpty() -> EmptyState(
                    icon = Icons.Filled.NotificationsNone,
                    title = "Belum ada notifikasi",
                    subtitle = emptyText,
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(ui.items, key = { it.id }) { notif ->
                        NotificationCard(notif, today) { onCardClick(notif) }
                    }

                    // Row ids are UUIDs, so a literal key cannot collide with one.
                    if (ui.hasMore || ui.isLoadingMore) {
                        item(key = "load_more") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (ui.isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    // Tapped, never triggered by scrolling to the end: spending a
                                    // request because someone flicked hard is what this app avoids.
                                    TextButton(onClick = viewModel::loadMore) {
                                        Text(
                                            "Muat lebih banyak",
                                            color = BrandTeal,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmed rather than instant, which is unusual for mark-all-read and deliberate here. There
    // is no undo and nowhere to put one — this app has no Snackbar anywhere, and no "mark unread"
    // exists. Rows never move or leave the list, so the tint and the dot are the *only* record of
    // what is new, and losing them loses the reader's place for good. And it reaches rows that are
    // not on screen, which nothing but a sentence can explain.
    if (confirmMarkAll) {
        AlertDialog(
            onDismissRequest = { confirmMarkAll = false },
            title = { Text("Tandai semua dibaca?") },
            text = {
                Text(
                    "Semua notifikasi Anda akan ditandai sudah dibaca, termasuk yang belum " +
                        "ditampilkan di layar ini. Tanda \"baru\" tidak bisa dikembalikan."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markAllRead()
                    confirmMarkAll = false
                }) { Text("Tandai") }
            },
            dismissButton = {
                TextButton(onClick = { confirmMarkAll = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun NotificationCard(
    notif: NotificationItem,
    today: LocalDate,
    onClick: () -> Unit,
) {
    val (icon, boxBg, tint) = iconFor(notif.type)
    // A read task notification does nothing at all when tapped, so it gets no ripple either —
    // better to read as "not tappable" than to promise something and deliver nothing.
    val isActionable = !notif.isRead || notif.target is NotifTarget.FeedbackThread
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Before .background(), or the ripple draws square over the rounded card.
            .clickable(enabled = isActionable, onClick = onClick)
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
            // Absent when the row carried nothing worth a second line — most often a parent's note
            // filed with mood and chips only. See NotifDisplay.bodyLineOf.
            notif.body?.let { body ->
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
            // Dropped entirely when the stored timestamp is unusable, rather than printed as a
            // placeholder — formatIsoDate returns null for exactly that case.
            formatIsoDate(notif.createdAt, today)?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedText,
                )
            }
        }
    }
}


private fun iconFor(type: NotifType): Triple<ImageVector, Color, Color> = when (type) {
    NotifType.TUGAS_BARU -> Triple(Icons.AutoMirrored.Filled.Assignment, TaskBoxBg, TaskTint)
    // Deliberately not Chat: that is FEEDBACK_BALASAN's glyph below, and two branches drawing the
    // same icon invite a later "simplification" that merges two kinds which are not the same thing.
    NotifType.FEEDBACK_BARU -> Triple(Icons.Filled.Feedback, NoteBoxBg, NoteTint)
    NotifType.FEEDBACK_BALASAN -> Triple(Icons.AutoMirrored.Filled.Chat, ReplyBoxBg, ReplyTint)
    NotifType.LAINNYA -> Triple(Icons.Filled.Notifications, IconBoxBg, MutedText)
}

/**
 * The card, not the screen.
 *
 * Previewing [NotificationScreen] would build its real ViewModel, which reaches
 * ServiceLocator and the Supabase client — neither of which exists in the Preview renderer. Same
 * reason RekapAbsensiScreen previews its row instead of itself.
 */
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun NotificationCardPreview() {
    SipatokkongTheme(dynamicColor = false) {
        Column(
            modifier = Modifier.background(AppBackground).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NotificationCard(
                NotificationItem(
                    id = "1",
                    type = NotifType.TUGAS_BARU,
                    title = "Kak Dewi memberi tugas untuk Andi",
                    body = "Latihan Sensori — Ajak anak menyentuh tekstur kain selama dua menit.",
                    createdAt = LocalDate.now().toString(),
                    isRead = false,
                    target = NotifTarget.None,
                ),
                today = LocalDate.now(),
            ) {}
            NotificationCard(
                NotificationItem(
                    id = "2",
                    type = NotifType.FEEDBACK_BALASAN,
                    title = "Kak Dewi · Andi",
                    body = "Bagus, lanjutkan latihan memegang sendoknya ya, Bu.",
                    createdAt = LocalDate.now().minusDays(1).toString(),
                    isRead = true,
                    target = NotifTarget.FeedbackThread("fb-1"),
                ),
                today = LocalDate.now(),
            ) {}
            // The volunteer's only kind, and the case that made body nullable: a note filed with
            // mood and chips alone carries CATATAN_RUTIN, which is dropped rather than repeated.
            // This card must show its title and date with no gap where the body would have been.
            NotificationCard(
                NotificationItem(
                    id = "3",
                    type = NotifType.FEEDBACK_BARU,
                    title = "Sari Wulandari · Andi",
                    body = null,
                    createdAt = LocalDate.now().minusDays(2).toString(),
                    isRead = false,
                    target = NotifTarget.FeedbackThread("fb-2"),
                ),
                today = LocalDate.now(),
            ) {}
            NotificationCard(
                NotificationItem(
                    id = "4",
                    type = NotifType.LAINNYA,
                    title = "Pemberitahuan",
                    body = "Sesuatu yang belum dikenal versi ini.",
                    createdAt = LocalDate.now().minusDays(4).toString(),
                    isRead = true,
                    target = NotifTarget.None,
                ),
                today = LocalDate.now(),
            ) {}
        }
    }
}
