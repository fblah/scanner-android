# Scan for Android

Native Kotlin / Jetpack Compose port of ../scan (PWA v0.4.0).

## Build

Java 17, Android SDK platform 35 and build-tools 35.0.0 are required.
Set sdk.dir in local.properties to your SDK installation, then run ./gradlew assembleDebug.

## Implementation status

The initial project scaffold contains a placeholder Compose screen. Feature parity is not implemented yet.

Remaining parity work:
- Camera capture and multi-image import, including Android share-in
- OpenCV edge detection, perspective correction, corner editor and magnifier
- Original, Magic, grayscale and black-and-white filters; rotation and re-crop
- Persistent document/page storage retaining original images
- Search, rename, duplicate, merge and bulk deletion
- Page reorder, move, copy and duplication
- PDF Auto/A4/Letter and JPG export, save and Android share sheet
- Anonymous analytics using the existing endpoint and event vocabulary, with opt-out; never send document names, identifiers or image content
- Device validation of capture, offline editing, export and persistence

## Releases and signing

Actions builds every pushed branch and uploads a signed APK artifact. Main publishes a rolling `latest` prerelease; pushing a tag such as `v0.1.0` publishes a permanent versioned release. CI run numbers supply increasing Android version codes. Only main and version tags publish releases.

All release APKs use the same dedicated Scan signing key from repository secrets KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD. Missing keys fail the build. Local key and credentials backup: ~/.config/scan-android-signing/ (keep a secure backup of this directory; never commit it). This is a self-generated Android signing certificate; no developer account is required for APK distribution.
