import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

import 'notification_storage_helper.dart';

class ChatNotificationPlugin {
  static const MethodChannel _channel = MethodChannel('Chat notifications');

  static Future<void> showCallNotification(Map<String, dynamic> data) async {
    try {
      int? notificationId = data['notificationId'];

      if (notificationId != null) {
        String lastNotificationId = await NotificationStorageHelper.getLastNotificationId();

        if (lastNotificationId == notificationId.toString()) {
          debugPrint("Duplicate notification detected in plugin, skipping call.");
          return;
        }

        await NotificationStorageHelper.storeLastNotificationId(notificationId.toString());
      }

      await _channel.invokeMethod('showCallNotification', data);
    } on PlatformException catch (e) {
      print("Error invoking showCallNotification: ${e.message}");
    }
  }

  static Future<void> cancelCallNotification(int notificationId) async {
    try {
      await _channel.invokeMethod('cancelCallNotification', {"notificationId": notificationId});
    } on PlatformException catch (e) {
      print("Error invoking cancelCallNotification: ${e.message}");
    }
  }

  static Future<void> initialize() async {
    await _channel.invokeMethod('initialize');
  }

  /// Whether the OS still lets this app launch the full-screen incoming call
  /// screen.
  ///
  /// Android 14 (API 34) turned `USE_FULL_SCREEN_INTENT` into a user-revocable
  /// special access. When it is revoked the call notification is still posted
  /// and still rings — Android just shows it as a heads-up instead of opening
  /// the full-screen call UI.
  ///
  /// Returns `true` on older Android versions and on any failure, so callers
  /// never nag the user because of an OEM quirk.
  static Future<bool> canUseFullScreenIntent() async {
    try {
      final result = await _channel.invokeMethod<bool>('canUseFullScreenIntent');
      return result ?? true;
    } on PlatformException catch (e) {
      debugPrint("Error invoking canUseFullScreenIntent: ${e.message}");
      return true;
    } on MissingPluginException {
      return true;
    }
  }

  /// Opens the system "Full screen notifications" page for this app.
  ///
  /// Returns `false` when there is nothing to open (below Android 14) or when
  /// no activity on the device handles the intent.
  static Future<bool> openFullScreenIntentSettings() async {
    try {
      final result =
          await _channel.invokeMethod<bool>('openFullScreenIntentSettings');
      return result ?? false;
    } on PlatformException catch (e) {
      debugPrint("Error invoking openFullScreenIntentSettings: ${e.message}");
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  // Future<void> triggerIncomingCallNotificationFromPlugin(String messageDataJson) async {
  //   try {
  //     final Map<String, dynamic> data = jsonDecode(messageDataJson);
  //     final String type = data['type'] ?? "alert";
  //
  //     if (["chat", "video", "call"].contains(type)) {
  //       final intent = AndroidIntent(
  //         action: "com.example.zastro_android_call_notifications.SHOW_CALL_NOTIFICATION",
  //         arguments: {
  //           "message_data_in_string": messageDataJson,
  //         },
  //         flags: const <int>[
  //           Flag.FLAG_INCLUDE_STOPPED_PACKAGES,
  //           Flag.FLAG_RECEIVER_FOREGROUND,
  //         ],
  //       );
  //       await intent.sendBroadcast();
  //     } else if (type == "cancel") {
  //       final int notificationId =
  //           int.tryParse(data['notification_id'] ?? "-1") ?? -1;
  //
  //       final intent = AndroidIntent(
  //         action: "com.example.zastro_android_call_notifications.CANCEL_CALL_NOTIFICATION",
  //         arguments: {
  //           "notificationId": notificationId,
  //         },
  //         flags: const <int>[
  //           Flag.FLAG_INCLUDE_STOPPED_PACKAGES,
  //           Flag.FLAG_RECEIVER_FOREGROUND,
  //         ],
  //       );
  //       await intent.sendBroadcast();
  //     }
  //   } catch (e) {
  //     debugPrint("Plugin trigger error: $e");
  //   }
  // }

  static Future<void> triggerBroadcastNotification(String messageDataJson) async {
    try {
      print("Sending JSON to Kotlin: $messageDataJson");
      await _channel.invokeMethod('triggerBroadcastNotification', {'message_data_in_string': messageDataJson});
      print("Sent JSON to Kotlin: $messageDataJson");
    } on PlatformException catch (e) {
      print("Error invoking triggerBroadcastNotification: ${e.message}");
    }
  }
}
