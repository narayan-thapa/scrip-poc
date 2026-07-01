package np.com.thapanarayan.backend.indicator.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.IndicatorsComputedEvent;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.marketdata.api.CandleSeriesReader;
import np.com.thapanarayan.backend.marketdata.api.MarketDataReadyEvent;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Resolves the indicator catalog and computes results, dispatching across built-in Ta4j indicators,
 * plugin {@link np.com.thapanarayan.backend.indicator.api.CustomIndicator} studies, and config-composed
 * studies. Also computes the canonical per-(symbol, date) snapshot after market data is ready.
 */
@Service
public class IndicatorService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);

    private final BuiltinIndicatorResolver builtins;
    private final CustomIndicatorRegistry customs;
    private final ConfigComposedStudies configStudies;
    private final CandleSeriesReader candles;
    private final IndicatorSnapshotDao snapshots;
    private final SeriesCache cache;
    private final IndicatorProperties props;
    private final ApplicationEventPublisher events;

    IndicatorService(BuiltinIndicatorResolver builtins, CustomIndicatorRegistry customs,
                     ConfigComposedStudies configStudies, CandleSeriesReader candles,
                     IndicatorSnapshotDao snapshots, SeriesCache cache, IndicatorProperties props,
                     ApplicationEventPublisher events) {
        this.builtins = builtins;
        this.customs = customs;
        this.configStudies = configStudies;
        this.candles = candles;
        this.snapshots = snapshots;
        this.cache = cache;
        this.props = props;
        this.events = events;
    }

    /** Full catalog: built-ins + plugins + config-composed studies. */
    public List<IndicatorDescriptor> catalog() {
        List<IndicatorDescriptor> all = new java.util.ArrayList<>(builtins.descriptors());
        all.addAll(customs.catalog());
        all.addAll(configStudies.descriptors());
        return all;
    }

    /** Compute one study's series for a symbol over a date range (cache-aware). */
    public IndicatorResult compute(String id, String symbol, LocalDate from, LocalDate to, ParamValues params) {
        List<CandleBar> bars = candles.series(symbol, from, to);
        if (bars.isEmpty()) {
            throw ApiException.notFound("No candles for " + symbol + " in " + from + ".." + to);
        }
        String lastDate = bars.get(bars.size() - 1).tradeDate().toString();
        String key = SeriesCache.key(symbol, id, Integer.toHexString(params.hashCode()), lastDate);
        return cache.getOrCompute(key, () -> dispatch(id, BarSeriesAdapter.toSeries(symbol, bars), params));
    }

    private IndicatorResult dispatch(String id, BarSeries series, ParamValues params) {
        if (builtins.supports(id)) {
            return builtins.compute(id, series, params);
        }
        Optional<np.com.thapanarayan.backend.indicator.api.CustomIndicator> custom = customs.find(id);
        if (custom.isPresent()) {
            return custom.get().compute(series, params);
        }
        if (configStudies.contains(id)) {
            return configStudies.compute(id, series);
        }
        throw ApiException.notFound("Unknown indicator: " + id);
    }

    public Optional<IndicatorSnapshot> latestSnapshot(String symbol) {
        return snapshots.findLatest(symbol);
    }

    /** Compute + persist the canonical snapshot for a symbol as of a date. */
    @Transactional
    public void computeSnapshot(String symbol, LocalDate asOf) {
        LocalDate from = asOf.minusDays(props.lookbackCalendarDays());
        List<CandleBar> bars = candles.series(symbol, from, asOf);
        if (bars.isEmpty()) {
            return;
        }
        BarSeries series = BarSeriesAdapter.toSeries(symbol, bars);
        var close = new ClosePriceIndicator(series);
        Double rsi14 = last(series, new RSIIndicator(close, 14));
        Double ema9 = last(series, new EMAIndicator(close, 9));
        Double ema21 = last(series, new EMAIndicator(close, 21));
        Double atr14 = last(series, new ATRIndicator(series, 14));
        Double adx14 = last(series, new ADXIndicator(series, 14));

        Map<String, Double> values = new LinkedHashMap<>();
        putIfPresent(values, "rsi14", rsi14);
        putIfPresent(values, "ema9", ema9);
        putIfPresent(values, "ema21", ema21);
        putIfPresent(values, "atr14", atr14);
        putIfPresent(values, "adx14", adx14);

        snapshots.upsert(new IndicatorSnapshot(symbol, asOf, values,
                bd(rsi14), bd(ema9), bd(ema21), bd(atr14)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onMarketDataReady(MarketDataReadyEvent e) {
        for (String symbol : candles.symbolsOn(e.tradeDate())) {
            try {
                computeSnapshot(symbol, e.tradeDate());
            } catch (RuntimeException ex) {
                log.warn("Snapshot failed for {} on {}", symbol, e.tradeDate(), ex);
            }
        }
        log.info("Indicator snapshots computed for {}", e.tradeDate());
        events.publishEvent(new IndicatorsComputedEvent(e.tradeDate(), e.suppressNotifications()));
    }

    private static Double last(BarSeries series, Indicator<Num> indicator) {
        if (series.getBarCount() == 0) {
            return null;
        }
        Num v = indicator.getValue(series.getEndIndex());
        return v.isNaN() ? null : v.doubleValue();
    }

    private static void putIfPresent(Map<String, Double> map, String key, Double v) {
        if (v != null) {
            map.put(key, v);
        }
    }

    private static BigDecimal bd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
