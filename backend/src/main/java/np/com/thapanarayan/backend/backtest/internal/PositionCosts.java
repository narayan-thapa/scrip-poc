package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;

/**
 * The fully-costed result of one round-trip position: slippage-adjusted fill prices,
 * the buy/sell cost breakdown, and gross vs net P&L after NEPSE frictions.
 */
record PositionCosts(
        BigDecimal entryFillPrice,
        BigDecimal exitFillPrice,
        BigDecimal buyCosts,
        BigDecimal sellCosts,
        BigDecimal totalCosts,
        BigDecimal grossPnl,
        BigDecimal netPnl) {

    BigDecimal entryValue(long quantity) {
        return entryFillPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
