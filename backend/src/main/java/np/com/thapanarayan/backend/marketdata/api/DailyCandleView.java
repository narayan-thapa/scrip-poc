package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Published daily candle with its symbol — for board-wide screening/ranking. */
public record DailyCandleView(
        String symbol,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal turnover,
        int tradesCount,
        BigDecimal changePct) {
}
