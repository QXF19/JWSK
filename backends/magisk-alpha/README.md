# Pinned Magisk Alpha backend

Upstream: https://github.com/vvb2060/Magisk

Source commit: `96221b69fae9910b1c0c75c2f92a4ebb2c2dc698`
(release `31000`, `96221b69-alpha`). The upstream source is GPL-3.0;
`LICENSE` is copied verbatim from that commit. This is a JWSK development fork,
not an official Alpha release or an endorsement by its authors.

## Reproducible source inputs

The workflow checks out the full commit and its pinned submodules, uses the
upstream ONDK r30.1 setup and builds all four upstream Android ABIs. The build
configuration sets the Alpha protocol version to 31000 independently of JWSK's
Android application version. No release APK or unverified third-party binary
is used as the native backend input.

Two minimal patches are applied in order:

1. Propagate the environment wrapper's subprocess exit status, so a failed
   standalone check cannot silently pass CI.
2. After upstream native code generation, change only the expected original
   manager package to `cn.jiangwang.jwsk`. The `check-signature`, `check-client`
   and `su-check-db` default features remain enabled.

The output contains `magisk`, `magiskinit`, `magiskboot`, `magiskpolicy` and
`libinit-ld.so`, their SHA-256 checksums, the unchanged patch scripts, license,
source revision and local patches. It excludes Rust static libraries and caches.
Native artifacts are development inputs, not an installable or boot-tested APK.

Both 64-bit targets receive workspace compilation and strict Clippy checks.
Formatting is checked on the modified `include/consts.rs`, with child traversal
disabled: the pinned upstream and third-party submodules have pre-existing
whole-workspace formatting differences under ONDK r30.1. Those unrelated files
are preserved rather than silently reformatted or counted as JWSK changes.

## Manager contract still required

Compiling the package identity alone does not implement a full Alpha manager.
Before enabling this backend in a public release, JWSK must supply the matching
signed manager payload, validated superuser
request handling, policy/module/settings operations and log management.
The backend's certificate and client checks must not be disabled to bypass that
work. Offline image patching must export a new image only after explicit user
selection; it must not flash a partition automatically.

`tools/AlphaApkSeal.java` implements the build-time APK version-comment contract.
It edits a temporary copy before re-signing with the original JWSK identity,
verifies v2 and v3 signatures and the resulting comment, and never overwrites
the input APK. Signing credentials are read from environment variables or the
ignored local signing properties, never passed as command-line arguments.
The native manager workflow runs its ZIP boundary tests and seals the signed
development output. That protocol is a prerequisite, not proof that the full
Alpha manager or any patched image has been tested on a device.
