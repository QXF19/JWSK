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
            System.out.println("Alpha APK comment and bootstrap tests passed (9 cases)");
        } finally {
            Files.deleteIfExists(zip);
            Files.deleteIfExists(bad);
            Files.deleteIfExists(directory);
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("ZIP protocol test failed");
    }
}
