package np.com.thapanarayan.backend.signal.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/** The §6.4 backtest hook: the confluence score as a replayable Ta4j indicator. */
class ConfluenceScoreIndicatorTest {

    private static final List<Ta4jStrategy> STRATEGIES = List.of(
            new TrendFollowingStrategy(), new MacdStrategy(), new SupertrendStrategy());

    private static Map<StrategyId, Double> weights() {
        Map<StrategyId, Double> w = new EnumMap<>(StrategyId.class);
        w.put(StrategyId.S1, 1.0);
        w.put(StrategyId.S5, 1.0);
        w.put(StrategyId.S6, 1.0);
        return w;
    }

    @Test
    void scoreIsBullishAndBoundedInAnUptrend() {
        BarSeries series = TestSeries.uptrend(80, 100, 2);
        ConfluenceScoreIndicator score = ConfluenceScoreIndicator.forSeries(series, STRATEGIES, weights());

        double end = score.getValue(series.getEndIndex()).doubleValue();
        assertThat(end).isPositive().isLessThanOrEqualTo(100.0);
    }

    @Test
    void scoreIsBearishInADowntrend() {
        BarSeries series = TestSeries.downtrend(80, 200, 2);
        ConfluenceScoreIndicator score = ConfluenceScoreIndicator.forSeries(series, STRATEGIES, weights());

        double end = score.getValue(series.getEndIndex()).doubleValue();
        assertThat(end).isNegative().isGreaterThanOrEqualTo(-100.0);
    }

    @Test
    void everyBarStaysWithinTheScoreRange() {
        BarSeries series = TestSeries.uptrend(80, 100, 2);
        ConfluenceScoreIndicator score = ConfluenceScoreIndicator.forSeries(series, STRATEGIES, weights());

        for (int i = 0; i <= series.getEndIndex(); i++) {
            assertThat(score.getValue(i).doubleValue()).isBetween(-100.0, 100.0);
        }
    }

    @Test
    void noWeightsYieldsZero() {
        BarSeries series = TestSeries.uptrend(40, 100, 2);
        ConfluenceScoreIndicator score = ConfluenceScoreIndicator.forSeries(series, STRATEGIES, Map.of());

        assertThat(score.getValue(series.getEndIndex()).doubleValue()).isZero();
    }
}
