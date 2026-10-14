import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipFile;

/** Build-time only: add Alpha's version-comment protocol BEFORE re-signing the APK. */
public final class AlphaApkSeal {
    static final String CERTIFICATE = "1f934155f4ffc1aeefb44faa15bdbef277ca4472435ed45e09fa583b9d67a76f";
    static final byte[] COMMENT = ("versionCode=31000\n"
        + "jwskAlphaSource=96221b69fae9910b1c0c75c2f92a4ebb2c2dc698\n")
        .getBytes(StandardCharsets.US_ASCII);

    public static void main(String[] args) {
        try {
            boolean bootstrap = args.length == 3 && args[0].equals("--bootstrap");
            if (args.length != 2 && !bootstrap) throw new IllegalArgumentException("Expected input and new output APK paths");
            int offset = bootstrap ? 1 : 0;
            seal(Path.of(args[offset]).toAbsolutePath().normalize(), Path.of(args[offset + 1]).toAbsolutePath().normalize(), bootstrap);
            System.out.println("Alpha manager protocol and original JWSK v2/v3 signing identity verified");
        } catch (Exception error) {
            // Signing providers can include their arguments in exception messages.
            // Do not dump exceptions or credentials into CI logs.
            System.err.println("Alpha APK sealing failed (" + error.getClass().getSimpleName() + ")");
            System.exit(1);
        }
    }

    static void seal(Path input, Path output) throws Exception {
        seal(input, output, false);
    }

    private static void seal(Path input, Path output, boolean bootstrap) throws Exception {
        if (!Files.isRegularFile(input) || Files.exists(output) || input.equals(output)) {
            throw new IllegalArgumentException("Input must exist and output must be a new file");
        }
        if (bootstrap) verifyBootstrapShape(input);
        else verify(input);
        Properties properties = new Properties();
        String propertiesPath = System.getenv("JWSK_SIGNING_PROPERTIES");
        Path configuration = propertiesPath == null ? null : Path.of(propertiesPath).toAbsolutePath();
        if (configuration != null) {
            try (var stream = Files.newInputStream(configuration)) { properties.load(stream); }
        }
        String keyPath = setting("KEYSTORE_FILE", properties);
        Path keyFile = Path.of(keyPath);
        if (!keyFile.isAbsolute()) {
            if (configuration == null) throw new IllegalArgumentException("KEYSTORE_FILE must be absolute");
            // Same legacy path convention as JWSK's Gradle signing configuration.
            keyFile = configuration.getParent().resolve("manager").resolve(keyFile).normalize();
        }
        char[] storePassword = setting("KEYSTORE_PASSWORD", properties).toCharArray();
        char[] keyPassword = setting("KEYSTORE_ALIAS_PASSWORD", properties).toCharArray();
        Path staging = null;
        try {
            KeyStore store = KeyStore.getInstance(keyFile.toFile(), storePassword);
            String alias = setting("KEYSTORE_ALIAS", properties);
            if (!store.isKeyEntry(alias)) throw new IllegalArgumentException("Private signing key required");
            X509Certificate certificate = (X509Certificate) store.getCertificate(alias);
            if (!fingerprint(certificate).equals(CERTIFICATE)) throw new SecurityException("Signing identity mismatch");
            PrivateKey privateKey = (PrivateKey) store.getKey(alias, keyPassword);
            staging = Files.createTempDirectory(output.getParent(), ".jwsk-alpha-seal-");
            Path prepared = Files.copy(input, staging.resolve("protocol.apk"));
            replaceComment(prepared, COMMENT);
            Path signed = staging.resolve("sealed.apk");
            var signer = new ApkSigner.SignerConfig.Builder("JWSK", privateKey, List.of(certificate)).build();
            new ApkSigner.Builder(List.of(signer))
                .setInputApk(prepared.toFile()).setOutputApk(signed.toFile())
                .setMinSdkVersion(34).setV1SigningEnabled(true).setV2SigningEnabled(true)
                .setV3SigningEnabled(true).setV4SigningEnabled(false)
                .setAlignmentPreserved(true).setLibraryPageAlignmentBytes(16384)
                .setOtherSignersSignaturesPreserved(false).build().sign();
            verify(signed);
            if (!Arrays.equals(readComment(signed), COMMENT)) throw new SecurityException("Protocol comment changed");
            Files.move(signed, output); // Never replace an existing artifact or the input APK.
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
            properties.clear();
            if (staging != null) {
                // Exact files in our newly created directory only, never recursive deletion.
                Files.deleteIfExists(staging.resolve("protocol.apk"));
                Files.deleteIfExists(staging.resolve("sealed.apk"));
                Files.deleteIfExists(staging);
            }
        }
    }

