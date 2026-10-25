package com.antoniusindra.sipatokkong.core.util

import java.time.LocalTime

/** Uses the device's local time; midnight through 04:59 is still night. */
fun greetingAt(time: LocalTime): String = when (time.hour) {
    in 5..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}
