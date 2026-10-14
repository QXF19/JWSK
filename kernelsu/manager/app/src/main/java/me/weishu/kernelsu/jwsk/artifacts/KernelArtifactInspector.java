package me.weishu.kernelsu.jwsk.artifacts;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** Read-only artifact inspection. No compiler, shell, root or partition access. */
public final class KernelArtifactInspector {
    public static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final byte[] BOOT_MAGIC = {'A', 'N', 'D', 'R', 'O', 'I', 'D', '!'};
    private static final byte[] ARM64_MAGIC = {'A', 'R', 'M', 0x64};

    public enum Kind { ANDROID_BOOT, ARM64_IMAGE, GZIP_ARM64_IMAGE }
    public enum Problem { TOO_LARGE, TRUNCATED, UNSUPPORTED, NO_KERNEL, INVALID_HEADER }

    public static final class InvalidArtifactException extends IOException {
        private static final long serialVersionUID = 1L;
        public final Problem problem;

        InvalidArtifactException(Problem problem) {
            super(problem.name());
            this.problem = problem;
        }
    }

    public record Header(Kind kind, int bootHeaderVersion, long kernelBytes) {}
    public record Inspection(Header header, long bytes, String sha256) {}

    private KernelArtifactInspector() {}

    /** SHA-256 identifies the selected bytes; it is NOT a signature or compatibility check. */
    public static Inspection inspect(File file) throws IOException {
        return inspectFile(file, false);
    }

    /** Boot-patch intake also accepts ramdisk-only init_boot, never raw kernel input. */
    public static Inspection inspectBootImage(File file) throws IOException {
        return inspectFile(file, true);
    }

