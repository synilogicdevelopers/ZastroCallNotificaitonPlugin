# zastro_android_call_notifications

## Getting Started

This project is a starting point for a Flutter
[plug-in package](https://flutter.dev/to/develop-plugins),
a specialized package that includes platform-specific implementation code for
Android.

For help getting started with Flutter development, view the documentation below:

A Flutter plugin to trigger call and microphone
notifications — including:

- **Incoming ringing call notifications** with ringtone and vibration
- **Ongoing call UI** with call timer updates
- **Microphone recording indicators**

Built for apps that require custom **VoIP-style UI** using foreground services.

## Features

✅ Incoming call notifications with full-screen UI  
✅ Ongoing call notification with timer  
✅ Mic recording notification with persistent indicator   
✅ Foreground service support for Android 10+

## Getting Started

1. Add dependency

dependencies:
zastro_android_call_notifications: ^<latest_version>

2. Import the package

3. Example usage

    ///Important Permissions for Notifications
    Future<void> requestImportantPermissions() async {
    Map<Permission, PermissionStatus> statuses = await [
    Permission.phone, // For CALL & FOREGROUND_SERVICE_PHONE_CALL
    // Permission.manageExternalStorage, // For MANAGE_OWN_CALLS (optional)
    Permission.notification, // For POST_NOTIFICATIONS (Android 13+)
    Permission.microphone,
    ].request();
    
    statuses.forEach((permission, status) {
    debugPrint('$permission: $status');
    });
    
    if (await Permission.phone.isDenied ||
    await Permission.notification.isDenied ||
    await Permission.microphone.isDenied) {
    debugPrint('Some permissions were denied. App may not function properly.');
    }
    }
    
    void handleCallResponse(Map<String, dynamic> data, String responseText) {
    Get.to(() => NotificationResponseScreen(NotificationResponseModel(
    data['uniqueId'],
    data['customerUniId'],
    data['caller_name'],
    data['caller_image'],
    data['notificationId'],
    responseText,
    data['type'])));
    }
    
    void setupMethodChannel() {
    channel.setMethodCallHandler((call) async {
    if (call.method == "onCallAction") {
    // Explicitly casting call.arguments to avoid type issues
    final Map<String, dynamic>? data = call.arguments != null
    ? Map<String, dynamic>.from(call.arguments as Map)
    : null;
    
          debugPrint("Android data : $data");
    
          if (data != null) {
            if (data['type'] == "chat") {
              if (data['action'] == "ACTION_ANSWER_CALL") {
                debugPrint("Call Accepted: ${data['caller_name']}");
                handleCallResponse(data, "ACCEPT CHAT");
              } else if (data['action'] == "ACTION_DECLINE_CALL") {
                debugPrint("Call Declined");
                handleCallResponse(data, "REJECT CHAT");
              } else if (data['action'] == "CALL_NOTIFICATION_CLICK") {
                Get.toNamed('/chatRequest');
              }
            }
            else if (data['type'] == "video") {
              if (data['action'] == "ACTION_ANSWER_CALL") {
                debugPrint("Call Accepted: ${data['caller_name']}");
                handleCallResponse(data, "ACCEPT VIDEO");
              } else if (data['action'] == "ACTION_DECLINE_CALL") {
                debugPrint("Call Declined");
                handleCallResponse(data, "REJECT VIDEO");
              } else if (data['action'] == "CALL_NOTIFICATION_CLICK") {
                Get.toNamed('/videoRequest');
              }
            }
            else if (data['type'] == "call") {
              if (data['action'] == "ACTION_ANSWER_CALL") {
                debugPrint("Call Accepted: ${data['caller_name']}");
                handleCallResponse(data, "ACCEPT CALL");
              } else if (data['action'] == "ACTION_DECLINE_CALL") {
                debugPrint("Call Declined");
                handleCallResponse(data, "REJECT CALL");
              } else if (data['action'] == "CALL_NOTIFICATION_CLICK") {
                Get.toNamed('/voiceRequest');
              }
            }
          }
        }
        return null;
    
    });
    }
    
    if (Platform.isAndroid) {
    await ChatNotificationPlugin.showCallNotification({
    "type": type,
    'uniqueId': uniqueId,
    "customerUniId": customerUniId,
    "notificationId": notificationId,
    "caller_name": customerName,
    "caller_image": customerImage,
    "message_data_in_string": jsonString,
    }); ///Show call notification
    }
    
    else if (type == "cancel") {
    if (Platform.isAndroid) {
    await ChatNotificationPlugin.cancelCallNotification(notificationId); ///Cancel Notification
    } else {
    // cancelNotification(notificationId);
    }
    }
    
    if(Platform.isAndroid){
    await ChatNotificationPlugin.triggerBroadcastNotification(
    jsonEncode(message.data)); ///background call
    }
    
    else if (type == "cancel") {
    if(Platform.isAndroid){
    await ChatNotificationPlugin.triggerBroadcastNotification(
    jsonEncode(message.data)); ///Cancel Notification
    } else {
    // cancelNotification(notificationId);
    }
    }


Android Setup
Required for foreground service and notification permissions.

AndroidManifest.xml

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

