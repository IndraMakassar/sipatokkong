package com.antoniusindra.sipatokkong

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.antoniusindra.sipatokkong.core.di.ServiceLocator
import com.antoniusindra.sipatokkong.data.repository.*
import com.antoniusindra.sipatokkong.ui.home.*
import com.antoniusindra.sipatokkong.ui.volunteer.*
import com.antoniusindra.sipatokkong.ui.theme.SipatokkongTheme
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/** Real screens, ViewModels and HTTP serialization; all writes use an in-memory engine. */
@RunWith(AndroidJUnit4::class)
class HistoryProgramUiTest {
    @get:Rule val compose = createComposeRule()
    @Volatile private var fail = false
    @Volatile private var cancelled = false
    private val calls = CopyOnWriteArrayList<String>()
    private val task get() = """{"id":"task-1","anak_id":"child-1","relawan_id":"volunteer-1","judul":"Latihan lama","instruksi":"Instruksi lama","media_kind":"image","media_path":"child-1/demo.jpg","priority_level":"medium","status":"assigned","deadline":"2025-01-01","cancelled_at":${if(cancelled) "\"2026-10-25T00:00:00Z\"" else "null"}}"""
    private val client = createSupabaseClient("https://fixture.invalid","test") {
        httpEngine = MockEngine { request ->
            val route = request.url.encodedPath.substringAfterLast('/')
            calls.add(route)
            val body = when(route) {
                "tugas" -> "[$task]"
                "anak" -> """[{"id":"child-1","nama":"Anak uji"}]"""
                "parent_task_history" -> """[{"id":"completion-old","occurrence_date":"2025-01-01","task":$task}]"""
                "edit_program" -> "\"task-1\""
                "cancel_one_off" -> { cancelled = true; "\"task-1\"" }
                else -> "[]"
            }
            respond(if(fail) """{"message":"unavailable"}""" else body,
                if(fail) HttpStatusCode.ServiceUnavailable else HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType,"application/json"))
        }
        install(Postgrest)
    }
    @After fun close() = runBlocking { client.close() }

    @Test fun historicalRowRetriesThenOpensExactCompletion() {
        fail = true
        var opened: String? = null
        val vm=TaskPagesViewModel(TaskRepository(client))
        compose.setContent { SipatokkongTheme { TaskHistoryRoute({}, { opened=it }, vm) } }
        compose.waitUntil(5000) { vm.uiState.value.error != null }
        compose.onNodeWithText("Belum ada setoran dalam rentang ini.").assertDoesNotExist()
        fail=false
        compose.onNodeWithText("Coba Lagi").performClick()
        compose.waitUntil(5000) { vm.uiState.value.rows.isNotEmpty() }
        compose.onNodeWithText("2025-01-01").assertIsDisplayed()
        compose.onNodeWithText("Latihan lama").performClick()
        compose.runOnIdle { assertEquals("completion-old",opened) }
        compose.onNodeWithText("Dari awal").performClick()
        compose.onNodeWithText("Terapkan").assertIsDisplayed()
        compose.onNodeWithText("Batal").performClick()
    }

    @Test fun volunteerEditsPrefilledInstructionsAndKeepsExistingMedia() {
        var returned=false
        val vm=CreateTaskViewModel(ChildRepository(client),TaskRepository(client)) { "volunteer-1" }
        compose.setContent { SipatokkongTheme { CreateTaskScreen(onBack={returned=true},editTaskId="task-1",viewModel=vm) } }
        compose.waitUntil(5000) { vm.uiState.value.editingTask != null }
        compose.onNodeWithText("Edit Program Ajar").assertIsDisplayed()
        compose.onNodeWithText("Instruksi lama").performScrollTo().performTextReplacement("Instruksi diperbaiki")
        compose.onNodeWithText("Simpan Program Ajar").performScrollTo().performClick()
        compose.waitUntil(5000) { returned }
        assertTrue(calls.contains("edit_program"))
        assertEquals("Instruksi diperbaiki",vm.uiState.value.instruksi)
    }

    @Test fun volunteerCancellationRequiresDialogAndShowsCancelledState() {
        val vm=ProgramListViewModel(TaskRepository(client),ChildRepository(client),ServiceLocator.sessionManager)
        compose.setContent { SipatokkongTheme { ProgramListScreen({}, {}, viewModel=vm) } }
        compose.waitUntil(5000) { !vm.uiState.value.isLoading }
        compose.onNodeWithText("Latihan lama").performClick()
        compose.onNodeWithText("Batalkan tugas").performScrollTo().performClick()
        compose.onNodeWithText("Batalkan tugas?").assertIsDisplayed()
        assertFalse(calls.contains("cancel_one_off"))
        compose.onAllNodesWithText("Batalkan tugas").onLast().performClick()
        compose.waitUntil(5000) { vm.uiState.value.programs.firstOrNull()?.cancelledAt != null }
        compose.onNodeWithText("Dibatalkan").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertDoesNotExist()
    }
}
