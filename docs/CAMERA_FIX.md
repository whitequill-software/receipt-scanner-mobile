# Camera launch repair

## Cause and investigation

The app declared `android.permission.CAMERA` but did not request its runtime
grant. For apps targeting Android 6 or later, launching `ACTION_IMAGE_CAPTURE`
with a declared, ungranted camera permission throws `SecurityException`.
`ReceiptCaptureSection` caught every exception and immediately opened the photo
picker, disguising that camera failure as Gallery behavior.

This is a confirmed source/configuration defect consistent with the Samsung
report. No device log from the affected Samsung was supplied, so the exact
exception on that device remains to be confirmed. The Android behavior is
documented in [MediaStore.ACTION_IMAGE_CAPTURE](https://developer.android.com/reference/android/provider/MediaStore#ACTION_IMAGE_CAPTURE).

The app uses a separate camera app, not CameraX or the Camera API. Remove the
unused `CAMERA` declaration instead of requesting unnecessary access. No storage
permission is needed for an app-private cache file shared through FileProvider.

The existing FileProvider configuration is correct: the authority resolves to
`<applicationId>.fileprovider`, matching `context.packageName`; the provider is
not exported, grants temporary URI permissions, and `camera_images` covers
`cache/receipt_images/`. The exports path is unrelated and unchanged.

## Changes

- Camera uses TakePicture with its full-size output URI. Add explicit read/write
  grants and ClipData for camera app interoperability; do not query or hard-code
  a Samsung camera package.
- Create a real, unique `.jpg` before launch, detect directory creation errors,
  and remove unused output on failure/cancellation.
- Keep the pending URI in saveable state so activity recreation does not lose
  a successful result. Prevent overlapping launches while capture is pending.
- Check that successful output is readable and nonempty before scanning it.
- Replace the Gallery fallback with an inline error message for missing camera,
  blocked access, storage failure, or other launch failures. Log exceptions with
  tag `ReceiptCapture`. The app does not log photo bytes or provider API keys.
- Gallery continues to use PickVisualMedia and the existing API-key setup gate
  remains unchanged. No layout redesign or new permission dialog was added.

## Automated verification

Robolectric/Compose regression tests exercise Camera versus Gallery routing,
permission declarations, real FileProvider URI read/write and root restrictions,
intent action/output/grants, missing-camera and security exceptions (including
logging and no picker fallback), cancellation cleanup, empty successful output,
saved-state restoration, and the existing API-key setup gate.

Run on a Linux host (matching CI):

```sh
./gradlew --no-daemon --stacktrace --continue :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The Windows run built the debug APK and passed lint (zero errors, 46 warnings).
Its new real-FileProvider tests encountered a host limitation: AndroidX
FileProvider checks Android `/` path separators against Windows canonical
backslash paths and throws `Failed to find configured root that contains ...`.
Six of the nine new tests consequently failed before camera launch. The 14
existing tests passed. The tests have not been skipped or replaced with a fake
FileProvider; Linux GitHub Actions provides the Android-compatible filesystem
semantics for this verification. See the PR for the final CI result.

## Samsung device validation still required

No Android device was connected to ADB during this repair. Robolectric cannot
prove Samsung Camera launch, URI grant enforcement across real applications,
or OEM activity lifecycle behavior.

1. Install the PR's debug APK on the affected Samsung; configure a test provider
   key. For a CI APK with a different signing key, use a test device/profile or
   back up required app data before uninstalling an older test build.
2. On a fresh install with no camera permission granted, tap Camera. Confirm
   Samsung Camera opens, take and accept a receipt photo, and verify the app
   receives the full image and opens the receipt processing/review flow.
3. Tap Gallery separately and confirm the photo picker opens and returns a photo.
4. Cancel capture, retry, rotate, and background/restore the app while the camera
   is open. Also test with Android developer option "Don't keep activities".
   Verify no stale image is scanned and the accepted photo is delivered once.
5. Test camera privacy restrictions or a disabled camera app where practical.
   Confirm an actionable message, no automatic picker, and a diagnostic in
   `adb logcat -s ReceiptCapture`. Record model, Android/One UI version, and the
   exception if capture still fails. Avoid including receipts or API keys in logs.
