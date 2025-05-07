package com.example.zastro_android_call_notifications

import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object MethodChannelHelper {
    private var methodChannel: MethodChannel? = null

    fun setMethodChannel(channel: MethodChannel) {
        methodChannel = channel
        println("MethodChannel set successfully")
    }

    suspend fun sendMessageToFlutter(data: Map<String, Any?>): String? {
        return suspendCancellableCoroutine { continuation ->
            if (methodChannel == null) {
                println("Error: MethodChannel is not initialized!")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            // Important to explicitly convert the values
            val formattedData: Map<String, Any?> = data.mapValues { it.value as Any? }

            println("Sending formatted message to Flutter: $formattedData")
            methodChannel?.invokeMethod("onCallAction", formattedData, object : MethodChannel.Result {
                override fun success(result: Any?) {
                    println("Flutter successfully received: $result")
                    continuation.resume(result?.toString())
                }

                override fun error(errorCode: String, errorMessage: String?, errorDetails: Any?) {
                    println("Error sending to Flutter: $errorCode - $errorMessage")
                    continuation.resume(null)
                }

                override fun notImplemented() {
                    println("Method not implemented in Flutter!")
                    continuation.resume(null)
                }
            })
        }
    }

    fun dispose() {
        println("Disposing MethodChannel...")
        methodChannel = null
    }

    fun isInitialized(): Boolean {
        return methodChannel != null
    }
}
