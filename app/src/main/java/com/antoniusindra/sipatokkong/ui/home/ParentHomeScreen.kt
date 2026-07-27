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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BackHand
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme

// ---------------------------------------------------------------------------
// Local brand palette — scoped to this screen so it matches the mockup exactly
// without touching the global (purple / dynamic-color) theme.
// ---------------------------------------------------------------------------
private val ScreenBg = Color(0xFFF6F7F9)
private val BrandTeal = Color(0xFF2E6E7E)      // "Sipatokkong" text, hero card, active nav
private val HeroTeal = Color(0xFF2F6C7C)

private val SensoriBoxBg = Color(0xFFCDE7E0)   // mint icon box
private val SensoriTint = Color(0xFF2E6E7E)
private val BicaraBoxBg = Color(0xFFD6E4F5)    // light-blue icon box
private val BicaraTint = Color(0xFF3A6FB0)

private val ChipWaitingBg = Color(0xFFF8DDB8)  // "Menunggu"
private val ChipWaitingText = Color(0xFF8A5A1E)
private val ChipRunningBg = Color(0xFFBFE3CE)  // "Berjalan"
private val ChipRunningText = Color(0xFF2E6E4E)

private val ProgressTrack = Color(0xFFE4E5E7)
private val TipsBg = Color(0xFFECEDEE)
private val TipsIcon = Color(0xFFD9A521)
private val MutedText = Color(0xFF6B7280)

// ---------------------------------------------------------------------------
// Sample data — placeholder until the `tugas` backend is wired up.
// ---------------------------------------------------------------------------
private data class SampleTask(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconBoxBg: Color,
    val iconTint: Color,
    val statusLabel: String,
    val statusBg: Color,
    val statusText: Color,
    val progressDone: Int,
    val progressTotal: Int,
)

private val sampleTasks = listOf(
    SampleTask(
        title = "Latihan Sensori",
        description = "Fokus pada koordinasi tangan dan stimulasi tekstur lembut.",
        icon = Icons.Filled.BackHand,
        iconBoxBg = SensoriBoxBg,
        iconTint = SensoriTint,
        statusLabel = "Menunggu",
        statusBg = ChipWaitingBg,
        statusText = ChipWaitingText,
        progressDone = 0,
        progressTotal = 3,
    ),
    SampleTask(
        title = "Latihan Bicara",
        description = "Latihan pengucapan vokal dasar dan meniru suara hewan.",
        icon = Icons.Filled.RecordVoiceOver,
        iconBoxBg = BicaraBoxBg,
        iconTint = BicaraTint,
        statusLabel = "Berjalan",
        statusBg = ChipRunningBg,
        statusText = ChipRunningText,
        progressDone = 2,
        progressTotal = 3,
    ),
)

/** Parent (Orang Tua) home dashboard. Mirrors `assests/dashboard orang tua.png`. */
@Composable
fun ParentHomeScreen(
    profile: Profile,
    onLogout: () -> Unit,
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
                0 -> BerandaContent(profile = profile, childName = childName)
                1 -> PlaceholderTab("Riwayat")
                else -> PlaceholderTab("Profil", onLogout = onLogout)
            }
        }
    }
}

@Composable
private fun BerandaContent(profile: Profile, childName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ParentTopBar()

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Selamat pagi,", style = MaterialTheme.typography.bodyMedium, color = MutedText)
            Text(
                "Halo, ${profile.fullName}!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        HeroCard(childName = childName)

        SectionHeader()

        sampleTasks.forEach { task -> TaskCard(task) }

        TipsCard(childName = childName)
    }
}

@Composable
private fun ParentTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SensoriBoxBg, CircleShape),
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
        IconButton(onClick = { /* TODO: buka notifikasi */ }) {
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
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            modifier = Modifier.clickable { /* TODO: buka daftar tugas lengkap */ }
        )
    }
}

@Composable
private fun TaskCard(task: SampleTask) {
    Card(
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
                    modifier = Modifier
                        .size(48.dp)
                        .background(task.iconBoxBg, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(task.icon, contentDescription = null, tint = task.iconTint)
                }
                Spacer(Modifier.weight(1f))
                StatusChip(task.statusLabel, task.statusBg, task.statusText)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { if (task.progressTotal == 0) 0f else task.progressDone.toFloat() / task.progressTotal },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp),
                    color = BrandTeal,
                    trackColor = ProgressTrack,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${task.progressDone}/${task.progressTotal}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MutedText,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, bg: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(bg, CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
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
        Spacer(Modifier.height(12.dp))
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("Segera hadir", color = MutedText, style = MaterialTheme.typography.bodyMedium)
        if (onLogout != null) {
            Spacer(Modifier.height(20.dp))
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
    icon: ImageVector,
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
            indicatorColor = SensoriBoxBg,
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
            profile = Profile(
                id = "preview",
                role = UserRole.ORANGTUA,
                fullName = "Ibu Almair",
            ),
            onLogout = {},
        )
    }
}
