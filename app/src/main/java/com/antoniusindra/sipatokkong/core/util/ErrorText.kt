package com.antoniusindra.sipatokkong.core.util

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Turns a thrown exception into a sentence a parent can act on.
 *
 * Every screen in this app used to write `e.message ?: "Gagal memuat data"` into its error state.
 * The friendly half of that expression was dead code: `message` is non-null for essentially every
 * exception, so what reached the screen was the raw text. Two examples of what people were actually
 * shown:
 *
 *  - a wrong PIN produced supabase-kt's five-line `RestException` blob -- the error code, the
 *    description, the full request URL, the request headers, and the HTTP method;
 *  - no signal produced `HTTP request to https://….supabase.co/… failed with message: Unable to
 *    resolve host "…": No address associated with hostname`.
 *
 * **The rule that matters is the last one: an unrecognised error yields the caller's own fallback,
 * never the raw text.** A worse message is always better than a stack trace on a screen belonging to
 * someone who cannot read English, let alone a URL. The raw text still reaches logcat.
 *
 * Split into two functions on purpose. supabase-kt's `RestException` cannot be constructed in a unit
 * test -- it requires a live Ktor `HttpResponse` -- so the classification lives in [forMessage],
 * which takes a plain string and is fully testable against real captured messages. [of] adds only
 * what a string cannot express: the exception's type.
 */
object ErrorText {

    const val OFFLINE = "Tidak ada koneksi internet. Periksa jaringan Anda lalu coba lagi."
    const val SLOW = "Koneksi terlalu lambat. Coba lagi."
    const val SERVER = "Tidak bisa menghubungi server. Coba lagi sebentar lagi."
    const val WRONG_LOGIN = "Nomor HP atau PIN salah."
    const val SESSION_EXPIRED = "Sesi Anda berakhir. Silakan masuk kembali."

    /**
     * Moved here from `CreatePresensiViewModel`, where it was a private one-off.
     *
     * `presensi_unik_harian` raises Postgres 23505, which arrives mentioning the index name --
     * unreadable, and it reads like a crash rather than "you already did this".
     */
    const val ALREADY_RECORDED = "Kunjungan untuk anak ini pada tanggal tersebut sudah dicatat."

    /**
     * Classification by message text. Pure, and the half worth testing.
     *
     * @param fallback the calling screen's own sentence, used for anything unrecognised.
     */
    fun forMessage(raw: String?, fallback: String): String = matchMessage(raw) ?: fallback

    /**
     * The same, plus the type checks a string cannot express.
     *
     * Message first, type second, and the order is load-bearing: `HttpRequestException` is an
     * `IOException` whose message names the real cause, so matching the text before the type is what
     * separates "no signal" from "the server refused".
     */
    fun of(e: Throwable, fallback: String): String =
        matchMessage(e.message) ?: when (e) {
            is UnknownHostException -> OFFLINE
            is SocketTimeoutException -> SLOW
            // Anything else that failed at the socket is the server being unreachable rather than
            // the phone being offline -- a distinction the user can act on differently.
            is IOException -> SERVER
            else -> fallback
        }

    private fun matchMessage(raw: String?): String? {
        val text = raw?.lowercase() ?: return null
        return when {
            text.contains("unable to resolve host") ||
                text.contains("no address associated") -> OFFLINE

            text.contains("timeout") || text.contains("timed out") -> SLOW

            // GoTrue's error_code for a wrong password, and the human wording beside it. Matched on
            // both because the code is the stable half and the sentence is the readable one.
            text.contains("invalid_credentials") ||
                text.contains("invalid login credentials") -> WRONG_LOGIN

            // Deliberately NOT matching bare "401"/"403": those are three digits that appear in
            // uuids, paths and row counts, and a false positive here would tell someone their
            // session expired when it had not.
            text.contains("jwt") ||
                text.contains("unauthorized") ||
                text.contains("not authorized") -> SESSION_EXPIRED

            text.contains("23505") || text.contains("presensi_unik_harian") -> ALREADY_RECORDED

            text.contains("failed to connect") ||
                text.contains("connection reset") ||
                text.contains("network is unreachable") ||
                text.contains("connection abort") -> SERVER

            else -> null
        }
    }
}
