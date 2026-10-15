package me.weishu.kernelsu.jwsk.artifacts;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.Assert.*;
import static me.weishu.kernelsu.jwsk.artifacts.KernelArtifactInspector.*;

public class BootImageIntakeTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void ramdiskOnlyInitBootIsAcceptedOnlyForPatching() throws Exception {
        byte[] bytes = boot(4, 0, 64);
        assertEquals(0, inspectBootImageHeader(bytes, bytes.length).kernelBytes());
        assertEquals(4, inspectBootImageHeader(bytes, bytes.length).bootHeaderVersion());
        assertProblem(Problem.NO_KERNEL, () -> inspectHeader(bytes, bytes.length));
    }

    @Test public void allSupportedBootVersionsCanBeInspectedForPatching() throws Exception {
        for (int version = 0; version <= 4; version++) {
            byte[] bytes = boot(version, 64, 64);
            assertEquals(version, inspectBootImageHeader(bytes, bytes.length).bootHeaderVersion());
        }
    }

    @Test public void noPayloadIsRejected() {
        byte[] bytes = boot(4, 0, 0);
        assertProblem(Problem.NO_KERNEL, () -> inspectBootImageHeader(bytes, bytes.length));
    }

    @Test public void rawKernelAndArchiveAreNotBootPatchInputs() {
        byte[] raw = new byte[128];
        System.arraycopy(new byte[]{'A', 'R', 'M', 0x64}, 0, raw, 56, 4);
        assertProblem(Problem.UNSUPPORTED, () -> inspectBootImageHeader(raw, raw.length));
        raw[0] = (byte) 0x1f; raw[1] = (byte) 0x8b;
        assertProblem(Problem.UNSUPPORTED, () -> inspectBootImageHeader(raw, raw.length));
    }

    @Test public void truncatedInitBootIsRejected() {
        byte[] bytes = boot(4, 0, 8192);
        assertProblem(Problem.TRUNCATED, () -> inspectBootImageHeader(bytes, bytes.length));
    }

    @Test public void futureHeaderOrInvalidV4HeaderIsRejected() {
        byte[] bytes = boot(5, 0, 64);
        assertProblem(Problem.UNSUPPORTED, () -> inspectBootImageHeader(bytes, bytes.length));
        put32(bytes, 40, 4); put32(bytes, 20, 1580);
        assertProblem(Problem.INVALID_HEADER, () -> inspectBootImageHeader(bytes, bytes.length));
    }

    @Test public void originalInitBootBytesRemainUnchangedAndHashMatches() throws Exception {
        byte[] bytes = boot(4, 0, 64);
        var file = temp.newFile("init_boot.img");
        Files.write(file.toPath(), bytes);
        var result = inspectBootImage(file);
        assertEquals(Kind.ANDROID_BOOT, result.header().kind());
        assertEquals(bytes.length, result.bytes());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), result.sha256());
        assertArrayEquals(bytes, Files.readAllBytes(file.toPath()));
        assertProblem(Problem.NO_KERNEL, () -> inspect(file));
    }

    @Test public void sizeAndV4SignatureBoundsRemainEnforced() {
        byte[] bytes = boot(4, 0, 64);
        assertProblem(Problem.TOO_LARGE, () -> inspectBootImageHeader(bytes, MAX_BYTES + 1));
        put32(bytes, 1580, 16384);
        assertProblem(Problem.TRUNCATED, () -> inspectBootImageHeader(bytes, bytes.length));
    }

    private static byte[] boot(int version, int kernel, int ramdisk) {
        byte[] bytes = new byte[8192];
        System.arraycopy("ANDROID!".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 8);
        put32(bytes, 8, kernel); put32(bytes, 40, version);
        if (version >= 3) {
            put32(bytes, 12, ramdisk); put32(bytes, 20, version == 3 ? 1580 : 1584);
        } else {
            put32(bytes, 16, ramdisk); put32(bytes, 36, 2048);
            if (version > 0) put32(bytes, 1644, version == 1 ? 1648 : 1660);
        }
        // 8192 bytes fits legacy kernel+ramdisk, but modern images with both need 12288.
        return version >= 3 && kernel > 0 && ramdisk > 0 ? java.util.Arrays.copyOf(bytes, 12288) : bytes;
    }

    private static void put32(byte[] bytes, int offset, long value) {
        for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (i * 8));
    }
    private interface Checked { void run() throws Exception; }
    private static void assertProblem(Problem expected, Checked body) {
        var error = assertThrows(InvalidArtifactException.class, body::run);
        assertEquals(expected, error.problem);
    }
}
