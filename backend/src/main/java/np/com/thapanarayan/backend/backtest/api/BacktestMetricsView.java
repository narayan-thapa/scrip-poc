package np.com.thapanarayan.backend.backtest.api;

import java.math.BigDecimal;

/**
 * Summary performance metrics for a completed run (§7.3). Percentages and ratios are
 * doubles; capital amounts are {@link BigDecimal}. All figures are cost-adjusted —
 * computed over the NEPSE-cost-applied trade ledger, not gross.
 *
 * @param totalReturnPct        total return over the period, percent
 * @param cagrPct               compound annual growth rate, percent
 * @param annualizedVolatilityPct annualized stdev of daily returns, percent
 * @param sharpe                annualized Sharpe ratio (risk-free 0)
 * @param sortino               annualized Sortino ratio (downside deviation)
 * @param maxDrawdownPct        worst peak-to-trough equity drawdown, percent
 * @param maxDrawdownDays       calendar-day duration of the max drawdown
 * @param winRatePct            share of closed trades that were profitable, percent
 * @param profitFactor          gross profit / gross loss
 * @param avgWinPct             average winning-trade return, percent
 * @param avgLossPct            average losing-trade return, percent
 * @param expectancy            expected return per trade, percent
 * @param exposurePct           fraction of bars holding a position, percent
 * @param tradeCount            number of closed trades
 * @param totalCosts            sum of all transaction costs (NPR)
 * @param startingCapital       capital the run began with (NPR)
 * @param finalEquity           ending equity (NPR)
 */
public record BacktestMetricsView(
        double totalReturnPct,
        double cagrPct,
        double annualizedVolatilityPct,
        double sharpe,
        double sortino,
        double maxDrawdownPct,
        int maxDrawdownDays,
        double winRatePct,
        double profitFactor,
        double avgWinPct,
        double avgLossPct,
        double expectancy,
        double exposurePct,
        int tradeCount,
        BigDecimal totalCosts,
        BigDecimal startingCapital,
        BigDecimal finalEquity) {
}
