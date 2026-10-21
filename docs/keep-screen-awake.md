# Keep screen awake while listening

Enable **Settings → Voice Input Keyboard → Keep screen awake while listening**.
The setting defaults to off and persists in the existing Preferences DataStore.
It applies to dictation through the Transcribro keyboard.

`VoiceInput` owns the `InputMethodService` dialog window and recognition client.
`ListeningScreenAwakeController` adds `FLAG_KEEP_SCREEN_ON` to that window only
when the setting is enabled, the input view is active, the window is visible,
and a listening request is active. It starts with the request so model startup
is also covered. It clears the flag immediately on manual Stop or Cancel, final
results, any recognition error, input/view completion, window hiding, or service
destruction. A synchronous start failure also clears the flag before propagating.
Late recognition callbacks do not acquire the flag again after dismissal.

The existing recognition service reports `onEndOfSpeech` between phrases even in
continuous mode. Those pauses keep the flag active; in auto-stop mode,
`onEndOfSpeech` releases it before transcription finishes. The auto-stop value
is captured for each listening request, independently of later preference changes.
Changing the screen-awake setting takes effect during an active listening session.

No wake locks, accessibility services, root access, new permissions, or global
screen-timeout changes are used. Android applies the flag only while the window
is visible. Explicit cleanup additionally prevents a later keyboard session from
inheriting it. The power button and system lock policy retain their normal effect.

The Whisper native module uses the same NDK r27c as the app and enables flexible
page sizes, so its libraries support 4 KB and 16 KB Android configurations.
This is independent of the screen-on flag and avoids relying on compatibility
mode for native library loading on newer devices.

## Build and automated checks

Use JDK 21 for Android 16 Robolectric tests, Android SDK 36/build tools 36.0.0,
and the native build tools required by the project.

```sh
git submodule update --init --recursive
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Package: `dev.soupslurpr.transcribro.debug`, allowing installation alongside the
upstream release. Open the debug app to accept the license/privacy policy, grant
microphone permission, enable its keyboard, and enable the new setting. Debug
preferences are separate from the upstream app's preferences.

Tests exercise actual window flags on Android API 29 and 36 using Robolectric,
recognition callbacks in both listening modes, errors including microphone denial
and recognizer-busy, start exceptions, preference changes/persistence, all IME
cleanup callbacks, destruction, and late callbacks after dismissal.
Robolectric does not verify a physical device's power policy or microphone pipeline.

## Validation of this change

- Debug assembly, unit tests, and Android lint passed with JDK 21 and API 36 tooling.
- 31 tests passed with no failures or skips (API 29 and API 36 window/lifecycle
  tests, plus the DataStore persistence test).
- `apksigner verify` passed for the debug APK.
- `zipalign -c -P 16 4` passed, and all bundled arm64-v8a and x86_64 native
  libraries have ELF load-segment alignment of at least 16 KB.
- The manifest retains microphone permission and the existing AndroidX-generated
  signature permission; no screen, power, accessibility, or root permission is added.
- Physical Pixel 10/GrapheneOS testing has not been performed in this workspace.

## Pixel 10 / GrapheneOS device checks

Physical device testing remains necessary. With the phone connected to the tailnet,
install the supplied debug APK and use a short screen timeout (e.g. 15 seconds):

1. Leave the setting off, start continuous dictation, and stop touching the phone.
   Normal screen timeout should still apply.
2. Enable the setting and start continuous dictation. Wait beyond the timeout,
   including pauses between phrases: the screen should stay awake.
3. Press Stop; allow transcription to finish. The normal timeout should resume.
4. Repeat with Cancel, Back to dismiss the keyboard, keyboard switching, opening
   settings, leaving the input app, and locking the phone. No screen-on request
   should remain from Transcribro.
5. Enable Auto-Stop Recognition. Start dictation and pause until listening stops.
   The normal timeout should resume even if transcription is still processing.
6. Revoke microphone permission and attempt dictation. Check that an error does
   not leave the screen awake. Restore permission and verify dictation still works.
7. Toggle the preference, restart the debug app, and verify it persisted. Verify
   transcripts, automatic sending, and switching keyboards still work as expected.

For developer verification while listening, `adb shell dumpsys window windows`
should show `KEEP_SCREEN_ON` on the visible Transcribro debug IME window, and it
should disappear after each cleanup action. Do not use a charger/emulator setting
that independently keeps the screen awake during timeout testing.
