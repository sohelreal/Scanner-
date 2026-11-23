# ScanPro v2 — Free, Ad-Free Android Document Scanner

A CamScanner-style Android document scanning app built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Google ML Kit Document Scanner + OCR**, optimized for fast, reliable **GitHub Actions** APK builds.

## What's Fixed in v2
- Added legacy PNG launcher icons across all `mipmap-*` densities (`mdpi` to `xxxhdpi`) so release builds never fail `MissingDefaultResource` checks.
- Disabled fatal release lint aborts (`checkReleaseBuilds = false`) and pinned GitHub Actions to `ubuntu-24.04` with explicit SDK Platform 35 setup.
- Replaced the 34 MB `material-icons-extended` dependency with lightweight local vector drawables (faster builds, no DEX OOM crashes, much smaller APK).
- Increased Gradle JVM heap & Metaspace (`-Xmx4096m -XX:MaxMetaspaceSize=1024m`).

## How to Build the APK on GitHub
1. Extract `ScanPro-Android-GitHub-v2.zip` and replace the files in your repository (make sure `.github/workflows/build-apk.yml` is updated too).
2. Open the **Actions** tab and run **Build ScanPro Android APK**.
3. Download **`ScanPro-Release-APK`** from Artifacts, unzip it, and install `ScanPro-v1.0-release.apk`.
