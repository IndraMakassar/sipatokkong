package com.antoniusindra.sipatokkong

import com.antoniusindra.sipatokkong.core.push.AvatarNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The letter and the colour of the round avatar on a push notification.
 *
 * A name reaches this code straight from `profiles.full_name`, typed by an admin, so it is arbitrary
 * human text rather than anything validated. Three ways that bites, each with a test below:
 * `firstOrNull()` on an emoji-prefixed name returns half a surrogate pair and draws a tofu box;
 * `"ß".uppercase()` is two characters, not one; and a name may contain no Latin letter at all, or no
 * letter at all.
 *
 * The colour rule is tested against exact values on purpose. `String.hashCode` is specified by the
 * language rather than left to the implementation, so "stable" here means stable across devices and
 * ART versions — not merely stable within one run.
 *
 * **The colour carries no meaning** — not role, not priority, not which child. It exists so two
 * senders in the same tray look different. Nothing should ever be inferred from it.
 *
 * Not covered, and not coverable here: the drawing itself. `Bitmap`, `Canvas` and `Paint` need
 * Android, and this suite has no Robolectric — the same wall `MediaDownloadRulesTest` describes.
 */
class AvatarNamingTest {

    // ---- which letter ----

    @Test
    fun `a normal name gives its first letter, uppercased`() {
        assertEquals("S", AvatarNaming.initialOf("Sari Wulandari"))
    }

    @Test
    fun `a lowercase name is uppercased`() {
        assertEquals("D", AvatarNaming.initialOf("dewi"))
    }

    @Test
    fun `leading whitespace is skipped`() {
        assertEquals("A", AvatarNaming.initialOf("   Andi"))
    }

    @Test
    fun `digits and punctuation are skipped to the first real letter`() {
        assertEquals("B", AvatarNaming.initialOf("2. Bu Ratna"))
        assertEquals("K", AvatarNaming.initialOf("-- Kak Dewi"))
    }

    @Test
    fun `a name with no letter at all falls back`() {
        assertEquals(AvatarNaming.FALLBACK_INITIAL, AvatarNaming.initialOf("123"))
        assertEquals(AvatarNaming.FALLBACK_INITIAL, AvatarNaming.initialOf("!!!"))
    }

    @Test
    fun `null and blank fall back`() {
        assertEquals(AvatarNaming.FALLBACK_INITIAL, AvatarNaming.initialOf(null))
        assertEquals(AvatarNaming.FALLBACK_INITIAL, AvatarNaming.initialOf("   "))
    }

    @Test
    fun `an Arabic name gives an Arabic letter`() {
        // Character.isLetter is true across scripts, so nothing special is needed for this to work.
        assertEquals("ع", AvatarNaming.initialOf("عائشة"))
    }

    @Test
    fun `a Han name gives its first character`() {
        assertEquals("张", AvatarNaming.initialOf("张伟"))
    }

    @Test
    fun `an eszett yields one character, not two`() {
        // "ß".uppercase() is "SS". Without the take(1) the circle would hold two letters at a size
        // chosen for one.
        assertEquals(1, AvatarNaming.initialOf("ßeta").length)
    }

    @Test
    fun `an emoji prefix is stepped over, not split`() {
        // The trap: firstOrNull() here returns a lone high surrogate, which renders as a tofu box.
        // Walking code points skips the emoji -- it is not a letter -- and lands on the S.
        assertEquals("S", AvatarNaming.initialOf("😀 Sari"))
    }

    // ---- which colour ----

    @Test
    fun `the colour is stable, and ignores case and surrounding space`() {
        val base = AvatarNaming.colorFor("Sari Wulandari")
        assertEquals(base, AvatarNaming.colorFor("Sari Wulandari"))
        assertEquals(base, AvatarNaming.colorFor("  sari wulandari  "))
    }

    @Test
    fun `every colour is an opaque palette member`() {
        val names = listOf(
            "Sari Wulandari", "Kak Dewi", "Bu Ratna", "Andi", "عائشة", "张伟", "", "   ", null,
        )
        for (name in names) {
            val c = AvatarNaming.colorFor(name)
            assertTrue("$name produced a colour outside the palette", c in AvatarNaming.PALETTE)
            // Fully opaque: a translucent disc would let the tray background through and wreck the
            // contrast the palette was chosen for.
            assertEquals("$name is not opaque", 0xFF, (c ushr 24) and 0xFF)
        }
    }
}
