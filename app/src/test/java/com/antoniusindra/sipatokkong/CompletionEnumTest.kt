package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.data.model.ChildResponse
import com.antoniusindra.sipatokkong.data.model.WorkDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the Kotlin ↔ Postgres enum contract.
 *
 * A one-character mismatch between a `dbValue` here and an enum label in
 * `0003_kemandirian_durasi.sql` only shows up at runtime, as a failed insert on a form the parent
 * already filled in. These assertions turn that into a build failure instead.
 */
class CompletionEnumTest {

    private val migration by lazy {
        // Tests run with the module directory as the working directory.
        File("../supabase/migrations/0003_kemandirian_durasi.sql")
            .takeIf { it.exists() }
            ?: File("supabase/migrations/0003_kemandirian_durasi.sql")
    }

    /** Enum labels declared for a given Postgres type, in declaration order. */
    private fun sqlEnumValues(typeName: String): List<String> {
        val sql = migration.readText()
        val body = Regex(
            """create\s+type\s+public\.$typeName\s+as\s+enum\s*\((.*?)\)\s*;""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(sql)?.groupValues?.get(1) ?: error("Type $typeName not found in ${migration.path}")

        return Regex("'([^']+)'").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `migration file is reachable from the test working directory`() {
        assertTrue(
            "Expected migration at ${migration.absolutePath}",
            migration.exists(),
        )
    }

    @Test
    fun `ChildResponse dbValues match the tingkat_kemandirian enum exactly and in order`() {
        assertEquals(
            sqlEnumValues("tingkat_kemandirian"),
            ChildResponse.entries.map { it.dbValue },
        )
    }

    @Test
    fun `WorkDuration dbValues match the durasi_pengerjaan enum exactly and in order`() {
        assertEquals(
            sqlEnumValues("durasi_pengerjaan"),
            WorkDuration.entries.map { it.dbValue },
        )
    }

    @Test
    fun `ChildResponse is ordered most to least independent`() {
        // The grid renders in declaration order, so this ordering is user-visible.
        assertEquals(
            listOf("Mandiri", "Dengan Bantuan", "Dengan Bantuan Penuh", "Belum Bisa"),
            ChildResponse.entries.map { it.label },
        )
    }

    @Test
    fun `WorkDuration is ordered shortest to longest`() {
        assertEquals(
            listOf("< 5 menit", "5–15 menit", "15–30 menit", "> 30 menit"),
            WorkDuration.entries.map { it.label },
        )
    }

    @Test
    fun `every option carries a non-blank label and emoji`() {
        ChildResponse.entries.forEach {
            assertTrue("label kosong pada $it", it.label.isNotBlank())
            assertTrue("emoji kosong pada $it", it.emoji.isNotBlank())
            assertTrue("dbValue kosong pada $it", it.dbValue.isNotBlank())
        }
        WorkDuration.entries.forEach {
            assertTrue("label kosong pada $it", it.label.isNotBlank())
            assertTrue("dbValue kosong pada $it", it.dbValue.isNotBlank())
        }
    }
}
