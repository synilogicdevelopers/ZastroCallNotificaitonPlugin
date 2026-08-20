package com.example.zastro_android_call_notifications

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.NonNull
import com.example.zastro_android_call_notifications.CallNotificationService
import com.example.zastro_android_call_notifications.CallReceiver
import com.example.zastro_android_call_notifications.CallActionReceiver
import com.example.zastro_android_call_notifications.CallOngoingTimeNotificationReceiver
import com.example.zastro_android_call_notifications.MethodChannelHelper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import org.json.JSONObject
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.net.Uri
import android.provider.Settings


class ZastroAndroidCallNotificationsPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var context: Context
    private lateinit var channel: MethodChannel

    //  private lateinit var callTimerChannel: MethodChannel
//  private lateinit var ongoingCallChannel: MethodChannel
    private lateinit var callReceiver: CallReceiver
    private lateinit var callActionReceiver: CallActionReceiver
    private lateinit var callOngoingReceiver: CallOngoingTimeNotificationReceiver
    private var activity: Activity? = null
    private var latestNotificationData: Map<String, Any?>? = null
    private var isAttachedToEngine = false


    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        isAttachedToEngine = true
        Log.d("FlutterCallkitIncoming", "onAttachedToEngine called")
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "Chat notifications")
        channel.setMethodCallHandler(this)

//    callTimerChannel = MethodChannel(binding.binaryMessenger, "Call Timer")
//    callTimerChannel.setMethodCallHandler(this)

