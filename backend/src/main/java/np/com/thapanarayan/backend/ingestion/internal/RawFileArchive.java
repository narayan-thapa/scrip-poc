package np.com.thapanarayan.backend.ingestion.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Immutable, content-addressed archive of raw scraped CSVs, so any day can be
 * deterministically reprocessed. Keys are derived only from the trade date and a
 * content hash — never from the (untrusted) original filename. The local
 * implementation can later be swapped for S3/MinIO without touching callers.
 */
interface RawFileArchive {

    /** A stored raw file. */
    record ArchivedFile(LocalDate tradeDate, String sha256, Path path, long sizeBytes) {
    }

    /**
     * Streams {@code content} to storage while hashing it (bounded memory). If a
     * file with the same content hash already exists for the date, returns the
     * existing entry instead of writing a duplicate.
     */
    ArchivedFile archive(LocalDate tradeDate, InputStream content) throws IOException;

    /** Most recently archived file for a date, for reprocessing. */
    Optional<ArchivedFile> findLatest(LocalDate tradeDate);
}
