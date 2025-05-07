package com.example.zastro_android_call_notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.app.NotificationManager
import android.app.ActivityManager


class CallOngoingTimeNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            "${context.packageName}.com.example.zastro_android_call_notifications.START_CALL_NOTIFICATION" -> {
                if (CallTimerService.instance?.isRunning != true) {
                    val serviceIntent = Intent(context, CallTimerService::class.java)
                serviceIntent.putExtra("initial_seconds", intent.getIntExtra("call_duration_seconds", 0))
                Handler(Looper.getMainLooper()).postDelayed({
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }, 500)
                }
            }

            "${context.packageName}.com.example.zastro_android_call_notifications.START_MICROPHONE_NOTIFICATION" -> {
                val isForeground = isAppInForeground(context)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !isForeground) {
                    Log.w("CallNotificationReceiver", "Skipped mic service start — Android 14+ + app not foreground")
                    return
                }

                val foregroundServiceIntent = Intent(context, CallForegroundService::class.java)
                Handler(Looper.getMainLooper()).postDelayed({
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(foregroundServiceIntent)
                } else {
                    context.startService(foregroundServiceIntent)
                }
                }, 500)
            }

            "${context.packageName}.com.example.zastro_android_call_notifications.UPDATE_CALL_NOTIFICATION" -> {
                val duration = intent.getIntExtra("call_duration_seconds", 0)
                Log.d("CallNotificationReceiver", "Updating call notification: $duration seconds")
                if (CallTimerService.instance != null && CallTimerService.instance?.isRunning == true) {
                    CallTimerService.updateCallDuration(duration)
                } else {
                    Log.d("CallNotificationReceiver", "Service is not running, skipping update")
                    val stopIntent = Intent(context, CallTimerService::class.java)
                    context.stopService(stopIntent)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(CallTimerService.CALL_NOTIFICATION_ID)
                }
            }

            "${context.packageName}.com.example.zastro_android_call_notifications.STOP_CALL_NOTIFICATION" -> {
                Log.d("CallNotificationReceiver", "STOP_CALL_NOTIFICATION received in BroadcastReceiver")

                val stopIntent = Intent(context, CallTimerService::class.java).apply {
                    action = "ACTION_CANCEL_ONGOING_CALL_NOTIFICATION"
                }
                context.stopService(stopIntent)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(CallTimerService.CALL_NOTIFICATION_ID)
            }

            "${context.packageName}.com.example.zastro_android_call_notifications.STOP_MIC_NOTIFICATION" -> {
                val micServiceIntent = Intent(context, CallForegroundService::class.java)
                context.stopService(micServiceIntent)
            }
        }
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }
}