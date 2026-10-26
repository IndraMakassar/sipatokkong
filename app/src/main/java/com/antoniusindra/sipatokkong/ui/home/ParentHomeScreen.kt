package com.antoniusindra.sipatokkong.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.antoniusindra.sipatokkong.core.util.greetingAt
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskOccurrence
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.components.NotificationBell
import com.antoniusindra.sipatokkong.ui.components.ErrorRetry
import com.antoniusindra.sipatokkong.ui.components.RichTextDisplay
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackListScreen
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.IconBoxBg
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.ProgressGradBottom
import com.antoniusindra.sipatokkong.ui.theme.ProgressGradTop
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenBg
import com.antoniusindra.sipatokkong.ui.theme.StatusGreenText
import com.antoniusindra.sipatokkong.ui.theme.StatusOrangeBg
import com.antoniusindra.sipatokkong.ui.theme.StatusOrangeText
import com.antoniusindra.sipatokkong.ui.theme.StatusRedBg
import com.antoniusindra.sipatokkong.ui.theme.StatusRedText
import com.antoniusindra.sipatokkong.ui.theme.StatusTealBg
import com.antoniusindra.sipatokkong.ui.theme.StatusTealText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

// ---------------------------------------------------------------------------
// Local brand palette — scoped to this screen so it matches the mockup exactly
// without touching the global (purple / dynamic-color) theme.
// ---------------------------------------------------------------------------
internal val IconTint = Color(0xFF2E6E7E)

internal val TipsBg = Color(0xFFECEDEE)
internal val TipsIcon = Color(0xFFD9A521)

/** Display chip (label + colors) for one dated occurrence. */
internal data class StatusStyle(val label: String, val bg: Color, val text: Color)

internal fun occurrenceStyle(occurrence: TaskOccurrence, today: LocalDate): StatusStyle = when {
    occurrence.isDone -> StatusStyle("Selesai", StatusTealBg, StatusTealText)
    occurrence.date < today -> StatusStyle("Terlambat", StatusRedBg, StatusRedText)
    occurrence.date == today -> StatusStyle("Hari ini", StatusGreenBg, StatusGreenText)
    else -> StatusStyle("Menunggu", StatusOrangeBg, StatusOrangeText)
}

internal val DateHeaderFormat =
    DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy", Locale.forLanguageTag("id-ID"))

/** "Hari Ini" / "Besok" / full date — friendlier than a bare date as a group header. */
internal fun dateHeaderLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hari Ini"
    today.plusDays(1) -> "Besok"
    else -> date.format(DateHeaderFormat)
}

