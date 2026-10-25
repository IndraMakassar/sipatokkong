package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.repository.*
import com.antoniusindra.sipatokkong.ui.home.TaskPagesViewModel
import com.antoniusindra.sipatokkong.ui.volunteer.CreateTaskViewModel
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.net.InetSocketAddress
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryProgramFlowTest {
    private lateinit var server: HttpServer
    private lateinit var client: SupabaseClient
    private lateinit var repository: TaskRepository
    @Volatile private var fail = false
    @Volatile private var response = "[]"
    private val requests = CopyOnWriteArrayList<Pair<String, String>>()
    private val task = """{"id":"task-1","anak_id":"child-1","relawan_id":"volunteer-1","judul":"Program lama","instruksi":"Instruksi lama","media_kind":"image","media_path":"child-1/demo.jpg","priority_level":"medium","status":"assigned","recurrence":"daily","starts_on":"2026-01-01","ends_on":"2027-01-01","revision":3}"""
    private fun rows(first: Int, last: Int) = (first..last).joinToString(",", "[", "]") {
        """{"id":"completion-$it","occurrence_date":"2025-01-01","task":$task}"""
    }
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = HttpServer.create(InetSocketAddress("127.0.0.1",0),0)
        server.createContext("/rest/v1/") { ex ->
            val path = ex.requestURI.path.substringAfterLast('/')
            requests.add(path to ex.requestBody.bufferedReader().readText())
            val data = when {
                fail -> """{"message":"Network temporarily unavailable"}"""
                path == "tugas" -> "[$task]"
                path == "edit_program" -> "\"task-2\""
                path == "anak" -> "[]"
                else -> response
            }.toByteArray()
            ex.responseHeaders.set("Content-Type","application/json")
            ex.sendResponseHeaders(if (fail) 503 else 200,data.size.toLong())
            ex.responseBody.use { it.write(data) }
        }
        server.start()
        client = createSupabaseClient("http://127.0.0.1:${server.address.port}","test") {
            defaultLogLevel = LogLevel.NONE; install(Postgrest)
        }
        repository = TaskRepository(client)
    }
    @After fun teardown() = runBlocking { client.close(); server.stop(0); Dispatchers.resetMain() }
    private suspend fun idle(vm: TaskPagesViewModel) = withTimeout(5000) { vm.uiState.first { !it.loading && (it.loaded || it.error != null) } }

    @Test fun `history cursor uses last visible completion and failed next page retains rows`() = runBlocking {
        response = rows(1,51)
        val vm = TaskPagesViewModel(repository)
        vm.ensureLoaded(); idle(vm)
        assertEquals(50,vm.uiState.value.rows.size)
        assertTrue(vm.uiState.value.hasMore)
        fail = true; vm.more(); idle(vm)
        assertEquals(50,vm.uiState.value.rows.size)
        assertNotNull(vm.uiState.value.error)
        fail = false; response = rows(51,60); vm.retry(); idle(vm)
        assertEquals(60,vm.uiState.value.rows.size)
        assertFalse(vm.uiState.value.hasMore)
        assertEquals("completion-50",Json.parseToJsonElement(requests.last().second).jsonObject["p_cursor_id"]!!.jsonPrimitive.content)
        assertNull(vm.uiState.value.error)
    }
    @Test fun `filter resets cursor uses inclusive date inputs and invalid range keeps data`() = runBlocking {
        response = rows(1,51)
        val vm = TaskPagesViewModel(repository)
        vm.ensureLoaded(); idle(vm)
        response = rows(100,101); vm.filter("2025-01-01","2025-01-01"); idle(vm)
        val params=Json.parseToJsonElement(requests.last().second).jsonObject
        assertEquals("2025-01-01",params["p_from"]!!.jsonPrimitive.content)
        assertEquals("2025-01-01",params["p_to"]!!.jsonPrimitive.content)
        assertNull(params["p_cursor_id"])
        assertEquals(2,vm.uiState.value.rows.size)
        vm.filter("2026-01-01","2025-01-01")
        assertEquals(2,vm.uiState.value.rows.size)
        assertNotNull(vm.uiState.value.error)
    }
    @Test fun `old overdue excludes recent thirty day window and keeps its own state`() = runBlocking {
        val vm=TaskPagesViewModel(repository,true) { LocalDate.of(2026,10,25) }
        response=rows(1,1); vm.ensureLoaded(); idle(vm)
        assertEquals("parent_old_overdue",requests.last().first)
        val params=Json.parseToJsonElement(requests.last().second).jsonObject
        assertEquals("2026-09-25",params["p_before"]!!.jsonPrimitive.content)
        assertEquals(1,vm.uiState.value.rows.size)
    }
    @Test fun `edit loads old content retains media and submits version checked RPC`() = runBlocking {
        val vm=CreateTaskViewModel(ChildRepository(client),repository) { "volunteer-1" }
        vm.loadEdit("task-1")
        withTimeout(5000) { vm.uiState.first { it.editingTask != null } }
        assertEquals("Instruksi lama",vm.uiState.value.instruksi)
        assertTrue(vm.uiState.value.hasMedia)
        vm.onInstruksiChange("Instruksi baru")
        fail=true; vm.submit()
        withTimeout(5000) { vm.uiState.first { !it.isSubmitting && it.error != null } }
        assertEquals("Instruksi baru",vm.uiState.value.instruksi)
        fail=false; vm.submit()
        withTimeout(5000) { vm.uiState.first { it.isSuccess } }
        val edits=requests.filter { it.first=="edit_program" }.map { Json.parseToJsonElement(it.second).jsonObject }
        assertEquals(2,edits.size)
        assertEquals(edits[0]["p_request_id"],edits[1]["p_request_id"])
        assertEquals("3",edits.last()["p_revision"]!!.jsonPrimitive.content)
        val changes=edits.last()["p_changes"]!!.jsonObject
        assertEquals("child-1/demo.jpg",changes["media_path"]!!.jsonPrimitive.content)
        assertEquals("Instruksi baru",changes["instruksi"]!!.jsonPrimitive.content)
        assertEquals("daily",changes["recurrence"]!!.jsonPrimitive.content)
    }
}
