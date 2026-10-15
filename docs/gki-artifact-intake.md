# GKI artifact intake - implementation status

The user confirmed on 2026-10-14 that JWSK only receives externally compiled
kernel/image artifacts. It does not build full GKI kernels. This route belongs
only in the unified installation/patching screen, alongside LKM and Alpha.

## Implemented backend

- `KernelArtifactRepository` reads a user-selected document into a uniquely named
  app-private cache file on an IO dispatcher. Provider filenames are display-only.
- `KernelArtifactInspector` recognizes Android boot headers v0-v4 with a nonempty
  kernel, raw AArch64 Image headers, and gzip-compressed AArch64 Images.
- SHA-256 is calculated over the original selected bytes. Neither the original
  document nor a boot partition is modified.
- Input and decompressed streams have a 512 MiB limit. Header sizes, page sizes,
  payload bounds, gzip CRC/truncation, and recovery offsets are checked.
- A ramdisk-only init_boot, unsupported format/version, malformed header, or
  oversized/truncated artifact is rejected. Filename extensions are not trusted.
- Cancellation/failure deletes only the exact temporary file created by that
  operation. The original selected URI is never a cleanup target.

## Important limits

These are format/length checks, not proof of trusted origin, GKI compliance,
KMI compatibility, KernelSU inclusion, correct manager signing identity, AVB
verification, or bootability on a particular device. A digest is not a signature.
Boot containers may contain a non-AArch64 kernel; the header does not establish
the payload architecture. No compatibility badge should be inferred from it.

The backend has no shell, JNI, root, compiler, network, or flashing dependencies.
No partition writes or automatic flashing are implemented by this intake route.
Raw x86 bzImage/ELF/vmlinux and other compression/container types are not accepted
by this initial inspector; they must not be presented as supported formats.

## Not yet complete

- Native file-picker/result UI and its integration in the approved installation
  flow. The backend alone is not a user-accessible completed feature.
- Process-death recovery, cache lifecycle and optional unchanged-artifact export.
- Device compatibility and JWSK manager certificate validation, when needed for
  a later installation operation. These are separate from receiving a file.
- Real-device acceptance and native screenshot verification.

## Verification

The Java inspector's 16 JUnit tests passed both standalone and in the Android
Gradle unit-test task on 2026-10-14. Tests cover boot header versions, raw/gzip
Images, original-byte hashes, unchanged inputs, malformed/truncated files,
ramdisk-only input, unknown versions, payload/signature bounds, and size limits.
These tests do not verify real-device root or kernel booting.

Format references:
- https://source.android.com/docs/core/architecture/bootloader/boot-image-header
- https://docs.kernel.org/arch/arm64/booting.html
