package np.com.thapanarayan.backend.marketdata.internal.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.api.TradeView;
import np.com.thapanarayan.backend.marketdata.internal.domain.DailyCandle;

/**
 * Builds a daily candle from a symbol's trades. Open = first trade by time, Close = last by time,
 * High/Low = max/min price, Volume = Σqty, Turnover = Σamount, VWAP = turnover ÷ volume. Pure.
 */
public final class CandleCalculator {

    private CandleCalculator() {
    }

    /** @param tradesInTimeOrder non-empty, ordered by trade time; @param prevClose may be null. */
    public static DailyCandle compute(String symbol, LocalDate date, List<TradeView> tradesInTimeOrder,
                                      BigDecimal prevClose) {
        if (tradesInTimeOrder.isEmpty()) {
            throw new IllegalArgumentException("no trades for " + symbol + " on " + date);
        }
        BigDecimal open = tradesInTimeOrder.get(0).price();
        BigDecimal close = tradesInTimeOrder.get(tradesInTimeOrder.size() - 1).price();
        BigDecimal high = open;
        BigDecimal low = open;
        long volume = 0;
        BigDecimal turnover = BigDecimal.ZERO;
        for (TradeView t : tradesInTimeOrder) {
            if (t.price().compareTo(high) > 0) {
                high = t.price();
            }
            if (t.price().compareTo(low) < 0) {
                low = t.price();
            }
            volume += t.quantity();
            turnover = turnover.add(t.amount());
        }
        BigDecimal vwap = volume == 0
                ? close
                : turnover.divide(BigDecimal.valueOf(volume), 4, RoundingMode.HALF_UP);

        BigDecimal changePct = null;
        if (prevClose != null && prevClose.signum() > 0) {
            changePct = close.subtract(prevClose)
                    .divide(prevClose, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return new DailyCandle(symbol, date, open, high, low, close, volume, turnover,
                tradesInTimeOrder.size(), vwap, prevClose, changePct);
    }
}
