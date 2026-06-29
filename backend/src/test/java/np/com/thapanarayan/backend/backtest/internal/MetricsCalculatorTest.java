package np.com.thapanarayan.backend.backtest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.backtest.api.BacktestMetricsView;

/** Cost-adjusted metrics (§7.3) over a hand-built equity curve and trade ledger. */
class MetricsCalculatorTest {

    private static final LocalDate D0 = LocalDate.of(2026, 1, 1);

    private static EquityPoint pt(int dayOffset, String equity) {
        return new EquityPoint(D0.plusDays(dayOffset), new BigDecimal(equity));
    }

    @Test
    void computesReturnDrawdownWinRateProfitFactorAndExposure() {
        List<EquityPoint> curve = List.of(
                pt(0, "100000"), pt(1, "110000"), pt(2, "99000"), pt(3, "121000"));
        List<TradeStat> trades = List.of(
                new TradeStat(10.0, new BigDecimal("1000"), new BigDecimal("10")),
                new TradeStat(-5.0, new BigDecimal("-500"), new BigDecimal("10")));

        BacktestMetricsView m = MetricsCalculator.compute(
                new BigDecimal("100000"), curve, trades, 2, 4);

        assertThat(m.totalReturnPct()).isEqualTo(21.0);
        assertThat(m.finalEquity()).isEqualByComparingTo("121000.00");
        assertThat(m.maxDrawdownPct()).isCloseTo(10.0, within(0.01)); // 110k → 99k
        assertThat(m.tradeCount()).isEqualTo(2);
        assertThat(m.winRatePct()).isEqualTo(50.0);
        assertThat(m.profitFactor()).isEqualTo(2.0);           // 1000 / 500
        assertThat(m.avgWinPct()).isEqualTo(10.0);
        assertThat(m.avgLossPct()).isEqualTo(-5.0);
        assertThat(m.expectancy()).isEqualTo(2.5);             // mean(10, -5)
        assertThat(m.exposurePct()).isEqualTo(50.0);           // 2 of 4 bars
        assertThat(m.totalCosts()).isEqualByComparingTo("20.00");
    }

    @Test
    void emptyRunReturnsStartingCapitalAndZeros() {
        BacktestMetricsView m = MetricsCalculator.compute(
                new BigDecimal("100000"), List.of(), List.of(), 0, 0);

        assertThat(m.totalReturnPct()).isEqualTo(0.0);
        assertThat(m.finalEquity()).isEqualByComparingTo("100000.00");
        assertThat(m.tradeCount()).isZero();
        assertThat(m.profitFactor()).isEqualTo(0.0);
        assertThat(m.exposurePct()).isEqualTo(0.0);
    }

    @Test
    void infiniteProfitFactorIsClampedForJsonSafety() {
        List<TradeStat> allWinners = List.of(
                new TradeStat(5.0, new BigDecimal("500"), BigDecimal.ZERO),
                new TradeStat(3.0, new BigDecimal("300"), BigDecimal.ZERO));

        BacktestMetricsView m = MetricsCalculator.compute(
                new BigDecimal("100000"), List.of(pt(0, "100000"), pt(1, "100800")), allWinners, 1, 2);

        assertThat(m.profitFactor()).isEqualTo(9999.99); // no losing trades → clamped, not Infinity
        assertThat(Double.isFinite(m.profitFactor())).isTrue();
    }
}
