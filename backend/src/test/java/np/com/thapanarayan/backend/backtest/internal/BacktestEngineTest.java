package np.com.thapanarayan.backend.backtest.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.backtest.api.CostModelSpec;
import np.com.thapanarayan.backend.signal.api.ConfluenceScoreProvider;

/**
 * Deterministic regression for the engine (Stage 6 "Done when"). A scripted score —
 * bullish for the first half, bearish for the second — drives exactly one round trip
 * with next-open fills, so trade prices, P&L, and the equity curve are fully
 * predictable. No Spring, no DB.
 */
class BacktestEngineTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    /** close[i] = 100 + i; open == close so next-open fills equal the bar close. */
    private static final double[] CLOSES = {100, 101, 102, 103, 104, 105, 106, 107, 108, 109};
    /** +60 (bullish, entry) for bars 0..5, then -60 (bearish, exit) for bars 6..9. */
    private static final double[] SCORES = {60, 60, 60, 60, 60, 60, -60, -60, -60, -60};

    private static BarSeries series() {
        BarSeries s = new BaseBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance()).withName("ABC").build();
        for (int i = 0; i < CLOSES.length; i++) {
            BigDecimal c = BigDecimal.valueOf(CLOSES[i]);
            s.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(START.plusDays(i).atStartOfDay().toInstant(ZoneOffset.UTC))
                    .openPrice(c).highPrice(c.add(BigDecimal.ONE)).lowPrice(c.subtract(BigDecimal.ONE))
                    .closePrice(c).volume(BigDecimal.valueOf(1000)).amount(c.multiply(BigDecimal.valueOf(1000)))
                    .bindTo(s).add();
        }
        return s;
    }

    private static CostModelSpec zeroCost() {
        return new CostModelSpec(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /** Provider that scripts the confluence score over whatever series it is given. */
    private static ConfluenceScoreProvider scriptedProvider() {
        return new ConfluenceScoreProvider() {
            @Override
            public Indicator<Num> scoreIndicator(BarSeries s) {
                return new CachedIndicator<Num>(s) {
                    @Override
                    protected Num calculate(int index) {
                        return getBarSeries().numFactory().numOf(SCORES[index]);
                    }

                    @Override
                    public int getCountOfUnstableBars() {
                        return 0;
                    }
                };
            }

            @Override
            public double buyThreshold() {
                return 35;
            }

            @Override
            public double sellThreshold() {
                return 35;
            }
        };
    }

    @Test
    void scriptedConfluenceProducesOneProfitableNextOpenRoundTrip() {
        BacktestEngine engine = new BacktestEngine(scriptedProvider());

        SymbolRun run = engine.runSymbol("ABC", series(), new NepseCostModel(zeroCost()),
                new BigDecimal("100000"), START, START.plusDays(9));

        // Entry signal at bar 0 → filled at bar 1 open (101); exit signal at bar 6 → filled at bar 7 open (107).
        assertThat(run.trades()).hasSize(1);
        TradeRecord t = run.trades().getFirst();
        assertThat(t.entryDate()).isEqualTo(START.plusDays(1));
        assertThat(t.entryPrice()).isEqualByComparingTo("101.00");
        assertThat(t.exitDate()).isEqualTo(START.plusDays(7));
        assertThat(t.exitPrice()).isEqualByComparingTo("107.00");
        assertThat(t.quantity()).isEqualTo(990L);            // floor(100000 / 101)
        assertThat(t.pnl()).isEqualByComparingTo("5940.00"); // (107-101) * 990, zero costs
        assertThat(t.entryReason()).contains("entry");
        assertThat(t.exitReason()).contains("exit");

        // Equity ends at 105940 (cash after the round trip) and the position spans bars 1..6.
        assertThat(run.equityByDate().lastEntry().getValue()).isEqualByComparingTo("105940.00");
        assertThat(run.barsInPosition()).isEqualTo(6);
        assertThat(run.totalBars()).isEqualTo(10);
    }

    @Test
    void abstainsWhenCapitalCannotBuyASingleShare() {
        BacktestEngine engine = new BacktestEngine(scriptedProvider());

        SymbolRun run = engine.runSymbol("ABC", series(), new NepseCostModel(zeroCost()),
                new BigDecimal("50"), START, START.plusDays(9)); // < one share at ~101

        assertThat(run.trades()).isEmpty();
    }
}
