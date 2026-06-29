package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.signal.api.ConfluenceScoreProvider;

/**
 * Publishes the confluence score indicator (the §6.4 backtest hook) by building a
 * {@link ConfluenceScoreIndicator} over the enabled Ta4j strategies at their
 * configured weights. The custom S4/S8 strategies are intentionally excluded — they
 * read floorsheet structures that are not reconstructed per historical bar.
 */
@Service
class ConfluenceScoreProviderImpl implements ConfluenceScoreProvider {

    private final List<Ta4jStrategy> ta4jStrategies;
    private final StrategyConfigService strategyConfig;
    private final SignalProperties properties;

    ConfluenceScoreProviderImpl(List<SignalStrategy> strategies, StrategyConfigService strategyConfig,
            SignalProperties properties) {
        this.ta4jStrategies = strategies.stream()
                .filter(Ta4jStrategy.class::isInstance)
                .map(Ta4jStrategy.class::cast)
                .sorted(java.util.Comparator.comparingInt(s -> s.id().ordinal()))
                .toList();
        this.strategyConfig = strategyConfig;
        this.properties = properties;
    }

    @Override
    public Indicator<Num> scoreIndicator(BarSeries series) {
        return ConfluenceScoreIndicator.forSeries(series, ta4jStrategies, strategyConfig.enabledWeights());
    }

    @Override
    public double buyThreshold() {
        return properties.buyThreshold();
    }

    @Override
    public double sellThreshold() {
        return properties.sellThreshold();
    }
}
