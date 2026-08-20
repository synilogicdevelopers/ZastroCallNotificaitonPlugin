package com.example.zastro_android_call_notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.os.PowerManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.engine.dart.DartExecutor
import java.io.Serializable
import org.json.JSONObject
import android.media.AudioFocusRequest
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

import kotlinx.coroutines.delay

import com.example.zastro_android_call_notifications.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import android.util.Log
import java.io.IOException


class CallNotificationService : Service() {

    companion object {
        /**
         * Dedicated, deliberately SILENT channel for the incoming call banner.
         *
         * This used to post to the host app's "chat_channel". A notification
         * channel's sound and vibration are fixed at creation and cannot be
         * changed afterwards, and the host app creates that channel first (with
         * a ringtone and a vibration pattern), so the `setSound(null, null)`
         * below was a no-op against it. The channel therefore rang the system
         * ringtone while this service rang its own — two ringtones at once, and
         * two vibrations. It was easy to miss only because both sounds used to
         * be the same system ringtone; a custom ringtone makes it obvious.
         *
         * The ringtone and vibration for a call are owned by this service, so
         * its channel must stay silent. The host app's own channel is left
         * untouched — other notification types still use it and still ring.
         */
        const val CHANNEL_ID = "zastro_incoming_call"
        const val FLUTTER_ENGINE_NAME = "flutter_engine"
        const val CHANNEL_NAME = "Chat notifications"
        const val ACTION_ANSWER_CALL = "ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "ACTION_DECLINE_CALL"
        const val CALL_NOTIFICATION_CLICK = "CALL_NOTIFICATION_CLICK"
        const val NOTIFICATION_ICON_RES_ID = "notification_icon_res_id"

        /**
         * Own request code for the full-screen intent so it can never collide
         * with the answer/decline/content pending intents above.
         */
        private const val FULL_SCREEN_REQUEST_CODE = 100

        /** Large enough for the notification avatar on any density. */
        private const val NOTIFICATION_AVATAR_SIZE_PX = 256
    }

    private var flutterEngine: FlutterEngine? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrationJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** Id of the call currently ringing, so teardown can be scoped to it. */
    private var ringingNotificationId: Int = -1

    /** Ringtone chosen for the call currently ringing. Null means system default. */
    private var ringtoneSpec: String? = null

    /**
     * When the current call started ringing. Captured once so the time shown on
     * the full screen UI does not drift if the notification is rebuilt.
     */
    private var callStartTime: Long = 0L

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        println("CallNotificationService Started!")


