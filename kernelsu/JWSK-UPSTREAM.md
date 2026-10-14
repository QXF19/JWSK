# JWSK upstream baseline

This directory is the KernelSU source baseline for the JWSK native manager migration.
Source: https://github.com/tiann/KernelSU
Tag: v3.3.1
Commit: 0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d
Retrieved: 2026-10-14

Preserve upstream licenses, notices and manager/kernel/userspace interfaces.
Original source is copied as a new directory; the previous JWSK manager is preserved.
Importing source does not mean the migration or device verification is complete.

Plan:
1. Verify the unchanged manager/native baseline with matching ksud dependencies.
2. Preserve upstream module, superuser and settings implementations.
3. Integrate the approved JWSK home/tools, artwork and unified settings.
4. Keep AI text assistance isolated from manual commands and native privileges.
5. Integrate Magisk Alpha with validated protocol and license/source attribution.
6. Build matching manager and kernel identity without disabling signature checks.
7. Run source, native, UI and device checks before publishing truthful documentation.

Final scope: a general Android 14+ manager with KernelSU LKM and Magisk Alpha image patching.
The manager does not build full GKI kernels; the user clarified that it only accepts externally compiled kernel/image artifacts in the same installation/patching entry.
The screenshot is only a device-information layout reference.
LKM compatibility and JWSK manager signature recognition must be verified; do not claim every kernel can be rooted.
Context7 is not available in this session; primary upstream documentation/source is used instead.

The copied upstream Gradle project is not yet the root release pipeline's build target.

Checkpoint (2026-10-14):
- The Android 14+ debug baseline builds for arm64-v8a and x86_64.
- All 90 Android Gradle unit tests pass, including 16 new artifact-intake tests.
- Windows archive builds resolve the original shared UAPI directly in CMake.
- Registry cache replacement uses an atomic move; host test paths are normalized.
- LKM update detection compares the pinned upstream backend version (32680),
  not JWSK's independent APK version (5).
- The bundled ksud binaries at this checkpoint are upstream baseline binaries,
  NOT a matching JWSK-signed LKM distribution. They must not be released as one.
- No device is connected and no emulator AVD is configured. An Android 37
  system image is installed, but no native runtime/screenshot evidence exists yet.
- GKI intake backend is implemented; its native UI is still pending. See
  `../docs/gki-artifact-intake.md` for its exact scope and limitations.
- Nothing from this migration has been published as a new GitHub release.
- A clean release build now uses the original JWSK release key and passes
  independent APK v2/v3 signing checks. Its certificate matches v1.2.0. This is
  still a local development validation artifact, not a completed native release.
- Missing-key and wrong-certificate preflight tests both correctly fail.
- AI protocol, encrypted storage and native screen-state code compile. The
  current unit suite passes 100 tests with no failures/errors, including ten
  new AI protocol, provider isolation, persistence failure and cancellation checks.
- An Android 14 debug APK including the approved, text-free home artwork builds.
  A dedicated debug-only capture activity is excluded from release builds.
- The Android 14 test AVD has been created and started with WHPX. Runtime
  capture remains pending until its first boot finishes; the earlier Android 37
  system-image directory was an incomplete installation, not a usable image.
- The AVD subsequently booted and ran the native Home, Tools, API settings and
  device-details routes. Encrypted API settings persisted across restart, and
  the unconfigured label disappeared. Fictitious test credentials were cleared.
  See `../docs/native-validation.md` for the exact limits of this evidence.
- Vendored ksud now reads the public JWSK identity/version manifest rather than
  deriving its backend version from the parent monorepo's Git commit count.
  Local `cargo ndk check` and `clippy` are blocked by missing cargo-ndk/host
  build tooling; formatting ran. The new Linux workflow performs the checks in
  order and builds matching LKM, ksuinit and ksud artifacts before the APK.
- A release-backend receipt guard prevents packaging the stock daemon as a
  completed JWSK backend. No matching native CI artifact has yet been verified.
- The first LKM CI run reached module linkage but failed artifact identity
  validation. Source inspection found a substantive incompatibility: upstream's
  1024-byte certificate cap always rejected JWSK's existing 1316-byte RSA-4096
  certificate. The parser now bounds certificates at 4096 bytes, allocates the
  exact length on the heap and frees it on every path. Exact expected length
  and SHA-256 comparison remain mandatory. A new CI run must validate this fix.