    /** Only our AAPT-generated no-code certificate anchor may start unsigned. */
    static void verifyBootstrapShape(Path input) throws Exception {
        try (var zip = new ZipFile(input.toFile())) {
            if (zip.size() < 1 || zip.size() > 2) throw new SecurityException("Unexpected bootstrap entries");
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.equals("AndroidManifest.xml") && !name.equals("resources.arsc")) {
                    throw new SecurityException("Executable or unexpected bootstrap entry");
                }
            }
            var resources = zip.getEntry("resources.arsc");
            if (resources != null) {
                if (resources.getSize() < 12 || resources.getSize() > 1024) throw new SecurityException("Non-empty bootstrap resources");
                try (var stream = zip.getInputStream(resources)) {
                    byte[] header = stream.readNBytes(12);
                    if (!Arrays.equals(Arrays.copyOf(header, 4), new byte[]{2, 0, 12, 0})
                        || header[8] != 0 || header[9] != 0 || header[10] != 0 || header[11] != 0) {
                        throw new SecurityException("Bootstrap must not contain a resource package");
                    }
                }
            }
            var entry = zip.getEntry("AndroidManifest.xml");
            if (entry == null || entry.isDirectory() || entry.getSize() < 8 || entry.getSize() > 65536) {
                throw new SecurityException("Invalid bootstrap manifest");
            }
            // Android binary XML header, not a source XML or an executable carrier.
            try (var stream = zip.getInputStream(entry)) {
                if (!Arrays.equals(stream.readNBytes(4), new byte[]{3, 0, 8, 0})) {
                    throw new SecurityException("Expected compiled bootstrap manifest");
                }
            }
        }
    }

    private static String setting(String name, Properties properties) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = properties.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing signing configuration");
        return value;
    }

    private static void verify(Path apk) throws Exception {
        // Alpha reads the v2 certificate even on Android 14+, so v3 alone is insufficient.
        var v2 = new ApkVerifier.Builder(apk.toFile()).setMinCheckedPlatformVersion(24)
            .setMaxCheckedPlatformVersion(27).build().verify();
        var current = new ApkVerifier.Builder(apk.toFile()).setMinCheckedPlatformVersion(34)
            .setMaxCheckedPlatformVersion(36).build().verify();
        if (!v2.isVerified() || !v2.isVerifiedUsingV2Scheme() || !current.isVerified()
            || !current.isVerifiedUsingV3Scheme() || v2.getSignerCertificates().size() != 1
            || current.getSignerCertificates().size() != 1
            || !fingerprint(v2.getSignerCertificates().getFirst()).equals(CERTIFICATE)
            || !fingerprint(current.getSignerCertificates().getFirst()).equals(CERTIFICATE)) {
            throw new SecurityException("APK signature validation failed");
        }
    }

    private static String fingerprint(X509Certificate certificate) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    /** Locate only a structurally valid final EOCD; ignore embedded signature-like bytes. */
    static long eocd(RandomAccessFile file) throws Exception {
        long length = file.length();
        if (length < 22) throw new IllegalArgumentException("Truncated ZIP");
        for (long offset = length - 22, end = Math.max(0, length - 22 - 65535); offset >= end; offset--) {
            file.seek(offset);
            if (file.readInt() != 0x504b0506) continue;
            file.seek(offset + 20);
            int commentLength = file.readUnsignedByte() | (file.readUnsignedByte() << 8);
            if (offset + 22 + commentLength != length) continue;
            // Require the single-disk, non-ZIP64 APK form expected by Alpha.
            file.seek(offset + 4);
            if (file.readInt() != 0) throw new IllegalArgumentException("Multi-disk ZIP is not an APK");
            file.seek(offset + 16);
            long centralOffset = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            if (centralOffset > offset) throw new IllegalArgumentException("Invalid central directory");
            return offset;
        }
        throw new IllegalArgumentException("Missing ZIP end record");
    }

    static void replaceComment(Path apk, byte[] comment) throws Exception {
        if (comment.length > 65535) throw new IllegalArgumentException("Oversized ZIP comment");
        try (var file = new RandomAccessFile(apk.toFile(), "rw")) {
            long offset = eocd(file);
            file.seek(offset + 20);
            file.writeByte(comment.length & 255);
            file.writeByte(comment.length >>> 8);
            file.write(comment);
            file.setLength(offset + 22 + comment.length);
        }
    }

    static byte[] readComment(Path apk) throws Exception {
        try (var file = new RandomAccessFile(apk.toFile(), "r")) {
            long offset = eocd(file);
            file.seek(offset + 20);
            int length = file.readUnsignedByte() | (file.readUnsignedByte() << 8);
            byte[] comment = new byte[length];
            file.readFully(comment);
            return comment;
        }
    }
}
