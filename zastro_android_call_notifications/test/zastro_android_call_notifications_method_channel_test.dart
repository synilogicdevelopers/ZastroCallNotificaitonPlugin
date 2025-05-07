import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zastro_android_call_notifications/zastro_android_call_notifications_method_channel.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  MethodChannelZastroAndroidCallNotifications platform = MethodChannelZastroAndroidCallNotifications();
  const MethodChannel channel = MethodChannel('zastro_android_call_notifications');

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      channel,
      (MethodCall methodCall) async {
        return '42';
      },
    );
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null);
  });

  test('getPlatformVersion', () async {
    expect(await platform.getPlatformVersion(), '42');
  });
}
