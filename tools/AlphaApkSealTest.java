import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** No credential or Android runtime required; run after compiling AlphaApkSeal. */
public final class AlphaApkSealTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("jwsk-alpha-protocol-test-");
        Path zip = directory.resolve("input.zip");
        Path bad = directory.resolve("bad.zip");
        try {
            byte[] payload = "original APK entry bytes".getBytes(StandardCharsets.UTF_8);
            try (var stream = new ZipOutputStream(Files.newOutputStream(zip))) {
                stream.putNextEntry(new ZipEntry("payload"));
                stream.write(payload);
                stream.closeEntry();
                stream.setComment("old comment");
            }
            AlphaApkSeal.replaceComment(zip, AlphaApkSeal.COMMENT);
            check(Arrays.equals(AlphaApkSeal.readComment(zip), AlphaApkSeal.COMMENT));
            try (var archive = new ZipFile(zip.toFile())) {
                check(Arrays.equals(archive.getInputStream(archive.getEntry("payload")).readAllBytes(), payload));
                String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload));
                AlphaApkSeal.checkDigest(archive, "payload", digest, 1024);
                try {
                    AlphaApkSeal.checkDigest(archive, "payload", "0".repeat(64), 1024);
                    throw new AssertionError("Changed native artifact accepted");
                } catch (SecurityException expected) { }
                try {
                    AlphaApkSeal.checkDigest(archive, "payload", digest, 4);
                    throw new AssertionError("Oversized artifact accepted");
                } catch (SecurityException expected) { }
            }
            try {
                AlphaApkSeal.verifyPackagedBackends(zip);
                throw new AssertionError("Missing backend receipts accepted");
            } catch (SecurityException expected) { }
            for (int[] ids : new int[][] {
                {0x7109871a, 0x42726577}, {0x7109871a, 0xf05368c0},
                {0x7109871a, 0x7109871a}, {0x42726577}
            }) {
                signingFixture(zip, bad, ids);
                boolean rejected = false;
                try { AlphaApkSeal.verifyKernelSuFormat(bad); }
                catch (SecurityException expected) { rejected = true; }
                check(rejected == !(ids.length == 2 && ids[1] == 0x42726577));
            }
            // A fake EOCD signature inside a comment must not confuse the scanner.
            byte[] nested = new byte[]{0x50, 0x4b, 0x05, 0x06, 4, 3, 2, 1};
            AlphaApkSeal.replaceComment(zip, nested);
            check(Arrays.equals(AlphaApkSeal.readComment(zip), nested));
            AlphaApkSeal.replaceComment(zip, new byte[65535]);
            check(AlphaApkSeal.readComment(zip).length == 65535);
            AlphaApkSeal.replaceComment(zip, new byte[0]);
            check(AlphaApkSeal.readComment(zip).length == 0);
            try {
                AlphaApkSeal.replaceComment(zip, new byte[65536]);
                throw new AssertionError("Oversized comment accepted");
            } catch (IllegalArgumentException expected) { }
            Files.write(bad, new byte[21]);
            try {
                AlphaApkSeal.readComment(bad);
                throw new AssertionError("Truncated ZIP accepted");
            } catch (IllegalArgumentException expected) { }
            Files.write(bad, new byte[64]);
            try {
                AlphaApkSeal.readComment(bad);
                throw new AssertionError("Missing EOCD accepted");
            } catch (IllegalArgumentException expected) { }
            try (var stream = new ZipOutputStream(Files.newOutputStream(bad))) {
                stream.putNextEntry(new ZipEntry("AndroidManifest.xml"));
                stream.write(new byte[]{3, 0, 8, 0, 8, 0, 0, 0});
                stream.closeEntry();
            }
            AlphaApkSeal.verifyBootstrapShape(bad);
            try (var stream = new ZipOutputStream(Files.newOutputStream(bad))) {
                stream.putNextEntry(new ZipEntry("classes.dex")); stream.write(new byte[8]); stream.closeEntry();
            }
            try {
                AlphaApkSeal.verifyBootstrapShape(bad);
                throw new AssertionError("Executable bootstrap accepted");
            } catch (SecurityException expected) { }
            System.out.println("Alpha APK comment, bootstrap, artifact and KernelSU signing tests passed (17 cases)");
        } finally {
            Files.deleteIfExists(zip);
            Files.deleteIfExists(bad);
            Files.deleteIfExists(directory);
        }
    }

    private static void signingFixture(Path source, Path target, int[] ids) throws Exception {
        byte[] original = Files.readAllBytes(source);
        int end;
        try (var file = new java.io.RandomAccessFile(source.toFile(), "r")) {
            end = Math.toIntExact(AlphaApkSeal.eocd(file));
        }
        var input = java.nio.ByteBuffer.wrap(original).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int central = input.getInt(end + 16);
        int blockLength = 32 + ids.length * 12;
        var output = java.nio.ByteBuffer.allocate(original.length + blockLength).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        output.put(original, 0, central).putLong(blockLength - 8);
        for (int id : ids) output.putLong(4).putInt(id);
        output.putLong(blockLength - 8).put("APK Sig Block 42".getBytes(StandardCharsets.US_ASCII));
        output.put(original, central, original.length - central);
        output.putInt(end + blockLength + 16, central + blockLength);
        Files.write(target, output.array());
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("ZIP protocol test failed");
    }
}
