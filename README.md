# Gurusthan Book Scanner — Stage 1

Android 15-compatible Kotlin / CameraX prototype. Capture an open book with two facing pages in landscape orientation. The app splits the photograph at its center, applies a **mild experimental gutter compensation**, runs bundled on-device ML Kit English OCR, and appends left and right text to a local master TXT file. Original and processed JPEGs are retained under app-private storage.

## Build on GitHub
Open **Actions → Build Android APK → Run workflow** (or check the push-triggered build). When the build succeeds, open the run's **Artifacts** and download `Gurusthan-BookScanner-debug-APK`. Unzip the artifact and tap `app-debug.apk` on your Android phone to install it. You may need to authorize installation from the app used to open the APK.

## Use
Grant camera permission. Hold phone in landscape with the gutter near the horizontal midpoint. Tap **CAPTURE TWO PAGES** for each spread, wait for the saved-spread counter to increase, then turn the page. **EXPORT MASTER TXT** uses Android sharing. **NEW BOOK** starts a separate book.

## Limitations
This is an **unverified prototype**, not a production scanner. No automatic page detection, duplicate detection, OCR review, or full 3D page flattening is implemented. The split is at the image center, so align the book carefully. Curvature correction is a small image transformation, not a geometric page-surface reconstruction. Hidden text cannot be recovered. The APK build is not yet verified. Test on a few noncritical pages before relying on the results.

## Privacy
OCR runs on-device using the bundled ML Kit Latin recognizer. Camera images and extracted text are stored in app-private local storage. Export shares text only when you request it.
