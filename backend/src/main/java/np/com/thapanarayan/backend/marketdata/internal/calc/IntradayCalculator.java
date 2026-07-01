package np.com.thapanarayan.backend.marketdata.internal.calc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.api.TradeView;

/**
 * Retrospective intraday candles: bucket time-ordered trades into fixed intervals (1m/5m/15m).
 * Computed on demand from the floorsheet for charting — never used for live signals (EOD system).
 */
public final class IntradayCalculator {

    public record IntradayBar(LocalDateTime bucketStart, BigDecimal open, BigDecimal high,
                              BigDecimal low, BigDecimal close, long volume) {}

    private IntradayCalculator() {
    }

    public static List<IntradayBar> bucket(List<TradeView> tradesInTimeOrder, int intervalMinutes) {
        int minutes = Math.max(1, intervalMinutes);
        List<IntradayBar> bars = new ArrayList<>();
        if (tradesInTimeOrder.isEmpty()) {
            return bars;
        }
        long step = Duration.ofMinutes(minutes).toMinutes();

        LocalDateTime bucketStart = null;
        BigDecimal open = null;
        BigDecimal high = null;
        BigDecimal low = null;
        BigDecimal close = null;
        long volume = 0;

        for (TradeView t : tradesInTimeOrder) {
            LocalDateTime start = floorToBucket(t.tradeTime(), minutes);
            if (bucketStart == null) {
                bucketStart = start;
                open = high = low = close = t.price();
                volume = 0;
            } else if (!start.equals(bucketStart)) {
                bars.add(new IntradayBar(bucketStart, open, high, low, close, volume));
                bucketStart = start;
                open = high = low = close = t.price();
                volume = 0;
            }
            high = t.price().max(high);
            low = t.price().min(low);
            close = t.price();
            volume += t.quantity();
        }
        bars.add(new IntradayBar(bucketStart, open, high, low, close, volume));
        return bars;
    }

    private static LocalDateTime floorToBucket(LocalDateTime time, int minutes) {
        int minuteOfDay = time.getHour() * 60 + time.getMinute();
        int floored = (minuteOfDay / minutes) * minutes;
        return time.toLocalDate().atTime(floored / 60, floored % 60);
    }
}
