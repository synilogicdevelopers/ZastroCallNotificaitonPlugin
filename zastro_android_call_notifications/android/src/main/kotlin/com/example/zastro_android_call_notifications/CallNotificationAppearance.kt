package com.example.zastro_android_call_notifications

import android.content.Context
import android.graphics.Color
import android.util.Log

/**
 * Look of the call notification, configured once by the host app and persisted.
 *
 * It is stored rather than passed per call because a call raised from a
 * data-only FCM payload never goes through Dart at all — the broadcast path
 * builds the notification with no app code involved, so anything passed per
 * call would be missing exactly when the app is killed.
 */
object CallNotificationAppearance {

    private const val TAG = "ZastroAppearance"
    private const val PREFS = "zastro_prefs"
    private const val KEY_SMALL_ICON = "notification_small_icon"
    private const val KEY_ACCENT_COLOR = "notification_accent_color"
    private const val KEY_COLORIZED = "notification_colorized"

    /**
     * Conventional names for an app's notification icon, tried in order when the
     * host app did not name one explicitly.
     */
    private val FALLBACK_ICON_NAMES = listOf("ic_notification", "ic_stat_notification", "ic_stat_name")

    fun store(context: Context, smallIcon: String?, accentColor: String?, colorized: Boolean?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (smallIcon.isNullOrBlank()) remove(KEY_SMALL_ICON) else putString(KEY_SMALL_ICON, smallIcon)
            if (accentColor.isNullOrBlank()) remove(KEY_ACCENT_COLOR) else putString(KEY_ACCENT_COLOR, accentColor)
            if (colorized == null) remove(KEY_COLORIZED) else putBoolean(KEY_COLORIZED, colorized)
        }.apply()
    }

    /**
     * Resource id for the notification's small icon.
     *
     * A small icon is drawn from its alpha channel only, so a full colour
     * launcher icon would render as a solid white blob. That is why this looks
     * for a proper monochrome notification icon in the host app and falls back
     * to the plugin's own icon instead of ever reaching for the launcher icon.
     */
    fun smallIconRes(context: Context): Int {
        val configured = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SMALL_ICON, null)

        val candidates = buildList {
            if (!configured.isNullOrBlank()) add(configured)
            addAll(FALLBACK_ICON_NAMES)
        }

        for (name in candidates) {
            val id = resolveDrawable(context, name)
            if (id != 0) {
                Log.d(TAG, "Notification small icon: $name")
                return id
            }
        }
        Log.d(TAG, "Notification small icon: falling back to plugin default")
        return R.drawable.incoming_call_arrow
    }

    private fun resolveDrawable(context: Context, name: String): Int = try {
        val clean = name.substringAfterLast('/')
        var id = context.resources.getIdentifier(clean, "drawable", context.packageName)
        if (id == 0) id = context.resources.getIdentifier(clean, "mipmap", context.packageName)
        id
    } catch (e: Exception) {
        Log.w(TAG, "Could not resolve icon '$name': ${e.message}")
        0
    }

    /** Accent colour, or null to leave the system default. */
    fun accentColor(context: Context): Int? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACCENT_COLOR, null)
        if (raw.isNullOrBlank()) return null
        return try {
            Color.parseColor(if (raw.startsWith("#")) raw else "#$raw")
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring unparseable accent colour '$raw': ${e.message}")
            null
        }
    }

    /**
     * Whether to paint the whole notification in the accent colour.
     *
     * Off by default: a light brand colour makes a colorized notification hard
     * to read, so the host app has to opt in deliberately.
     */
    fun colorized(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_COLORIZED, false)
}
