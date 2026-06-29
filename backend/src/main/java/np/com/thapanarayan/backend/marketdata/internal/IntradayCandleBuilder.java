package np.com.thapanarayan.backend.marketdata.internal;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;

/**
 * Buckets a symbol's trades into fixed intraday intervals. Bucket edges are
 * aligned to midnight of the trade date ({@code interval}-minute grid), so the
 * same interval always yields the same buckets — useful context, not a live
 * signal source (§6.2).
 */
final class IntradayCandleBuilder {

    private IntradayCandleBuilder() {
    }

    /** One bucket's OHLCV plus its start time. */
    record IntradayBucket(LocalDateTime bucketStart, OhlcvAggregate ohlcv) {
    }

    static List<IntradayBucket> build(LocalDate tradeDate, List<FloorsheetTradeRecord> symbolTrades,
            int intervalMinutes) {
        LocalDateTime dayStart = tradeDate.atStartOfDay();
        Map<LocalDateTime, List<FloorsheetTradeRecord>> byBucket = new TreeMap<>();
        for (FloorsheetTradeRecord t : symbolTrades) {
            long minutes = Duration.between(dayStart, t.tradeTime()).toMinutes();
            long bucketIndex = Math.floorDiv(minutes, intervalMinutes);
            LocalDateTime bucketStart = dayStart.plusMinutes(bucketIndex * intervalMinutes);
            byBucket.computeIfAbsent(bucketStart, k -> new ArrayList<>()).add(t);
        }

        List<IntradayBucket> buckets = new ArrayList<>(byBucket.size());
        for (var e : byBucket.entrySet()) {
            List<FloorsheetTradeRecord> ordered = e.getValue().stream()
                    .sorted(Comparator.comparing(FloorsheetTradeRecord::tradeTime))
                    .toList();
            buckets.add(new IntradayBucket(e.getKey(), OhlcvAggregate.of(ordered)));
        }
        return buckets;
    }
}
