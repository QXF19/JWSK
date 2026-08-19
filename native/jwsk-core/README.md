# JWSK Native Core

This small, dependency-free Rust library provides the integrity-mixing core for
JWSK journal records through a primitive-only JNI boundary. Streaming SHA-256
and bounded parsing live in the Kotlin/Java service layer, which avoids moving
Java-owned buffers across JNI and provides a fallback when an ABI is missing.

Build the Android libraries with `scripts/build-rust-android.ps1`.
