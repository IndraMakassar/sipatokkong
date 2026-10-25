package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.Profile
import com.antoniusindra.sipatokkong.data.model.UserRole
import com.antoniusindra.sipatokkong.data.repository.ChildRepository
import com.antoniusindra.sipatokkong.data.repository.FeedbackRepository
import com.antoniusindra.sipatokkong.data.repository.ProfileRepository
import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import com.antoniusindra.sipatokkong.ui.feedback.FeedbackThreadViewModel
import com.antoniusindra.sipatokkong.ui.task.TaskDetailViewModel
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** Real ViewModels/repositories/HTTP decoding, with failures injected at the server boundary. */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowRecoveryTest {
    private lateinit var server: HttpServer
    private lateinit var client: SupabaseClient
    private val bodies = ConcurrentHashMap<String, String>()
    @Volatile private var failing = false
    private val parent = Profile(id = "parent-1", role = UserRole.ORANGTUA, fullName = "Parent")

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/v1/") { exchange ->
            val table = exchange.requestURI.path.substringAfterLast('/')
            val body = (if (failing) """{"message":"unavailable"}""" else bodies[table] ?: "[]").toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (failing) 503 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "test-key") {
            defaultLogLevel = LogLevel.NONE
            install(Postgrest)
        }
        bodies["feedback"] = """[{"id":"thread-1","anak_id":"child-1","orangtua_id":"parent-1","isi":"Catatan","status":"open"}]"""
        bodies["anak"] = """[{"id":"child-1","nama":"Anak","orangtua_id":"parent-1"}]"""
        bodies["feedback_balasan"] = "[]"
        bodies["tugas"] = """[{"id":"task-1","anak_id":"child-1","relawan_id":"volunteer-1","judul":"Latihan","media_kind":"image","media_path":"child-1/demo.jpg","priority_level":"medium","status":"open","recurrence":"daily","starts_on":"2026-01-01"}]"""
        bodies["tugas_penyelesaian"] = "[]"
    }

    @After
    fun teardown() = runBlocking {
        if (::client.isInitialized) client.close()
        if (::server.isInitialized) server.stop(0)
        Dispatchers.resetMain()
    }

    private fun chat() = FeedbackThreadViewModel(
        FeedbackRepository(client), ChildRepository(client), ProfileRepository(client), { parent },
    )

    @Test
    fun `chat recovers automatically and preserves draft and messages across a failed poll`() = runBlocking {
        val vm = chat()
        vm.refresh("thread-1")
        vm.onReplyChange("Draf belum dikirim")
        bodies["feedback_balasan"] = """[{"id":"reply-1","feedback_id":"thread-1","author_id":"volunteer-1","isi":"Balasan baru","created_at":"2026-10-25T10:00:00Z"}]"""
        vm.refresh("thread-1")
        assertEquals("Balasan baru", vm.uiState.value.replies.single().isi)
        failing = true
        vm.refresh("thread-1")
        assertNotNull(vm.uiState.value.loadError)
        assertNotNull(vm.uiState.value.feedback)
        assertEquals(1, vm.uiState.value.replies.size)
        assertEquals("Draf belum dikirim", vm.uiState.value.replyText)
        failing = false
        vm.refresh("thread-1")
        assertNull(vm.uiState.value.loadError)
        assertEquals("Draf belum dikirim", vm.uiState.value.replyText)
    }

    @Test
    fun `failed initial chat load can retry without leaving the screen`() = runBlocking {
        val vm = chat()
        failing = true
        vm.refresh("thread-1")
        assertNull(vm.uiState.value.feedback)
        failing = false
        vm.retry()
        val state = withTimeout(5_000) { vm.uiState.first { it.feedback != null && !it.isLoading } }
        assertNull(state.loadError)
    }

    @Test
    fun `task detail retries same occurrence and preserves form draft`() = runBlocking {
        val vm = TaskDetailViewModel(TaskRepository(client)) { parent }
        failing = true
        vm.load("task-1", LocalDate.of(2026, 10, 12))
        withTimeout(5_000) { vm.uiState.first { it.loadError != null } }
        vm.onCatatanChange("Catatan draf")
        failing = false
        vm.retry()
        val state = withTimeout(5_000) { vm.uiState.first { it.tugas != null && !it.isLoading } }
        assertNull(state.loadError)
        assertEquals(LocalDate.of(2026, 10, 12), state.occurrenceDate)
        assertEquals("Catatan draf", state.catatan)
    }

    @Test
    fun `evaluation notification resolves historical occurrence instead of today's schedule`() = runBlocking {
        bodies["tugas_penyelesaian"] = """[{"id":"completion-1","tugas_id":"task-1","orangtua_id":"parent-1","occurrence_date":"2026-01-12","feedback_relawan":"Lanjutkan","dokumentasi_id":null}]"""
        val vm = TaskDetailViewModel(TaskRepository(client)) { parent }
        vm.loadCompletion("completion-1")
        val state = withTimeout(5_000) { vm.uiState.first { !it.isLoading } }
        assertNull(state.loadError)
        assertEquals(LocalDate.of(2026, 1, 12), state.occurrenceDate)
        assertTrue(state.alreadyDone)
        assertEquals("Lanjutkan", state.feedbackRelawan)
    }
}
