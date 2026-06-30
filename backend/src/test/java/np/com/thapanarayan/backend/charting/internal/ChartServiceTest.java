package np.com.thapanarayan.backend.charting.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.charting.api.ChartView;
import np.com.thapanarayan.backend.indicator.api.IndicatorCatalogEntry;
import np.com.thapanarayan.backend.indicator.api.IndicatorPoint;
import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesQuery;
import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesView;
import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketDataQuery;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalQuery;
import np.com.thapanarayan.backend.signal.api.SignalView;
import np.com.thapanarayan.backend.smc.api.SmcAnalysisQuery;
import np.com.thapanarayan.backend.smc.api.SmcView;

/** Composition + ETag behaviour of the charting service, over fake upstream ports. */
class ChartServiceTest {

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 2);

    private static DailyCandleView candle(LocalDate d, double close) {
        BigDecimal c = BigDecimal.valueOf(close);
        return new DailyCandleView("ABC", d, c, c, c, c, 1000L, c, c, null, null, 1);
    }

    private final MarketDataQuery marketData = new FakeMarketData();
    private final IndicatorSeriesQuery indicators = new FakeIndicators();
    private final SignalQuery signals = new FakeSignals();
    private final SmcAnalysisQuery smcAnalysis = new FakeSmc();
    private final ChartService service = new ChartService(marketData, indicators, signals, smcAnalysis);

    @Test
    void composesCandlesOverlaysVolumeProfileAndSignalMarkers() {
        ChartView view = service.compose("abc", FROM, TO, List.of("ema"), List.of("volprofile"));

        assertThat(view.symbol()).isEqualTo("ABC");
        assertThat(view.candles()).hasSize(2);
        assertThat(view.indicators()).singleElement()
                .satisfies(i -> assertThat(i.indicator()).isEqualTo("ema"));
        assertThat(view.volumeProfile()).isNotNull();
        assertThat(view.signals()).singleElement().satisfies(m -> {
            assertThat(m.action()).isEqualTo(SignalAction.BUY);
            assertThat(m.date()).isEqualTo(TO);
        });
    }

    @Test
    void omitsVolumeProfileWhenNotRequested() {
        ChartView view = service.compose("ABC", FROM, TO, List.of(), List.of());

        assertThat(view.volumeProfile()).isNull();
        assertThat(view.indicators()).isEmpty();
    }

    @Test
    void attachesSmcOnlyWhenRequested() {
        assertThat(service.compose("ABC", FROM, TO, List.of(), List.of()).smc()).isNull();

        ChartView withSmc = service.compose("ABC", FROM, TO, List.of(), List.of("smc"));
        assertThat(withSmc.smc()).isNotNull();
        assertThat(withSmc.smc().symbol()).isEqualTo("ABC");
    }

    @Test
    void etagIsStableForSameContentAndChangesWithRange() {
        ChartView a = service.compose("ABC", FROM, TO, List.of("ema"), List.of());
        ChartView b = service.compose("ABC", FROM, TO, List.of("ema"), List.of());
        ChartView c = service.compose("ABC", FROM, TO.plusDays(30), List.of("ema"), List.of());

        assertThat(service.etag(a)).isEqualTo(service.etag(b));
        assertThat(service.etag(a)).isNotEqualTo(service.etag(c));
    }

    private static final class FakeMarketData implements MarketDataQuery {
        @Override
        public List<DailyCandleView> dailyCandles(String symbol, LocalDate from, LocalDate to) {
            return List.of(candle(FROM, 100), candle(TO, 102));
        }

        @Override
        public List<DailyCandleView> recentDailyCandles(String symbol, LocalDate asOf, int limit) {
            return List.of();
        }

        @Override
        public Optional<DailyCandleView> latestCandle(String symbol) {
            return Optional.empty();
        }

        @Override
        public List<String> symbolsWithData(LocalDate tradeDate) {
            return List.of();
        }

        @Override
        public Optional<VolumeProfileView> volumeProfile(String symbol, LocalDate tradeDate) {
            return Optional.of(new VolumeProfileView(symbol, tradeDate, 1, BigDecimal.ONE,
                    BigDecimal.valueOf(99), BigDecimal.valueOf(103), BigDecimal.valueOf(101),
                    BigDecimal.valueOf(102), BigDecimal.valueOf(100), 1000L, 700L, List.of()));
        }

        @Override
        public Optional<BrokerFlowView> brokerFlow(String symbol, LocalDate tradeDate) {
            return Optional.empty();
        }
    }

    private static final class FakeIndicators implements IndicatorSeriesQuery {
        @Override
        public List<IndicatorCatalogEntry> catalog() {
            return List.of();
        }

        @Override
        public IndicatorSeriesView computeSeries(String symbol, String indicator, List<Integer> params,
                LocalDate from, LocalDate to) {
            return new IndicatorSeriesView(symbol, indicator, List.of(9),
                    Map.of(indicator, List.of(new IndicatorPoint(to, BigDecimal.valueOf(101)))));
        }
    }

    private static final class FakeSmc implements SmcAnalysisQuery {
        @Override
        public SmcView analyze(String symbol, LocalDate from, LocalDate to) {
            return new SmcView(symbol.trim().toUpperCase(), 2, List.of(), List.of());
        }
    }

    private static final class FakeSignals implements SignalQuery {
        @Override
        public Optional<SignalView> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public Optional<SignalView> latestForSymbol(String symbol) {
            return Optional.empty();
        }

        @Override
        public List<SignalView> forSymbol(String symbol, LocalDate from, LocalDate to) {
            return List.of(new SignalView(UUID.randomUUID(), symbol, TO, SignalAction.BUY,
                    BigDecimal.valueOf(42), 60, List.of(), List.of(), "BUY ABC", Instant.now()));
        }

        @Override
        public List<SignalView> forDate(LocalDate tradeDate) {
            return List.of();
        }

        @Override
        public List<SignalView> latest(Optional<SignalAction> action) {
            return List.of();
        }
    }
}
