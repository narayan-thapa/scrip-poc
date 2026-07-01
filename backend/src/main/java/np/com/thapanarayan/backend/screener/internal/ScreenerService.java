package np.com.thapanarayan.backend.screener.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import np.com.thapanarayan.backend.indicator.api.BarSeriesFactory;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.marketdata.api.CandleSeriesReader;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketBoard;
import np.com.thapanarayan.backend.marketdata.api.MarketSummaryView;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.ActiveRow;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.BreadthDto;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.DashboardDto;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.PriceDropRow;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.RvolRow;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.SignalTag;
import np.com.thapanarayan.backend.signal.api.SignalReader;
import np.com.thapanarayan.backend.signal.api.SignalView;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * Read-side scans over marketdata + signal (§F11/F12, §6.6). Every scan applies a liquidity floor
 * and a warm-up guard (a scrip needs ≥N prior sessions), annotates rows with the current signal, and
 * counts windows as trading-day candle rows. The day dashboard is cache-friendly (pre-warmed at EOD).
 */
@Service
public class ScreenerService {

    private final MarketBoard board;
    private final CandleSeriesReader candles;
    private final SignalReader signals;
    private final ScreenerCache cache;
    private final ScreenerProperties props;

    ScreenerService(MarketBoard board, CandleSeriesReader candles, SignalReader signals,
                    ScreenerCache cache, ScreenerProperties props) {
        this.board = board;
        this.candles = candles;
        this.signals = signals;
        this.cache = cache;
        this.props = props;
    }

    public LocalDate resolveDate(LocalDate date) {
        return date != null ? date : board.latestTradeDate().orElse(LocalDate.now());
    }

    // ---- High / Low trade ranking ----

    public List<ActiveRow> activeRanking(LocalDate date, String by, boolean high, int limit) {
        Map<String, SignalView> sig = signals.byDate(date);
        Comparator<DailyCandleView> cmp = switch (by) {
            case "volume" -> Comparator.comparingLong(DailyCandleView::volume);
            case "trades" -> Comparator.comparingInt(DailyCandleView::tradesCount);
            default -> Comparator.comparing(DailyCandleView::turnover);
        };
        return board.candlesOn(date).stream()
                // "Low trade" is the liquidity tail — apply the floor so zero-trade noise doesn't dominate.
                .filter(c -> high || c.turnover().longValue() >= props.minTurnover())
                .sorted(high ? cmp.reversed() : cmp)
                .limit(limit)
                .map(c -> new ActiveRow(c.symbol(), c.close(), c.changePct(), c.volume(), c.turnover(),
                        c.tradesCount(), tag(sig, c.symbol())))
                .toList();
    }

    // ---- Relative volume ----

    public List<RvolRow> relativeVolume(LocalDate date, int window, boolean spike, Double minRatio, Double minZScore,
                                        int limit) {
        Map<String, SignalView> sig = signals.byDate(date);
        List<RvolRow> rows = new ArrayList<>();
        for (DailyCandleView c : board.candlesOn(date)) {
            if (c.turnover().longValue() < props.minTurnover()) {
                continue; // liquidity floor
            }
            List<CandleBar> history = fetchHistory(c.symbol(), date, window + 1);
            List<Long> priors = priorVolumes(history, date, window);
            if (priors.size() < window) {
                continue; // warm-up guard: need N prior sessions
            }
            ScreenerCalc.Rvol rvol = ScreenerCalc.rvol(priors, c.volume());
            boolean keep = spike
                    ? rvol.ratio() >= (minRatio != null ? minRatio : props.spikeThreshold())
                            && (minZScore == null || rvol.zScore() >= minZScore)
                    : rvol.ratio() <= (minRatio != null ? minRatio : props.dropThreshold());
            if (keep) {
                rows.add(new RvolRow(c.symbol(), c.volume(), rvol.ratio(), rvol.zScore(), c.changePct(),
                        tag(sig, c.symbol())));
            }
        }
        // Spikes ranked by z-score (more robust); drops by the raw ratio ascending.
        rows.sort(spike ? Comparator.comparingDouble(RvolRow::rvolZ).reversed()
                : Comparator.comparingDouble(RvolRow::rvolRatio));
        return rows.stream().limit(limit).toList();
    }

    // ---- Sharp price drop ----

