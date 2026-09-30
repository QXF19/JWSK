# JWSK Native Core

This small, dependency-free Rust library provides the integrity-mixing core for
JWSK journal records through a primitive-only JNI boundary. Streaming SHA-256
and bounded parsing live in the Kotlin/Java service layer, which avoids moving
Java-owned buffers across JNI and provides a fallback when an ABI is missing.

Build the Android libraries with `scripts/build-rust-android.ps1` on Windows
(requires the MSVC linker for host tests), or `bash scripts/build-rust-android.sh`
on Linux. Release CI runs Rust formatting checks, host unit tests and both Android
ABI builds before packaging. Windows without MSVC can use `-SkipHostTests` for
cross-compilation; CI still runs the tests before publication.

The manager checks JNI API version 120 and signed/unsigned test vectors on load.
The log screen reports Rust JNI versus Kotlin fallback at runtime. The stamp is
a diagnostic checksum, not a cryptographic authentication mechanism. Java
`FileDigest` performs bounded-memory SHA-256 for images and module ZIP files.
