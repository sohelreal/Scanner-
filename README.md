# ScanPro v3 — Free, Ad-Free Android Document Scanner

## Fixed in v3
- Fixed Google ML Kit Document Scanner dependency version to `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1` (Google Maven publishes `16.0.0-beta1`, not `16.0.0`).
- Cleaned up `ic_camera.xml` vector path and removed `--stacktrace` from `.github/workflows/build-apk.yml` so build output is concise and readable.
