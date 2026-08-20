package com.example.zastro_android_call_notifications

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import io.flutter.FlutterInjector
import java.io.File

/**
 * Turns the `ringtone` value a host app (or an FCM payload) sends into
 * something [MediaPlayer] can actually play.
 *
 * Supported forms, checked in this order:
 *
 * | Value                              | Resolved as                          |
 * |------------------------------------|--------------------------------------|
 * | `null`, empty, `"default"`         | system default ringtone              |
 * | `assets/sounds/incoming.mp3`       | Flutter asset bundled with the app   |
 * | `raw/my_tone` or `my_tone`         | `res/raw/my_tone` in the host app    |
 * | `content://…`, `android.resource://…`, `file://…`, `http(s)://…` | that URI |
 * | `/storage/emulated/0/tone.mp3`     | absolute file path                   |
 *
 * Anything that fails to resolve falls back to the system default ringtone —
 * a silent incoming call is never an acceptable outcome.
 */
object RingtoneResolver {

    private const val TAG = "ZastroRingtone"
    private const val PREFS = "zastro_prefs"
    private const val KEY_DEFAULT_RINGTONE = "default_ringtone"

    const val DEFAULT = "default"

    /** Remembers a ringtone to use whenever a call arrives without an explicit one. */
    fun storeDefault(context: Context, spec: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply { if (spec.isNullOrBlank()) remove(KEY_DEFAULT_RINGTONE) else putString(KEY_DEFAULT_RINGTONE, spec) }
            .apply()
    }

    fun readDefault(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_RINGTONE, null)

    /**
     * Picks the ringtone for this call: the one passed with the call if there is
     * one, otherwise the app's stored default, otherwise the system ringtone.
     */
    fun effectiveSpec(context: Context, callSpec: String?): String? =
        callSpec?.takeIf { it.isNotBlank() } ?: readDefault(context)

    /**
     * Points [player] at [spec]. Returns false when the caller should fall back
     * to the system default ringtone.
     */
    fun applyTo(player: MediaPlayer, context: Context, spec: String?): Boolean {
        val value = spec?.trim().orEmpty()
        if (value.isEmpty() || value.equals(DEFAULT, ignoreCase = true)) return false

        return try {
            val asset = openFlutterAsset(context, value)
            if (asset != null) {
                asset.use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                Log.d(TAG, "Ringtone resolved as Flutter asset: $value")
                return true
            }

            val uri = resolveUri(context, value) ?: cachedFlutterAsset(context, value)
            if (uri == null) {
                Log.w(TAG, "Ringtone '$value' could not be resolved, using system default")
                return false
            }
            player.setDataSource(context, uri)
            Log.d(TAG, "Ringtone resolved as uri: $uri")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Ringtone '$value' failed to load, using system default: ${e.message}")
            false
        }
    }

    /**
     * Flutter assets live inside the APK's `assets/flutter_assets/`.
     *
     * [AssetManager.openFd] only works for assets the build left uncompressed.
     * That covers the usual audio formats (mp3, wav, ogg, m4a, aac are all on
     * aapt's no-compress list), but not every one — so a compressed asset is
     * unpacked into the cache once and played from there instead of silently
     * degrading to the system ringtone.
     */
    private fun openFlutterAsset(context: Context, value: String): AssetFileDescriptor? {
        if (!looksLikeFlutterAsset(value)) return null
        val key = lookupKeyForAsset(value) ?: return null
        return try {
            context.assets.openFd(key)
        } catch (e: Exception) {
            Log.d(TAG, "Asset '$value' is compressed, unpacking to cache: ${e.message}")
            null
        }
    }

    /** Uri of [value] unpacked into the cache, or null if it is not an asset. */
    private fun cachedFlutterAsset(context: Context, value: String): Uri? {
        if (!looksLikeFlutterAsset(value)) return null
        val key = lookupKeyForAsset(value) ?: return null
        return try {
            val target = File(context.cacheDir, "zastro_ringtone_" + value.replace('/', '_'))
            if (!target.exists() || target.length() == 0L) {
                context.assets.open(key).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.length() > 0L) Uri.fromFile(target) else null
        } catch (e: Exception) {
            Log.d(TAG, "Not a Flutter asset ('$value'): ${e.message}")
            null
        }
    }

    /**
     * [FlutterInjector] is the supported way to map an asset path to its key.
     * It needs the loader to be initialised, which is not guaranteed in a
     * service started straight from a broadcast, so the well-known
     * `flutter_assets/` prefix is used as a fallback.
     */
    private fun lookupKeyForAsset(value: String): String? = try {
        FlutterInjector.instance().flutterLoader().getLookupKeyForAsset(value)
    } catch (t: Throwable) {
        Log.d(TAG, "FlutterLoader unavailable, using default asset path: ${t.message}")
        "flutter_assets/$value"
    }

    private fun looksLikeFlutterAsset(value: String): Boolean =
        !value.startsWith("/") &&
            !value.contains("://") &&
            !value.startsWith("raw/") &&
            value.contains('.')

    private fun resolveUri(context: Context, value: String): Uri? {
        if (value.contains("://")) return Uri.parse(value)

        if (value.startsWith("/")) {
            val file = File(value)
            return if (file.exists()) Uri.fromFile(file) else null
        }

        val rawName = value.removePrefix("raw/").substringBeforeLast('.')
        val resId = context.resources.getIdentifier(rawName, "raw", context.packageName)
        return if (resId != 0) {
            Uri.parse("android.resource://${context.packageName}/$resId")
        } else {
            null
        }
    }
}
