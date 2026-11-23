# ScanPro — Free, Ad-Free Android Document Scanner

A CamScanner-style Android document scanning app built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Google ML Kit Document Scanner + OCR**. Designed to build directly into an installable APK via **GitHub Actions** (no Android Studio required).

## Features
- **AI Edge Detection & Auto-Capture**: Automatically detects page corners and flattens perspective.
- **CamScanner-Style Filters & Cleanup**: Apply Color, Grayscale, Auto-Enhance, Black & White filters, rotate/crop pages, and remove shadows, stains, or fingers.
- **Multi-Page PDF & Gallery Import**: Scan up to 100 pages per document or import photos from your gallery.
- **Built-in OCR Text Extraction**: Extract copyable text from any scanned page using on-device ML Kit Text Recognition.
- **Watermark-Free & 100% Ad-Free**: Export clean PDFs directly to `Downloads/ScanPro`, share via any app, or print.

## How to Build the APK on GitHub
1. Create a new repository on GitHub (e.g., `ScanPro`).
2. Extract `ScanPro-Android-GitHub.zip` and upload all files (including the `.github/workflows/build-apk.yml` file).
3. Open the **Actions** tab in your GitHub repository.
4. Click **Build ScanPro Android APK** → wait ~2–3 minutes for the green checkmark.
5. Scroll to **Artifacts** and download **`ScanPro-Release-APK`** (unzip it to get `ScanPro-v1.0-release.apk`).
6. Transfer the APK to your Android phone and install it!
