import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'zastro_android_call_notifications_method_channel.dart';

abstract class ZastroAndroidCallNotificationsPlatform extends PlatformInterface {
  /// Constructs a ZastroAndroidCallNotificationsPlatform.
  ZastroAndroidCallNotificationsPlatform() : super(token: _token);

  static final Object _token = Object();

  static ZastroAndroidCallNotificationsPlatform _instance = MethodChannelZastroAndroidCallNotifications();

  /// The default instance of [ZastroAndroidCallNotificationsPlatform] to use.
  ///
  /// Defaults to [MethodChannelZastroAndroidCallNotifications].
  static ZastroAndroidCallNotificationsPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [ZastroAndroidCallNotificationsPlatform] when
  /// they register themselves.
  static set instance(ZastroAndroidCallNotificationsPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