//    ongoingCallChannel = MethodChannel(binding.binaryMessenger, "Ongoing Call Notifications")
//    ongoingCallChannel.setMethodCallHandler(this)

        MethodChannelHelper.setMethodChannel(channel)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        Log.d(
            "IsolateCheck",
            "Kotlin MethodCall received on thread: ${Thread.currentThread().name}"
        )
        try {
            when (call.method) {
                "initialize" -> {
                    Log.d("ZastroPlugin", "Plugin Initialized")
                    result.success("Initialization successful")
                }

                "triggerBroadcastNotification" -> {
                    val data = call.arguments as Map<String, Any?>?
                    // Prepare the data you need for your broadcast
                    val messageData = data?.get("message_data_in_string") as String
                    Log.d("ZastroPlugin", "Received JSON string: $messageData")
                    println("Received: $messageData")
                    try {
                        val json = JSONObject(messageData)

                        val type = json.optString("type", "").trim().lowercase()

                        // Safely extract notification_id as Int
                        val notificationId = try {
                            json.optString("notification_id", "-1").toInt()
                        } catch (e: Exception) {
                            Log.e("ZastroPlugin", "Error parsing notification_id: ${e.message}")
                            -1
                        }

//                        Log.d("ZastroPlugin", "Parsed type: $type")
//                        Log.d("ZastroPlugin", "Parsed notification_id: $notificationId")

                        val prefs = context.getSharedPreferences("zastro_prefs", Context.MODE_PRIVATE)

                        if (type == "cancel") {
                            prefs.edit().putBoolean("notif_cancelled_$notificationId", true).apply()
                        } else {
                            val isCancelled = prefs.getBoolean("notif_cancelled_$notificationId", false)
                            if (isCancelled) {
                                Log.d("ZastroPlugin", "Notification $notificationId already cancelled, not showing again.")
                                result.success("Notification already cancelled, skipping.")
                                return
                            }
                        }

                        val action = if (type == "cancel") {
                            "${context.packageName}.com.example.zastro_android_call_notifications.CANCEL_CALL_NOTIFICATION"
                        } else {
                            "${context.packageName}.com.example.zastro_android_call_notifications.SHOW_CALL_NOTIFICATION"
                        }
                        val intent =
                            Intent(action).apply {
                                putExtra("message_data_in_string", messageData)
                            }
                        intent.setPackage(context.packageName)
//                        Log.d(
//                            "FlutterCallkitIncoming",
//                            "Triggering triggerBroadcastNotification with data: " + messageData.toString()
//                        )

                        context.sendBroadcast(intent)
                        println("Broadcast sent!")
                        result.success("Broadcast sent successfully!")
                    } catch (e: Exception) {
                        println("Broadcast failed: ${e.message}")
                        result.error("BROADCAST_ERROR", e.message, null)
                    }
                }

                /*
                 * Sets the ringtone used when a call arrives without an explicit
                 * one — including calls raised from a data-only FCM payload.
                 */
                "setDefaultRingtone" -> {
                    RingtoneResolver.storeDefault(context, call.argument<String>("ringtone"))
                    result.success(null)
                }

                "getDefaultRingtone" -> {
                    result.success(RingtoneResolver.readDefault(context))
                }

                /*
                 * Look of the call notification. Stored rather than passed per
                 * call, because a call raised from a data-only FCM payload never
                 * goes through Dart at all.
                 */
                "setCallNotificationAppearance" -> {
                    CallNotificationAppearance.store(
                        context,
                        call.argument<String>("smallIcon"),
                        call.argument<String>("accentColor"),
                        call.argument<Boolean>("colorized")
                    )
                    result.success(null)
                }

                "showCallNotification" -> {
                    val type = call.argument<String>("type") ?: ""
                    val uniqueId = call.argument<String>("uniqueId") ?: ""
                    val customerUniId = call.argument<String>("customerUniId") ?: ""
                    val notificationId = call.argument<Int>("notificationId") ?: 1001
                    val callerName = call.argument<String>("caller_name") ?: "Unknown Caller"
                    val callerImage = call.argument<String>("caller_image") ?: ""
                    val messageDataInString =
                        call.argument<String>("message_data_in_string") ?: "{}"
                    val ringtone = call.argument<String>("ringtone")
//                    Log.d(
//                        "FlutterCallkitIncoming",
//                        "Triggering showIncomingNotification with data: " + messageDataInString.toString()
//                    )

                    startCallNotificationService(
                        type,
                        uniqueId,
                        customerUniId,
                        notificationId,
                        callerName,
                        callerImage,
                        messageDataInString,
                        ringtone
                    )
                    result.success("Call notification started")
                }

                "cancelCallNotification" -> {
                    val notificationId = call.argument<Int>("notificationId") ?: -1
                    val intent = Intent(context, CallNotificationService::class.java).apply {
                        action = "ACTION_CANCEL_CALL_NOTIFICATION"
                        putExtra("notificationId", notificationId)
                    }
                    context.startService(intent)
                    result.success(null)
                }

                /*
                 * Android 14 (API 34) turned USE_FULL_SCREEN_INTENT into a
                 * user-revocable special access. Without it the incoming call
                 * notification is still posted, but the system shows it as a
                 * heads-up instead of launching the full-screen call screen.
                 */
                "canUseFullScreenIntent" -> {
                    result.success(canUseFullScreenIntent())
                }

                "openFullScreenIntentSettings" -> {
                    result.success(openFullScreenIntentSettings())
                }

                /*
                 * Drops the action the host app has just finished handling.
                 *
                 * The activity's own intent has to be cleared too, not just the
                 * cached copy: the intent outlives the launch, so a config
                 * change (rotation, theme, locale) re-runs handleIntent and
                 * would resurrect the very action that was just consumed.
                 */
                "clearNotificationData" -> {
                    latestNotificationData = null
                    activity?.intent?.apply {
                        removeExtra("key")
                        removeExtra("message_data_in_string")
                        removeExtra("notificationJson")
                    }
                    result.success(null)
                }

                "notificationData" -> {
                    if (latestNotificationData != null) {
                        result.success(latestNotificationData)
                    } else {
                        result.error("NO_DATA", "No notification data available", null)
                    }
                }

                "onCallAction" -> {
                    val data = call.arguments as? Map<String, Any?> ?: emptyMap()
                    Log.d("ZastroPlugin", "Flutter sent data: $data")
                    result.success("Data received successfully!")
                }

                "startOngoingCallNotification" -> {
                    val seconds = call.argument<Int>("call_duration_seconds") ?: 0
                    try {
                        val intent =
                            Intent("${context.packageName}.com.example.zastro_android_call_notifications.START_CALL_NOTIFICATION").apply {
                                putExtra("call_duration_seconds", seconds)
                            }
                        intent.setPackage(context.packageName)
                        context.sendBroadcast(intent)
                        result.success("START_CALL_NOTIFICATION broadcast sent!")
                    } catch (e: Exception) {
                        println("Broadcast failed: ${e.message}")
                        result.error("BROADCAST_ERROR", e.message, null)
                    }
                }

                "startMicNotification" -> {
                    try {
                        val intent =
                            Intent("${context.packageName}.com.example.zastro_android_call_notifications.START_MICROPHONE_NOTIFICATION")
                        intent.setPackage(context.packageName)
                        context.sendBroadcast(intent)
                        result.success("START_MICROPHONE_NOTIFICATION broadcast sent!")
                    } catch (e: Exception) {
                        println("Broadcast failed: ${e.message}")
                        result.error("BROADCAST_ERROR", e.message, null)
                    }
                }

                "updateCallDuration" -> {
                    val seconds = call.argument<Int>("call_duration_seconds") ?: 0
                    try {
                        val intent =
                            Intent("${context.packageName}.com.example.zastro_android_call_notifications.UPDATE_CALL_NOTIFICATION").apply {
                                putExtra("call_duration_seconds", seconds)
                            }
                        intent.setPackage(context.packageName)
                        context.sendBroadcast(intent)
                        result.success("UPDATE_CALL_NOTIFICATION broadcast sent!")
                    } catch (e: Exception) {
                        println("Broadcast failed: ${e.message}")
                        result.error("BROADCAST_ERROR", e.message, null)
                    }

                }

                "stopOngoingCallNotification" -> {
                    val intent =
                        Intent("${context.packageName}.com.example.zastro_android_call_notifications.STOP_CALL_NOTIFICATION")
                    intent.setPackage(context.packageName)
                    context.sendBroadcast(intent)
                    result.success("STOP_CALL_NOTIFICATION broadcast sent!")
                }

                "stopMicNotification" -> {
                    val intent =
                        Intent("${context.packageName}.com.example.zastro_android_call_notifications.STOP_MIC_NOTIFICATION")
                    intent.setPackage(context.packageName)
                    context.sendBroadcast(intent)
                    result.success("STOP_MIC_NOTIFICATION broadcast sent!")
                }

                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("ERROR", e.localizedMessage, null)
        }
    }

    /** `true` on anything below Android 14, where the permission is implicit. */
    private fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.canUseFullScreenIntent()
        } catch (e: Exception) {
            Log.w("ZastroPlugin", "canUseFullScreenIntent check failed: ${e.message}")
            true
        }
    }

    /**
     * Opens the "Full screen notifications" special access page for this app.
     * Returns false when there is nothing to open (below Android 14) or when no
     * OEM activity handles the intent, so the caller can stay silent.
     */
    private fun openFullScreenIntentSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")
            )
            val launcher = activity
            if (launcher != null) {
                launcher.startActivity(intent)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            true
        } catch (e: Exception) {
            Log.w("ZastroPlugin", "Could not open full screen intent settings: ${e.message}")
            false
        }
    }

    private fun startCallNotificationService(
        type: String,
        uniqueId: String,
        customerUniId: String,
        notificationId: Int,
        callerName: String,
        callerImage: String,
        messageDataInString: String,
        ringtone: String?,
    ) {
        val intent = Intent(context, CallNotificationService::class.java).apply {
            putExtra("type", type)
            putExtra("uniqueId", uniqueId)
            putExtra("customerUniId", customerUniId)
            putExtra("notificationId", notificationId)
            putExtra("caller_name", callerName)
            putExtra("caller_image", callerImage)
            putExtra("message_data_in_string", messageDataInString)
            putExtra("ringtone", ringtone)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.d("FlutterCallkitIncoming", "onAttachedToActivity called")
        activity = binding.activity
        binding.addOnNewIntentListener { intent ->
            handleIntent(intent)
            true
        }
        handleIntent(binding.activity.intent)

        /*// Register all broadcast receivers
        callReceiver = CallReceiver()
        val callFilter = IntentFilter().apply {
          addAction("${context.packageName}.com.example.zastro_android_call_notifications.SHOW_CALL_NOTIFICATION")
          addAction("${context.packageName}.com.example.zastro_android_call_notifications.CANCEL_CALL_NOTIFICATION")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.registerReceiver(callReceiver, callFilter, Context.RECEIVER_EXPORTED)
        } else {
          @Suppress("DEPRECATION")
          context.registerReceiver(callReceiver, callFilter)
        }


        callActionReceiver = CallActionReceiver()
        val actionFilter = IntentFilter().apply {
          addAction("CALL_NOTIFICATION_CLICK")
          addAction("ACTION_ANSWER_CALL")
          addAction("ACTION_DECLINE_CALL")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.registerReceiver(callActionReceiver, actionFilter, Context.RECEIVER_EXPORTED)
        } else {
          @Suppress("DEPRECATION")
          context.registerReceiver(callActionReceiver, actionFilter)
        }

        callOngoingReceiver = CallOngoingTimeNotificationReceiver()
        val ongoingFilter = IntentFilter().apply {
          addAction("${context.packageName}.com.example.zastro_android_call_notifications.START_CALL_NOTIFICATION")
          addAction("${context.packageName}.com.example.zastro_android_call_notifications.START_MICROPHONE_NOTIFICATION")
          addAction("${context.packageName}.com.example.zastro_android_call_notifications.UPDATE_CALL_NOTIFICATION")
          addAction("${context.packageName}.com.example.zastro_android_call_notifications.STOP_CALL_NOTIFICATION")
          addAction("${context.packageName}.com.example.zastro_android_call_notifications.STOP_MIC_NOTIFICATION")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          context.registerReceiver(callOngoingReceiver, ongoingFilter, Context.RECEIVER_EXPORTED)
        } else {
          @Suppress("DEPRECATION")
          context.registerReceiver(callOngoingReceiver, ongoingFilter)
        }*/
    }

    private fun handleIntent(intent: Intent?) {
        intent?.extras?.let { extras ->
            latestNotificationData = extras.keySet().associateWith { key -> extras.get(key) }

            val action = extras.getString("key", "") ?: return
            if (action == "ACTION_ANSWER_CALL" || action == "ACTION_DECLINE_CALL" || action == "CALL_NOTIFICATION_CLICK") {
                try {
                    val messageData = extras.getString("message_data_in_string", "")
                    val notificationId = JSONObject(messageData ?: "{}")
                        .optString("notification_id", "-1")
                        .toInt()

                    if (notificationId != -1 && notificationId > 0) {
                        val stopIntent = Intent(context, CallNotificationService::class.java)
                        stopIntent.action = "ACTION_CANCEL_CALL_NOTIFICATION"
                        stopIntent.putExtra("notificationId", notificationId)
                        context.stopService(stopIntent)
                    }
                } catch (e: Exception) {
                    Log.e("ZastroPlugin", "Error parsing notification ID", e)
                }
            }
        }
    }

    override fun onDetachedFromActivity() {
        activity = null

        /*try {
          context.unregisterReceiver(callReceiver)
        } catch (e: Exception) {
          Log.w("ZastroPlugin", "CallReceiver already unregistered or not registered: ${e.message}")
        }
        try {
          context.unregisterReceiver(callActionReceiver)
        } catch (e: Exception) {
          Log.w("ZastroPlugin", "CallActionReceiver already unregistered or not registered: ${e.message}")
        }
        try {
          context.unregisterReceiver(callOngoingReceiver)
        } catch (e: Exception) {
          Log.w("ZastroPlugin", "CallOngoingReceiver already unregistered or not registered: ${e.message}")
        }*/
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        isAttachedToEngine = false
        Log.d("FlutterCallkitIncoming", "onDetachedFromEngine called")
        MethodChannelHelper.dispose()
        channel.setMethodCallHandler(null)
//    callTimerChannel.setMethodCallHandler(null)
//    ongoingCallChannel.setMethodCallHandler(null)
    }
}
