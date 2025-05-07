import 'package:flutter_test/flutter_test.dart';
import 'package:zastro_android_call_notifications/zastro_android_call_notifications.dart';
import 'package:zastro_android_call_notifications/zastro_android_call_notifications_platform_interface.dart';
import 'package:zastro_android_call_notifications/zastro_android_call_notifications_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockZastroAndroidCallNotificationsPlatform
    with MockPlatformInterfaceMixin
    implements ZastroAndroidCallNotificationsPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final ZastroAndroidCallNotificationsPlatform initialPlatform = ZastroAndroidCallNotificationsPlatform.instance;

  test('$MethodChannelZastroAndroidCallNotifications is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelZastroAndroidCallNotifications>());
  });

  test('getPlatformVersion', () async {
    ZastroAndroidCallNotifications zastroAndroidCallNotificationsPlugin = ZastroAndroidCallNotifications();
    MockZastroAndroidCallNotificationsPlatform fakePlatform = MockZastroAndroidCallNotificationsPlatform();
    ZastroAndroidCallNotificationsPlatform.instance = fakePlatform;

    expect(await zastroAndroidCallNotificationsPlugin.getPlatformVersion(), '42');
  });
}
