#!/usr/bin/env bash
# Disposable Linux CI workspace only. Never loads a module or touches an AVD.
set -euo pipefail
trap 'printf "Target kernel build failed at line %s\n" "$LINENO" >&2' ERR

repo="$(cd "$(dirname "$0")/../.." && pwd)"
test -n "${RUNNER_TEMP:-}"
work="$RUNNER_TEMP/jwsk-api34-kernel"
# Refuse reuse, avoiding silent source/config contamination or recursive removal.
test ! -e "$work"
mkdir -p "$work/kernel" "$work/clang" "$work/out" "$work/artifacts"
kernel_commit=7e35917775b8b3e3346a87f294e334e258bf15e6
clang_commit=eed2fff8b93ce059eea7ccd8fc5eee37f8adb432
release=6.1.23-android14-4-00257-g7e35917775b8-ab9964412
config="$repo/tools/emulator-kernel/emulator-api34.config"
verify="$repo/tools/emulator-kernel/verify.py"
cache="$RUNNER_TEMP/jwsk-api34-prepared-cache"

git -C "$work/kernel" init
git -C "$work/kernel" remote add origin https://android.googlesource.com/kernel/common
git -C "$work/kernel" fetch --depth=1 origin "$kernel_commit"
git -C "$work/kernel" checkout --detach FETCH_HEAD
test "$(git -C "$work/kernel" rev-parse HEAD)" = "$kernel_commit"

# Official immutable Git revision. Record archive digest, never use a moving tag
# or an unrelated clang 17 package. The original directory contains build 9796371.
url="https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86/+archive/$clang_commit/clang-r487747.tar.gz"
curl --fail --location --retry 4 --output "$work/clang.tar.gz" "$url"
tar -xzf "$work/clang.tar.gz" -C "$work/clang"
test -f "$work/clang/manifest_9796371.xml"
export PATH="$work/clang/bin:$PATH"
clang --version | tee "$work/artifacts/clang-version.txt"
grep -Fq 'Android (9796371, based on r487747)' "$work/artifacts/clang-version.txt"
grep -Fq 'clang version 17.0.0' "$work/artifacts/clang-version.txt"
sha256sum "$work/clang.tar.gz" > "$work/artifacts/toolchain-archive.sha256"

cache_hit=false
if [[ -f "$cache/ready-v1" ]]; then
    test "$(cat "$cache/kernel-commit")" = "$kernel_commit"
    test "$(cat "$cache/clang-commit")" = "$clang_commit"
    # Cache inputs are fixed and the cached config, compile-time release and
    # reference driver's actual CRC/layout must all pass before reuse.
    python3 "$verify" config "$config" "$cache/out/.config"
    python3 "$verify" release-header "$cache/out/include/generated/utsrelease.h"
    python3 "$verify" module "$cache/reference-virtio_net.ko"
    cp -a "$cache/out/." "$work/out/"
    cache_hit=true
else
    cp "$config" "$work/out/.config"
fi
make_args=(-C "$work/kernel" "O=$work/out" ARCH=x86_64 LLVM=1 LLVM_IAS=1
           "KERNELRELEASE=$release" 'KCFLAGS=-D__ANDROID_COMMON_KERNEL__')
# KERNELRELEASE is the captured target's build identifier, not an altered .ko
# string. Actual structural + module_layout CRC gates below are mandatory.
make "${make_args[@]}" olddefconfig
python3 "$verify" config "$config" "$work/out/.config"
if [[ "$cache_hit" == true ]]; then
    make "${make_args[@]}" -j2 modules_prepare 2>&1 | tee "$work/artifacts/kernel-build.log"
else
    make "${make_args[@]}" -j2 vmlinux modules 2>&1 | tee "$work/artifacts/kernel-build.log"
fi
test -s "$work/out/Module.symvers"
cp "$work/out/.config" "$work/artifacts/kernel.config"
cp "$work/out/Module.symvers" "$work/artifacts/Module.symvers"
cp "$work/out/include/generated/utsrelease.h" "$work/artifacts/utsrelease.h"
# This Android Makefile's `kernelrelease` display target recomputes SCM metadata
# instead of using the explicit KERNELRELEASE consumed by utsrelease.h. Verify
# the compile-time header itself; the final ELF vermagic is independently gated.
python3 "$verify" release-header "$work/out/include/generated/utsrelease.h"
python3 "$verify" config "$config" "$work/out/.config"

