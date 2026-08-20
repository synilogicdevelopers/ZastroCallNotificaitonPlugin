package com.example.zastro_android_call_notifications

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the caller photo once and hands the same bitmap to both the call
 * notification and the full screen call screen.
 *
 * The previous inline loader used `BitmapFactory.decodeStream()` straight on a
 * socket, which returns null for slow or chunked responses, and it did not
 * follow redirects — the two usual reasons a caller photo silently never
 * appears. This buffers the body first, follows redirects (including across
 * http/https, which `HttpURLConnection` refuses to do on its own) and
 * downsamples so a large photo cannot blow up memory.
 *
 * Every failure is logged and returns null; nothing here ever throws at the
 * caller, so a missing photo can never take the call down.
 */
object CallerPhotoLoader {

    private const val TAG = "ZastroCallerPhoto"

    /** Comfortably above the 136dp avatar on any density. */
    private const val MAX_DIMENSION = 512

    /** Refuse absurd payloads rather than trying to decode them. */
    private const val MAX_BYTES = 8 * 1024 * 1024

    private const val MAX_REDIRECTS = 5
    private const val TIMEOUT_MS = 15_000

    private val mutex = Mutex()
    private var cachedUrl: String? = null
    private var cachedBitmap: Bitmap? = null

    /**
     * Returns the photo at [url], downloading it at most once.
     *
     * The mutex means the screen and the notification never fetch the same
     * photo twice: whichever asks second simply waits and gets the cached copy.
     */
    suspend fun load(url: String?): Bitmap? {
        val source = url?.trim().orEmpty()
        if (source.isEmpty()) return null

        return mutex.withLock {
            cachedBitmap?.let { cached ->
                if (cachedUrl == source && !cached.isRecycled) return@withLock cached
            }
            val bitmap = withContext(Dispatchers.IO) { downloadAny(source) }
            if (bitmap != null) {
                cachedUrl = source
                cachedBitmap = bitmap
            }
            bitmap
        }
    }

    /**
     * Tries each acceptable form of [url] until one works.
     *
     * Android blocks cleartext HTTP by default from targetSdk 28 onwards, so an
     * `http://` url in a push payload always fails natively even though the same
     * url loads fine in Flutter (Dart has its own network stack and is not
     * covered by the platform's cleartext policy). Almost every host that still
     * hands out `http://` links also serves them over TLS, so the secure form is
     * tried first and the original is kept only as a fallback for apps that do
     * permit cleartext.
     */
    private fun downloadAny(url: String): Bitmap? {
        for (candidate in candidatesFor(url)) {
            val bitmap = download(candidate)
            if (bitmap != null) {
                if (candidate != url) {
                    Log.d(TAG, "Loaded over https; the payload url was cleartext http")
                }
                return bitmap
            }
        }
        return null
    }

    private fun candidatesFor(url: String): List<String> =
        if (url.startsWith("http://", ignoreCase = true)) {
            listOf("https://" + url.substring("http://".length), url)
        } else {
            listOf(url)
        }

    private fun download(url: String): Bitmap? {
        var target = url
        repeat(MAX_REDIRECTS) {
            val connection = try {
                (URL(target).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    doInput = true
                    // Handled manually below so http <-> https hops still work.
                    instanceFollowRedirects = false
                    setRequestProperty("Accept", "image/*")
                    setRequestProperty("User-Agent", "ZastroCallNotifications")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cannot open connection to $target: ${e.message}")
                return null
            }

            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) {
                        Log.w(TAG, "Redirect from $target had no Location header")
                        return null
                    }
                    // Resolves relative locations against the current url.
                    target = URL(URL(target), location).toString()
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Caller photo request failed: HTTP $code for $target")
                    return null
                }
                val bytes = connection.inputStream.use { readCapped(it) }
                if (bytes == null) {
                    Log.w(TAG, "Caller photo at $target is larger than ${MAX_BYTES}B")
                    return null
                }
                if (bytes.isEmpty()) {
                    Log.w(TAG, "Caller photo at $target was empty")
                    return null
                }
                val bitmap = decodeSampled(bytes)
                if (bitmap == null) {
                    Log.w(TAG, "Caller photo at $target is not a decodable image")
                } else {
                    Log.d(TAG, "Caller photo loaded: ${bitmap.width}x${bitmap.height}")
                }
                return bitmap
            } catch (e: Exception) {
                Log.w(TAG, "Caller photo download failed for $target: ${e.message}")
                return null
            } finally {
                try {
                    connection.disconnect()
                } catch (e: Exception) {
                    // Nothing useful to do; the request is already over.
                }
            }
        }
        Log.w(TAG, "Caller photo exceeded $MAX_REDIRECTS redirects")
        return null
    }

    /** Reads the whole body, or null if it goes past [MAX_BYTES]. */
    private fun readCapped(input: java.io.InputStream): ByteArray? {
        val buffer = ByteArray(16 * 1024)
        val output = ByteArrayOutputStream()
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            if (output.size() + read > MAX_BYTES) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /** Decodes at no more than [MAX_DIMENSION] so a huge photo cannot OOM. */
    private fun decodeSampled(bytes: ByteArray): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        var sampleSize = 1
        var width = bounds.outWidth
        var height = bounds.outHeight
        while (width / sampleSize > MAX_DIMENSION || height / sampleSize > MAX_DIMENSION) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory decoding caller photo")
        null
    } catch (e: Exception) {
        Log.w(TAG, "Could not decode caller photo: ${e.message}")
        null
    }

    /** Centre-crops [source] to a square and masks it into a circle. */
    fun toCircular(source: Bitmap, targetSize: Int): Bitmap? = try {
        val size = targetSize.coerceAtLeast(1)
        val cropSize = minOf(source.width, source.height)
        val squared = Bitmap.createBitmap(
            source,
            (source.width - cropSize) / 2,
            (source.height - cropSize) / 2,
            cropSize,
            cropSize
        )
        val scaled = Bitmap.createScaledBitmap(squared, size, size, true)

        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        output
    } catch (e: Exception) {
        Log.w(TAG, "Could not round caller photo: ${e.message}")
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Out of memory rounding caller photo")
        null
    }
}
