package com.antoniusindra.sipatokkong.ui.volunteer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackListScreen
import com.antoniusindra.sipatokkong.ui.home.ProfileScreen
import com.antoniusindra.sipatokkong.ui.theme.AppBackground
import com.antoniusindra.sipatokkong.ui.theme.BrandTeal
import com.antoniusindra.sipatokkong.ui.theme.IconBoxBg
import com.antoniusindra.sipatokkong.ui.theme.MutedText
import com.antoniusindra.sipatokkong.ui.theme.ProgressGradBottom
import com.antoniusindra.sipatokkong.ui.theme.ProgressGradTop
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme

/**
 * The volunteer's home, replacing the four unstyled buttons that used to live inline in the nav
 * graph — the only screen in the app that still showed an English string and a raw role enum.
 *
 * Three tabs held in local state rather than nav routes, matching `ParentHomeScreen`. Putting the
 * feedback list in a tab also fixes a dead end: as a pushed destination it had no back button at
 * all, so the volunteer could only leave it with the system gesture.
 *
 * Brand colours are imported directly rather than read from `MaterialTheme.colorScheme`. Dynamic
 * colour is enabled app-wide, so on Android 12+ the scheme comes from the user's wallpaper and
 * `colorScheme.primary` is not `BrandTeal`. The parent screens do the same for the same reason.
 *
 * Stateless apart from the selected tab, so the `@Preview` at the bottom actually renders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolunteerHomeScreen(
    profile: Profile,
    ui: VolunteerHomeUiState,
    onCreateProgram: () -> Unit,
    onOpenPrograms: () -> Unit,
    onOpenFeedbackThread: (String) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedTab) {
                            0 -> "Sipatokkong"
                            1 -> "Feedback Orang Tua"
                            else -> "Profil Saya"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = if (selectedTab == 0) BrandTeal else Color.Black,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                VolunteerNavItem(selectedTab == 0, Icons.Filled.Home, "Beranda") { selectedTab = 0 }
                VolunteerNavItem(selectedTab == 1, Icons.AutoMirrored.Filled.Chat, "Feedback") { selectedTab = 1 }
                VolunteerNavItem(selectedTab == 2, Icons.Filled.Person, "Profil") { selectedTab = 2 }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> VolunteerBeranda(
                    profile = profile,
                    ui = ui,
                    onCreateProgram = onCreateProgram,
                    onOpenPrograms = onOpenPrograms,
                    onRetry = onRetry,
                )

                1 -> FeedbackListScreen(
                    title = "Feedback Orang Tua",
                    onOpenThread = onOpenFeedbackThread,
                    // No onCompose: a volunteer replies to threads, they do not start them.
                    emptyText = "Belum ada catatan dari orang tua.",
                    useScaffold = false,
                )

                else -> ProfileScreen(
                    profile = profile,
                    onLogout = onLogout,
                    roleLabel = "Relawan",
                    showFamilyStatus = false,
                )
            }
        }
    }
}

@Composable
private fun VolunteerBeranda(
    profile: Profile,
    ui: VolunteerHomeUiState,
    onCreateProgram: () -> Unit,
    onOpenPrograms: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text("Selamat datang,", style = MaterialTheme.typography.bodyMedium, color = MutedText)
            Text("Halo, ${profile.fullName}!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        VolunteerSummaryCard(ui = ui, onRetry = onRetry)

        ActionCard(
            icon = Icons.Filled.Add,
            title = "Buat Program Ajar",
            description = "Susun tugas terapi baru untuk anak binaan Anda.",
            onClick = onCreateProgram,
        )
        ActionCard(
            icon = Icons.AutoMirrored.Filled.Assignment,
            title = "Program Ajar Saya",
            description = "Lihat program yang berjalan dan hentikan bila perlu.",
            onClick = onOpenPrograms,
        )

        if (ui.children.isNotEmpty()) {
            Text("Anak Binaan Saya", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            ChildChips(names = ui.children.map { it.nama })
        }
    }
}

/**
 * The gradient hero card, carrying the one number that answers "what am I running right now".
 *
 * Same `drawWithCache` treatment as the parent's weekly-progress card so the two roles' home
 * screens read as the same app.
 */
@Composable
private fun VolunteerSummaryCard(ui: VolunteerHomeUiState, onRetry: () -> Unit) {
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
            Text(
                "Ringkasan",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
            )

            when {
                ui.isLoading -> CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )

                ui.error != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Gagal memuat ringkasan.",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = BrandTeal,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("Coba Lagi", fontWeight = FontWeight.Bold) }
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Metric(value = ui.activeCount.toString(), label = "program berjalan")
                        Spacer(Modifier.width(24.dp))
                        Metric(value = ui.childCount.toString(), label = "anak binaan")
                    }
                    Text(
                        ui.summary,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, label: String) {
    Column {
        Text(value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            label,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * A tappable action row.
 *
 * Deliberately the parent screens' card idiom — white, 16dp radius, 2dp elevation on the grey
 * canvas — rather than `AdminActionCard`, which is flat `surfaceVariant` and would therefore take
 * its colour from the wallpaper under dynamic colour.
 */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(IconBoxBg, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = BrandTeal)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildChips(names: List<String>) {
    // verticalArrangement matters: without it, chips that wrap onto a second line sit flush
    // against the one above.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        names.forEach { name ->
            Row(
                modifier = Modifier
                    .background(Color.White, CircleShape)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.ChildCare,
                    contentDescription = null,
                    tint = BrandTeal,
                    modifier = Modifier.size(16.dp),
                )
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.VolunteerNavItem(
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
            indicatorColor = IconBoxBg,
            unselectedIconColor = MutedText,
            unselectedTextColor = MutedText,
        ),
    )
}

@Preview(showBackground = true)
@Composable
private fun VolunteerHomeScreenPreview() {
    SipatokkongTheme(dynamicColor = false) {
        VolunteerHomeScreen(
            profile = Profile(
                id = "1",
                fullName = "Kevin Saputra",
                role = UserRole.RELAWAN,
                phone = "081234567890",
            ),
            ui = VolunteerHomeUiState(isLoading = false),
            onCreateProgram = {},
            onOpenPrograms = {},
            onOpenFeedbackThread = {},
            onRetry = {},
            onLogout = {},
        )
    }
}
