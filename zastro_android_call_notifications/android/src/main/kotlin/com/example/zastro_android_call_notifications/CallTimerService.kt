package com.example.zastro_android_call_notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import android.app.PendingIntent

class CallTimerService : Service() {
    private var handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    var isRunning = false
    var isEnded = false

    companion object {
        const val CALL_NOTIFICATION_ID = 1
        const val CALL_TIMER_CHANNEL_ID = "call_timer"
        const val CALL_TIMER_CHANNEL_NAME = "Call Timer"

        var instance: CallTimerService? = null

        fun updateCallDuration(seconds: Int) {
            instance?.seconds = seconds
            instance?.updateNotification(seconds)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = getNotification(0)
        Log.d("ForegroundService", "started")
        if (intent != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
                Log.d("ForegroundService", "iff")
                try {
                    startForeground(
                        CALL_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } catch (e: Exception) {
                    Log.e("CallTimerService", "Error starting foreground service", e)
                }
            } else {
                Log.d("ForegroundService", "else")
                startForeground(CALL_NOTIFICATION_ID, notification)
            }
        }

        if (intent?.action == "ACTION_CANCEL_ONGOING_CALL_NOTIFICATION") {
            stopCallService()
        }

        if (!isRunning) {
            seconds = intent?.getIntExtra("initial_seconds", 0) ?: 0
            isRunning = true
            startTimer()
        }

        return START_STICKY
    }

    private fun startTimer() {
        handler.post(object : Runnable {
            override fun run() {
                if (isRunning) {
                    updateNotification(seconds)
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun updateNotification(seconds: Int) {
        val notification = getNotification(seconds)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (isRunning) {
            notificationManager.notify(CALL_NOTIFICATION_ID, notification)
        }
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val sec = seconds % 60
        return String.format("%02d:%02d", minutes, sec)
    }

    private fun getNotification(seconds: Int): Notification {
        val currentTimeMillis = System.currentTimeMillis() - (seconds * 1000)

        val fullScreenIntent = Intent()
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CALL_TIMER_CHANNEL_ID)
            .setContentTitle("Ongoing Call")
            .setContentText("Call Duration: ${formatTime(seconds)}")
            .setSmallIcon(R.drawable.ic_ongoing_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALL_TIMER_CHANNEL_ID, CALL_TIMER_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopCallService() {
        if (!isRunning) return
        isEnded = true
        isRunning = false
        instance = null
        handler.removeCallbacksAndMessages(null)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(CALL_NOTIFICATION_ID)
        Log.d("ForegroundService", "isRunning before startTimer check")
        Handler(Looper.getMainLooper()).postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
        }, 500)
        Log.d("ForegroundService", "stopped")
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
