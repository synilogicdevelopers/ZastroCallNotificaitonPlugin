import 'dart:isolate';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class OngoingCallNotificationHandler {


  static const MethodChannel _ongoingCallChannel = MethodChannel('Chat notifications');


  static Future<void> startOnGoingCallNotification(int seconds) async {
    print('[Isolate] startOnGoingCallNotification() Hash: ${Isolate.current.hashCode}');
    try {
      await _ongoingCallChannel.invokeMethod('startOngoingCallNotification', {
        'call_duration_seconds': seconds,
      });
    } catch (e) {
      if (kDebugMode) {
        print("Error starting call notification: $e");
      }
    }
  }

  static Future<void> startMicNotification() async {
    print('[Isolate] startMicNotification() Hash: ${Isolate.current.hashCode}');
    try {
      await _ongoingCallChannel.invokeMethod('startMicNotification');
    } catch (e) {
      if (kDebugMode) {
        print("Error starting mic notification: $e");
      }
    }
  }

  static Future<void> updateCallDuration(int seconds) async {
    print('[Isolate] updateCallTimer() Hash: ${Isolate.current.hashCode}');
    try {
      await _ongoingCallChannel.invokeMethod('updateCallDuration', {
        'call_duration_seconds': seconds,
      });
    } catch (e) {
      if (kDebugMode) {
        print("Error updating call duration: $e");
      }
    }
  }

  static Future<void> stopOngoingCallNotification() async {
    print('[Isolate] stopOngoingCallNotification() Hash: ${Isolate.current.hashCode}');
    try {
      await _ongoingCallChannel.invokeMethod('stopOngoingCallNotification');
    } catch (e) {
      if (kDebugMode) {
        print("Error stopping call notification: $e");
      }
    }
  }

  static Future<void> stopMicNotification() async {
    print('[Isolate] stopMicNotification() Hash: ${Isolate.current.hashCode}');
    try {
      await _ongoingCallChannel.invokeMethod('stopMicNotification');
    } catch (e) {
      if (kDebugMode) {
        print("Error stopping mic notification: $e");
      }
    }
  }

}
