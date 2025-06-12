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


class CallNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "chat_channel"
        const val FLUTTER_ENGINE_NAME = "flutter_engine"
        const val CHANNEL_NAME = "Chat notifications"
        const val ACTION_ANSWER_CALL = "ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "ACTION_DECLINE_CALL"
        const val CALL_NOTIFICATION_CLICK = "CALL_NOTIFICATION_CLICK"
        const val NOTIFICATION_ICON_RES_ID = "notification_icon_res_id"
    }

    private var flutterEngine: FlutterEngine? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrationJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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


        val uniqueId = intent?.getStringExtra("uniqueId") ?: ""
        val customerUniId = intent?.getStringExtra("customerUniId") ?: ""
        println("CallNotificationService Started!$CALL_NOTIFICATION_ID")

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
                serviceScope.launch {
                    val callerBitmap = if (callerImage.isNotEmpty()) getBitmapFromURL(callerImage) else null
                    val notification = createCallNotification(messageDataInString, callerName, callerImage, CALL_NOTIFICATION_ID, type, uniqueId, customerUniId, callerBitmap)


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

                startRingtone()
                startVibration()
                wakeScreen()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        stopRingtone()
        stopVibration()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createCallNotification(messageDataInString: String, callerName: String, callerImage: String, CALL_NOTIFICATION_ID: Int, type: String, uniqueId: String, customerUniId: String, callerBitmap: Bitmap?): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fullScreenIntent = Intent()
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 500, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

        val intent = TransparentActivity.getIntent(this, CALL_NOTIFICATION_CLICK, messageDataInString, bundle)

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

        val answerIntent = TransparentActivity.getIntent(this, ACTION_ANSWER_CALL, messageDataInString, bundle)

        val answerPendingIntent = if (isAppInForeground()) {
            PendingIntent.getActivity(
                this, 0, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 4, launchIntent.apply {
                    putExtra("key", ACTION_ANSWER_CALL) } ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val declineIntent = TransparentActivity.getIntent(this, ACTION_DECLINE_CALL, messageDataInString, bundle)

        val declinePendingIntent = if (isAppInForeground()) {
            PendingIntent.getActivity(
                this, 1, declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 5, launchIntent.apply {
                    putExtra("key", ACTION_DECLINE_CALL) } ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.incoming_call_arrow)
            .setContentTitle("Incoming $type")
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

         val person = Person.Builder().setName(callerName)
                .apply { if (callerBitmap != null)
                    setIcon(androidx.core.graphics.drawable.IconCompat.createWithBitmap(callerBitmap))
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
            audioManager.requestAudioFocus(null, AudioManager.STREAM_RING, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

        if (focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@CallNotificationService, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                isLooping = true
                prepare()
                start()
            }
        }

        // Set volume manually
//        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
//        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, AudioManager.FLAG_SHOW_UI)
    }

    private fun startVibration() {
        stopVibration()

        vibrationJob = GlobalScope.launch {
            while (isActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500, 700), 0))
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

    private suspend fun getBitmapFromURL(src: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try { BitmapFactory.decodeStream(URL(src).openConnection().getInputStream()) }
            catch (e: Exception) { null }
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningTasks = activityManager.runningAppProcesses ?: return false

        for (task in runningTasks) {
            if (task.processName == packageName && task.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return true
            }
        }
        return false
    }

}