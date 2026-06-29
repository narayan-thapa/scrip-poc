package np.com.thapanarayan.backend.backtest.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One round-trip trade in the run's ledger (§7.3): signal-at-close, fill-next-open.
 * Carries the structured entry/exit reasons so the UI can explain each trade, not
 * just the aggregate.
 *
 * @param entryDate   the next-open fill date after the entry signal
 * @param exitDate    the next-open fill date after the exit signal, or {@code null} if still open
 * @param quantity    shares held (sized from the per-symbol capital allocation)
 * @param costs       total NEPSE costs across entry + exit (NPR)
 * @param pnl         net profit/loss after costs (NPR)
 * @param returnPct   net return on the position, percent
 * @param entryReason why the position was opened (confluence score crossed +T_buy)
 * @param exitReason  why it was closed (score crossed -T_sell, or end-of-period)
 */
public record BacktestTradeView(
        String symbol,
        LocalDate entryDate,
        BigDecimal entryPrice,
        LocalDate exitDate,
        BigDecimal exitPrice,
        long quantity,
        BigDecimal costs,
        BigDecimal pnl,
        double returnPct,
        String entryReason,
        String exitReason) {
}
