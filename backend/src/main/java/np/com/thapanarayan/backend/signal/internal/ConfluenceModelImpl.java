package np.com.thapanarayan.backend.signal.internal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import np.com.thapanarayan.backend.signal.api.ConfluenceModel;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfig;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfigRepository;
import org.springframework.stereotype.Service;

/**
 * Snapshots enabled strategies + weights into a reusable scoring function that evaluates the panel at
 * a given bar and blends via {@link ConfluenceScorer}. Thresholds don't matter here — the backtester
 * applies its own ± entry/exit thresholds over the returned score.
 */
@Service
class ConfluenceModelImpl implements ConfluenceModel {

    private final List<SignalStrategy> strategies;
    private final StrategyConfigRepository configs;
    private final ConfluenceScorer scorer;

    ConfluenceModelImpl(List<SignalStrategy> strategies, StrategyConfigRepository configs, ConfluenceScorer scorer) {
        this.strategies = strategies;
        this.configs = configs;
        this.scorer = scorer;
    }

    @Override
    public ConfluenceFunction scorer() {
        Map<String, StrategyConfig> byId = configs.findAll().stream()
                .collect(Collectors.toMap(StrategyConfig::getId, Function.identity()));
        List<SignalStrategy> active = strategies.stream()
                .filter(s -> byId.get(s.id()) == null || byId.get(s.id()).isEnabled())
                .toList();
        return ctx -> {
            List<ConfluenceScorer.Evaluated> evaluated = active.stream()
                    .map(s -> new ConfluenceScorer.Evaluated(s.id(), s.name(), weight(byId, s), s.evaluate(ctx)))
                    .toList();
            // Thresholds are irrelevant to the numeric score; use 0 so action just reflects sign.
            return scorer.score(evaluated, 0, 0).score();
        };
    }

    private static double weight(Map<String, StrategyConfig> byId, SignalStrategy s) {
        StrategyConfig c = byId.get(s.id());
        return c != null ? c.getWeight().doubleValue() : s.defaultWeight();
    }
}
