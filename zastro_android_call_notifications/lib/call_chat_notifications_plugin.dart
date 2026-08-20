import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

import 'notification_storage_helper.dart';

class ChatNotificationPlugin {
  static const MethodChannel _channel = MethodChannel('Chat notifications');

  /// Shows the incoming call notification and full screen call screen.
  ///
  /// [data] keys: `type`, `uniqueId`, `customerUniId`, `notificationId`,
  /// `caller_name`, `caller_image`, `message_data_in_string`, and optionally
  /// `ringtone` to override the ringtone for this one call — see
  /// [setDefaultRingtone] for the accepted values.
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

  /// Sets the ringtone used for incoming calls that do not name one themselves,
  /// including calls raised from a data-only FCM payload while the app is dead.
  ///
  /// Accepted values for [ringtone] (and for the per-call `"ringtone"` key of
  /// [showCallNotification], and the `"ringtone"` field of an FCM payload):
  ///
  /// * `null`, `''` or `'default'` — the system ringtone.
  /// * a Flutter asset path, e.g. `'assets/sounds/incoming_call.mp3'`. It must
  ///   be listed under `flutter: assets:` in the host app's `pubspec.yaml`.
  /// * an Android raw resource, e.g. `'raw/incoming_call'` or `'incoming_call'`
  ///   for `android/app/src/main/res/raw/incoming_call.mp3`.
  /// * a `content://`, `android.resource://`, `file://` or `http(s)://` uri.
  /// * an absolute file path, e.g. `'/storage/emulated/0/tone.mp3'`.
  ///
  /// Anything that cannot be resolved falls back to the system ringtone, so a
  /// bad value can never leave an incoming call silent.
  static Future<void> setDefaultRingtone(String? ringtone) async {
    try {
      await _channel.invokeMethod('setDefaultRingtone', {'ringtone': ringtone});
    } on PlatformException catch (e) {
      debugPrint("Error invoking setDefaultRingtone: ${e.message}");
    } on MissingPluginException {
      // Non-Android platform — nothing to configure.
    }
  }

  /// Configures how the incoming call notification looks.
  ///
  /// Stored natively, so it also applies to calls raised from a data-only FCM
  /// payload while the app is dead. Call it once during startup.
  ///
  /// * [smallIcon] — drawable name for the status bar icon, e.g.
  ///   `'ic_notification'`. A small icon is drawn from its alpha channel only,
  ///   so this must be a monochrome notification icon, never the full colour
  ///   launcher icon. When omitted the plugin looks for `ic_notification`,
  ///   `ic_stat_notification` and `ic_stat_name` in the host app before falling
  ///   back to its own icon.
  /// * [accentColor] — `'#RRGGBB'` or `'#AARRGGBB'`, tints the icon and actions.
  /// * [colorized] — paints the whole notification in [accentColor]. Off by
  ///   default: a light brand colour makes a colorized notification unreadable,
  ///   so check it on a real device before turning it on.
  static Future<void> setCallNotificationAppearance({
    String? smallIcon,
    String? accentColor,
    bool? colorized,
  }) async {
    try {
      await _channel.invokeMethod('setCallNotificationAppearance', {
        'smallIcon': smallIcon,
        'accentColor': accentColor,
        'colorized': colorized,
      });
    } on PlatformException catch (e) {
      debugPrint("Error invoking setCallNotificationAppearance: ${e.message}");
    } on MissingPluginException {
      // Non-Android platform — nothing to configure.
    }
  }

  /// The ringtone previously stored with [setDefaultRingtone], or null.
  static Future<String?> getDefaultRingtone() async {
    try {
      return await _channel.invokeMethod<String>('getDefaultRingtone');
    } on PlatformException catch (e) {
      debugPrint("Error invoking getDefaultRingtone: ${e.message}");
      return null;
    } on MissingPluginException {
      return null;
    }
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
