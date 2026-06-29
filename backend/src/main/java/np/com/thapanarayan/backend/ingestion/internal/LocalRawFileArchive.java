package np.com.thapanarayan.backend.ingestion.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

/**
 * Filesystem-backed {@link RawFileArchive}. Stores under
 * {@code <root>/raw/<trade-date>/<sha256>.csv}. Streams input through a SHA-256
 * digest into a temp file, then atomically moves it into place — single pass,
 * bounded memory, content-addressed (re-archiving identical bytes is a no-op).
 */
@Component
class LocalRawFileArchive implements RawFileArchive {

    private final Path root;

    LocalRawFileArchive(IngestionProperties properties) {
        this.root = Paths.get(properties.archiveRoot()).toAbsolutePath().normalize();
    }

    @Override
    public ArchivedFile archive(LocalDate tradeDate, InputStream content) throws IOException {
        Path dir = root.resolve("raw").resolve(tradeDate.toString());
        Files.createDirectories(dir);
        Path temp = Files.createTempFile(dir, "incoming-", ".part");

        String hash;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream in = new DigestInputStream(content, digest)) {
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            hash = HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            Files.deleteIfExists(temp);
            throw new IllegalStateException("SHA-256 unavailable", e);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temp);
            throw e;
        }

        Path target = dir.resolve(hash + ".csv");
        if (Files.exists(target)) {
            Files.deleteIfExists(temp); // identical content already archived
        } else {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new ArchivedFile(tradeDate, hash, target, Files.size(target));
    }

    @Override
    public Optional<ArchivedFile> findLatest(LocalDate tradeDate) {
        Path dir = root.resolve("raw").resolve(tradeDate.toString());
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".csv"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .map(p -> toArchivedFile(tradeDate, p));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static ArchivedFile toArchivedFile(LocalDate date, Path p) {
        String name = p.getFileName().toString();
        String hash = name.substring(0, name.length() - ".csv".length());
        long size;
        try {
            size = Files.size(p);
        } catch (IOException e) {
            size = -1;
        }
        return new ArchivedFile(date, hash, p, size);
    }
}
