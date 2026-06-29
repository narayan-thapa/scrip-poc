package np.com.thapanarayan.backend.signal.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;

/** Direction and applicability of representative Ta4j (S1) and custom (S8) strategies. */
class StrategyVoteTest {

    private static SymbolContext ctx(BarSeries series, BrokerFlowView brokerFlow) {
        return new SymbolContext("TEST", LocalDate.of(2026, 2, 1), series, null, brokerFlow);
    }

    @Test
    void trendFollowingIsBullishInAnUptrend() {
        StrategyVote v = new TrendFollowingStrategy().evaluate(ctx(TestSeries.uptrend(60, 100, 2), null));

        assertThat(v.applicable()).isTrue();
        assertThat(v.vote()).isEqualTo(1.0);
        assertThat(v.signedContribution()).isPositive();
        assertThat(v.reasons()).isNotEmpty();
    }

    @Test
    void trendFollowingIsBearishInADowntrend() {
        StrategyVote v = new TrendFollowingStrategy().evaluate(ctx(TestSeries.downtrend(60, 200, 2), null));

        assertThat(v.applicable()).isTrue();
        assertThat(v.vote()).isEqualTo(-1.0);
        assertThat(v.signedContribution()).isNegative();
    }

    @Test
    void trendFollowingAbstainsWhenSeriesTooShort() {
        StrategyVote v = new TrendFollowingStrategy().evaluate(ctx(TestSeries.uptrend(5, 100, 2), null));

        assertThat(v.applicable()).isFalse();
        assertThat(v.isNeutral()).isTrue();
    }

    @Test
    void brokerFlowReadsBuyerConcentrationOnARisingDayAsBullish() {
        BrokerFlowView accumulation = new BrokerFlowView(
                "TEST", LocalDate.of(2026, 2, 1), List.of(),
                5, new BigDecimal("0.70"),   // top buyer holds 70% of buy volume
                8, new BigDecimal("0.20"),   // top seller only 20%
                new BigDecimal("0.50"), new BigDecimal("0.10"));

        StrategyVote v = new BrokerFlowStrategy().evaluate(ctx(TestSeries.of(100, 105), accumulation));

        assertThat(v.applicable()).isTrue();
        assertThat(v.vote()).isEqualTo(1.0);
        assertThat(v.reasons()).singleElement()
                .satisfies(r -> assertThat(r.narrative()).contains("accumulation"));
    }

    @Test
    void brokerFlowAbstainsWithoutData() {
        StrategyVote v = new BrokerFlowStrategy().evaluate(ctx(TestSeries.of(100, 105), null));

        assertThat(v.applicable()).isFalse();
    }
}
