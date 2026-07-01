package np.com.thapanarayan.backend.signal.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import np.com.thapanarayan.backend.indicator.api.BarSeriesFactory;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import np.com.thapanarayan.backend.signal.internal.ConfluenceScorer.Evaluated;
import np.com.thapanarayan.backend.signal.internal.strategies.TrendFollowingStrategy;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

/** Unit tests for the confluence scorer and a representative Ta4j strategy (no Spring, no Docker). */
class SignalEngineLogicTest {

    private final ConfluenceScorer scorer = new ConfluenceScorer();

    private static Reason reason(String id, double contribution) {
        return new Reason(id, "ind", "cond", "obs", "thr", contribution, "why");
    }

    @Test
    void confluenceBlendsWeightedVotesToBuy() {
        var result = scorer.score(List.of(
                new Evaluated("S1", "Trend", 1.0, StrategyVote.bullish(0.9, reason("S1", 0.9))),
                new Evaluated("S5", "MACD", 1.0, StrategyVote.bullish(0.8, reason("S5", 0.8))),
                new Evaluated("S2", "MeanRev", 1.0, StrategyVote.neutral())), 35, 35);

        // (0.9 + 0.8 + 0) / 3 * 100 ≈ 56.7 → BUY
        assertThat(result.score()).isGreaterThan(35);
        assertThat(result.action()).isEqualTo(SignalAction.BUY);
        assertThat(result.votes()).hasSize(3);
        assertThat(result.topReasons()).isNotEmpty();
    }

    @Test
    void confluenceHoldsWhenMixed() {
        var result = scorer.score(List.of(
                new Evaluated("S1", "Trend", 1.0, StrategyVote.bullish(0.9, reason("S1", 0.9))),
                new Evaluated("S6", "Supertrend", 1.0, StrategyVote.bearish(0.9, reason("S6", -0.9)))), 35, 35);

        assertThat(result.action()).isEqualTo(SignalAction.HOLD);
        assertThat(Math.abs(result.score())).isLessThan(35);
    }

    @Test
    void confluenceRecordsDissents() {
        var result = scorer.score(List.of(
                new Evaluated("S1", "Trend", 1.0, StrategyVote.bullish(1.0, reason("S1", 1.0))),
                new Evaluated("S5", "MACD", 1.0, StrategyVote.bullish(1.0, reason("S5", 1.0))),
                new Evaluated("S2", "MeanRev", 1.0, StrategyVote.bearish(0.5, reason("S2", -0.5)))), 35, 35);

        assertThat(result.action()).isEqualTo(SignalAction.BUY);
        assertThat(result.dissents()).contains("S2");
    }

    @Test
    void trendStrategyVotesBullishInUptrend() {
        List<CandleBar> bars = new java.util.ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        double price = 100;
        for (int i = 0; i < 60; i++) {
            bars.add(new CandleBar(d, bd(price), bd(price + 1), bd(price - 1), bd(price + 0.8), 1000));
            price += 1.5;
            d = d.plusDays(1);
        }
        BarSeries series = BarSeriesFactory.fromCandles("UP", bars);
        var ctx = SymbolContext.atEnd("UP", d, series, Optional.empty(), Optional.empty());

        StrategyVote vote = new TrendFollowingStrategy().evaluate(ctx);
        assertThat(vote.vote()).isGreaterThan(0);
        assertThat(vote.reasons()).isNotEmpty();
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
