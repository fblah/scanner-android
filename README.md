# Scan for Android

Native Kotlin / Jetpack Compose port of ../scan (PWA v0.4.0).

## Build

Java 17, Android SDK platform 35 and build-tools 35.0.0 are required.
Set sdk.dir in local.properties to your SDK installation, then run ./gradlew assembleDebug.

## Implementation status

Implemented native flows: camera capture using the system camera, multi-image import and share-in, atomic document index persistence, search, rename, document duplication/merge/deletion, page selection/duplication/move/copy/deletion and move-up ordering. Imports perform OpenCV edge detection and perspective correction; the corner editor supports manual adjustment. Original images are retained for re-cropping. Filters include Original, Magic illumination correction, grayscale and adaptive B&W, with rotation. PDF Auto/A4/Letter and JPG sharing and PDF saving are available. Anonymous telemetry has an opt-out. GitHub update checks and APK installation are available.

Known parity gaps: long-press drag reordering, crop magnifier, explicit continuous scan review/retake flow, direct JPG saving, exact analytics event/property parity and offline telemetry retry. Corner detection currently uses edge contours rather than all three PWA strategies. Filters can accumulate on the current rendered image; Original restores the source. Files from deleted documents are currently retained in private storage. UI still needs a refinement pass.

Validation: assembleDebug and lintDebug pass. No Android device is attached, so camera, share-in, OpenCV runtime, persistence after process death, installer updates and exported file rendering still require device/emulator verification. This is development software, not a fully verified feature-parity release.

## Releases and signing

Actions builds every pushed branch and uploads a signed APK artifact. Main publishes a rolling `latest` prerelease; pushing a tag such as `v0.1.0` publishes a permanent versioned release. CI run numbers supply increasing Android version codes. Only main and version tags publish releases.

All release APKs use the same dedicated Scan signing key from repository secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD. Missing keys fail the build. Local key and credentials backup: ~/.config/scan-android-signing/ (keep a secure backup of this directory; never commit it). This is a self-generated Android signing certificate; no developer account is required for APK distribution.

## In-app updates

The app checks the GitHub `latest` prerelease on launch, compares its `build=N` metadata to the installed version code and offers a download. Use **App updates** after download to launch Android's installer. Android may require enabling “Allow from this source” for Scan; then tap App updates again. Installation requires user confirmation and preserves data when upgrading APKs signed by the persistent release key. Debug APKs use a different development certificate and cannot upgrade release installations. Device validation of the download/permission/installer flow remains required.
