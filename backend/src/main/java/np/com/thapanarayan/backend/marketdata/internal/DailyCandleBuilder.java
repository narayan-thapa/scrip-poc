package np.com.thapanarayan.backend.marketdata.internal;

import java.util.Comparator;
import java.util.List;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;

/**
 * Builds the daily OHLCV aggregate for one symbol on one date. previous_close and
 * change% are <em>not</em> computed here — they need the calendar and the prior
 * day's candle, which the orchestrating service supplies.
 */
final class DailyCandleBuilder {

    private DailyCandleBuilder() {
    }

    static OhlcvAggregate build(List<FloorsheetTradeRecord> symbolTrades) {
        List<FloorsheetTradeRecord> ordered = symbolTrades.stream()
                .sorted(Comparator.comparing(FloorsheetTradeRecord::tradeTime))
                .toList();
        return OhlcvAggregate.of(ordered);
    }
}
