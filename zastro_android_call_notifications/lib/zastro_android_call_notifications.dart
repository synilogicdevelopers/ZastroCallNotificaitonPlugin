
import 'zastro_android_call_notifications_platform_interface.dart';

class ZastroAndroidCallNotifications {
  Future<String?> getPlatformVersion() {
    return ZastroAndroidCallNotificationsPlatform.instance.getPlatformVersion();
  }
}