    private static Inspection inspectFile(File file, boolean bootImageOnly) throws IOException {
        long size = file.length();
        if (size > MAX_BYTES) throw invalid(Problem.TOO_LARGE);
        if (size < 2) throw invalid(Problem.TRUNCATED);
        byte[] prefix = new byte[(int) Math.min(4096, size)];
        MessageDigest digest = sha256();
        long count = 0;
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = input.read(buffer)) != -1) {
                checkInterrupted();
                if (n == 0) continue;
                if (count + n > MAX_BYTES) throw invalid(Problem.TOO_LARGE);
                if (count < prefix.length) {
                    System.arraycopy(buffer, 0, prefix, (int) count,
                            (int) Math.min(n, prefix.length - count));
                }
                count += n;
                digest.update(buffer, 0, n);
            }
        }
        if (count != size) throw invalid(Problem.TRUNCATED);
        Header header;
        if (bootImageOnly) {
            header = inspectBootImageHeader(prefix, size);
        } else if (prefix[0] == (byte) 0x1f && prefix[1] == (byte) 0x8b) {
            header = inspectGzip(file);
        } else {
            header = inspectHeader(prefix, size);
        }
        return new Inspection(header, size, HexFormat.of().formatHex(digest.digest()));
    }

    /** Parse format structure only. Never infers GKI/KMI, trusted manager identity or bootability. */
    public static Header inspectHeader(byte[] prefix, long size) throws IOException {
        checkSize(size);
        if (prefix.length > size || prefix.length < Math.min(64, size)) {
            throw invalid(Problem.TRUNCATED);
        }
        if (matches(prefix, 0, BOOT_MAGIC)) return inspectBoot(prefix, size, false);
        if (matches(prefix, 56, ARM64_MAGIC)) {
            long imageBytes = u64(prefix, 16);
            // Old arm64 headers may have image_size == 0. Do not invent that size.
            if (imageBytes != 0 && (imageBytes < 64 || imageBytes > size)) {
                throw invalid(Problem.TRUNCATED);
            }
            return new Header(Kind.ARM64_IMAGE, -1, imageBytes);
        }
        throw invalid(Problem.UNSUPPORTED);
    }

    /** Format validation only; does not establish device compatibility or bootability. */
    public static Header inspectBootImageHeader(byte[] prefix, long size) throws IOException {
        checkSize(size);
        if (prefix.length > size || prefix.length < Math.min(64, size)) throw invalid(Problem.TRUNCATED);
        if (!matches(prefix, 0, BOOT_MAGIC)) throw invalid(Problem.UNSUPPORTED);
        return inspectBoot(prefix, size, true);
    }

    private static Header inspectBoot(byte[] h, long size, boolean allowRamdiskOnly) throws IOException {
        long version = u32(h, 40);
        if (version > 4) throw invalid(Problem.UNSUPPORTED);
        long kernel = u32(h, 8);
        if (kernel == 0 && (!allowRamdiskOnly || version < 3 || u32(h, 12) == 0)) {
            throw invalid(Problem.NO_KERNEL); // GKI intake never treats init_boot as a compiled kernel.
        }
        long page;
        long end;
        if (version >= 3) {
            long headerSize = version == 3 ? 1580 : 1584;
            requirePrefix(h, (int) headerSize);
            if (u32(h, 20) != headerSize) throw invalid(Problem.INVALID_HEADER);
            page = 4096;
            end = page + aligned(kernel, page);
            end += aligned(u32(h, 12), page);
            if (version == 4) end += u32(h, 1580);
        } else {
            int headerSize = version == 0 ? 1632 : version == 1 ? 1648 : 1660;
            requirePrefix(h, headerSize);
            page = u32(h, 36);
            if (page < headerSize || page > 65536 || (page & (page - 1)) != 0) {
                throw invalid(Problem.INVALID_HEADER);
            }
            if (version > 0 && u32(h, 1644) != headerSize) {
                throw invalid(Problem.INVALID_HEADER);
            }
            end = page + aligned(kernel, page);
            end += aligned(u32(h, 16), page) + aligned(u32(h, 24), page);
            if (version > 0) {
                long recoverySize = u32(h, 1632);
                long recoveryOffset = u64(h, 1636);
                if (recoverySize != 0) {
                    if (recoveryOffset != end) throw invalid(Problem.INVALID_HEADER);
                    end += aligned(recoverySize, page);
                }
            }
            if (version == 2) end += aligned(u32(h, 1648), page);
        }
        if (end > size) throw invalid(Problem.TRUNCATED);
        return new Header(Kind.ANDROID_BOOT, (int) version, kernel);
    }

    private static Header inspectGzip(File file) throws IOException {
        byte[] prefix = new byte[64];
        int prefixBytes = 0;
        long count = 0;
        // Consume the complete gzip stream for CRC/truncation validation, with a bomb limit.
        try (InputStream input = new GZIPInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = input.read(buffer)) != -1) {
                checkInterrupted();
                if (n == 0) continue;
                count += n;
                if (count > MAX_BYTES) throw invalid(Problem.TOO_LARGE);
                int take = Math.min(n, prefix.length - prefixBytes);
                System.arraycopy(buffer, 0, prefix, prefixBytes, take);
                prefixBytes += take;
            }
        }
        Header header = inspectHeader(Arrays.copyOf(prefix, prefixBytes), count);
        if (header.kind != Kind.ARM64_IMAGE) throw invalid(Problem.UNSUPPORTED);
        return new Header(Kind.GZIP_ARM64_IMAGE, -1, header.kernelBytes);
    }

    private static void checkSize(long size) throws InvalidArtifactException {
        if (size > MAX_BYTES) throw invalid(Problem.TOO_LARGE);
        if (size < 64) throw invalid(Problem.TRUNCATED);
    }

    private static void requirePrefix(byte[] h, int bytes) throws InvalidArtifactException {
        if (h.length < bytes) throw invalid(Problem.TRUNCATED);
    }

    private static long u32(byte[] h, int offset) throws InvalidArtifactException {
        requirePrefix(h, offset + 4);
        return ((long) h[offset] & 255) | (((long) h[offset + 1] & 255) << 8)
                | (((long) h[offset + 2] & 255) << 16) | (((long) h[offset + 3] & 255) << 24);
    }

    private static long u64(byte[] h, int offset) throws InvalidArtifactException {
        long high = u32(h, offset + 4);
        if ((high & 0x80000000L) != 0) throw invalid(Problem.INVALID_HEADER);
        return u32(h, offset) | (high << 32);
    }

    private static long aligned(long value, long page) { return (value + page - 1) & ~(page - 1); }
    private static boolean matches(byte[] data, int offset, byte[] expected) {
        if (offset + expected.length > data.length) return false;
        for (int i = 0; i < expected.length; i++) if (data[offset + i] != expected[i]) return false;
        return true;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }

    private static void checkInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Inspection cancelled");
    }

    private static InvalidArtifactException invalid(Problem problem) {
        return new InvalidArtifactException(problem);
    }
}
