import 'package:shared_preferences/shared_preferences.dart';

class NotificationStorageHelper {
  static const String _lastNotificationIdKey = 'last_notification_id';

  static Future<void> storeLastNotificationId(String id) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_lastNotificationIdKey, id);
  }

  static Future<String> getLastNotificationId() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_lastNotificationIdKey) ?? '';
  }
}
