# Changelog

## 1.1.1

- Fixed the GitHub Actions Gradle wrapper execute permission failure.
- Added stable Release signing through encrypted repository secrets while preserving the v1.1.0 signing certificate.
- Updated the GitHub Actions toolchain to Node.js 24-compatible action versions.
- Fixed Release and Debug APK discovery and artifact upload paths.
- Added an explicit fallback signing path for manually dispatched test builds.
- Added the Root detection and journaling sequence diagram.
- Removed Java and Kotlin deprecation warnings from `Shizuku` and `FileObserver` usage.

## 1.1.0

- Added the state-driven unified Root home screen for Magisk, KernelSU, hybrid and ADB modes.
- Added dedicated Magisk/KernelSU module, Magisk policy and Root log screens.
- Added the Rust JNI log-integrity core with a Kotlin fallback.
- Integrated Root framework detection, executor selection and journaling into patching and ADB workflows.
- Preserved the JWSK Comput command interface and Android 12–16 support.

## 1.0.0

- Rebranded the application as JWSK / 江望sk with package `cn.jiangwang.jwsk`.
- Set the supported system range to Android 12–16 (API 31–36).
- Added an offline boot/init_boot image patch center.
- Integrated the official Magisk 30.7 patch engine.
- Integrated the official KernelSU 3.2.5 LKM patch engine.
- Added KernelSU custom-kernel replacement mode.
- Added input/output SHA-256 display, patch logs, explicit confirmation, and SAF export.
- Added a new JWSK launcher icon and independent permissions.
- Explicitly excluded automatic flashing, root hiding, anti-detection, and integrity bypass features.
