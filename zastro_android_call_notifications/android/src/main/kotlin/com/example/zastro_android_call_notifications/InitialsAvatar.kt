package com.example.zastro_android_call_notifications

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log

/**
 * Draws a caller's initials in a coloured circle, for calls that arrive without
 * a photo url — which is common when a backend only sends a name.
 *
 * The colour is derived from the name, so the same caller always gets the same
 * one and two callers on screen back to back look distinct.
 */
object InitialsAvatar {

    private const val TAG = "ZastroInitials"

    /** Muted, dark enough for white text at any size. */
    private val PALETTE = intArrayOf(
        0xFF1E88E5.toInt(), // blue
        0xFF00897B.toInt(), // teal
        0xFF43A047.toInt(), // green
        0xFFF4511E.toInt(), // deep orange
        0xFF8E24AA.toInt(), // purple
        0xFF3949AB.toInt(), // indigo
        0xFF00838F.toInt(), // cyan
        0xFF6D4C41.toInt()  // brown
    )

    /** Returns null when there is nothing usable to draw, so callers can fall back. */
    fun create(name: String?, size: Int): Bitmap? {
        val initials = initialsOf(name) ?: return null
        val target = size.coerceAtLeast(1)

        return try {
            val bitmap = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val radius = target / 2f

            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorFor(name.orEmpty())
            }
            canvas.drawCircle(radius, radius, radius, background)

            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = target * 0.38f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            // Centre on the glyph bounds, not the font metrics, so one and two
            // letter initials both sit optically centred.
            val bounds = Rect()
            text.getTextBounds(initials, 0, initials.length, bounds)
            canvas.drawText(initials, radius, radius + bounds.height() / 2f, text)

            bitmap
        } catch (e: Exception) {
            Log.w(TAG, "Could not draw initials avatar: ${e.message}")
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Out of memory drawing initials avatar")
            null
        }
    }

    /** Up to two initials, e.g. "Ashish Jaga" -> "AJ". */
    private fun initialsOf(name: String?): String? {
        val words = name?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val letters = buildString {
            // Only letters and digits, so "+91..." or "-" never becomes an initial.
            words.take(2).forEach { word ->
                word.firstOrNull { it.isLetterOrDigit() }?.let { append(it.uppercaseChar()) }
            }
        }
        return letters.takeIf { it.isNotEmpty() }
    }

    private fun colorFor(name: String): Int {
        val hash = name.fold(0) { acc, c -> acc * 31 + c.code }
        return PALETTE[Math.floorMod(hash, PALETTE.size)]
    }
}
