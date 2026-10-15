# Native manager validation checkpoint

## 2026-10-15: Cold-boot Alpha and application identities

The following supersedes the older Alpha limitations below. This remains a
development checkpoint, not approval for a stable release.

- The dedicated Android 14 x86_64 AVD cold-booted using the app-patched private
  ramdisk, without live injection. SELinux remained Enforcing; ADB remained
  the ordinary shell user. No SDK image or physical partition was modified.
- The latest original-certificate debug build passed 117 unit tests, with
  zero failures/errors. The APK seal utility passed 13 artifact/protocol tests.
- Application-UID instrumentation on that cold-booted backend verified six
  executable receipts, scripts/bootstrap, manager identity and actual uid 0.
- Persistent grant/revoke/restore, inert module ZIP installation/listing/
  disable/enable, settings persistence, and the test application's DenyList
  target passed through the actual manager repositories. Original settings
  were restored and test module directories removed.
- Real PackageManager labels, packages, icon metadata and UID association
  passed. The new Alpha superuser list uses app identities, with UID confined
  to details. Expired policies do not count as active ROOT badges.
- Native UI interactions verified the new per-app Allow control: the separate
  test app returned `exit=0` and `0`; revoking returned `exit=13` and
  `Permission denied`. After a synchronization fix, ROOT appeared immediately
  on grant and disappeared immediately on revoke without manual refresh.
- Phone light/dark/1.3 font-scale and portrait expanded-size captures now exist
  in `.impeccable/review/`. These are valid emulator captures submitted for
  independent UI review, not yet a finished review or hardware coverage.
- Real independent test-app consent previously returned uid 0 on allow and
  Permission denied on deny. Ordinary apps were rejected by the root-only
  event provider. Authorization callbacks now emit native access toasts when
  notifications are enabled.
- Actual v4 init_boot patching again passed, preserving the source hash and
  cleaning the app-private workspace. Pending boot runtime files and private
  access databases are excluded from backup/transfer.
- KSU target-specific emulator loading, complete device-class UI review and
  the final signed release workflow remain pending. No physical test phone
  is available. No cross-OEM or whole-device stability claim is made.

Date: 2026-10-14. This is a development checkpoint, not a release approval.

## Verified

- Android 14+ native KernelSU manager source builds for arm64-v8a and x86_64.
- The current Gradle unit suite reports 100 tests, zero failures and zero errors.
- Android 14 Google APIs x86_64 emulator installed and launches the debug APK.
  It runs with WHPX; no physical phone was connected or modified.
- The approved illustration is rendered by Android from a drawable resource.
  Native text, controls and navigation remain separate from the image.
- Home displays the emulator's actual unavailable Root state and actual kernel/KMI.
- Home's kernel-version row opens manager version, full kernel version, model,
  fingerprint, SELinux and process Seccomp details. Long values wrap and support
  native text selection. Values are from the emulator, not the Xiaomi reference.
- Navigation is visible without Root. Native authorization checks are unchanged.
- Tools has AI assistant, manual Comput and KernelSU log entries. API settings
  are reached through Settings. Settings retains the upstream KernelSU options.
- API configuration was saved on the emulator with a deliberately fictitious
  model and credential, without making an AI service request. Android Keystore
  produced the encrypted `no_backup/jwsk-ai/profiles.aesgcm` file, mode 0600.
  The test credential was absent from the stored file's plaintext byte search.
- After app restart, configuration remained readable, password fields remained
  blank and the Tools "Not configured" label disappeared. Clearing the fictitious
  credential restored that label. No real API credential was supplied.
- Actual screenshots and UI hierarchies are in `.impeccable/review/`, including
  `configured-tools.png`, `api-final.png` and `device-details-real.png`.

## Current limitations

- No physical-device Root grant, module lifecycle, LKM boot or Alpha boot has
  been verified. The emulator does not run the JWSK KernelSU kernel module.
- The first end-to-end native CI run succeeded at
  https://github.com/QXF19/JWSK/actions/runs/38557933833 (source
  `1c69a74513801ad2891f4498762b7b10b07101b6`). It compiled all eight LKM
  variants, both matching Rust daemons, checked/linted/formatted Rust, ran the
  Android tests and produced a signed development manager. Its release guard
  verified the JWSK package, public certificate and daemon hashes, rejecting
  stock-daemon identities. This does not establish runtime Root operation.
- Local debug builds still contain ignored baseline daemon files until they
  are replaced by the verified CI inputs; do not treat a local debug APK as
  equivalent to the successful signed native artifact.
- Magisk Alpha source is pinned for integration; its patching backend and full
  manager identity/control integration are not complete.
- Dark theme, large text and expanded-device captures are still pending for the
  complete implementation. The screenshots above are not broad-device coverage.
