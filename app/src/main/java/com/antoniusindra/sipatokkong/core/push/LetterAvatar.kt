package com.antoniusindra.sipatokkong.core.push

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.graphics.createBitmap

/**
 * Draws the round sender avatar for a push notification.
 *
 * The Android half of [AvatarNaming] — which letter and which colour is decided there and tested;
 * this only puts it on a canvas.
 */
object LetterAvatar {

    /**
     * A circle in the sender's colour with their initial centred in it.
     *
     * Sized from the system's own large-icon dimension so the bitmap is neither upscaled into
     * blurriness nor larger than anything will display. One bitmap per push, drawn in well under a
     * millisecond and handed straight to the system — a cache here would be state to invalidate for
     * no measurable gain.
     */
    fun bitmap(context: Context, name: String?): Bitmap {
        val size = context.resources
            .getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
            .coerceAtLeast(128)

        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)

        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AvatarNaming.colorFor(name)
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, disc)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = size * 0.44f
        }
        // Centring on size/2f alone sits the glyph too low: that is the vertical centre of the box,
        // not of the letter. Offsetting by half the ascent+descent puts the letter's own middle on
        // the circle's middle.
        val fm = text.fontMetrics
        val baseline = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(AvatarNaming.initialOf(name), size / 2f, baseline, text)

        return bmp
    }
}
