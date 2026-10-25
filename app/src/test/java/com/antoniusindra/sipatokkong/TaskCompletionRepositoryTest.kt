package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.repository.TaskRepository
import com.sun.net.httpserver.HttpServer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Exercise the real repository HTTP request and decoding against a local PostgREST fixture. */
class TaskCompletionRepositoryTest {
    private lateinit var server: HttpServer
    private lateinit var client: SupabaseClient
    private lateinit var repository: TaskRepository

    private val completion = Json.parseToJsonElement(
        """{
            "tugas_id":"task-1",
            "occurrence_date":"2026-10-25",
            "dokumentasi_id":null,
            "feedback_relawan":"Sudah bagus, lanjutkan latihan secara perlahan."
        }""",
    ) as JsonObject

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/v1/tugas_penyelesaian") { exchange ->
            val parameters = exchange.requestURI.rawQuery.split('&').associate {
                val (key, value) = it.split('=', limit = 2)
                URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
            // PostgREST returns only requested columns, so the old query loses the evaluation.
            val selected = parameters.getValue("select").split(',')
            val matches = parameters["tugas_id"] == "eq.task-1" &&
                parameters["occurrence_date"] == "eq.2026-10-25"
            val rows = if (matches) listOf(JsonObject(completion.filterKeys { it in selected }))
                else emptyList()
            val body = JsonArray(rows).toString().toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        client = createSupabaseClient("http://127.0.0.1:${server.address.port}", "test-anon-key") {
            defaultLogLevel = LogLevel.NONE
            install(Postgrest)
        }
        repository = TaskRepository(client)
    }

    @After
    fun tearDown() = runBlocking {
        if (::client.isInitialized) client.close()
        if (::server.isInitialized) server.stop(0)
    }

    @Test
    fun `completed task retains volunteer evaluation even without proof media`() = runBlocking {
        val result = repository.completionFor("task-1", "2026-10-25")
        assertEquals("Sudah bagus, lanjutkan latihan secara perlahan.", result?.feedbackRelawan)
        assertNull(result?.dokumentasiId)
    }

    @Test
    fun `another occurrence does not inherit a different day's evaluation`() = runBlocking {
        assertNull(repository.completionFor("task-1", "2026-10-26"))
    }
}