        val CALL_NOTIFICATION_ID = intent?.getIntExtra("notificationId", -1) ?: -1
        val callerName = intent?.getStringExtra("caller_name") ?: "Unknown Caller"
        val callerImage = intent?.getStringExtra("caller_image") ?: ""
        val type = intent?.getStringExtra("type") ?: ""
        val messageDataInString = intent?.getStringExtra("message_data_in_string") ?: ""
        println("Received messageDataInString: $messageDataInString")
        val messageData: JSONObject? = if (!messageDataInString.isNullOrEmpty()) {
            try {
                JSONObject(messageDataInString)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
        println("Converted messageData: $messageData")


        val ringtone = intent?.getStringExtra("ringtone")
        val uniqueId = intent?.getStringExtra("uniqueId") ?: ""
        val customerUniId = intent?.getStringExtra("customerUniId") ?: ""
        println("CallNotificationService Started!$CALL_NOTIFICATION_ID")

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent?.action) {
            "ACTION_ANSWER_CALL" -> {
                println("Call Answered")
                stopRingtone()
                stopVibration()
                if (CALL_NOTIFICATION_ID != -1) notificationManager.cancel(CALL_NOTIFICATION_ID)
                stopSelf()
            }//Not coming in use right now, but kept it of neede in future
            "ACTION_DECLINE_CALL" -> {
                println("Call Declined")
                stopRingtone()
                stopVibration()
                if (CALL_NOTIFICATION_ID != -1) notificationManager.cancel(CALL_NOTIFICATION_ID)
                stopSelf()
            }//Not coming in use right now, but kept it of neede in future
            "ACTION_CANCEL_CALL_NOTIFICATION" -> {
                if (CALL_NOTIFICATION_ID != -1) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                    stopRingtone()
                    stopVibration()
                    notificationManager.cancel(CALL_NOTIFICATION_ID)
                    stopSelf()
                }
            }

            else -> {
                ringingNotificationId = CALL_NOTIFICATION_ID
                ringtoneSpec = RingtoneResolver.effectiveSpec(this, ringtone)
                callStartTime = System.currentTimeMillis()
                // Step 1: Immediately show notification without photo
                val placeholderNotification = createCallNotification(
                    messageDataInString,
                    callerName,
                    callerImage,
                    CALL_NOTIFICATION_ID,
                    type,
                    uniqueId,
                    customerUniId,
                    callerBitmap = null
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        CALL_NOTIFICATION_ID,
                        placeholderNotification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(CALL_NOTIFICATION_ID, placeholderNotification)
                }

                // Step 2: ring straight away.
                //
                // These used to sit at the end of the photo-download coroutine,
                // so the phone stayed silent until the caller's picture had been
                // fetched — up to the full connect+read timeout on a cold start,
                // which is exactly the terminated-app case. It went unnoticed
                // while the notification channel rang by itself; now that the
                // channel is silent (so a custom ringtone is possible at all),
                // the service is the only thing making noise and it has to start
                // immediately. A call must never wait on the network to ring.
                startRingtone()
                startVibration()
                wakeScreen()

                // Step 3: in the background, fetch the photo and refresh the
                // notification with it. Purely cosmetic, and skipped entirely if
                // the photo cannot be loaded.
                serviceScope.launch {
                    val callerBitmap = CallerPhotoLoader.load(callerImage) ?: return@launch
                    val notification = createCallNotification(
                        messageDataInString,
                        callerName,
                        callerImage,
                        CALL_NOTIFICATION_ID,
                        type,
                        uniqueId,
                        customerUniId,
                        callerBitmap
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                        try {
                            startForeground(
                                CALL_NOTIFICATION_ID,
                                notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                            )
                        } catch (e: Exception) {
                            println("CallNotificationService Error starting foreground service ${e.localizedMessage}")
                        }
                    } else {
                        startForeground(CALL_NOTIFICATION_ID, notification)
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopRingtone()
        stopVibration()
        // The call stopped ringing (answered, declined, cancelled by the caller
        // or stopped by Flutter) — take the full-screen UI down with it.
        IncomingCallActivity.dismiss(this, ringingNotificationId)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /** "Incoming voice call" / "Incoming video call" / "Incoming chat request". */
    private fun incomingLabel(type: String): String = when (type.trim().lowercase()) {
        "call" -> "Incoming voice call"
        "video" -> "Incoming video call"
        "chat" -> "Incoming chat request"
        else -> "Incoming call"
    }

    /**
     * "Incoming chat request · 4:47 pm".
     *
     * The timestamp in the notification header is drawn by the system, and it
     * shows "now" for anything posted in the last minute — there is no API to
     * force an absolute time there. So the start time goes in the body text,
     * where it is always visible and does not change as the call rings.
     */
    private fun incomingLabelWithTime(type: String): String {
        val label = incomingLabel(type)
        if (callStartTime <= 0L) return label
        return try {
            val time = android.text.format.DateFormat.getTimeFormat(this)
                .format(java.util.Date(callStartTime))
            "$label · $time"
        } catch (e: Exception) {
            Log.w("CallNotificationService", "Could not format call time: ${e.message}")
            label
        }
    }

    private fun createCallNotification(
        messageDataInString: String,
        callerName: String,
        callerImage: String,
        CALL_NOTIFICATION_ID: Int,
        type: String,
        uniqueId: String,
        customerUniId: String,
        callerBitmap: Bitmap?
    ): Notification {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming calls",
                // Still HIGH: anything lower loses the heads-up banner and the
                // full screen intent along with it.
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ringing chat, voice and video call requests"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Silent on purpose — startRingtone()/startVibration() own both,
                // which is what lets a host app choose its own ringtone.
                setSound(null, null)
                enableVibration(false)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            // FLAG_ACTIVITY_NEW_TASK is not optional here. getLaunchIntentForPackage()
            // sets it, and assigning `flags` (rather than or-ing) used to wipe it out —
            // so an activity started from these pending intents had no task to land in
            // whenever the app was not already foregrounded, and simply never opened.
            // That is why answer/decline worked from TransparentActivity (which sets the
            // flag) but not straight from the notification or the full screen call UI.
            // Same flag set as TransparentActivity, which is the path known to work.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            putExtra("message_data_in_string", messageDataInString)
        } ?: Intent().apply {
            val mainActivityIntent = packageManager.getLaunchIntentForPackage(packageName)

            if (mainActivityIntent != null) {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("message_data_in_string", messageDataInString)
                mainActivityIntent.putExtras(this)
            } else {
                Log.e("TransparentActivity", "MainActivity launch intent could not be retrieved")
            }
        }

        val bundle = Bundle().apply {
            putInt("notificationId", CALL_NOTIFICATION_ID)
            putString("caller_name", callerName)
            putString("caller_image", callerImage)
            putString("type", type)
            putString("uniqueId", uniqueId)
            putString("customerUniId", customerUniId)
        }

        val intent = TransparentActivity.getIntent(
            this,
            CALL_NOTIFICATION_CLICK,
            messageDataInString,
            bundle
        )

        // Send a broadcast if the app is in the foreground, else open the app
        val pendingIntent = if (isAppInForeground()) {
            PendingIntent.getActivity(
                this, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 2, launchIntent.apply {
                    putExtra("key", CALL_NOTIFICATION_CLICK)
                } ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }


        println("Sending messageDataInString: $messageDataInString")

        val answerIntent =
            TransparentActivity.getIntent(this, ACTION_ANSWER_CALL, messageDataInString, bundle)

        val answerPendingIntent = if (isAppInForeground()) {
            PendingIntent.getActivity(
                this, 0, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 4, launchIntent.apply {
                    putExtra("key", ACTION_ANSWER_CALL)
                } ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val declineIntent =
            TransparentActivity.getIntent(this, ACTION_DECLINE_CALL, messageDataInString, bundle)

        val declinePendingIntent = if (isAppInForeground()) {
            PendingIntent.getActivity(
                this, 1, declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 5, launchIntent.apply {
                    putExtra("key", ACTION_DECLINE_CALL)
                } ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // Full-screen intent. This is what makes a WhatsApp-style incoming call
        // screen appear while the app is backgrounded, terminated or the device
        // is locked — a background activity start is otherwise blocked on
        // Android 10+. The same pending intents the notification's own buttons
        // carry are handed to the screen, so both paths behave identically.
        // If the user/OEM has revoked USE_FULL_SCREEN_INTENT (Android 14+),
        // Android silently degrades this to the heads-up notification below,
        // which is exactly the previous behaviour.
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            FULL_SCREEN_REQUEST_CODE,
            IncomingCallActivity.getIntent(
                this,
                CALL_NOTIFICATION_ID,
                callerName,
                callerImage,
                type,
                callStartTime,
                messageDataInString,
                uniqueId,
                customerUniId
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            // The host app's own notification icon when it has one, so the shade
            // shows the app's mark instead of a generic arrow.
            .setSmallIcon(CallNotificationAppearance.smallIconRes(this))
            .setContentText(incomingLabelWithTime(type))
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            // Stamped when the call started rather than when the notification was
            // (re)built, so it reads as the real start time instead of "now".
            .setWhen(if (callStartTime > 0L) callStartTime else System.currentTimeMillis())
            .setShowWhen(true)
            .setUsesChronometer(false)
            // The notification is rebuilt once the caller photo arrives; without
            // this the heads-up banner pops a second time on that update.
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        CallNotificationAppearance.accentColor(this)?.let { accent ->
            notificationBuilder.setColor(accent)
            // Painting the whole notification is opt-in: a light brand colour
            // makes a colorized notification unreadable.
            if (CallNotificationAppearance.colorized(this)) {
                notificationBuilder.setColorized(true)
            }
        }

        // Same fallback as the full screen screen, so the shade never shows a
        // faceless call just because the payload carried no photo url.
        val personBitmap = callerBitmap?.let { CallerPhotoLoader.toCircular(it, NOTIFICATION_AVATAR_SIZE_PX) }
            ?: InitialsAvatar.create(callerName, NOTIFICATION_AVATAR_SIZE_PX)

        val person = Person.Builder().setName(callerName)
            .apply {
                if (personBitmap != null)
                    setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(personBitmap))
            }
            .build()
        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            person, declinePendingIntent, answerPendingIntent
        )
        notificationBuilder.setStyle(callStyle)
        notificationBuilder.setContentIntent(pendingIntent)
//            notificationBuilder.setFullScreenIntent(pendingIntent, true) // Use below one
//            if (!isAppInForeground()) {
//                notificationBuilder.setFullScreenIntent(pendingIntent, true)
//            }
//            notificationManager.notify(CALL_NOTIFICATION_ID, notificationBuilder.build())

        return notificationBuilder.build()
    }


    private fun startRingtone() {
        stopRingtone()
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
        } else {
            null
        }
        val focusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_RING,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        // Ring even if focus was refused. Focus used to be a hard gate: when the
        // request was denied nothing played and nothing was logged, which the
        // notification channel's own ringtone used to paper over. For an
        // incoming call a missed ring is the worse outcome, so the denial is
        // logged and playback goes ahead.
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(
                "CallNotificationService",
                "Audio focus not granted (result=$focusResult) — ringing anyway"
            )
        }

        // The app's own tone first, then the system chain. Either way something
        // has to ring: the notification channel is silent by design, so this
        // player is the only thing making noise.
        if (ringtoneSpec != null && playCustomRingtone(ringtoneSpec!!)) return
        if (playSystemRingtone()) return
        Log.e(
            "CallNotificationService",
            "No ringtone could be played at all — the call is ringing silently"
        )
            // Set volume manually
//        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
//        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, AudioManager.FLAG_SHOW_UI)
        }

        /**
         * Starts the ringtone described by [spec], or the system default when it
         * is null. Returns false if playback could not be started, so the caller
         * can retry with the default.
         */
        private fun newRingtonePlayer(): MediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        /** Plays the ringtone the host app asked for. False means fall through. */
        private fun playCustomRingtone(spec: String): Boolean {
            val player = newRingtonePlayer()
            return try {
                if (RingtoneResolver.applyTo(player, this, spec)) {
                    startPlayer(player)
                } else {
                    releaseQuietly(player)
                    false
                }
            } catch (e: Exception) {
                Log.w("CallNotificationService", "Custom ringtone '$spec' failed: ${e.message}")
                releaseQuietly(player)
                false
            }
        }

        /**
         * Plays the phone's own ringtone, trying every source in turn.
         *
         * This used to try `getDefaultUri(TYPE_RINGTONE)` and nothing else. That
         * resolves to whatever tone the user picked, and when the pick lives in
         * external media reading it needs READ_MEDIA_AUDIO on Android 13+ — a
         * permission a calling app has no reason to hold. `setDataSource` then
         * threw SecurityException, and with the notification channel silent by
         * design the call rang not at all. The later entries below are the
         * built-in tones, which never need a permission.
         */
        private fun playSystemRingtone(): Boolean {
            for ((label, uri) in systemRingtoneCandidates()) {
                if (uri == null) continue
                val player = newRingtonePlayer()
                try {
                    player.setDataSource(this, uri)
                    if (startPlayer(player)) {
                        Log.d("CallNotificationService", "Ringing with system tone: $label")
                        return true
                    }
                } catch (e: Exception) {
                    Log.w("CallNotificationService", "System tone '$label' unusable: ${e.message}")
                    releaseQuietly(player)
                }
            }
            return false
        }

        private fun systemRingtoneCandidates(): List<Pair<String, Uri?>> = listOf(
            "user ringtone" to runCatching {
                RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            }.getOrNull(),
            "default ringtone" to runCatching {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }.getOrNull(),
            "built-in ringtone" to Settings.System.DEFAULT_RINGTONE_URI,
            "default notification tone" to runCatching {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }.getOrNull(),
            "built-in notification tone" to Settings.System.DEFAULT_NOTIFICATION_URI
        )

        private fun startPlayer(player: MediaPlayer): Boolean = try {
            player.isLooping = true
            player.prepare()
            player.start()
            mediaPlayer = player
            true
        } catch (e: IOException) {
            Log.w("CallNotificationService", "Ringtone could not be opened: ${e.message}")
            releaseQuietly(player)
            false
        } catch (e: Exception) {
            Log.w("CallNotificationService", "Ringtone could not start: ${e.message}")
            releaseQuietly(player)
            false
        }

        private fun releaseQuietly(player: MediaPlayer) {
            try {
                player.release()
            } catch (e: Exception) {
                // Already gone; nothing useful to do.
            }
        }

        private fun startVibration() {
            stopVibration()

            vibrationJob = GlobalScope.launch {
                while (isActive) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(
                            VibrationEffect.createWaveform(
                                longArrayOf(
                                    0,
                                    700,
                                    500,
                                    700
                                ), 0
                            )
                        )
                    } else {
                        vibrator?.vibrate(longArrayOf(0, 700, 500, 700), 0)
                    }
                    kotlinx.coroutines.delay(3000)
                }
            }
        }


        private fun stopRingtone() {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }

        private fun stopVibration() {
            vibrationJob?.cancel()
            vibrationJob = null
            vibrator?.cancel()
        }


        private fun wakeScreen() {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "CallNotificationService:WakeLock"
            ).acquire(5000)
        }

        private fun isAppInForeground(): Boolean {
            val activityManager =
                getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningTasks = activityManager.runningAppProcesses ?: return false

            for (task in runningTasks) {
                if (task.processName == packageName && task.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return true
                }
            }
            return false
        }

    }