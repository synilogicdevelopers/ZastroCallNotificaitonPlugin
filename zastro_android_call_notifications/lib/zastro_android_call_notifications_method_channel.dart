import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'zastro_android_call_notifications_platform_interface.dart';

/// An implementation of [ZastroAndroidCallNotificationsPlatform] that uses method channels.
class MethodChannelZastroAndroidCallNotifications extends ZastroAndroidCallNotificationsPlatform {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('zastro_android_call_notifications');

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
