package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;

/**
 * Open/High/Low/Close + volume/turnover/VWAP for a time-ordered set of trades.
 * Shared by the daily and intraday candle builders so OHLCV is computed one way.
 *
 * @param tradeCount number of trades that formed this candle
 */
record OhlcvAggregate(
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal turnover,
        BigDecimal vwap,
        int tradeCount) {

    static final int PRICE_SCALE = 4;

    /**
     * Reduces a non-empty, time-ordered trade list into one candle. Open is the
     * first trade's price and close the last, so callers must pass trades already
     * sorted by trade time.
     */
    static OhlcvAggregate of(List<FloorsheetTradeRecord> ordered) {
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("cannot build a candle from zero trades");
        }
        BigDecimal open = ordered.getFirst().price();
        BigDecimal close = ordered.getLast().price();
        BigDecimal high = open;
        BigDecimal low = open;
        long volume = 0L;
        BigDecimal turnover = BigDecimal.ZERO;
        for (FloorsheetTradeRecord t : ordered) {
            if (t.price().compareTo(high) > 0) {
                high = t.price();
            }
            if (t.price().compareTo(low) < 0) {
                low = t.price();
            }
            volume += t.quantity();
            turnover = turnover.add(t.amount());
        }
        BigDecimal vwap = volume == 0L
                ? close
                : turnover.divide(BigDecimal.valueOf(volume), PRICE_SCALE, RoundingMode.HALF_UP);
        return new OhlcvAggregate(open, high, low, close, volume, turnover, vwap, ordered.size());
    }
}
