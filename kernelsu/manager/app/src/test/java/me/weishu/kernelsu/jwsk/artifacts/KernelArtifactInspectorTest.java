package me.weishu.kernelsu.jwsk.artifacts;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;
import static me.weishu.kernelsu.jwsk.artifacts.KernelArtifactInspector.*;

public class KernelArtifactInspectorTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void bootVersionsZeroThroughFour() throws Exception {
        for (int version = 0; version <= 4; version++) {
            byte[] boot = boot(version);
            Header header = inspectHeader(boot, boot.length);
            assertEquals(Kind.ANDROID_BOOT, header.kind());
            assertEquals(version, header.bootHeaderVersion());
            assertEquals(64, header.kernelBytes());
        }
    }

    @Test public void rawArm64Image() throws Exception {
        assertEquals(Kind.ARM64_IMAGE, inspectHeader(image(), 128).kind());
    }

    @Test public void oldImageWithUnknownDeclaredSizeRemainsUnknown() throws Exception {
        byte[] image = image();
        put32(image, 16, 0);
        assertEquals(0, inspectHeader(image, 128).kernelBytes());
    }

    @Test public void hashIsForOriginalBytesAndFileIsUnchanged() throws Exception {
        File file = temp.newFile("misleading-name.zip");
        byte[] image = image();
        Files.write(file.toPath(), image);
        Inspection result = inspect(file);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image)), result.sha256());
        assertEquals(image.length, result.bytes());
        assertArrayEquals(image, Files.readAllBytes(file.toPath()));
    }

    @Test public void gzipArm64IsAcceptedWithoutChangingCompressedFile() throws Exception {
        File file = temp.newFile("Image.gz");
        try (GZIPOutputStream stream = new GZIPOutputStream(new FileOutputStream(file))) { stream.write(image()); }
        byte[] before = Files.readAllBytes(file.toPath());
        assertEquals(Kind.GZIP_ARM64_IMAGE, inspect(file).header().kind());
        assertArrayEquals(before, Files.readAllBytes(file.toPath()));
    }

    @Test public void gzipNonKernelIsRejected() throws Exception {
        File file = temp.newFile();
        try (GZIPOutputStream stream = new GZIPOutputStream(new FileOutputStream(file))) { stream.write(new byte[128]); }
        assertProblem(Problem.UNSUPPORTED, () -> inspect(file));
    }

    @Test public void truncatedGzipFails() throws Exception {
        File file = temp.newFile();
        try (GZIPOutputStream stream = new GZIPOutputStream(new FileOutputStream(file))) { stream.write(image()); }
        byte[] data = Files.readAllBytes(file.toPath());
        Files.write(file.toPath(), java.util.Arrays.copyOf(data, data.length - 4));
        assertThrows(java.io.IOException.class, () -> inspect(file));
    }

    @Test public void initBootIsNotACompiledKernel() {
        byte[] boot = boot(4);
        put32(boot, 8, 0);
        assertProblem(Problem.NO_KERNEL, () -> inspectHeader(boot, boot.length));
    }

    @Test public void futureBootHeaderFailsClosed() {
        byte[] boot = boot(4);
        put32(boot, 40, 5);
        assertProblem(Problem.UNSUPPORTED, () -> inspectHeader(boot, boot.length));
    }

    @Test public void incorrectHeaderSizeIsRejected() {
        byte[] boot = boot(3);
        put32(boot, 20, 1584);
        assertProblem(Problem.INVALID_HEADER, () -> inspectHeader(boot, boot.length));
    }

    @Test public void invalidPageSizeIsRejected() {
        byte[] boot = boot(0);
        put32(boot, 36, 3072);
        assertProblem(Problem.INVALID_HEADER, () -> inspectHeader(boot, boot.length));
    }

    @Test public void payloadOutsideFileIsRejected() {
        byte[] boot = boot(4);
        put32(boot, 8, 0xffffffffL);
        assertProblem(Problem.TRUNCATED, () -> inspectHeader(boot, boot.length));
    }

    @Test public void v4SignatureOutsideFileIsRejected() {
        byte[] boot = boot(4);
        put32(boot, 1580, 16384);
        assertProblem(Problem.TRUNCATED, () -> inspectHeader(boot, boot.length));
    }

    @Test public void unknownOrTinyFilesRejected() {
        assertProblem(Problem.UNSUPPORTED, () -> inspectHeader(new byte[64], 64));
        assertProblem(Problem.TRUNCATED, () -> inspectHeader(new byte[63], 63));
    }

    @Test public void sizeLimitAndImageLengthAreChecked() {
        assertProblem(Problem.TOO_LARGE, () -> inspectHeader(image(), MAX_BYTES + 1));
        byte[] image = image();
        put32(image, 16, 4096);
        assertProblem(Problem.TRUNCATED, () -> inspectHeader(image, image.length));
    }

    @Test public void invalidRecoveryOffsetIsRejected() {
        byte[] boot = boot(1);
        put32(boot, 1632, 64);
        put32(boot, 1636, 1);
        assertProblem(Problem.INVALID_HEADER, () -> inspectHeader(boot, boot.length));
    }

    private static byte[] boot(int version) {
        byte[] h = new byte[12288];
        System.arraycopy("ANDROID!".getBytes(StandardCharsets.US_ASCII), 0, h, 0, 8);
        put32(h, 8, 64);
        put32(h, 40, version);
        if (version >= 3) { put32(h, 20, version == 3 ? 1580 : 1584); }
        else {
            put32(h, 36, 4096);
            if (version > 0) put32(h, 1644, version == 1 ? 1648 : 1660);
        }
        return h;
    }

    private static byte[] image() {
        byte[] h = new byte[128];
        System.arraycopy(new byte[]{'A', 'R', 'M', 0x64}, 0, h, 56, 4);
        put32(h, 16, h.length);
        return h;
    }

    private static void put32(byte[] h, int offset, long value) {
        for (int i = 0; i < 4; i++) h[offset + i] = (byte) (value >>> (i * 8));
    }

    private interface Checked { void run() throws Exception; }
    private static void assertProblem(Problem expected, Checked body) {
        InvalidArtifactException actual = assertThrows(InvalidArtifactException.class, body::run);
        assertEquals(expected, actual.problem);
    }
}
