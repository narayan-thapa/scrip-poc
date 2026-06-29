package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

import np.com.thapanarayan.backend.backtest.api.BacktestMetricsView;

/**
 * Computes the cost-adjusted performance metrics (§7.3) over the run's equity curve
 * and closed-trade ledger. Pure and Spring-free.
 *
 * <p>Design note: metrics are computed here rather than via Ta4j {@code
 * AnalysisCriterion}s because the figures must reflect the <em>NEPSE</em> cost model
 * (tiered brokerage, SEBON, DP, CGT, slippage), which Ta4j's built-in cost models
 * cannot express. Ta4j still drives <em>trade generation</em> (BarSeriesManager +
 * next-open fills); this layer scores the resulting cost-applied ledger.</p>
 */
final class MetricsCalculator {

    private static final double TRADING_DAYS = 252.0;
    private static final double DAYS_PER_YEAR = 365.25;

    private MetricsCalculator() {
    }

    static BacktestMetricsView compute(BigDecimal startingCapital, List<EquityPoint> curve,
            List<TradeStat> trades, int barsInPosition, int totalBars) {
        BigDecimal finalEquity = curve.isEmpty() ? startingCapital : curve.getLast().equity();
        double start = startingCapital.doubleValue();
        double end = finalEquity.doubleValue();
        double totalReturnPct = start > 0 ? (end / start - 1.0) * 100.0 : 0.0;

        double[] daily = dailyReturns(curve);
        double meanDaily = mean(daily);
        double stdDaily = std(daily, meanDaily);
        double downside = downsideDeviation(daily);

        double annualVolPct = stdDaily * Math.sqrt(TRADING_DAYS) * 100.0;
        double sharpe = stdDaily > 0 ? meanDaily / stdDaily * Math.sqrt(TRADING_DAYS) : 0.0;
        double sortino = downside > 0 ? meanDaily / downside * Math.sqrt(TRADING_DAYS) : 0.0;
        double cagrPct = cagr(start, end, curve) * 100.0;

        Drawdown dd = maxDrawdown(curve);

        int tradeCount = trades.size();
        long winners = trades.stream().filter(TradeStat::isWin).count();
        double winRatePct = tradeCount > 0 ? 100.0 * winners / tradeCount : 0.0;
        double profitFactor = profitFactor(trades);
        double avgWinPct = trades.stream().filter(TradeStat::isWin)
                .mapToDouble(TradeStat::returnPct).average().orElse(0.0);
        double avgLossPct = trades.stream().filter(t -> !t.isWin())
                .mapToDouble(TradeStat::returnPct).average().orElse(0.0);
        double expectancy = trades.stream().mapToDouble(TradeStat::returnPct).average().orElse(0.0);
        double exposurePct = totalBars > 0 ? 100.0 * barsInPosition / totalBars : 0.0;
        BigDecimal totalCosts = trades.stream().map(TradeStat::costs)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

        return new BacktestMetricsView(
                round(totalReturnPct), round(cagrPct), round(annualVolPct), round(sharpe), round(sortino),
                round(dd.pct()), dd.days(), round(winRatePct), round(profitFactor),
                round(avgWinPct), round(avgLossPct), round(expectancy), round(exposurePct),
                tradeCount, totalCosts, startingCapital.setScale(2, RoundingMode.HALF_UP),
                finalEquity.setScale(2, RoundingMode.HALF_UP));
    }

    private static double[] dailyReturns(List<EquityPoint> curve) {
        if (curve.size() < 2) {
            return new double[0];
        }
        double[] r = new double[curve.size() - 1];
        for (int i = 1; i < curve.size(); i++) {
            double prev = curve.get(i - 1).equity().doubleValue();
            double cur = curve.get(i).equity().doubleValue();
            r[i - 1] = prev > 0 ? cur / prev - 1.0 : 0.0;
        }
        return r;
    }

    private static double cagr(double start, double end, List<EquityPoint> curve) {
        if (start <= 0 || end <= 0 || curve.size() < 2) {
            return 0.0;
        }
        long days = ChronoUnit.DAYS.between(curve.getFirst().date(), curve.getLast().date());
        double years = days / DAYS_PER_YEAR;
        if (years <= 0) {
            return 0.0;
        }
        return Math.pow(end / start, 1.0 / years) - 1.0;
    }

    private static Drawdown maxDrawdown(List<EquityPoint> curve) {
        double peak = Double.NEGATIVE_INFINITY;
        java.time.LocalDate peakDate = null;
        double worst = 0.0;
        int worstDays = 0;
        for (EquityPoint p : curve) {
            double eq = p.equity().doubleValue();
            if (eq > peak) {
                peak = eq;
                peakDate = p.date();
            }
            double dd = peak > 0 ? (eq - peak) / peak : 0.0;
            if (dd < worst) {
                worst = dd;
                worstDays = peakDate == null ? 0 : (int) ChronoUnit.DAYS.between(peakDate, p.date());
            }
        }
        return new Drawdown(-worst * 100.0, worstDays);
    }

    private static double profitFactor(List<TradeStat> trades) {
        double grossProfit = trades.stream().map(TradeStat::netPnl)
                .filter(p -> p.signum() > 0).mapToDouble(BigDecimal::doubleValue).sum();
        double grossLoss = trades.stream().map(TradeStat::netPnl)
                .filter(p -> p.signum() < 0).mapToDouble(p -> -p.doubleValue()).sum();
        if (grossLoss == 0.0) {
            return grossProfit > 0 ? Double.POSITIVE_INFINITY : 0.0;
        }
        return grossProfit / grossLoss;
    }

    private static double mean(double[] xs) {
        if (xs.length == 0) {
            return 0.0;
        }
        double s = 0;
        for (double x : xs) {
            s += x;
        }
        return s / xs.length;
    }

    private static double std(double[] xs, double mean) {
        if (xs.length < 2) {
            return 0.0;
        }
        double sse = 0;
        for (double x : xs) {
            sse += (x - mean) * (x - mean);
        }
        return Math.sqrt(sse / (xs.length - 1));
    }

    private static double downsideDeviation(double[] xs) {
        if (xs.length == 0) {
            return 0.0;
        }
        double sse = 0;
        for (double x : xs) {
            if (x < 0) {
                sse += x * x;
            }
        }
        return Math.sqrt(sse / xs.length);
    }

    /** Rounds to 2dp and keeps the value JSON-safe (no NaN/Infinity in the persisted JSONB). */
    private static double round(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        if (Double.isInfinite(v)) {
            return v > 0 ? 9999.99 : -9999.99; // e.g. profit factor with zero losing trades
        }
        return Math.round(v * 100.0) / 100.0;
    }

    private record Drawdown(double pct, int days) {
    }
}