    public List<PriceDropRow> priceDrop(LocalDate date, int window, String metric, Double threshold, int limit) {
        Map<String, SignalView> sig = signals.byDate(date);
        List<PriceDropRow> rows = new ArrayList<>();
        for (DailyCandleView c : board.candlesOn(date)) {
            if (c.turnover().longValue() < props.minTurnover()) {
                continue; // liquidity floor on by default
            }
            List<CandleBar> history = fetchHistory(c.symbol(), date, window + 1);
            if (history.isEmpty()) {
                continue;
            }
            boolean insufficient = history.size() < window + 1;
            List<CandleBar> slice = history.subList(Math.max(0, history.size() - (window + 1)), history.size());
            double closeNow = slice.get(slice.size() - 1).close().doubleValue();
            double closeNAgo = slice.get(0).close().doubleValue();
            double windowHigh = slice.stream().mapToDouble(b -> b.high().doubleValue()).max().orElse(closeNow);
            double windowLow = slice.stream().mapToDouble(b -> b.low().doubleValue()).min().orElse(closeNow);
            double atrPct = atrPercent(c.symbol(), history, closeNow);

            ScreenerCalc.PriceDrop pd = ScreenerCalc.priceDrop(closeNow, closeNAgo, windowHigh, atrPct, window);
            double value = switch (metric) {
                case "drawdown" -> pd.drawdownFromHigh();
                case "sharpness" -> pd.sharpness();
                default -> pd.pctChange();
            };
            if (threshold != null && value > threshold) {
                continue; // keep only drops at/below the threshold
            }
            double rvolRatio = ScreenerCalc.rvol(priorVolumes(history, date, window), c.volume()).ratio();
            rows.add(new PriceDropRow(c.symbol(), c.close(), BigDecimal.valueOf(closeNAgo), pd.pctChange(),
                    pd.drawdownFromHigh(), pd.sharpness(), BigDecimal.valueOf(windowHigh), BigDecimal.valueOf(windowLow),
                    c.volume(), rvolRatio, insufficient, tag(sig, c.symbol())));
        }
        rows.sort(Comparator.comparingDouble(this::metricValue).thenComparing(PriceDropRow::symbol));
        return rows.stream().limit(limit).toList();
    }

    private double metricValue(PriceDropRow r) {
        // Rank most-negative first across whichever lens the row carries; pctChange is the anchor.
        return r.pctChange();
    }

    // ---- Day dashboard (cached) ----

    public DashboardDto dayDashboard(LocalDate date, int window) {
        String key = date + ":dashboard:" + window;
        return cache.getOrCompute(key, () -> {
            MarketSummaryView s = board.summary(date).orElse(null);
            BreadthDto breadth = s == null ? new BreadthDto(0, 0, 0, 0, BigDecimal.ZERO, 0)
                    : new BreadthDto(s.advances(), s.declines(), s.unchanged(), s.totalVolume(),
                            s.totalTurnover(), s.totalTrades());
            return new DashboardDto(date.toString(), breadth,
                    activeRanking(date, "turnover", true, 10),
                    activeRanking(date, "turnover", false, 10),
                    relativeVolume(date, window, true, null, null, 15),
                    relativeVolume(date, window, false, null, null, 15));
        });
    }

    /** Cache a price-drop preset for instant loads (called by the EOD pre-warm). */
    public List<PriceDropRow> priceDropCached(LocalDate date, int window, String metric, Double threshold, int limit) {
        String key = date + ":drop:" + window + ":" + metric + ":" + threshold + ":" + limit;
        return cache.getOrCompute(key, () -> priceDrop(date, window, metric, threshold, limit));
    }

    void evict(LocalDate date) {
        cache.evictDate(date.toString());
    }

    // ---- helpers ----

    private SignalTag tag(Map<String, SignalView> sig, String symbol) {
        SignalView v = sig.get(symbol);
        return v == null ? null : new SignalTag(v.action().name(), v.score());
    }

    /** Trading-day candles up to {@code date}, fetching a generous calendar range to cover N sessions. */
    private List<CandleBar> fetchHistory(String symbol, LocalDate date, int minSessions) {
        LocalDate from = date.minusDays(minSessions * 2L + 15);
        return candles.series(symbol, from, date);
    }

    private List<Long> priorVolumes(List<CandleBar> history, LocalDate date, int window) {
        List<Long> vols = new ArrayList<>();
        for (CandleBar b : history) {
            if (!b.tradeDate().isEqual(date)) {
                vols.add(b.volume());
            }
        }
        return vols.size() <= window ? vols : vols.subList(vols.size() - window, vols.size());
    }

    private double atrPercent(String symbol, List<CandleBar> history, double closeNow) {
        if (history.size() < 15 || closeNow == 0) {
            return 0;
        }
        BarSeries series = BarSeriesFactory.fromCandles(symbol, history);
        var atr = new ATRIndicator(series, 14).getValue(series.getEndIndex());
        var close = new ClosePriceIndicator(series).getValue(series.getEndIndex());
        if (atr.isNaN() || close.isNaN() || close.doubleValue() == 0) {
            return 0;
        }
        return atr.doubleValue() / close.doubleValue() * 100;
    }
}
