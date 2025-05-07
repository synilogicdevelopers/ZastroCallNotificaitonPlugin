package com.example.zastro_android_call_notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*
import org.json.JSONObject

class CallActionReceiver : BroadcastReceiver() {
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        coroutineScope.coroutineContext.cancelChildren()

        val action = intent?.action ?: return
        val callData: Map<String, Any?> = mapOf(
            "action" to action,
            "notificationId" to intent.getIntExtra("notificationId", -1) as Any,
            "caller_name" to (intent.getStringExtra("caller_name") ?: "Unknown Caller") as Any,
            "caller_image" to (intent.getStringExtra("caller_image") ?: "") as Any,
            "type" to (intent.getStringExtra("type") ?: "") as Any,
            "uniqueId" to (intent.getStringExtra("uniqueId") ?: "") as Any,
            "customerUniId" to (intent.getStringExtra("customerUniId") ?: "") as Any
        )

        println("Received broadcast: $action, Data: $callData")
        context.stopService(Intent(context, CallNotificationService::class.java))

        coroutineScope.launch {
            if (MethodChannelHelper.isInitialized()) {
                try {
                    val response = MethodChannelHelper.sendMessageToFlutter(callData)
                    println("Response from Flutter: $response")
                } catch (e: Exception) {
                    println("Error sending message to Flutter: ${e.message}")
                }
            } else {
                println("Error: MethodChannel is not initialized")
            }
        }
    }
}