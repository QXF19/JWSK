#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ndk_path="${ANDROID_NDK_HOME:-${ANDROID_HOME:?ANDROID_HOME is required}/ndk/29.0.14206865}"
toolchain="$ndk_path/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -d "$toolchain"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$toolchain/aarch64-linux-android31-clang"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$toolchain/x86_64-linux-android31-clang"

cd "$project_root/native/jwsk-core"
rustup target add aarch64-linux-android x86_64-linux-android
cargo fmt --check
cargo test --locked
for target in aarch64-linux-android x86_64-linux-android; do
  cargo build --locked --release --target "$target"
done
install -m 644 target/aarch64-linux-android/release/libjwsk_core.so "$project_root/manager/src/main/jniLibs/arm64-v8a/libjwsk_core.so"
install -m 644 target/x86_64-linux-android/release/libjwsk_core.so "$project_root/manager/src/main/jniLibs/x86_64/libjwsk_core.so"
