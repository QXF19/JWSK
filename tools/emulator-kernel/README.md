# Isolated API 34 x86_64 compatibility investigation

This is **not a production module**, a phone installer, a kernel-image flasher,
or a general Android compatibility claim. The workflow never accesses an AVD or
loads a module. A successful build means the static gates passed, not that Root
or module lifecycle behavior was tested. Do not publish this artifact as a
universal module or bundle it in the normal release matrix.

## Fixed target and observed evidence

- Original dedicated AVD: `emulator-5580`, Google APIs Android 14 x86_64.
- Official kernel: <https://android.googlesource.com/kernel/common/+/7e35917775b8b3e3346a87f294e334e258bf15e6>.
- Captured release: `6.1.23-android14-4-00257-g7e35917775b8-ab9964412`.
- `emulator-api34.config` is an unmodified configuration snapshot, not device data
  or credentials; SHA-256 `45b89fa54f7bee763e356a77a41f17905b3f958e6441504dce0f3016f90aff80`.
  Its source is the GPL-2.0 Linux kernel configuration.
- Official compiler checkout: `eed2fff8b93ce059eea7ccd8fc5eee37f8adb432`
  of <https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86>,
  directory `clang-r487747`, manifest build `9796371`, clang `17.0.0`.
- Target BTF: `struct module` size `0x380`, init `0x150`, exit `0x370`,
  core_layout `0x180`. The target's own `virtio_net.ko` agrees.
- Target module_layout CRC: `0x38786c34`, from that original module's `__versions`.

The generic DDK-built test module had size `0x400`, init `0x180`, exit `0x3b0`,
and empty CRC records. Its init relocation collided with the target kernel's
already populated core_layout.base. The kernel correctly rejected the nonzero
relocation destination. Replacing vermagic cannot repair a structure ABI mismatch.
The exact individual DDK configuration difference remains unproven.

## Build and gates

Run the separate **JWSK isolated API34 kernel compatibility** manual workflow.
It fetches immutable official sources and builds vmlinux/modules normally to
generate Module.symvers. Captured config preservation is mandatory (only host
pahole's version metadata may differ). No signing, CFI, version, relocation, or
APK identity checks are disabled. KERNELRELEASE identifies the original target
when generating build headers; the module bytes are never post-edited to alter
their version. The new vmlinux is only a build intermediate, not the original
Google-signed kernel and not a replacement boot image.

For the external KSU module, KBUILD_MODPOST_WARN preserves diagnostics for its
existing non-exported-symbol resolver while keeping ordinary exported-symbol
CRC generation. The original modpost code remains untouched. The generic KSU
check_symbol wrapper intentionally demands empty versions and is therefore not
used for this distinct target-specific diagnostic build.

The compile-time `include/generated/utsrelease.h` must match the captured target,
as must the final ELF vermagic. This source revision's `make kernelrelease`
display command recomputes its checkout's SCM suffix independently of the
explicit build identifier; it is not the source of the compiled header value.

The separate cache uses an exact kernel/compiler/config/Ubuntu/cache-format key
and no prefix fallback. It whitelists only `.config`, Makefile, Module.symvers,
vmlinux, System.map, generated include/scripts, x86 generated headers and SELinux
build products. It never includes `certs/` or the entire output tree, and refuses
private-key filenames or PEM markers before marking the cache ready. The
reference `virtio_net.ko` must pass the target layout/CRC/version gate first.
Cache reuse rechecks this reference, the full configuration and UTS_RELEASE;
KSU is always freshly compiled and gated. Later KSU failures can therefore reuse
the validated public kernel products without caching any signing secret. A new
kernel input or whitelist format requires a new cache key.

The pure-standard-library verifier rejects unexpected architecture, malformed
ELF tables, wrong module layout, nonzero pointer relocation slots, missing target
CRC, wrong version, or missing JWSK package/certificate hash. It does not establish
complete KMI/CFI ABI compatibility. The generated certificate/key used internally
by a fresh kernel build is ephemeral and is never uploaded. No release signing
secrets are requested by this workflow.

After a successful static build, a separately authorized, scoped emulator test
must inspect module-load diagnostics and exercise real manager recognition,
grant/revoke, Root commands, logs, and lifecycle. A load failure remains a failure;
do not force it, zero kernel metadata, or remove a safety check. ARM64 and other
Android/KMI versions require their own runtime testing; this x86_64 test cannot
stand in for physical OEM devices.
