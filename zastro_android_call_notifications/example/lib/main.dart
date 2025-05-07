import 'dart:convert';
import 'dart:io';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:zastro_android_call_notifications/call_chat_notifications_plugin.dart';
import 'package:zastro_android_call_notifications/zastro_android_call_notifications.dart';

void main() async {
  await ChatNotificationPlugin.initialize(); ///Initialize plugin

  requestImportantPermissions(); /// Important Permissions for Notifications
  // setupMethodChannel(); /// Important for Notifications
  runApp(const MyApp());
}

/*void handleCallResponse(Map<String, dynamic> data, String responseText) {
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
}*/

@pragma('vm:entry-point')
Future<void> configureFirebaseMessaging() async {
  FirebaseMessaging.instance.requestPermission(
      alert: true,
      announcement: false,
      badge: true,
      carPlay: false,
      criticalAlert: true,
      provisional: false,
      sound: true);

  // Handle the received message when the app is in background
  FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);

  // Handle the received message when the app is in foreground
  FirebaseMessaging.onMessage.listen((RemoteMessage message) async {
    debugPrint(
        "main notification (onMessage) Data-> ${message.data.toString()}");
    Map<String, dynamic> messageData = Map<String, dynamic>.from(message.data);/// *** Add for plugin
    String jsonString = jsonEncode(messageData);/// *** Add for plugin
    debugPrint("Sending JSON to Android: $jsonString");
    String? title;
    String? body;
    String type = "alert";
    String image = '';
    String logo = '';
    int notificationId = -1;
    if (message.data.isNotEmpty) {
      //PayloadData
      title = message.data['title'];
      body = message.data['body'];
      type = message.data['type'];
      image = message.data['image'];
      logo = message.data['logo'];
      notificationId = int.parse(message.data['notification_id']);
    } else {
      title = message.notification?.title;
      body = message.notification?.body;
    }

    // Validate if the notification ID is same as the last one
    // if (lastNotificationId == notificationId.toString()) {
    //   debugPrint("Duplicate notification detected, ignoring.");
    //   return;  // Skip processing this notification
    // }
    //
    // // Store this notification ID for next comparison
    // await storeLastNotificationId(notificationId.toString());

    if (type == "call") {
      String? uniqueId = message.data['uniqueId'];
      String? customerUniId = message.data['customerUniId'];
      String? customerName = message.data['customerName'];
      String? customerImage = message.data['customerImage'];
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
      } else {
        // cancelAllNotifications();
        // AwesomeNotifications().createNotification(
      }
    } else if (type == "cancel") {
      if (Platform.isAndroid) {
        await ChatNotificationPlugin.cancelCallNotification(notificationId); ///Cancel Notification
      } else {
        // cancelNotification(notificationId);
      }
    }
  });

}

@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {

  debugPrint(
      "main notification (onBackgroundMessage) Data-> ${message.data.toString()}");
  String? title;
  String? body;
  String type = "alert";
  String image = '';
  String logo = '';
  int notificationId = -1;
  if (message.data.isNotEmpty) {
    //PayloadData
    title = message.data['title'];
    body = message.data['body'];
    type = message.data['type'];
    image = message.data['image'];
    logo = message.data['logo'];
    notificationId = int.parse(message.data['notification_id']);
  } else {
    title = message.notification?.title;
    body = message.notification?.body;
  }

  if (type == "chat") {
    String? uniqueId = message.data['uniqueId'];
    String? customerUniId = message.data['customerUniId'];
    String? customerName = message.data['customerName'];
    String? customerImage = message.data['customerImage'];
    if(Platform.isAndroid){
      await ChatNotificationPlugin.triggerBroadcastNotification(
          jsonEncode(message.data)); ///background call
    } else {
      // cancelAllNotifications();
      // AwesomeNotifications().createNotification(
    }
  } else if (type == "cancel") {
    if(Platform.isAndroid){
      await ChatNotificationPlugin.triggerBroadcastNotification(
          jsonEncode(message.data)); ///Cancel Notification
    } else {
    // cancelNotification(notificationId);
    }
  } else {
    // AwesomeNotifications().createNotification(
    //     content: NotificationContent(
  }
}

