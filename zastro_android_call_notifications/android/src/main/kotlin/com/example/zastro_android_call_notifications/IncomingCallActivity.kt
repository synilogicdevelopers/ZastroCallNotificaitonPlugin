package com.example.zastro_android_call_notifications

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
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
import java.util.Date

/**
 * WhatsApp-style full-screen incoming call screen.
 *
 * It is launched by the `setFullScreenIntent(...)` of the call notification
 * posted by [CallNotificationService], which is the only sanctioned way for an
 * app to show UI from the background on Android 10+. It therefore works while
 * the app is in the background, while it is killed/terminated and while the
 * device is locked or the screen is off.
 *
 * The screen deliberately owns **no** business logic: Accept / Decline simply
 * replay what the notification's own buttons do — broadcast the action, then
 * open the app with the same `key` extra and payload — so the existing Dart
 * flow (`key` -> `HomeController.getNotificationData()` ->
 * `processNotificationAction`) stays byte-for-byte identical. Nothing
 * downstream can tell whether the user tapped the notification or this screen.
 */
class IncomingCallActivity : Activity() {

    companion object {
        private const val TAG = "ZastroIncomingCallUi"

        /** Sent by [CallNotificationService] when the call stops ringing. */
        /** Must match the keys CallNotificationService puts on the launch intent. */
        private const val ACTION_ANSWER = "ACTION_ANSWER_CALL"
        private const val ACTION_DECLINE = "ACTION_DECLINE_CALL"

        const val ACTION_DISMISS_CALL_UI =
            "com.example.zastro_android_call_notifications.DISMISS_INCOMING_CALL_UI"

        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_IMAGE = "caller_image"
        const val EXTRA_TYPE = "type"

        /** Wall-clock millis at which the call started ringing. */
        const val EXTRA_CALL_START_TIME = "call_start_time"

        /** Raw push payload, forwarded to the app exactly as the notification does. */
        const val EXTRA_MESSAGE_DATA = "message_data_in_string"

        const val EXTRA_UNIQUE_ID = "uniqueId"
        const val EXTRA_CUSTOMER_UNI_ID = "customerUniId"


        /** Builds the intent used as the notification's full-screen intent. */
        fun getIntent(
            context: Context,
            notificationId: Int,
            callerName: String,
            callerImage: String,
            type: String,
            callStartTime: Long,
            messageDataInString: String,
            uniqueId: String,
            customerUniId: String
        ): Intent = Intent(context, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_CALLER_NAME, callerName)
            putExtra(EXTRA_CALLER_IMAGE, callerImage)
            putExtra(EXTRA_TYPE, type)
            putExtra(EXTRA_CALL_START_TIME, callStartTime)
            putExtra(EXTRA_MESSAGE_DATA, messageDataInString)
            putExtra(EXTRA_UNIQUE_ID, uniqueId)
            putExtra(EXTRA_CUSTOMER_UNI_ID, customerUniId)
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

        val type = intent.getStringExtra(EXTRA_TYPE).orEmpty()
        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.zastro_unknown_caller)
        val callerImage = intent.getStringExtra(EXTRA_CALLER_IMAGE).orEmpty()

        findViewById<TextView>(R.id.zastro_caller_name).text = callerName
        findViewById<ImageView>(R.id.zastro_call_type_icon).setImageResource(iconFor(type))

        bindAppIdentity()
        bindTypeAndTime(type, intent.getLongExtra(EXTRA_CALL_START_TIME, 0L))
        bindAppBadge()

        findViewById<ImageButton>(R.id.zastro_btn_answer).setOnClickListener { onAnswer() }
        findViewById<ImageButton>(R.id.zastro_btn_decline).setOnClickListener { onDecline() }
        findViewById<View>(R.id.zastro_call_root).setOnClickListener { /* swallow taps */ }

