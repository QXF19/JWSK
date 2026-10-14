# Native manager validation checkpoint

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
