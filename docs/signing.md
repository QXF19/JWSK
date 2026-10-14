# JWSK release signing

Release signing reuses the existing JWSK release identity so that compatible
versions can be installed as updates. The key is not regenerated for each build.
Signing does not prove Root functionality or device compatibility.

## Public identity

Expected signing-certificate SHA-256:

`1f934155f4ffc1aeefb44faa15bdbef277ca4472435ed45e09fa583b9d67a76f`

This is a public certificate fingerprint, not a private key or password.
Its DER-encoded certificate length, verified locally, is 1316 bytes (`0x0524`).
The public KernelSU build parameters are recorded in
`kernelsu/jwsk-kernel-identity.properties`. They do not compile a GKI kernel or
establish device compatibility by themselves.

## Native manager build

The new `kernelsu/manager` Gradle project reads the existing ignored repository
`signing.properties`, or the file specified by `JWSK_SIGNING_PROPERTIES`.
Supported keys are `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS` and
`KEYSTORE_ALIAS_PASSWORD`. Environment variables with those names override file
values. Relative keystore paths retain the legacy convention: relative to the
repository's original `manager/` directory. Use an absolute path in CI.

`verifyReleaseSigning` requires complete credentials, reads only the public
certificate for the selected key entry, and checks its fingerprint against the
published identity. `preReleaseBuild` and `validateSigningRelease` depend on this
check. Release uses the named release signing configuration, with no debug-key
fallback. Incorrect private-key passwords also fail Android's signing task.

Developer debug builds still use Android's normal debug signing and are not
publishable release artifacts. Their signature is intentionally different.
The previous manager's `signing.gradle` also rejects missing or changed release
credentials instead of falling back to a debug certificate.

## CI and publication

The existing release workflow requires all four GitHub signing Secrets even for
manually triggered release builds. It no longer generates a debug fallback key.
Signing files, keystores and passwords must never be committed or uploaded as
release attachments. Only APKs, public fingerprints and optional public
certificates may be published.
The workflow independently checks the final APK with Android `apksigner` and
requires the pinned certificate fingerprint before artifact upload/publication.

The root release workflow still targets the old manager until the native
migration and matching LKM build are complete. Do not create a release tag for
the incomplete migration merely because a signed local APK can be produced.

Before publishing, verify the final APK with Android `apksigner`, compare its
certificate with the fingerprint above, and retain the same identity for the
matching KernelSU LKM. Kernel-side package and certificate checks must remain
enabled. A correctly signed manager alone does not make an upstream stock LKM
recognize JWSK.

## Verified checks (2026-10-14)

- The published v1.2.0 APK independently passes `apksigner verify` and contains
  the pinned certificate above.
- The local private-key entry exposes the same public certificate, DER size
  1316 bytes. The keystore and signing properties have not been modified.
- The native manager's `preReleaseBuild` rejects missing credentials and a
  different (debug) certificate in separate negative tests. Neither negative
  test modifies a keystore.
- The legacy manager's certificate check and debug-unit-test regression pass.
- A clean native-manager release build and all 90 unit tests pass. Its local
  validation output `JWSK_1.3.0-dev_5-release.apk` (13,231,094 bytes) passes normal
  `apksigner` verification using v3 and a separate forced v2 verification. Both
  signers expose the same pinned JWSK certificate. The v2 verifier range 24-27
  is diagnostic only; the app still requires API 34 or newer.
- Validation APK SHA-256:
  `220b8f893c790e1b4425837be8e83cc9aa7f0fda47194f615349b679b8418a02`.
- This signed development baseline is not a completed or published release.
  No APK has been installed on a device during these checks.

Subsequent native migration checkpoint: debug APKs now run in the dedicated
Android 14 emulator, with 100 unit tests passing. A new release-backend guard
requires a matching native artifact receipt, rejects the known stock ksud
hashes, and checks each ABI's hash and JWSK package identity. Fresh release
builds are intentionally blocked until the matching LKM/ksuinit/ksud pipeline
produces those artifacts. The earlier signed baseline remains historical
validation evidence, not a distributable completed release.
