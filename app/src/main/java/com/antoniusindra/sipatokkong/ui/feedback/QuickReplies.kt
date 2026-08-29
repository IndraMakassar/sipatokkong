package com.antoniusindra.sipatokkong.ui.feedback

/**
 * Canned replies a volunteer can send with one tap.
 *
 * A behavior note is closed only by a volunteer's reply — the `on_balasan_insert` trigger is what
 * sets `status = 'answered'` and notifies the parent. So a positive note with nothing to ask has no
 * way to close itself, and sits on the parent's home screen under an orange "Menunggu" chip
 * indefinitely. For a parent who took the trouble to report good news, that reads as being ignored,
 * and it discourages the very reporting this app depends on.
 *
 * Typing a sentence for every "everything went fine" note is a cost a volunteer with a caseload
 * will not keep paying, so these make acknowledging one cost a single tap. They are sent as real
 * replies, so the parent gets something to read rather than a chip quietly changing colour.
 *
 * Written in short, plain Bahasa Indonesia: the parents reading them have low literacy.
 */
val QUICK_REPLIES = listOf(
    "Terima kasih, kabar baiknya kami catat 🙏",
    "Bagus sekali, lanjutkan ya",
    "Baik, nanti kami tindak lanjuti saat kunjungan",
)
