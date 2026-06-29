package np.com.thapanarayan.backend.signal.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;

import np.com.thapanarayan.backend.indicator.api.BarSeriesProvider;
import np.com.thapanarayan.backend.marketdata.api.MarketDataQuery;
import np.com.thapanarayan.backend.platform.api.DomainEventPublisher;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalView;
import np.com.thapanarayan.backend.signal.api.SignalsGeneratedEvent;
import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * Generates and persists daily signals (§10.6) — the pipeline's terminal value step.
 * For each symbol it builds the warm-up {@link BarSeries}, runs every
 * {@link SignalStrategy}, blends their votes through the {@link ConfluenceScorer},
 * and stores the action with its full auditable breakdown. Regeneration merges on
 * the natural {@code (symbol, date)} key, so a date can be reprocessed idempotently.
 */
@Service
class SignalGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationService.class);

    private final List<SignalStrategy> strategies;
    private final ConfluenceScorer scorer;
    private final StrategyConfigService strategyConfig;
    private final BarSeriesProvider barSeries;
    private final MarketDataQuery marketData;
    private final SignalRepository signals;
    private final DomainEventPublisher events;
    private final NepseClock clock;
    private final SignalProperties properties;
    private final io.micrometer.core.instrument.Counter signalsGenerated;
    private final io.micrometer.core.instrument.Timer generationTimer;

    SignalGenerationService(List<SignalStrategy> strategies, ConfluenceScorer scorer,
            StrategyConfigService strategyConfig, BarSeriesProvider barSeries, MarketDataQuery marketData,
            SignalRepository signals, DomainEventPublisher events, NepseClock clock, SignalProperties properties,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        // Catalog order (S1, S2, …) so the stored breakdown is always stable.
        this.strategies = strategies.stream()
                .sorted(java.util.Comparator.comparingInt(s -> s.id().ordinal()))
                .toList();
        this.scorer = scorer;
        this.strategyConfig = strategyConfig;
        this.barSeries = barSeries;
        this.marketData = marketData;
        this.signals = signals;
        this.events = events;
        this.clock = clock;
        this.properties = properties;
        this.signalsGenerated = meterRegistry.counter("nepse.signals.generated");
        this.generationTimer = meterRegistry.timer("nepse.signals.generation");
    }

    /**
     * Generates signals for every symbol that has market data on {@code date} and
     * publishes {@link SignalsGeneratedEvent}.
     *
     * @return number of signals persisted
     */
    @Transactional
    public int generateForDate(LocalDate date, boolean suppressNotifications) {
        io.micrometer.core.instrument.Timer.Sample sample = io.micrometer.core.instrument.Timer.start();
        Map<StrategyId, Double> weights = strategyConfig.enabledWeights();
        List<String> buySymbols = new java.util.ArrayList<>();
        int count = 0;
        for (String symbol : marketData.symbolsWithData(date)) {
            Optional<SignalEntity> saved = generate(symbol, date, weights);
            if (saved.isPresent()) {
                count++;
                if (saved.get().getAction() == SignalAction.BUY) {
                    buySymbols.add(symbol);
                }
            }
        }
        sample.stop(generationTimer);
        signalsGenerated.increment(count);
        log.info("Generated {} signals on {} ({} BUY)", count, date, buySymbols.size());
        events.publish(new SignalsGeneratedEvent(date, count, List.copyOf(buySymbols), suppressNotifications));
        return count;
    }

    /** Generates and persists a single (symbol, date) signal, if candle history exists. */
    @Transactional
    public Optional<SignalView> generateForSymbol(String symbol, LocalDate date) {
        return generate(symbol, date, strategyConfig.enabledWeights())
                .map(e -> SignalMapper.toView(e, properties.maxTopReasons()));
    }

    private Optional<SignalEntity> generate(String symbol, LocalDate date, Map<StrategyId, Double> weights) {
        BarSeries series = barSeries.dailySeries(symbol, date, properties.lookbackBars());
        if (series.getBarCount() == 0) {
            return Optional.empty();
        }
        SymbolContext ctx = new SymbolContext(symbol, date, series,
                marketData.volumeProfile(symbol, date).orElse(null),
                marketData.brokerFlow(symbol, date).orElse(null));

        Map<StrategyId, StrategyVote> votes = new LinkedHashMap<>();
        for (SignalStrategy strategy : strategies) {
            votes.put(strategy.id(), strategy.evaluate(ctx));
        }
        ConfluenceResult result = scorer.score(votes, weights);

        SignalEntity e = signals.findBySymbolAndTradeDate(symbol, date).orElseGet(SignalEntity::new);
        if (e.getId() == null) {
            e.setId(UUID.randomUUID());
        }
        e.setSymbol(symbol);
        e.setTradeDate(date);
        e.setAction(result.action());
        e.setScore(result.score());
        e.setBarCount(series.getBarCount());
        e.setVotes(result.votes().stream().map(SignalGenerationService::toStored).toList());
        e.setNarrative(NarrativeBuilder.build(symbol, result.action(), result.score(), result.votes()));
        e.setComputedAt(Instant.now(clock.clock()));
        return Optional.of(signals.save(e));
    }

    private static StoredVote toStored(ScoredVote sv) {
        List<StoredReason> reasons = sv.vote().reasons().stream()
                .map(SignalGenerationService::toStored)
                .toList();
        return new StoredVote(sv.id(), sv.label(), sv.vote().vote(), sv.vote().confidence(),
                sv.weight(), sv.contribution(), sv.applicable(), reasons);
    }

    private static StoredReason toStored(Reason r) {
        return new StoredReason(r.strategyId(), r.indicator(), r.condition(),
                String.valueOf(r.observedValue()), String.valueOf(r.threshold()),
                r.contribution(), r.narrative());
    }
}
