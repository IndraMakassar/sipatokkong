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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.Tugas
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme

// ---------------------------------------------------------------------------
// Local brand palette — scoped to this screen so it matches the mockup exactly
// without touching the global (purple / dynamic-color) theme.
// ---------------------------------------------------------------------------
private val ScreenBg = Color(0xFFF6F7F9)
private val BrandTeal = Color(0xFF2E6E7E)
private val HeroTeal = Color(0xFF2F6C7C)
private val IconBoxBg = Color(0xFFCDE7E0)
private val IconTint = Color(0xFF2E6E7E)

private val ChipWaitingBg = Color(0xFFF8DDB8)
private val ChipWaitingText = Color(0xFF8A5A1E)
private val ChipRunningBg = Color(0xFFBFE3CE)
private val ChipRunningText = Color(0xFF2E6E4E)
private val ChipDoneBg = Color(0xFFCDE7E0)
private val ChipDoneText = Color(0xFF2E6E7E)
private val ChipOverdueBg = Color(0xFFF6D2CE)
private val ChipOverdueText = Color(0xFF9B3B31)

private val TipsBg = Color(0xFFECEDEE)
private val TipsIcon = Color(0xFFD9A521)
private val MutedText = Color(0xFF6B7280)

/** Maps a `tugas.status` value to a display chip (label + colors). */
private data class StatusStyle(val label: String, val bg: Color, val text: Color)

private fun statusStyle(status: String): StatusStyle = when (status) {
    "done" -> StatusStyle("Selesai", ChipDoneBg, ChipDoneText)
    "submitted" -> StatusStyle("Dikirim", ChipRunningBg, ChipRunningText)
    "overdue" -> StatusStyle("Terlambat", ChipOverdueBg, ChipOverdueText)
    else -> StatusStyle("Menunggu", ChipWaitingBg, ChipWaitingText) // assigned
}

/** Parent (Orang Tua) home dashboard. Mirrors `assests/dashboard orang tua.png`. */
@Composable
fun ParentHomeScreen(
    profile: Profile,
    onLogout: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenTask: (String) -> Unit = {},
    tasks: List<Tugas> = emptyList(),
    isLoadingTasks: Boolean = false,
    childName: String = "Almair",
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = ScreenBg,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                ParentNavItem(selectedTab == 0, Icons.Filled.Home, "Beranda") { selectedTab = 0 }
                ParentNavItem(selectedTab == 1, Icons.Filled.History, "Riwayat") { selectedTab = 1 }
                ParentNavItem(selectedTab == 2, Icons.Filled.Person, "Profil") { selectedTab = 2 }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> BerandaContent(
                    profile = profile,
                    childName = childName,
                    tasks = tasks,
                    isLoadingTasks = isLoadingTasks,
                    onOpenNotifications = onOpenNotifications,
                    onOpenTask = onOpenTask,
                )
                1 -> PlaceholderTab("Riwayat")
                else -> PlaceholderTab("Profil", onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun BerandaContent(
    profile: Profile,
    childName: String,
    tasks: List<Tugas>,
    isLoadingTasks: Boolean,
    onOpenNotifications: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ParentTopBar(onBellClick = onOpenNotifications)

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Selamat pagi,", style = MaterialTheme.typography.bodyMedium, color = MutedText)
            Text("Halo, ${profile.fullName}!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        HeroCard(childName = childName)

        SectionHeader()

        when {
            isLoadingTasks -> Box(
                Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            tasks.isEmpty() -> Text(
                "Belum ada tugas terapi minggu ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )

            else -> tasks.forEach { task -> TaskCard(task, onClick = { onOpenTask(task.id) }) }
        }

        TipsCard(childName = childName)
    }
}

@Composable
private fun ParentTopBar(onBellClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).background(IconBoxBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Foto profil", tint = BrandTeal)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Sipatokkong",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BrandTeal,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onBellClick) {
            Icon(Icons.Filled.Notifications, contentDescription = "Notifikasi", tint = MutedText)
        }
    }
}

@Composable
private fun HeroCard(childName: String) {
    Card(
        onClick = { /* TODO: buka form catatan kondisi anak */ },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HeroTeal)
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Catat Kondisi $childName Hari Ini",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Bagaimana perasaan dan perkembangan si kecil hari ini?",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SentimentSatisfiedAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Tugas Terapi Minggu Ini",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            "Lihat Semua",
            color = BrandTeal,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TaskCard(task: Tugas, onClick: () -> Unit) {
    val style = statusStyle(task.status)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(48.dp).background(IconBoxBg, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = IconTint)
                }
                Spacer(Modifier.weight(1f))
                StatusChip(style.label, style.bg, style.text)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.judul, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (!task.instruksi.isNullOrBlank()) {
                    Text(
                        task.instruksi,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )
                }
                if (!task.deadline.isNullOrBlank()) {
                    Text(
                        "Deadline: ${task.deadline}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedText,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, bg: Color, textColor: Color) {
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
private fun PlaceholderTab(label: String, onLogout: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = MutedText,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.size(12.dp))
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Segera hadir", color = MutedText, style = MaterialTheme.typography.bodyMedium)
        if (onLogout != null) {
            Spacer(Modifier.size(20.dp))
            Text(
                "Keluar",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onLogout() }
            )
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
        ParentHomeScreen(
            profile = Profile(id = "preview", role = UserRole.ORANGTUA, fullName = "Ibu Almair"),
            onLogout = {},
            tasks = listOf(
                Tugas(
                    id = "1", anakId = "a", relawanId = "r", judul = "Latihan Sensori",
                    instruksi = "Ajak anak menyentuh tekstur kain selama 2 menit.",
                    mediaKind = "image", mediaPath = "x", priorityLevel = "medium",
                    deadline = "2026-08-01", status = "assigned",
                ),
                Tugas(
                    id = "2", anakId = "a", relawanId = "r", judul = "Latihan Bicara",
                    instruksi = "Latihan pengucapan vokal dasar.",
                    mediaKind = "video", mediaPath = "y", priorityLevel = "high",
                    deadline = null, status = "done",
                ),
            ),
        )
    }
}
