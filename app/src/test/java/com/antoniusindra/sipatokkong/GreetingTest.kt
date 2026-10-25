package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.util.greetingAt
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GreetingTest(private val time: String, private val expected: String) {
    @Test
    fun `greeting matches the local time including period boundaries`() {
        assertEquals(expected, greetingAt(LocalTime.parse(time)))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun cases(): List<Array<String>> = listOf(
            arrayOf("00:00", "Selamat malam"),
            arrayOf("04:59:59.999999999", "Selamat malam"),
            arrayOf("05:00", "Selamat pagi"),
            arrayOf("08:30", "Selamat pagi"),
            arrayOf("10:59:59.999999999", "Selamat pagi"),
            arrayOf("11:00", "Selamat siang"),
            arrayOf("12:30", "Selamat siang"),
            arrayOf("14:59:59.999999999", "Selamat siang"),
            arrayOf("15:00", "Selamat sore"),
            arrayOf("17:59:59.999999999", "Selamat sore"),
            arrayOf("18:00", "Selamat malam"),
            arrayOf("23:59:59.999999999", "Selamat malam"),
        )
    }
}
