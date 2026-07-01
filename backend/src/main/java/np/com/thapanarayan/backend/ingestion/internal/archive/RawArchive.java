package np.com.thapanarayan.backend.ingestion.internal.archive;

import java.io.InputStream;

/**
 * Immutable archive of every raw scraped CSV so any day can be deterministically reprocessed.
 * Keys are derived from the trade date + content hash ({@code raw/{date}/{hash}.csv}) — the
 * untrusted filename is NEVER used as a path component (path-traversal safe, Decision A).
 */
public interface RawArchive {

    /** Store the bytes under a controlled key and return that key. Idempotent for identical content. */
    String store(java.time.LocalDate tradeDate, String contentHash, byte[] content);

    /** Open a previously archived object for reprocessing. */
    InputStream open(String key);
}