// Future<void> onActionReceivedImplementationMethod(
//     ReceivedAction receivedAction) async {
//   debugPrint(
//       'main notification (onActionReceivedImplementationMethod) $receivedAction');
//   if (receivedAction.actionLifeCycle == NotificationLifeCycle.Foreground) {
//     debugPrint('Notification tapped in foreground.');
//     if (receivedAction.buttonKeyPressed == "ACCEPT CHAT" ||
//         receivedAction.buttonKeyPressed == "REJECT CHAT" ||
//         receivedAction.payload!['type'] == "chat") {
//   } else {
//     Get.toNamed('/dashboard');
//   }
// }


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

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  final _zastroAndroidCallNotificationsPlugin =
      ZastroAndroidCallNotifications();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    // We also handle the message potentially returning null.
    try {
      platformVersion =
          await _zastroAndroidCallNotificationsPlugin.getPlatformVersion() ??
              'Unknown platform version';
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Text('Running on: $_platformVersion\n'),
        ),
      ),
    );
  }
}


///Important changes in Home controller for handling bg notifications
class HomeController extends GetxController with WidgetsBindingObserver {
  static const platform = MethodChannel('Chat notifications');
  Map<String, dynamic>? notificationData;
  Map<String, dynamic>? notificationDataStored;
  int? lastProcessedNotificationId;
/*
  @override
  Future<void> onInit() async {
    super.onInit();
    WidgetsBinding.instance.addObserver(this); //Important line to initiate WidgetBinding Observer
    getDashboard();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await getNotificationData();
    });
  }

  @override
  void onClose() {
    WidgetsBinding.instance.removeObserver(this);
    super.onClose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      debugPrint("App is in resumed state, fetching notification data...");
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        await getNotificationData(isFromResume: true);
      });
    }
  }

  Future<void> getNotificationData({bool? isFromResume}) async {
    try {
      if (kDebugMode) {
        print("Invoking method channel to get notification data...");
      }

      final Map<dynamic, dynamic>? data =
      await platform.invokeMethod('notificationData');

      if (data != null) {
        if (kDebugMode) {
          // var messageData = data['message_data_in_string'];
          // print("Received data: $messageData");
          print("Received data: $data");
        }
        final String? actionKey = data['key'];
        // final notificationJson = data['notificationJson'];
        dynamic notificationJson;
        if (data.containsKey('message_data_in_string')) {
          notificationJson = data['message_data_in_string'];
          *//*try {
            final Map<String, dynamic> messageData = jsonDecode(notificationJson);
            print("Parsed Message Data: $messageData");
          } catch (e) {
            print("JSON Parsing Error: $e");
          }*//*
        } else {
          notificationJson = data['notificationJson'];
        }

        if (notificationJson != null) {
          final Map<String, dynamic> notificationData =
          jsonDecode(notificationJson);

          if (kDebugMode) {
            print("Parsed notificationData: $notificationData");
          }

          *//*final Map<String, dynamic> content =
                notificationData['content'] ?? {};
            final Map<String, dynamic> payload = content['payload'] ?? {};
            final String url = payload['url'] ?? '';
            final int notificationId = content['id'] ?? 0;*//*
          Map<String, dynamic> content = {};
          Map<String, dynamic> payload = {};
          String url = '';
          int notificationId = 0;
          if (data.containsKey('message_data_in_string')) {
            notificationId = int.tryParse(notificationData['notification_id'].toString()) ?? 0;
          } else {
            content =
                notificationData['content'] ?? {};
            payload = content['payload'] ?? {};
            url = payload['url'] ?? '';
            notificationId = content['id'] ?? 0;
          }

          String? rawNotificationId = await SharedPreferencesHelper.getData(
              AppConstants.lastProcessedNotificationId);
          // lastProcessedNotificationId = int.tryParse(rawNotificationId ?? '') ?? 0; //Remove below line, keep this line instead, but not right now as it is not tested, if ever troubles, use this one -Zaid
          if (rawNotificationId != null && rawNotificationId.isNotEmpty) {
            lastProcessedNotificationId = int.tryParse(rawNotificationId) ?? 0;
          } else {
            lastProcessedNotificationId = 0;
          }
          if (kDebugMode) {
            print(
                "Raw Notification ID from SharedPreferences: $rawNotificationId");
          }

          if (kDebugMode) {
            print(
                "URL: $url, ID: $notificationId, lastID: $lastProcessedNotificationId, key: $actionKey");
          }

          if (notificationId != lastProcessedNotificationId) {
            await SharedPreferencesHelper.saveData(
                AppConstants.lastProcessedNotificationId,
                notificationId.toString());
            if (kDebugMode) {
              print("called");
            }
            if (isFromResume == true) {
              if (kDebugMode) {
                print("Handling notification on resume...");
                print("Notiff${notificationData.toString()}");
              }
              handleNavigation(content, actionKey, notificationData, isFromResume: isFromResume);
            } else {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                handleNavigation(content, actionKey, notificationData);
              });
            }
            // WidgetsBinding.instance.addPostFrameCallback((_) {
            //   print("addPostFrameCallback");processNotificationAction
            //   if (actionKey == null) {
            //     if (content['payload']['type'] == "chat") {
            //       Get.toNamed('/chatRequest');
            //     } else if (content['payload']['type'] ==
            //         "video") {
            //       Get.toNamed('/videoRequest');
            //     } else if (content['payload']['type'] ==
            //         "call") {
            //       Get.toNamed('/voiceRequest');
            //     }
            //   } else {
            //     print("processNotificationAction");
            //     processNotificationAction(notificationData, actionKey);
            //   }
            // });
          }
        } else {
          if (kDebugMode) {
            print("No notificationJson found");
          }
        }
      } else {
        if (kDebugMode) {
          print("No data received from method channel");
        }
      }
    } on PlatformException catch (e) {
      if (kDebugMode) {
        print("Failed to get notification data: ${e.message}");
      }
    } on MissingPluginException catch (e) {
      if (kDebugMode) {
        print(
            "MissingPluginException: Method not implemented on native side - ${e.message}");
      }
    } catch (e) {
      if (kDebugMode) {
        print("Error fetching notification data: $e");
      }
    }
  }

  void handleNavigation(Map<String, dynamic> content, String? actionKey,
      Map<String, dynamic> notificationData, {bool? isFromResume}) {
    if (actionKey == null) {
      if (content['payload']['type'] == "chat") {
        Get.toNamed('/chatRequest');
      } else if (content['payload']['type'] == "video") {
        Get.toNamed('/videoRequest');
      } else if (content['payload']['type'] == "call") {
        Get.toNamed('/voiceRequest');
      }
    } else if (actionKey == "CALL_NOTIFICATION_CLICK") {
      if (notificationData['type'] == "chat") {
        Get.toNamed('/chatRequest');
      } else if (notificationData['type'] == "video") {
        Get.toNamed('/videoRequest');
      } else if (notificationData['type'] == "call") {
        Get.toNamed('/voiceRequest');
      }
    } else {
      if (isFromResume == true) {
        processNotificationAction(notificationData, actionKey);
      } else {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          processNotificationAction(notificationData, actionKey);
        });
      }
    }
  }


  Future<void> processNotificationAction(
      Map<String, dynamic> notificationJson, String receivedActionKey) async {
    // WidgetsBinding.instance.addPostFrameCallback((_) async {
    if(receivedActionKey == "ACTION_ANSWER_CALL" || receivedActionKey == "ACTION_DECLINE_CALL"){
      try {
        final int? notificationId =
        int.tryParse(notificationJson['notification_id']?.toString() ?? '');
        if (notificationId == null) {
          if (kDebugMode) {
            print("Invalid or null notificationId");
          }
          return;
        }
        if (kDebugMode) {
          print('''
        ${notificationJson['uniqueId']},
        ${notificationJson['customerUniId']},
        ${notificationJson['customerName']},
        ${notificationJson['customerImage']},
        $notificationId,
        $receivedActionKey,
        ${notificationJson['type']},
        ''');
        }

        if (notificationJson['type'] == "chat") {
          Get.to(() => NotificationResponseScreen(NotificationResponseModel(
            notificationJson['uniqueId'],
            notificationJson['customerUniId'],
            notificationJson['customerName'],
            notificationJson['customerImage'],
            notificationId,
            receivedActionKey == "ACTION_ANSWER_CALL" ? "ACCEPT CHAT" : "REJECT CHAT",
            notificationJson['type'],
          )));
        } else if (notificationJson['type'] == "video") {
          Get.to(() => NotificationResponseScreen(NotificationResponseModel(
            notificationJson['uniqueId'],
            notificationJson['customerUniId'],
            notificationJson['customerName'],
            notificationJson['customerImage'],
            notificationId,
            receivedActionKey == "ACTION_ANSWER_CALL" ? "ACCEPT VIDEO" : "REJECT VIDEO",
            notificationJson['type'],
          )));
        } else if (notificationJson['type'] == "call") {
          Get.to(() => NotificationResponseScreen(NotificationResponseModel(
            notificationJson['uniqueId'],
            notificationJson['customerUniId'],
            notificationJson['customerName'],
            notificationJson['customerImage'],
            notificationId,
            receivedActionKey == "ACTION_ANSWER_CALL" ? "ACCEPT CALL" : "REJECT CALL",
            notificationJson['type'],
          )));
        }
      } catch (e) {
        if (kDebugMode) {
          print("Error during navigation: $e");
        }
      }
    } else {
      try {
        final int? notificationId =
        int.tryParse(notificationJson['content']['id']?.toString() ?? '');
        if (notificationId == null) {
          if (kDebugMode) {
            print("Invalid or null notificationId");
          }
          return;
        }
        if (kDebugMode) {
          print('''
        ${notificationJson['content']['payload']['uniqueId']},
        ${notificationJson['content']['payload']['customerUniId']},
        ${notificationJson['content']['payload']['customerName']},
        ${notificationJson['content']['payload']['customerImage']},
        $notificationId,
        $receivedActionKey,
        ${notificationJson['content']['payload']['type']},
        ''');
        }
        if (receivedActionKey == "ACCEPT CHAT" ||
            receivedActionKey == "REJECT CHAT" ||
            notificationJson['content']['payload']['type'] == "chat") {
          Get.to(() => NotificationResponseScreen(NotificationResponseModel(
            notificationJson['content']['payload']['uniqueId'],
            notificationJson['content']['payload']['customerUniId'],
            notificationJson['content']['payload']['customerName'],
            notificationJson['content']['payload']['customerImage'],
            notificationId,
            receivedActionKey,
            notificationJson['content']['payload']['type'],
          )));
        } else if (receivedActionKey == "ACCEPT VIDEO" ||
            receivedActionKey == "REJECT VIDEO" ||
            notificationJson['content']['payload']['type'] == "video") {
          Get.to(() => NotificationResponseScreen(NotificationResponseModel(
            notificationJson['content']['payload']['uniqueId'],
            notificationJson['content']['payload']['customerUniId'],
            notificationJson['content']['payload']['customerName'],
            notificationJson['content']['payload']['customerImage'],
            notificationId,
            receivedActionKey,
            notificationJson['content']['payload']['type'],
          )));
        } else if (receivedActionKey == "ACCEPT CALL" ||
            receivedActionKey == "REJECT CALL" ||
            notificationJson['content']['payload']['type'] == "call") {
          Get.to(() => NotificationResponseScreen(NotificationResponseModel(
            notificationJson['content']['payload']['uniqueId'],
            notificationJson['content']['payload']['customerUniId'],
            notificationJson['content']['payload']['customerName'],
            notificationJson['content']['payload']['customerImage'],
            notificationId,
            receivedActionKey,
            notificationJson['content']['payload']['type'],
          )));
        }
      } catch (e) {
        if (kDebugMode) {
          print("Error during navigation: $e");
        }
      }
    }
    // });
  }*/

}
