package np.com.thapanarayan.backend.indicator.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.indicator.api.BarSeriesProvider;
import np.com.thapanarayan.backend.indicator.api.IndicatorCatalogEntry;
import np.com.thapanarayan.backend.indicator.api.IndicatorPoint;
import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesQuery;
import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesView;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketDataQuery;
import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.platform.api.TradingCalendar;

/**
 * Ad-hoc parametrized indicator series for charting and the published
 * {@link BarSeriesProvider} adapter. Series are computed over a warm-up window
 * (so the first in-range value isn't cold-started) and cached in Redis keyed by
 * {@code (symbol, indicator, params, lastDate)} — deterministic EOD data means a
 * cache hit is always valid.
 */
@Service
class IndicatorSeriesService implements BarSeriesProvider, IndicatorSeriesQuery {

    private static final int SCALE = 4;

    private final MarketDataQuery marketData;
    private final TradingCalendar calendar;
    private final IndicatorSeriesCache cache;
    private final IndicatorProperties properties;

    IndicatorSeriesService(MarketDataQuery marketData, TradingCalendar calendar,
            IndicatorSeriesCache cache, IndicatorProperties properties) {
        this.marketData = marketData;
        this.calendar = calendar;
        this.cache = cache;
        this.properties = properties;
    }

    @Override
    public List<IndicatorCatalogEntry> catalog() {
        List<IndicatorCatalogEntry> entries = new ArrayList<>();
        for (IndicatorType t : IndicatorType.values()) {
            entries.add(t.toEntry());
        }
        return entries;
    }

    @Override
    @Transactional(readOnly = true)
    public IndicatorSeriesView computeSeries(String symbol, String indicatorKey, List<Integer> params,
            LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new DomainException("BAD_RANGE", "'from' must not be after 'to'");
        }
        IndicatorType type = IndicatorType.fromKey(indicatorKey)
                .orElseThrow(() -> new NotFoundException("Unknown indicator: " + indicatorKey));
        List<Integer> effectiveParams = params == null || params.isEmpty() ? type.defaultParams() : params;

        String cacheKey = IndicatorSeriesCache.key(symbol, type.key(), effectiveParams, to);
        var cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        LocalDate warmStart = warmupStart(from);
        List<DailyCandleView> candles = marketData.dailyCandles(symbol, warmStart, to);
        if (candles.isEmpty()) {
            throw new NotFoundException("No candles for " + symbol + " in range " + warmStart + ".." + to);
        }

        IndicatorBarSeries series = BarSeriesAdapter.decimal(symbol, candles);
        Map<String, Indicator<Num>> lines = IndicatorResolver.resolve(type, series.series(), effectiveParams);

        Map<String, List<IndicatorPoint>> out = new LinkedHashMap<>();
        BarSeries s = series.series();
        for (var line : lines.entrySet()) {
            List<IndicatorPoint> points = new ArrayList<>();
            for (int i = s.getBeginIndex(); i <= s.getEndIndex(); i++) {
                LocalDate date = series.dates().get(i);
                if (date.isBefore(from) || date.isAfter(to)) {
                    continue;
                }
                BigDecimal v = value(line.getValue(), i);
                if (v != null) {
                    points.add(new IndicatorPoint(date, v));
                }
            }
            out.put(line.getKey(), points);
        }

        IndicatorSeriesView view = new IndicatorSeriesView(symbol, type.key(), effectiveParams, out);
        cache.put(cacheKey, view);
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public BarSeries dailySeries(String symbol, LocalDate asOf, int lookback) {
        List<DailyCandleView> candles = marketData.recentDailyCandles(symbol, asOf, lookback);
        return BarSeriesAdapter.decimal(symbol, candles).series();
    }

    /** Extend the window back by the look-back so in-range values are warmed up. */
    private LocalDate warmupStart(LocalDate from) {
        try {
            return calendar.minusTradingDays(from, properties.lookbackBars());
        } catch (RuntimeException outOfCoverage) {
            return from;
        }
    }

    private static BigDecimal value(Indicator<Num> indicator, int index) {
        try {
            Num n = indicator.getValue(index);
            return n == null ? null : n.bigDecimalValue().setScale(SCALE, RoundingMode.HALF_UP);
        } catch (RuntimeException notAvailable) {
            return null;
        }
    }
}
