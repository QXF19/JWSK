# Third-party notices

JWSK combines and modifies components from several open-source projects. This file records the upstream projects and the versions used by JWSK 1.0.0.

## Shevery / Shizuku

- Source: https://github.com/HmnDev-Tech/shevery
- Original project: https://github.com/RikkaApps/Shizuku
- Base revision: Shevery v13.8.0.r29
- License: Apache License 2.0
- Use in JWSK: privileged service, manager UI, ADB/root startup and supporting libraries
- Changes: independent application ID and permissions, new branding and icon, Android 12–16 SDK range, and the JWSK image patch hub

The Apache License 2.0 text is preserved in `LICENSES/Apache-2.0.txt`. JWSK is not affiliated with or endorsed by the upstream authors.

## Magisk

- Source: https://github.com/topjohnwu/Magisk
- Version: 30.7
- License: GNU General Public License v3.0
- Use in JWSK: `boot_patch.sh`, `util_functions.sh`, Magisk Stub, ChromeOS signing resources, `magiskboot`, `magiskinit`, `magisk`, `init-ld`, and BusyBox binaries extracted from the official release APK

The integrated Magisk components retain their upstream copyright and license. JWSK does not include third-party root-hiding forks or anti-detection modifications.

## KernelSU

- Source: https://github.com/tiann/KernelSU
- Version: 3.2.5
- Manager/userspace license: GNU General Public License v3.0 or later
- Kernel license: GNU General Public License v2.0 only
- Use in JWSK: official `ksud` userspace binary from the release manager APK for offline boot/init_boot patching

JWSK does not redistribute a device-specific kernel. A custom kernel selected by the user must be compatible with that device and supplied under its own applicable terms.

## Combined work

JWSK is distributed under GPL-3.0-or-later. Individual third-party files remain available under their upstream licenses where applicable. Source modifications and build instructions are published with the project so recipients can inspect and rebuild the application.
