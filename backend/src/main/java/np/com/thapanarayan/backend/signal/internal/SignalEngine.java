package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import np.com.thapanarayan.backend.indicator.api.BarSeriesFactory;
import np.com.thapanarayan.backend.indicator.api.IndicatorsComputedEvent;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.marketdata.api.CandleSeriesReader;
import np.com.thapanarayan.backend.marketdata.api.MarketAnalytics;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.SignalsGeneratedEvent;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfig;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ta4j.core.BarSeries;

/**
 * Runs the strategy panel + confluence scorer per symbol and persists a BUY/SELL/HOLD signal with
 * structured reasons. Triggered after indicators are computed; idempotent (re-running a date replaces
 * its signals). Emits {@link SignalsGeneratedEvent} — the fan-out point for backtest + notifications.
 */
@Service
public class SignalEngine {

    private static final Logger log = LoggerFactory.getLogger(SignalEngine.class);

    private final List<SignalStrategy> strategies;
    private final ConfluenceScorer scorer;
    private final CandleSeriesReader candles;
    private final MarketAnalytics analytics;
    private final StrategyConfigRepository configs;
    private final SignalDao signals;
    private final SignalProperties props;
    private final ApplicationEventPublisher events;

    SignalEngine(List<SignalStrategy> strategies, ConfluenceScorer scorer, CandleSeriesReader candles,
                 MarketAnalytics analytics, StrategyConfigRepository configs, SignalDao signals,
                 SignalProperties props, ApplicationEventPublisher events) {
        this.strategies = strategies;
        this.scorer = scorer;
        this.candles = candles;
        this.analytics = analytics;
        this.configs = configs;
        this.signals = signals;
        this.props = props;
        this.events = events;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onIndicatorsComputed(IndicatorsComputedEvent e) {
        generate(e.tradeDate(), e.suppressNotifications());
    }

    @Transactional
    public void generate(LocalDate date, boolean suppressNotifications) {
        Map<String, StrategyConfig> configById = configs.findAll().stream()
                .collect(Collectors.toMap(StrategyConfig::getId, Function.identity()));
        List<SignalStrategy> active = strategies.stream()
                .filter(s -> enabled(configById, s))
                .toList();

        int buy = 0;
        int sell = 0;
        for (String symbol : candles.symbolsOn(date)) {
            SignalRecord signal = evaluateSymbol(symbol, date, active, configById);
            if (signal == null) {
                continue;
            }
            signals.upsert(signal);
            if (signal.action() == SignalAction.BUY) {
                buy++;
            } else if (signal.action() == SignalAction.SELL) {
                sell++;
            }
        }
        log.info("Signals generated for {}: {} BUY, {} SELL", date, buy, sell);
        events.publishEvent(new SignalsGeneratedEvent(date, buy, sell, suppressNotifications));
    }

    private SignalRecord evaluateSymbol(String symbol, LocalDate date, List<SignalStrategy> active,
                                        Map<String, StrategyConfig> configById) {
        List<CandleBar> bars = candles.series(symbol, date.minusDays(props.lookbackCalendarDays()), date);
        if (bars.isEmpty()) {
            return null;
        }
        BarSeries series = BarSeriesFactory.fromCandles(symbol, bars);
        SymbolContext ctx = SymbolContext.atEnd(symbol, date, series,
                analytics.volumeProfile(symbol, date), analytics.brokerFlow(symbol, date));

        List<ConfluenceScorer.Evaluated> evaluated = active.stream()
                .map(s -> {
                    StrategyVote vote = s.evaluate(ctx);
                    double weight = weightOf(configById, s);
                    return new ConfluenceScorer.Evaluated(s.id(), s.name(), weight, vote);
                })
                .toList();

        ConfluenceResult result = scorer.score(evaluated, props.buyThreshold(), props.sellThreshold());
        return new SignalRecord(UUID.randomUUID(), symbol, date, result.action(), result.score(),
                result.confidence(), result.topReasons(), result.votes(), OffsetDateTime.now());
    }

    private boolean enabled(Map<String, StrategyConfig> configById, SignalStrategy s) {
        StrategyConfig c = configById.get(s.id());
        return c == null || c.isEnabled();
    }

    private double weightOf(Map<String, StrategyConfig> configById, SignalStrategy s) {
        StrategyConfig c = configById.get(s.id());
        return c != null ? c.getWeight().doubleValue() : s.defaultWeight();
    }
}