        loadCallerPhoto(callerImage, callerName)
    }

    /** App icon and name in the header, so the user sees which app is ringing. */
    private fun bindAppIdentity() {
        val nameView = findViewById<TextView>(R.id.zastro_app_name)
        val iconView = findViewById<ImageView>(R.id.zastro_app_icon)

        nameView.text = try {
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            Log.w(TAG, "Could not read app label: ${e.message}")
            ""
        }

        val icon = try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read app icon: ${e.message}")
            null
        }
        if (icon == null) {
            iconView.visibility = View.GONE
        } else {
            iconView.setImageDrawable(icon)
            iconView.visibility = View.VISIBLE
        }
    }

    /**
     * "Incoming voice call · 12:58 pm". The time uses the user's own 12/24 hour
     * setting, and is dropped entirely rather than risk showing a wrong one.
     */
    private fun bindTypeAndTime(type: String, startTime: Long) {
        val view = findViewById<TextView>(R.id.zastro_call_type_label)
        val label = getString(labelFor(type))

        val time = if (startTime <= 0L) {
            null
        } else {
            try {
                android.text.format.DateFormat.getTimeFormat(this).format(Date(startTime))
            } catch (e: Exception) {
                Log.w(TAG, "Could not format call time: ${e.message}")
                null
            }
        }

        view.text = if (time == null) label else getString(R.string.zastro_type_and_time, label, time)
    }

    /**
     * Host app launcher icon in the bottom-right of the caller photo, the way
     * WhatsApp badges its own calls. Stays hidden if the icon cannot be loaded.
     */
    private fun bindAppBadge() {
        val badge = findViewById<ImageView>(R.id.zastro_app_badge)
        val icon = try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load app icon for badge: ${e.message}")
            null
        }
        if (icon == null) {
            badge.visibility = View.GONE
            return
        }
        // Launcher icons are square (or adaptive), so round them to match the
        // circular badge instead of letting the corners poke out of the ring.
        val size = resources.getDimensionPixelSize(R.dimen.zastro_call_badge_size) -
            2 * resources.getDimensionPixelSize(R.dimen.zastro_call_badge_padding)
        val bitmap = drawableToBitmap(icon, size)
        val circular = bitmap?.let { CallerPhotoLoader.toCircular(it, size) }
        badge.setImageDrawable(
            if (circular != null) BitmapDrawable(resources, circular) else icon
        )
        badge.visibility = View.VISIBLE
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap? = try {
        val target = size.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(target, target, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, target, target)
        drawable.draw(canvas)
        bitmap
    } catch (e: Exception) {
        Log.w(TAG, "Could not rasterise app icon: ${e.message}")
        null
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

    private fun loadCallerPhoto(url: String, callerName: String) {
        // A rebind means a different caller: drop any download still in flight
        // so it cannot land on top of the new one.
        photoJob?.cancel()
        val avatar = findViewById<ImageView>(R.id.zastro_caller_avatar)

        // Sized from the resource, not the view: the download can finish before
        // the first layout pass, when getWidth() is still 0.
        val size = resources.getDimensionPixelSize(R.dimen.zastro_call_avatar_size)

        // Start on the best offline avatar we can draw, so the screen never
        // shows an empty circle even for the split second before a photo lands.
        showFallbackAvatar(avatar, callerName, size)

        if (url.isBlank()) {
            Log.d(TAG, "No caller photo url for this call, showing initials")
            return
        }
        photoJob = uiScope.launch {
            val bitmap = CallerPhotoLoader.load(url)
            if (bitmap == null) {
                Log.d(TAG, "Caller photo unavailable, keeping initials")
                return@launch
            }
            if (!isActive || isFinishing) return@launch
            val circular = CallerPhotoLoader.toCircular(bitmap, size) ?: return@launch
            avatar.setPadding(0, 0, 0, 0)
            avatar.setImageDrawable(BitmapDrawable(resources, circular))
        }
    }

    /**
     * Initials when the caller has a usable name, otherwise the generic person
     * glyph. Backends often send a name but no photo url.
     */
    private fun showFallbackAvatar(avatar: ImageView, callerName: String, size: Int) {
        val initials = InitialsAvatar.create(callerName, size)
        if (initials != null) {
            avatar.setPadding(0, 0, 0, 0)
            avatar.setImageDrawable(BitmapDrawable(resources, initials))
            return
        }
        val inset = resources.getDimensionPixelSize(R.dimen.zastro_call_avatar_padding)
        avatar.setPadding(inset, inset, inset, inset)
        avatar.setImageResource(R.drawable.zastro_ic_avatar_placeholder)
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
            handOff(ACTION_ANSWER, "answer")
            closeCallUi()
        }
    }

    private fun onDecline() {
        if (actionTaken) return
        actionTaken = true
        unregisterDismissReceiver()
        stopRinging()
        // Same shape as onAnswer, deliberately. Declining also has to bring the
        // app up — that is where the decline API call and the RTDB write live —
        // and neither can happen behind a locked keyguard.
        dismissKeyguardThen {
            handOff(ACTION_DECLINE, "decline")
            closeCallUi()
        }
    }

    /**
     * Hands the action back to the app, doing exactly what TransparentActivity
     * does — but directly, from this activity.
     *
     * The action pending intents route through TransparentActivity, which
     * declares the same empty taskAffinity as this screen and therefore lives in
     * the same task. Finishing this screen tore that task down and took the
     * freshly launched MainActivity with it: the app came up, drew one frame and
     * vanished, so the action never reached Dart. Tapping the notification's own
     * buttons worked precisely because this screen was not in the picture.
     *
     * Doing both steps here keeps the observable behaviour identical (the
     * broadcast still reaches CallActionReceiver, the app still opens with the
     * same `key` and payload) without the shared-task round trip.
     */
    private fun handOff(actionKey: String, label: String) {
        notifyActionReceiver(actionKey, label)
        openAppDirectly(actionKey, label)
    }

    /** The same broadcast TransparentActivity sends, so `onCallAction` still fires. */
    private fun notifyActionReceiver(actionKey: String, label: String) {
        try {
            val broadcast = Intent(actionKey).apply {
                setPackage(packageName)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_CALLER_NAME, intent.getStringExtra(EXTRA_CALLER_NAME).orEmpty())
                putExtra(EXTRA_CALLER_IMAGE, intent.getStringExtra(EXTRA_CALLER_IMAGE).orEmpty())
                putExtra(EXTRA_TYPE, intent.getStringExtra(EXTRA_TYPE).orEmpty())
                putExtra(EXTRA_UNIQUE_ID, intent.getStringExtra(EXTRA_UNIQUE_ID).orEmpty())
                putExtra(
                    EXTRA_CUSTOMER_UNI_ID,
                    intent.getStringExtra(EXTRA_CUSTOMER_UNI_ID).orEmpty()
                )
            }
            sendBroadcast(broadcast)
            Log.d(TAG, "Broadcast $label action to the app")
        } catch (e: Exception) {
            Log.w(TAG, "Could not broadcast $label action: ${e.message}")
        }
    }

    /**
     * Opens the app with the same `key` + payload the notification would carry.
     * This screen is a visible activity, so it is always allowed to start one —
     * no background-activity-start restriction applies.
     */
    private fun openAppDirectly(actionKey: String, label: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Log.e(TAG, "No launch intent for $packageName — cannot open the app for $label")
                return
            }
            launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            launchIntent.putExtra("key", actionKey)
            launchIntent.putExtra(
                EXTRA_MESSAGE_DATA,
                intent.getStringExtra(EXTRA_MESSAGE_DATA).orEmpty()
            )
            startActivity(launchIntent)
            Log.d(TAG, "Opened the app for $label")
        } catch (e: Exception) {
            Log.e(TAG, "Could not open the app for $label", e)
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
        closeCallUi()
    }

    private fun closeCallUi() {
        if (isFinishing) return
        // Never finishAndRemoveTask() here: removing the task also sends the
        // user to the home screen, and that yanked away the MainActivity the
        // answer/decline pending intent had just brought forward — the app
        // visibly opened for one frame and was pushed straight back down, so
        // the action never reached Dart. TransparentActivity shares this
        // activity's empty taskAffinity, so it lives in the same task and was
        // being torn down with it too.
        //
        // Nothing is lost by finishing normally: the manifest already marks
        // this activity excludeFromRecents, which is all the removal was for.
        finish()
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

}
