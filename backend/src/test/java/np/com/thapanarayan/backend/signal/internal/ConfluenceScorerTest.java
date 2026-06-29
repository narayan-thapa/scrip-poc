package np.com.thapanarayan.backend.signal.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.StrategyId;

/** The confluence blend (§6.4): weighting, normalisation, thresholds, and applicability. */
class ConfluenceScorerTest {

    private final ConfluenceScorer scorer =
            new ConfluenceScorer(new SignalProperties(260, 35.0, 35.0, 3));

    private static Map<StrategyId, Double> equalWeights(StrategyId... ids) {
        Map<StrategyId, Double> w = new EnumMap<>(StrategyId.class);
        for (StrategyId id : ids) {
            w.put(id, 1.0);
        }
        return w;
    }

    private static StrategyVote vote(double v, double c) {
        return new StrategyVote(v, c, true, List.of());
    }

    @Test
    void unanimousStrongBullishVotesProduceBuy() {
        Map<StrategyId, StrategyVote> votes = new LinkedHashMap<>();
        votes.put(StrategyId.S1, vote(1, 0.8));
        votes.put(StrategyId.S5, vote(1, 0.9));

        ConfluenceResult r = scorer.score(votes, equalWeights(StrategyId.S1, StrategyId.S5));

        // 100 * (0.8 + 0.9) / 2 = 85
        assertThat(r.score().doubleValue()).isEqualTo(85.0);
        assertThat(r.action()).isEqualTo(SignalAction.BUY);
    }

    @Test
    void unanimousStrongBearishVotesProduceSell() {
        Map<StrategyId, StrategyVote> votes = new LinkedHashMap<>();
        votes.put(StrategyId.S1, vote(-1, 0.7));
        votes.put(StrategyId.S6, vote(-1, 0.9));

        ConfluenceResult r = scorer.score(votes, equalWeights(StrategyId.S1, StrategyId.S6));

        assertThat(r.score().doubleValue()).isEqualTo(-80.0);
        assertThat(r.action()).isEqualTo(SignalAction.SELL);
    }

    @Test
    void neutralButApplicableStrategiesDampenTheScore() {
        Map<StrategyId, StrategyVote> votes = new LinkedHashMap<>();
        votes.put(StrategyId.S1, vote(1, 0.8)); // strong bull
        votes.put(StrategyId.S2, StrategyVote.neutral()); // ran, no signal — counts in denominator
        votes.put(StrategyId.S3, StrategyVote.neutral());

        ConfluenceResult r = scorer.score(votes, equalWeights(StrategyId.S1, StrategyId.S2, StrategyId.S3));

        // 100 * 0.8 / 3 ≈ 26.67 → below +35 → HOLD (broad agreement is required)
        assertThat(r.score().doubleValue()).isEqualTo(26.67);
        assertThat(r.action()).isEqualTo(SignalAction.HOLD);
    }

    @Test
    void notApplicableStrategiesAreExcludedFromTheDenominator() {
        Map<StrategyId, StrategyVote> votes = new LinkedHashMap<>();
        votes.put(StrategyId.S1, vote(1, 0.8));
        votes.put(StrategyId.S4, StrategyVote.notApplicable()); // no volume profile — must not dilute

        ConfluenceResult r = scorer.score(votes, equalWeights(StrategyId.S1, StrategyId.S4));

        // Only S1 is applicable: 100 * 0.8 / 1 = 80
        assertThat(r.score().doubleValue()).isEqualTo(80.0);
        assertThat(r.action()).isEqualTo(SignalAction.BUY);
        assertThat(r.votes()).filteredOn(v -> v.id() == StrategyId.S4)
                .singleElement().satisfies(v -> {
                    assertThat(v.applicable()).isFalse();
                    assertThat(v.contribution()).isZero();
                });
    }

    @Test
    void weightsBiasTheBlend() {
        Map<StrategyId, StrategyVote> votes = new LinkedHashMap<>();
        votes.put(StrategyId.S1, vote(1, 1.0));   // bull
        votes.put(StrategyId.S2, vote(-1, 1.0));  // bear

        Map<StrategyId, Double> weights = new EnumMap<>(StrategyId.class);
        weights.put(StrategyId.S1, 3.0);
        weights.put(StrategyId.S2, 1.0);

        ConfluenceResult r = scorer.score(votes, weights);

        // 100 * (3*1 - 1*1) / (3 + 1) = 50
        assertThat(r.score().doubleValue()).isEqualTo(50.0);
        assertThat(r.action()).isEqualTo(SignalAction.BUY);
    }

    @Test
    void noEnabledWeightsYieldsHoldAtZero() {
        Map<StrategyId, StrategyVote> votes = new LinkedHashMap<>();
        votes.put(StrategyId.S1, vote(1, 1.0));

        ConfluenceResult r = scorer.score(votes, Map.of());

        assertThat(r.score().doubleValue()).isEqualTo(0.0);
        assertThat(r.action()).isEqualTo(SignalAction.HOLD);
    }
}