- The Impeccable web source-reference scanner does not resolve Android
  `painterResource(R.drawable...)` references. It refuses the hero record before
  pixel scoring despite a byte-identical packaged plate and actual native
  capture. This was recorded as a tool limitation, not forced into a passed gate.
- No new release, tag, GitHub About change or completed migration has been
  published. The root release workflow still targets the previous manager.

## Subsequent local changes awaiting upload and verification

- Removed ordinary `su` fallback from the shared KernelSU shell factory.
  When the kernel does not recognize JWSK, offline operations receive a regular
  app shell, not an authorization request to a different Root manager.
- Both UI modes now use the JWSK home surface. Material settings also expose
  API configuration; shared Miuix surfaces receive a themed context in either
  mode. These source changes compiled and the 100-test Android suite passed.
  Further Material/dark/large-text emulator captures are not yet completed.
- Alpha source compiled for all four upstream ABIs, and the 64-bit workspace
  checks and strict Clippy commands completed in run 38558340810. That run
  failed on pre-existing whole-workspace formatting differences in upstream
  and its submodules. A scoped formatting fix was uploaded and run
  https://github.com/QXF19/JWSK/actions/runs/38559436486 succeeded, including
  runtime artifact packaging and source provenance. No Alpha runtime or boot
  result is asserted from that source-build success.
- The build-time Alpha APK protocol utility passed seven ZIP-boundary tests.
  Re-signing an existing local JWSK release baseline produced a separate test
  copy with the required comment and verified original-certificate v2/v3
  signatures. All 184 non-META-INF payload entries remained byte-identical,
  and SDK zipalign verified alignment including 16 KiB native-library checks.
  That test copy is not a new public release or full Alpha manager.
- The next native workflow revision adds that seal step and a single verified
  development APK output. This revision is local and has not run on GitHub yet.
- Added a shared boot-patch intake mode that accepts ramdisk-only init_boot
  while the existing GKI intake still rejects it as a compiled kernel. Eight
  new Java tests and the original 16 artifact tests passed together using the
  local JDK/JUnit runner (24 tests, zero failures). This is input validation,
  not evidence of a completed Alpha patch or a boot-tested image.
- README was rewritten locally to distinguish native development, verified
  checkpoints, remaining Alpha work and the historical v1.2.0 release. The
  About text is a draft in `docs/github-about.md`, not an applied repository
  setting. These documentation changes still await upload.
- An approval-service usage error interrupted new GitHub/emulator operations.
  It was not bypassed. Previously authorized running tasks were allowed to
  finish; local source work and local tests continued.
  The user then requested continuation, and a normal approval check succeeded;
  GitHub status reads and artifact downloads resumed.

## 2026-10-15: Alpha runtime checkpoint

- Combined CI run 38593966791 and Alpha run 38593966804 succeeded at
  b9b8d11e347d092f625f61bd032edd87b2b57cfa. About has been applied and the
  rewritten README uploaded to the development branch; earlier notes above
  describe the state of their respective checkpoints.
- Application-UID instrumentation on the dedicated Android 14 x86_64 AVD
  verified all five Alpha executable hashes, script hashes and the original
  JWSK certificate in the embedded bootstrap APK.
- A v4 init_boot fixture containing the unmodified SDK ramdisk was actually
  patched by Alpha. Its output parsed successfully and contained Magisk's
  ramdisk marker; input SHA-256 remained unchanged, and the private work
  directory was removed after release. No partition was flashed.
- A temporary Alpha live setup, adapted from the pinned upstream emulator
  script, accepted JWSK's original signing identity. The application process
  queried the read-only manager protocol and executed `id -u`, receiving 0.
  This validates the live daemon integration, not cold boot or physical OEMs.
- Runtime validation caught AGP stripping native executable bytes after the
  build receipts were generated. Packaging now preserves Alpha and ksud
  executables. The APK seal tool verifies both ABIs and all asset digests
  from the final APK before and after signing, and validates the anchor APK.
  Its 13 ZIP/bootstrap/artifact checks pass.
- The new offline patch screen lives only under Install and patch. It uses
  Android document selection/export, bounded subprocesses and cancellation;
  private input and output ownership survives rotation through a ViewModel.
- KernelSU's generic DDK module did not load on this AVD's older 6.1.23 kernel.
  The target struct module is 0x380 bytes with init at 0x150; the DDK module
  expects 0x400 and 0x180. No relocation or signature checks were bypassed.
  Matching-kernel validation remains open.
- The user has no dedicated physical test phone. Alpha authorization prompts,
  policy/module/settings screens, cold boot, complete KSU lifecycle and
  final device-class UI review remain release blockers. No stable release
  is claimed at this checkpoint.
