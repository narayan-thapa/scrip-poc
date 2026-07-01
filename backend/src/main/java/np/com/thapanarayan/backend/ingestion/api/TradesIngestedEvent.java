package np.com.thapanarayan.backend.ingestion.api;

import java.time.LocalDate;

/**
 * Published (after commit) once a trading day's raw trades are ingested. Downstream modules react:
 * marketdata aggregates candles/volume-profile/broker-flow (Phase 3); the notification fan-out is
 * skipped for backfilled historical dates via {@link #suppressNotifications()} so a backfill doesn't
 * spam users — only the latest/live date notifies.
 */
public record TradesIngestedEvent(
        LocalDate tradeDate,
        int rowsAccepted,
        boolean suppressNotifications) {
}
