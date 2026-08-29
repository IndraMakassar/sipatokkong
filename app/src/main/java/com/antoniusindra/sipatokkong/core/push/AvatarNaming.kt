package com.antoniusindra.sipatokkong.core.push

/**
 * The letter and the colour of the round avatar on a push notification.
 *
 * There is no photo anywhere in this app — `profiles` has no image column and no storage bucket
 * holds one — so a sender's face has to be drawn from their name. The in-app child cards already
 * work this way (`Child.kt`, "first letter of the name, for the avatar circle").
 *
 * Kept free of Android types so the rules can be unit-tested: `Bitmap`, `Canvas` and `Paint` are
 * unreachable in this suite, which is the same split `MediaNaming` makes for downloads. What is
 * worth getting right is which letter and which colour, and those are here.
 */
object AvatarNaming {

    /** The character drawn in the circle, or "?" when the name yields nothing usable. */
    const val FALLBACK_INITIAL = "?"

    /**
     * Four colours, chosen so white text on any of them clears 4.5:1.
     *
     * Deterministic per name rather than one fixed brand colour, and that is the whole point: a
     * volunteer assigned to four children receives notes from four different parents, and four
     * identical teal discs have to be read letter by letter, while four different-coloured ones are
     * told apart at a glance. That is why messaging apps do it.
     *
     * **The colour means nothing.** Not a role, not a priority, not a child. It exists only to make
     * two senders look different from each other, and nobody should ever read more into it.
     *
     * `0xFF9A5A00` is the one value not lifted straight from the palette: literal BrandOrange
     * `#F8991D` with white text is 2.1:1 and unreadable, and varying the *text* colour per entry
     * would push a legibility rule down into the drawing code. Ints rather than colour resources
     * because resolving one needs a Context, which would drag Android into this file.
     */
    val PALETTE = intArrayOf(
        0xFF2E6E7E.toInt(),  // BrandTeal
        0xFF1E4C57.toInt(),  // BrandTealDeep
        0xFF9A5A00.toInt(),  // BrandOrange, darkened until white text is legible on it
        0xFF4F6D5A.toInt(),  // muted brand-adjacent green
    )

    /**
     * The first letter of a name, uppercased.
     *
     * Walks **code points**, not chars. `name.firstOrNull()` on "😀 Sari" returns half a surrogate
     * pair, which renders as a tofu box; iterating code points steps over the emoji — it is not a
     * letter — and lands on the S.
     *
     * `take(1)` after uppercasing is not belt-and-braces either: `"ß".uppercase()` is `"SS"`, two
     * characters, and drawing both in a circle sized for one looks like a mistake.
     *
     * Non-Latin scripts work without special handling, because `Character.isLetter` is true for
     * them: "عائشة" gives "ع" and "张伟" gives "张".
     */
    fun initialOf(name: String?): String {
        val trimmed = name?.trim().orEmpty()
        var i = 0
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            if (Character.isLetter(cp)) {
                return String(Character.toChars(cp)).uppercase().take(1)
            }
            i += Character.charCount(cp)
        }
        return FALLBACK_INITIAL
    }

    /**
     * A stable colour for a name.
     *
     * `String.hashCode` is specified by the language rather than left to the implementation, so the
     * same name yields the same colour on every device, every process and every ART version — which
     * is what makes this testable against exact expected values instead of merely "consistent
     * within one run".
     *
     * Case- and whitespace-insensitive, so " sari " and "Sari" are one person.
     */
    fun colorFor(name: String?): Int {
        val key = name?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return PALETTE[0]
        return PALETTE[Math.floorMod(key.hashCode(), PALETTE.size)]
    }
}
