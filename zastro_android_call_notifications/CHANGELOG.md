## 0.0.1

## [0.0.2] - 2025-05-12
- Min Flutter SDK updated to 3.0.2


## [0.0.3] - 2025-05-12
- Compatible with AGP 8.1.0 and compileSdk 34

## [0.0.4] - 2025-05-12
- Solving unknown caller issue

## [0.0.5] - 2025-05-12
- Solving unknown caller issue, 0.0.4 version was a failed attempt.

## [0.0.6] - 2025-05-12
- Solving unknown caller issue, 0.0.5 version had a return error.

## [0.0.7] - 2025-05-12
- Solved unknown caller issue and Notification not cancelling issue. Please don`t use 0.0.4 - 0.0.6 versions .

## [0.0.8] - 2025-05-23
- Solved Ghost ring issue.

## [0.0.9] - 2025-07-03
- Fixed crash causing code.

## 0.0.10
- Fixed notification field: replaced `contentTitle` with `contentText` to correctly detect notification types (chat, video call, audio call).

## 0.0.11
- Added a WhatsApp-style full screen incoming call screen (`IncomingCallActivity`).
  The full screen intent on the call notification was previously built from an
  empty `Intent()`, so it resolved to nothing and never opened any UI — the call
  only ever appeared as a heads-up / lock screen notification. It now shows a
  real call screen while the app is in the background, terminated, or the device
  is locked or asleep.
- Accept / Decline on the new screen fire the exact same `PendingIntent`s the
  notification's own buttons carry, so existing host app call handling keeps
  working unchanged — no migration needed.
- Added `ChatNotificationPlugin.canUseFullScreenIntent()` and
  `ChatNotificationPlugin.openFullScreenIntentSettings()`. Android 14 (API 34)
  made `USE_FULL_SCREEN_INTENT` a user-revocable special access; when it is
  revoked Android silently downgrades the call to a heads-up notification, so
  host apps need to be able to detect and repair that.