if [[ "$cache_hit" != true ]]; then
    # Whitelist only public build products needed by external Kbuild. Never
    # cache certs/, source signing keys, full out/, or the private .pem files.
    python3 "$verify" module "$work/out/drivers/net/virtio_net.ko"
    test ! -e "$cache"
    mkdir -p "$cache/out/arch/x86" "$cache/out/security"
    for file in .config Makefile Module.symvers vmlinux System.map; do
        cp -a "$work/out/$file" "$cache/out/$file"
    done
    cp -a "$work/out/include" "$work/out/scripts" "$cache/out/"
    cp -a "$work/out/arch/x86/include" "$cache/out/arch/x86/"
    cp -a "$work/out/security/selinux" "$cache/out/security/"
    cp "$work/out/drivers/net/virtio_net.ko" "$cache/reference-virtio_net.ko"
    test -z "$(find "$cache" -type f \( -name '*.pem' -o -name '*.key' -o -name '*.p12' -o -name '*.pfx' \) -print -quit)"
    if grep -rIlE -- '-----BEGIN ([A-Z0-9]+ )*PRIVATE KEY-----' "$cache"; then
        printf 'Refusing to cache private key material\n' >&2
        exit 1
    fi
    printf '%s\n' "$kernel_commit" > "$cache/kernel-commit"
    printf '%s\n' "$clang_commit" > "$cache/clang-commit"
    touch "$cache/ready-v1"
fi
# Save a validated prepared kernel even if a later KSU compilation fails.
printf 'cache_ready=true\n' >> "$GITHUB_OUTPUT"

# Do not patch modpost/check_exports, remove version tables, or invoke the
# generic DDK check_symbol utility (which intentionally requires empty CRCs).
# KSU's existing kallsyms resolver handles non-exported imports. WARN retains
# modpost diagnostics and all known export CRCs instead of deleting checks.
set -a
source "$repo/kernelsu/jwsk-kernel-identity.properties"
set +a
identity=("KSU_PACKAGE_NAME=$KSU_PACKAGE_NAME" "KSU_EXPECTED_SIZE=$KSU_EXPECTED_SIZE"
          "KSU_EXPECTED_HASH=$KSU_EXPECTED_HASH" "KSU_PINNED_VERSION=$KSU_PINNED_VERSION")
make "${make_args[@]}" -j2 "M=$repo/kernelsu/kernel" "src=$repo/kernelsu/kernel" \
    modules CONFIG_KSU=m CONFIG_KSU_X86_PATCH_SYSCALL_DISPATCHER=y \
    KBUILD_MODPOST_WARN=1 "${identity[@]}" 2>&1 | tee "$work/artifacts/module-build.log"
module="$repo/kernelsu/kernel/kernelsu.ko"
python3 "$verify" module "$module" --identity "$repo/kernelsu/jwsk-kernel-identity.properties" \
    | tee "$work/artifacts/static-verification.json"
cp "$module" "$work/artifacts/api34-exact-target-kernelsu.ko"
llvm-readelf -h -S -r "$module" > "$work/artifacts/module-layout.txt"
llvm-readelf -p .modinfo "$module" > "$work/artifacts/module-modinfo.txt"
git -C "$work/kernel" diff --exit-code
printf 'kernel_commit=%s\nclang_commit=%s\nsource_commit=%s\nruntime_verified=false\n' \
    "$kernel_commit" "$clang_commit" "$(git -C "$repo" rev-parse HEAD)" \
    > "$work/artifacts/provenance.properties"
cp "$repo/tools/emulator-kernel/README.md" "$work/artifacts/README.md"
(cd "$work/artifacts" && sha256sum ./*.ko ./*.config Module.symvers \
    static-verification.json provenance.properties > SHA256SUMS)
printf 'artifact_dir=%s\n' "$work/artifacts" >> "$GITHUB_OUTPUT"
