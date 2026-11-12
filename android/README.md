# VideoDL for Android

This is the Android edition of VideoDL. It lives beside the Python/PySide6 desktop application and does not replace or alter it.

## Stack

- Kotlin and Jetpack Compose for the Android user interface
- Chaquopy 17.2 with embedded Python 3.11
- The official yt-dlp Python package and yt-dlp-ejs scripts
- Android foreground service for downloads
- Android MediaStore to publish completed files into Download/VideoDL

There is no VideoDL backend server. URL analysis and media downloads run on the device.

## Build

Open the android directory in Android Studio, or build from a machine with JDK 17, Android SDK API 36 and Gradle 8.13 installed:

    cd android
    gradle :app:assembleDebug

The APK is created at:

    app/build/outputs/apk/debug/app-debug.apk

The GitHub Actions workflow at .github/workflows/android.yml builds the APK automatically when Android files change and uploads it as the VideoDL-android-debug workflow artifact.

The initial build targets arm64-v8a phones and x86_64 emulators. minSdk is 29 (Android 10).

## Runtime behavior and current limits

- The app bundles Python and the official yt-dlp Python package. It does not shell out to Termux or require a separate Python installation.
- Downloads run in an Android foreground service and can continue while the activity is not visible.
- Completed files are copied to the public Download/VideoDL folder through Android MediaStore.
- Cookie exports are copied to the app's private files directory. Do not share the exported file: it may contain account session credentials.
- The Android edition does not bundle FFmpeg in this alpha. It therefore selects single-file media formats; separate high-quality video and audio streams cannot be merged. Audio-only downloads retain the source audio format and are not converted to MP3.
- YouTube's current JavaScript challenge solution requires a supported runtime such as Deno, Node or QuickJS. The Android APK currently includes the yt-dlp-ejs scripts but does not yet bundle one of those runtimes, so YouTube downloads may fail when a challenge is required.
- The Android task view currently handles one active download at a time. Persistent multi-task queue management is a later milestone.

## Permissions

The app requests Internet access, notifications and a foreground data-sync service. It does not request broad file-system access. Completed downloads are published through MediaStore.

## Dependencies and licenses

The VideoDL Android source is covered by the repository's top-level MIT license. Chaquopy, Python, yt-dlp, yt-dlp-ejs, Jetpack Compose and other dependencies retain their own licenses. See the top-level NOTICE and THIRD_PARTY_LICENSES directory. PySide6 is not used by the Android APK.
