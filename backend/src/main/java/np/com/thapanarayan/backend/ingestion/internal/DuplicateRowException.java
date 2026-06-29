package np.com.thapanarayan.backend.ingestion.internal;

/**
 * Thrown when a contract id repeats within the same file. Counted as a duplicate
 * (not a rejection); the {@code contract_id} upsert would absorb it anyway, but
 * skipping early avoids redundant writes and gives an accurate duplicate count.
 */
class DuplicateRowException extends RuntimeException {

    DuplicateRowException(String contractId) {
        super("Duplicate contract_id within file: " + contractId);
    }
}
