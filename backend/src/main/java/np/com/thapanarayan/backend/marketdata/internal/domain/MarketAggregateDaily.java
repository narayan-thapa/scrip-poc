package np.com.thapanarayan.backend.marketdata.internal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Whole-market totals + breadth for the reserved {@code NEPSE} symbol. Index-proxy fields are a
 * cap-weighted approximation (nullable when {@code listed_shares} is unknown); the official index is
 * a separate ingested feed (nullable here).
 */
public record MarketAggregateDaily(
        LocalDate tradeDate,
        long totalVolume,
        BigDecimal totalTurnover,
        long totalTrades,
        int advances,
        int declines,
        int unchanged,
        BigDecimal indexProxyOpen,
        BigDecimal indexProxyHigh,
        BigDecimal indexProxyLow,
        BigDecimal indexProxyClose,
        BigDecimal officialIndexClose) {
}
