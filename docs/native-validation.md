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
- Bundled ksud is still the upstream baseline and must be replaced by a build
  containing LKM artifacts that trust JWSK's package and release certificate.
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
