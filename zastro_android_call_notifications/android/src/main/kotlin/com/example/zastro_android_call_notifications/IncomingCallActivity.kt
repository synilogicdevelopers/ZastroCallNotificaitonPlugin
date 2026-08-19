package com.example.zastro_android_call_notifications

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * WhatsApp-style full-screen incoming call screen.
 *
 * It is launched by the `setFullScreenIntent(...)` of the call notification
 * posted by [CallNotificationService], which is the only sanctioned way for an
 * app to show UI from the background on Android 10+. It therefore works while
 * the app is in the background, while it is killed/terminated and while the
 * device is locked or the screen is off.
 *
 * The screen deliberately owns **no** business logic: Accept / Decline / body
 * tap simply fire the very same [PendingIntent]s that the notification's own
 * buttons carry, so the existing Dart flow (`key` extra -> `HomeController.
 * getNotificationData()` -> `processNotificationAction`) stays byte-for-byte
 * identical. Nothing downstream can tell whether the user tapped the
 * notification or this screen.
 */
class IncomingCallActivity : Activity() {

    companion object {
        private const val TAG = "ZastroIncomingCallUi"

        /** Sent by [CallNotificationService] when the call stops ringing. */
        const val ACTION_DISMISS_CALL_UI =
            "com.example.zastro_android_call_notifications.DISMISS_INCOMING_CALL_UI"

        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_IMAGE = "caller_image"
        const val EXTRA_TYPE = "type"

        private const val EXTRA_ANSWER_INTENT = "zastro_answer_pending_intent"
        private const val EXTRA_DECLINE_INTENT = "zastro_decline_pending_intent"
        private const val EXTRA_CONTENT_INTENT = "zastro_content_pending_intent"

        /**
         * Builds the intent used as the notification's full-screen intent.
         *
         * The three [PendingIntent]s are the exact instances attached to the
         * notification, so this screen can never drift from the notification.
         */
        fun getIntent(
            context: Context,
            notificationId: Int,
            callerName: String,
            callerImage: String,
            type: String,
            answerPendingIntent: PendingIntent?,
            declinePendingIntent: PendingIntent?,
            contentPendingIntent: PendingIntent?
        ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALLER_IMAGE, callerImage)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_ANSWER_INTENT, answerPendingIntent)
            putExtra(EXTRA_DECLINE_INTENT, declinePendingIntent)
            putExtra(EXTRA_CONTENT_INTENT, contentPendingIntent)
        }

        /**
         * Closes the screen if it happens to be showing. Safe to call always.
         *
         * [notificationId] scopes the dismissal to one call, so a teardown that
         * overlaps with the arrival of the next call can never close the newer
         * call's screen. Pass -1 to dismiss whatever is showing.
         */
        fun dismiss(context: Context, notificationId: Int) {
            try {
                val intent = Intent(ACTION_DISMISS_CALL_UI)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                context.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not broadcast dismiss: ${e.message}")
            }
        }
    }

    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var notificationId: Int = -1
    private var answerPendingIntent: PendingIntent? = null
    private var declinePendingIntent: PendingIntent? = null
    private var contentPendingIntent: PendingIntent? = null

    /** Guards against a double tap firing two pending intents. */
    private var actionTaken = false
    private var dismissReceiverRegistered = false

    /**
     * The notification is posted before the system fires the full-screen
     * intent, but the safety net in [onResume] must not race that ordering on
     * slow devices — so it only ever runs when coming *back* to the screen.
     */
    private var hasResumedOnce = false

    /** In-flight caller photo download, cancelled whenever the screen rebinds. */
    private var photoJob: Job? = null

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val dismissedId = intent?.getIntExtra(EXTRA_NOTIFICATION_ID, -1) ?: -1
            if (dismissedId != -1 && notificationId != -1 && dismissedId != notificationId) {
                Log.d(TAG, "Dismiss for call $dismissedId ignored, showing $notificationId")
                return
            }
            Log.d(TAG, "Call stopped ringing, closing full-screen UI")
            closeScreen()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContentView(R.layout.zastro_activity_incoming_call)
        applyImmersiveFlags()
        registerDismissReceiver()
        bind(intent)
    }

    /**
     * The activity is `singleTask`, so a re-posted notification for the same or
     * a newer call is delivered here instead of stacking a second screen.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        actionTaken = false
        bind(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!hasResumedOnce) {
            hasResumedOnce = true
            return
        }
        // Safety net: if the ringing service died without broadcasting (process
        // killed, notification swiped by the system), do not leave a dead
        // full-screen call on the user's device.
        if (!actionTaken && notificationId != -1 && !isCallNotificationActive()) {
            Log.d(TAG, "Call notification no longer active, closing full-screen UI")
            closeScreen()
        }
    }

    override fun onDestroy() {
        unregisterDismissReceiver()
        uiScope.cancel()
        super.onDestroy()
    }

    /**
     * Matches WhatsApp/dialer behaviour closely enough to stay predictable:
     * back leaves the screen but keeps the call ringing, so the user can still
     * accept or decline from the notification itself.
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "MissingSuperCall")
    override fun onBackPressed() {
        closeScreen()
    }

    // ---------------------------------------------------------------- binding

    private fun bind(intent: Intent) {
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        answerPendingIntent = intent.pendingIntentExtra(EXTRA_ANSWER_INTENT)
        declinePendingIntent = intent.pendingIntentExtra(EXTRA_DECLINE_INTENT)
        contentPendingIntent = intent.pendingIntentExtra(EXTRA_CONTENT_INTENT)

        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.zastro_unknown_caller)
        val callerImage = intent.getStringExtra(EXTRA_CALLER_IMAGE).orEmpty()

        findViewById<TextView>(R.id.zastro_caller_name).text = callerName
        findViewById<TextView>(R.id.zastro_call_type_label).setText(labelFor(type))
        findViewById<ImageView>(R.id.zastro_call_type_icon).setImageResource(iconFor(type))
        findViewById<TextView>(R.id.zastro_call_subtitle).text = appLabel()

        findViewById<ImageButton>(R.id.zastro_btn_answer).setOnClickListener { onAnswer() }
        findViewById<ImageButton>(R.id.zastro_btn_decline).setOnClickListener { onDecline() }
        findViewById<View>(R.id.zastro_call_root).setOnClickListener { /* swallow taps */ }

        loadCallerPhoto(callerImage)
    }

    private fun labelFor(type: String): Int = when (type.trim().lowercase()) {
        "call" -> R.string.zastro_incoming_voice_call
        "video" -> R.string.zastro_incoming_video_call
        "chat" -> R.string.zastro_incoming_chat
        else -> R.string.zastro_incoming_call
    }

    private fun iconFor(type: String): Int = when (type.trim().lowercase()) {
        "video" -> R.drawable.zastro_ic_call_type_video
        "chat" -> R.drawable.zastro_ic_call_type_chat
        else -> R.drawable.zastro_ic_call_type_voice
    }

    private fun appLabel(): String = try {
        packageManager.getApplicationLabel(applicationInfo).toString()
    } catch (e: Exception) {
        ""
    }

    private fun loadCallerPhoto(url: String) {
        // A rebind means a different caller: drop any download still in flight
        // so it cannot land on top of the new one, and go back to the
        // placeholder until the new photo arrives.
        photoJob?.cancel()
        val avatar = findViewById<ImageView>(R.id.zastro_caller_avatar)
        val placeholderPadding =
            resources.getDimensionPixelSize(R.dimen.zastro_call_avatar_padding)
        avatar.setPadding(
            placeholderPadding, placeholderPadding, placeholderPadding, placeholderPadding
        )
        avatar.setImageResource(R.drawable.zastro_ic_avatar_placeholder)

        if (url.isBlank()) return
        photoJob = uiScope.launch {
            val bitmap = withContext(Dispatchers.IO) { downloadBitmap(url) } ?: return@launch
            if (!isActive || isFinishing) return@launch
            val circular = toCircularBitmap(bitmap, avatar.width.takeIf { it > 0 } ?: bitmap.width)
            avatar.setPadding(0, 0, 0, 0)
            avatar.setImageDrawable(BitmapDrawable(resources, circular))
        }
    }

    // ---------------------------------------------------------------- actions

    private fun onAnswer() {
        if (actionTaken) return
        actionTaken = true
        unregisterDismissReceiver()
        stopRinging()
        // Answering has to bring the Flutter UI up, which cannot happen behind a
        // locked keyguard — ask the system to dismiss it first.
        dismissKeyguardThen {
            fire(answerPendingIntent ?: contentPendingIntent)
            finishAndRemoveTaskCompat()
        }
    }

    private fun onDecline() {
        if (actionTaken) return
        actionTaken = true
        unregisterDismissReceiver()
        stopRinging()
        // Declining intentionally does NOT force an unlock: it must behave
        // exactly like the notification's own Decline button.
        fire(declinePendingIntent)
        finishAndRemoveTaskCompat()
    }

    private fun fire(pendingIntent: PendingIntent?) {
        if (pendingIntent == null) {
            Log.w(TAG, "No pending intent to fire — nothing to do")
            return
        }
        try {
            pendingIntent.send()
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "Pending intent was already cancelled: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fire pending intent", e)
        }
    }

    /** Stops the ringtone/vibration immediately, before any navigation happens. */
    private fun stopRinging() {
        try {
            val stopIntent = Intent(this, CallNotificationService::class.java).apply {
                action = "ACTION_CANCEL_CALL_NOTIFICATION"
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            stopService(stopIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop call service: ${e.message}")
        }
    }

    private fun dismissKeyguardThen(action: () -> Unit) {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            keyguardManager == null ||
            !keyguardManager.isKeyguardLocked
        ) {
            action()
            return
        }
        try {
            keyguardManager.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = action()
                    override fun onDismissCancelled() = action()
                    override fun onDismissError() = action()
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "requestDismissKeyguard failed: ${e.message}")
            action()
        }
    }

    // ----------------------------------------------------------- window setup

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun applyImmersiveFlags() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    // ------------------------------------------------------------- lifecycle helpers

    private fun registerDismissReceiver() {
        if (dismissReceiverRegistered) return
        val filter = IntentFilter(ACTION_DISMISS_CALL_UI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dismissReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag", "DEPRECATION")
            registerReceiver(dismissReceiver, filter)
        }
        dismissReceiverRegistered = true
    }

    private fun unregisterDismissReceiver() {
        if (!dismissReceiverRegistered) return
        try {
            unregisterReceiver(dismissReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Dismiss receiver already unregistered: ${e.message}")
        }
        dismissReceiverRegistered = false
    }

    private fun closeScreen() {
        unregisterDismissReceiver()
        finishAndRemoveTaskCompat()
    }

    private fun finishAndRemoveTaskCompat() {
        if (isFinishing) return
        try {
            finishAndRemoveTask()
        } catch (e: Exception) {
            finish()
        }
        // No overridePendingTransition() needed — ZastroIncomingCallTheme
        // already sets windowAnimationStyle to @null.
    }

    private fun isCallNotificationActive(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.activeNotifications.any { it.id == notificationId }
        } catch (e: Exception) {
            // Never close the screen because of a permission/OEM quirk.
            true
        }
    }

    // ---------------------------------------------------------------- utils

    @Suppress("DEPRECATION", "UNCHECKED_CAST")
    private fun Intent.pendingIntentExtra(key: String): PendingIntent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, PendingIntent::class.java)
        } else {
            getParcelableExtra<Parcelable>(key) as? PendingIntent
        }

    private fun downloadBitmap(src: String): Bitmap? = try {
        val connection = URL(src).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.doInput = true
        connection.connect()
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        Log.w(TAG, "Caller photo could not be loaded: ${e.message}")
        null
    }

    /** Centre-crops [source] to a square and masks it into a circle. */
    private fun toCircularBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val size = targetSize.coerceAtLeast(1)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cropSize = minOf(source.width, source.height)
        val left = (source.width - cropSize) / 2
        val top = (source.height - cropSize) / 2
        val squared = Bitmap.createBitmap(source, left, top, cropSize, cropSize)
        val scaled = Bitmap.createScaledBitmap(squared, size, size, true)

        paint.shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        return output
    }
}