/** Parent (Orang Tua) home dashboard. Mirrors `assests/dashboard orang tua.png`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentHomeScreen(
    profile: Profile,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenTask: (String, LocalDate) -> Unit = { _, _ -> },
    onOpenFeedbackThread: (String) -> Unit = {},
    onComposeFeedback: () -> Unit = {},
    onSeeAllHistory: () -> Unit = {},
    occurrencesByDate: List<Pair<LocalDate, List<TaskOccurrence>>> = emptyList(),
    weekDone: Int = 0,
    weekTotal: Int = 0,
    today: LocalDate = LocalDate.now(),
    isLoadingTasks: Boolean = false,
    taskError: String? = null,
    onRetryTasks: () -> Unit = {},
    childName: String = "Almair",
    unreadNotifications: Int = 0,
    olderTasks: @Composable () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val title = when (selectedTab) {
        0 -> "Sipatokkong"
        1 -> "Catatan Perilaku"
        else -> "Profil"
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == 0) BrandTeal else Color.Black
                    )
                },
                actions = {
                    if (selectedTab == 0) {
                        NotificationBell(unreadNotifications, onOpenNotifications)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                ParentNavItem(selectedTab == 0, Icons.Filled.Home, "Beranda") { selectedTab = 0 }
                ParentNavItem(selectedTab == 1, Icons.AutoMirrored.Filled.Chat, "Catatan") { selectedTab = 1 }
                ParentNavItem(selectedTab == 2, Icons.Filled.Person, "Profil") { selectedTab = 2 }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> BerandaContent(
                    profile = profile,
                    childName = childName,
                    occurrencesByDate = occurrencesByDate,
                    weekDone = weekDone,
                    weekTotal = weekTotal,
                    today = today,
                    isLoadingTasks = isLoadingTasks,
                    taskError = taskError,
                    onRetryTasks = onRetryTasks,
                    onOpenTask = onOpenTask,
                    onSeeAllHistory = onSeeAllHistory,
                    olderTasks = olderTasks,
                )
                1 -> FeedbackListScreen(
                    title = "Catatan Perilaku & Feedback",
                    onOpenThread = onOpenFeedbackThread,
                    onCompose = onComposeFeedback,
                    useScaffold = false // I will add this parameter to FeedbackListScreen
                )
                else -> ProfileScreen(profile = profile, onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun BerandaContent(
    profile: Profile,
    childName: String,
    occurrencesByDate: List<Pair<LocalDate, List<TaskOccurrence>>>,
    weekDone: Int,
    weekTotal: Int,
    today: LocalDate,
    isLoadingTasks: Boolean,
    taskError: String?,
    onRetryTasks: () -> Unit,
    onOpenTask: (String, LocalDate) -> Unit,
    onSeeAllHistory: () -> Unit,
    olderTasks: @Composable () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val greeting by produceState(greetingAt(LocalTime.now()), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Refresh immediately after returning to the app, and while the home stays open.
            while (isActive) {
                value = greetingAt(LocalTime.now())
                delay(60_000L)
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 12.dp)) {
            Text("$greeting,", style = MaterialTheme.typography.bodyMedium, color = MutedText)
            Text("Halo, ${profile.fullName}!", fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }

        if (!isLoadingTasks && taskError == null) {
            WeeklyProgressCard(childName = childName, done = weekDone, total = weekTotal)
        }
        if (taskError != null && !isLoadingTasks) {
            ErrorRetry(message = taskError, onRetry = onRetryTasks)
        }

        SectionHeader(onSeeAllClick = onSeeAllHistory)

        when {
            isLoadingTasks -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            taskError != null && occurrencesByDate.isEmpty() -> Unit

            occurrencesByDate.isEmpty() -> Text(
                "Belum ada tugas terapi yang perlu dikerjakan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )

            else -> occurrencesByDate.forEach { (date, items) ->
                Text(
                    dateHeaderLabel(date, today),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (date < today) StatusRedText else MutedText,
                )
                items.forEach { occurrence ->
                    TaskCard(
                        occurrence = occurrence,
                        today = today,
                        onClick = { onOpenTask(occurrence.task.id, occurrence.date) },
                    )
                }
            }
        }

        olderTasks()
//        TipsCard(childName = childName)
    }
}

@Composable
private fun WeeklyProgressCard(childName: String, done: Int, total: Int) {
    val active = total - done

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val gradient = Brush.linearGradient(listOf(ProgressGradTop, ProgressGradBottom))
                    onDrawBehind {
                        drawRect(gradient)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.10f),
                            radius = size.minDimension * 0.55f,
                            center = Offset(size.width * 0.92f, -size.height * 0.15f),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = 0.06f),
                            radius = size.minDimension * 0.40f,
                            center = Offset(size.width * 0.12f, size.height * 1.15f),
                        )
                    }
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Progres Minggu Ini", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            Text(childName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (total == 0) {
                Text(
                    "Belum ada tugas minggu ini.",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.weight(1f).height(10.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "$done/$total selesai",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    if (active > 0) "$active tugas masih perlu dikerjakan" else "Semua tugas sudah selesai 🎉",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(onSeeAllClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Tugas Terapi Minggu Ini",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        // Labelled "Riwayat", not "Lihat Semua": the destination only lists finished
        // occurrences, so the old label promised more than it delivered.
        Text(
            "Riwayat",
            color = BrandTeal,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { onSeeAllClick() }
        )
    }
}

@Composable
internal fun TaskCard(occurrence: TaskOccurrence, today: LocalDate, onClick: () -> Unit) {
    val task = occurrence.task
    val style = occurrenceStyle(occurrence, today)
    // A finished occurrence stays on the list as proof of work, but recedes visually so the
    // eye lands on what still needs doing.
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (occurrence.isDone) 0.6f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (occurrence.isDone) 0.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(IconBoxBg, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        tint = IconTint
                    )
                }

                Text(
                    task.judul,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (occurrence.isDone) TextDecoration.LineThrough else null,
                    // Two, not one and not unbounded. `weight(1f)` only bounds the width, so a long
                    // judul wrapped as far as it liked and one card towered over its neighbours —
                    // card height is the only rhythm this list has. Two lines at 17sp bold in ~55%
                    // of the width is about 40 characters, which fits real programme names
                    // ("Latihan Motorik Halus — Meronce Manik"); one would clip ordinary titles.
                    // Tapping the card opens the full title, so nothing is lost here.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                StatusChip(style.label, style.bg, style.text)
            }

            if (!task.instruksi.isNullOrBlank() || task.isRecurring) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!task.instruksi.isNullOrBlank()) {
                        RichTextDisplay(
                            task.instruksi,
                            style = MaterialTheme.typography.bodyMedium.copy(color = MutedText),
                            maxLines = 2
                        )
                    }
                    if (task.isRecurring) {
                        Text(
                            task.toRecurrenceRule().describe(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusChip(label: String, bg: Color, textColor: Color) {
    Box(
        modifier = Modifier.background(bg, CircleShape).padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TipsCard(childName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TipsBg)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = TipsIcon)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tips Hari Ini", fontWeight = FontWeight.Bold)
                Text(
                    "Konsistensi adalah kunci. Berikan pujian kecil setiap kali $childName mencoba hal baru.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
        }
    }
}


@Composable
private fun androidx.compose.foundation.layout.RowScope.ParentNavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = BrandTeal,
            selectedTextColor = BrandTeal,
            indicatorColor = IconBoxBg,
            unselectedIconColor = MutedText,
            unselectedTextColor = MutedText,
        )
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun ParentHomeScreenPreview() {
    SipatokkongTheme(dynamicColor = false) {
        val today = LocalDate.of(2026, 8, 1)
        val sensori = Task(
            id = "1", anakId = "a", relawanId = "r", judul = "Latihan Sensori",
            instruksi = "Ajak anak menyentuh tekstur kain selama 2 menit.",
            mediaKind = "image", mediaPath = "x", priorityLevel = "medium",
            deadline = "2026-08-01", status = "assigned",
        )
        val bicara = Task(
            id = "2", anakId = "a", relawanId = "r", judul = "Latihan Bicara",
            instruksi = "Latihan pengucapan vokal dasar.",
            mediaKind = "video", mediaPath = "y", priorityLevel = "high",
            status = "assigned",
            recurrence = "weekly", recurrenceDays = listOf(1, 4),
            startsOn = "2026-08-01", endsOn = "2026-09-01",
        )
        ParentHomeScreen(
            profile = Profile(id = "preview", role = UserRole.ORANGTUA, fullName = "Ibu Almair"),
            onLogout = {},
            today = today,
            weekDone = 1,
            weekTotal = 3,
            occurrencesByDate = listOf(
                today to listOf(
                    TaskOccurrence(sensori, today, isDone = false),
                    // Finished one, so the dimmed + struck-through styling is visible here too.
                    TaskOccurrence(bicara, today, isDone = true),
                ),
                today.plusDays(3) to listOf(
                    TaskOccurrence(bicara, today.plusDays(3), isDone = false),
                ),
            ),
        )
    }
}
