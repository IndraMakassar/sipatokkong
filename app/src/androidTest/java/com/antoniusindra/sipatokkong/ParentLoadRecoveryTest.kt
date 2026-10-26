package com.antoniusindra.sipatokkong

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.Task
import com.antoniusindra.sipatokkong.data.model.TaskOccurrence
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.ui.home.ParentHistoryScreen
import com.antoniusindra.sipatokkong.ui.home.ParentHomeScreen
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParentLoadRecoveryTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 10, 25)
    private val profile = Profile("parent-1", UserRole.ORANGTUA, "Orang Tua")

    @Test
    fun homeFailureOffersRetryWithoutClaimingThereAreNoTasks() {
        var retries = 0
        compose.setContent {
            SipatokkongTheme {
                ParentHomeScreen(
                    profile = profile, onLogout = {},
                    taskError = "Koneksi terputus", onRetryTasks = { retries++ },
                )
            }
        }
        compose.onNodeWithText("Koneksi terputus").assertIsDisplayed()
        compose.onNodeWithText("Belum ada tugas minggu ini.").assertDoesNotExist()
        compose.onNodeWithText("Belum ada tugas terapi yang perlu dikerjakan.").assertDoesNotExist()
        compose.onNodeWithText("Coba Lagi").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun historyShowsEmptyOnlyAfterLoadingSucceedsAndRetryIsActionable() {
        val loading = mutableStateOf(true)
        val error = mutableStateOf<String?>(null)
        var retries = 0
        compose.setContent {
            SipatokkongTheme {
                ParentHistoryScreen(
                    history = emptyList(), today = today, onBack = {}, onOpenTask = { _, _ -> },
                    isLoading = loading.value, error = error.value, onRetry = { retries++ },
                )
            }
        }
        compose.onNodeWithText("Belum ada tugas yang selesai.").assertDoesNotExist()
        compose.runOnIdle { loading.value = false; error.value = "Server tidak tersedia" }
        compose.onNodeWithText("Belum ada tugas yang selesai.").assertDoesNotExist()
        compose.onNodeWithText("Coba Lagi").performClick()
        compose.runOnIdle { assertEquals(1, retries); error.value = null }
        compose.onNodeWithText("Belum ada tugas yang selesai.").assertIsDisplayed()
    }

    @Test
    fun aFailedRefreshKeepsPreviouslyLoadedTasksVisible() {
        val task = Task(
            id = "task-1", anakId = "child-1", relawanId = "volunteer-1", judul = "Latihan tersimpan",
            mediaKind = "image", mediaPath = "demo.jpg", priorityLevel = "medium", status = "assigned",
        )
        compose.setContent {
            SipatokkongTheme {
                ParentHomeScreen(
                    profile = profile, onLogout = {}, taskError = "Koneksi terputus",
                    occurrencesByDate = listOf(today to listOf(TaskOccurrence(task, today, false))),
                )
            }
        }
        compose.onNodeWithText("Koneksi terputus").assertIsDisplayed()
        compose.onNodeWithText("Latihan tersimpan").performScrollTo().assertIsDisplayed()
    }
}
