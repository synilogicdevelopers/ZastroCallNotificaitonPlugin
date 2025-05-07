package com.example.zastro_android_call_notifications

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.app.ActivityManager
import android.util.Log

class TransparentActivity : Activity() {

    companion object {
        fun getIntent(context: Context, action: String, messageData: String?, data: Bundle?): Intent {
            val intent = Intent(context, TransparentActivity::class.java)
            intent.action = action
            intent.putExtra("message_data_in_string", messageData)
            intent.putExtra("data", data)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return intent
        }
    }


    override fun onStart() {
        super.onStart()
        setVisible(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val messageDataInString = intent.getStringExtra("message_data_in_string") ?: "No Data"

        val data = intent.getBundleExtra("data")


        val notificationId = data?.getInt("notificationId", -1) ?: -1
        val callerName = data?.getString("caller_name") ?: "Unknown Caller"
        val callerImage = data?.getString("caller_image") ?: ""
        val type = data?.getString("type") ?: ""
        val uniqueId = data?.getString("uniqueId") ?: ""
        val customerUniId = data?.getString("customerUniId") ?: ""

        val broadcastIntent = Intent(intent.action).apply {
            putExtra("notificationId", notificationId)
            putExtra("caller_name", callerName)
            putExtra("caller_image", callerImage)
            putExtra("type", type)
            putExtra("uniqueId", uniqueId)
            putExtra("customerUniId", customerUniId)
        }

        println("Sending BroadcastmessageDataInString: $messageDataInString")
        broadcastIntent.setPackage(packageName)
        sendBroadcast(broadcastIntent)

        val context = applicationContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            putExtra("message_data_in_string", messageDataInString)
            putExtra("key", intent.action)
        }

        if (launchIntent != null) {
            context.startActivity(launchIntent)
            Log.d("TransparentActivity", "App main activity launched")
        } else {
            Log.e("TransparentActivity", "Failed to get launch intent for app")
        }

        finish()
        overridePendingTransition(0, 0) // No animation
    }
}
