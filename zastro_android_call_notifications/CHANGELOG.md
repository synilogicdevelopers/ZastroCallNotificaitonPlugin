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
- The call screen shows the host app's icon and name, the caller's photo with the
  app icon badged on it, the call type, and the time the call started ringing.
- Caller photo loading rewritten (`CallerPhotoLoader`). The old inline loader
  called `BitmapFactory.decodeStream()` straight on a socket, which returns null
  for slow or chunked responses, and did not follow redirects — the two usual
  reasons a caller photo silently never appeared. It now buffers the body,
  follows redirects, downsamples large images, and is shared between the
  notification and the call screen so the photo is fetched once.
- Cleartext `http://` photo urls are retried over `https://`. Android blocks
  cleartext by default from targetSdk 28, so an `http://` url in a push payload
  always failed natively even though the same url loads fine in Flutter.
- Added `InitialsAvatar`: when no photo is available the caller's initials are
  drawn in a colour derived from their name, in both the notification and the
  call screen.
- **Custom ringtone support.** A ringtone can be supplied per call via
  `showCallNotification({'ringtone': ...})`, per call from the server via a
  `ringtone` field in the FCM payload, or app-wide via
  `ChatNotificationPlugin.setDefaultRingtone(...)`. Accepts a Flutter asset path,
  an `res/raw` name, a `content://`/`file://`/`http(s)://` uri, or an absolute
  path. Anything unresolvable falls back to the system ringtone, so a bad value
  can never leave a call silent.
- Incoming calls now use their own **silent** notification channel,
  `zastro_incoming_call`. Previously the call posted to the host app's channel,
  whose sound and vibration are fixed at creation and could not be overridden —
  so the channel rang the system ringtone while the service rang its own. Two
  ringtones and two vibrations, masked only because both sounds used to be the
  same system ringtone. The ringtone and vibration belong to the service, which
  is what makes a custom ringtone possible.
- The ringtone now starts immediately instead of at the end of the caller-photo
  download, which had left the phone silent until the picture was fetched — up
  to the full network timeout on a cold start, i.e. exactly the terminated-app
  case. A refused audio-focus request no longer silently suppresses the ring
  either; it is logged and playback proceeds.
- Added `ChatNotificationPlugin.setCallNotificationAppearance(...)` for the
  notification's small icon and accent colour. The small icon now defaults to the
  host app's own `ic_notification` where one exists.
- The notification's timestamp is the moment the call started ringing rather than
  being restamped whenever the notification is rebuilt, and the call type and
  start time are written into the body text.
- Answer and decline now reliably bring the app to the foreground.
  `FLAG_ACTIVITY_NEW_TASK` was being wiped from the launch intent by an
  assignment to `flags`, so an activity started from those pending intents had no
  task to land in whenever the app was not already foregrounded. The call screen
  also no longer routes actions through `TransparentActivity` (they share an
  empty `taskAffinity`, so finishing the call screen tore down the freshly
  launched activity with it) and closes with `finish()` rather than
  `finishAndRemoveTask()`, which was navigating to the home screen.
- Implemented `clearNotificationData`, which host apps were already calling and
  which previously always threw `MissingPluginException`. It clears both the
  cached data and the activity's intent extras.

## 0.0.12
- Fixed: with no custom ringtone configured, an incoming call could ring with no
  sound at all. Only `RingtoneManager.getDefaultUri(TYPE_RINGTONE)` was tried,
  which resolves to whatever tone the user picked — and when that pick lives in
  external media, reading it needs `READ_MEDIA_AUDIO` on Android 13+, a
  permission a calling app has no reason to hold. `setDataSource` threw
  `SecurityException`, and because 0.0.11 made the call notification channel
  silent by design there was nothing else left to make a sound.
  The system ringtone now falls through several sources — the user's tone, the
  default alias, then the built-in ringtone and notification tones, which never
  need a permission — and logs which one it ended up using.

