package np.com.thapanarayan.backend.ingestion.api;

import java.time.LocalDate;

/**
 * Published (AFTER_COMMIT) once a date's trades are persisted. Drives the
 * downstream pipeline (aggregation → indicators → signals).
 *
 * @param tradeDate             the authoritative trade date (from the filename)
 * @param jobId                 the ingestion job that produced these trades
 * @param accepted              number of trades persisted
 * @param suppressNotifications true for historical backfill dates, so the
 *                              notification fan-out does not spam users with
 *                              stale alerts; false for the live daily run
 */
public record TradesIngestedEvent(
        LocalDate tradeDate,
        long jobId,
        int accepted,
        boolean suppressNotifications) {
}
