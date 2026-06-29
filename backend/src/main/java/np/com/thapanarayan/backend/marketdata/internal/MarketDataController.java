package np.com.thapanarayan.backend.marketdata.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.IntradayCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketSummaryView;
import np.com.thapanarayan.backend.marketdata.api.MoverType;
import np.com.thapanarayan.backend.marketdata.api.MoverView;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;
import np.com.thapanarayan.backend.platform.api.NotFoundException;

/**
 * Read API for derived market data, plus an admin re-aggregation trigger.
 *
 * <p>Date-scoped lookups default to the symbol's most recently aggregated date
 * when {@code date} is omitted. TODO(Stage 8 / IAM): gate the aggregate trigger
 * with role ADMIN once Spring Security is wired; today it is unauthenticated.</p>
 */
@RestController
@RequestMapping("/api/v1/market")
@Validated
class MarketDataController {

    private final MarketDataQueryService query;
    private final MarketDataAggregationService aggregation;

    MarketDataController(MarketDataQueryService query, MarketDataAggregationService aggregation) {
        this.query = query;
        this.aggregation = aggregation;
    }

    @GetMapping("/candles/{symbol}")
    List<DailyCandleView> candles(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "120") @Min(1) @Max(2000) int limit) {
        if (from != null && to != null) {
            return query.dailyCandles(symbol, from, to);
        }
        LocalDate asOf = latestDateOrThrow(symbol);
        return query.recentDailyCandles(symbol, asOf, limit);
    }

    @GetMapping("/candles/{symbol}/latest")
    DailyCandleView latest(@PathVariable String symbol) {
        return query.latestCandle(symbol)
                .orElseThrow(() -> new NotFoundException("No candle for " + symbol));
    }

    @GetMapping("/candles/{symbol}/intraday")
    List<IntradayCandleView> intraday(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return query.intradayCandles(symbol, resolveDate(symbol, date));
    }

    @GetMapping("/volume-profile/{symbol}")
    VolumeProfileView volumeProfile(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = resolveDate(symbol, date);
        return query.volumeProfile(symbol, d)
                .orElseThrow(() -> new NotFoundException("No volume profile for " + symbol + " on " + d));
    }

    @GetMapping("/broker-flow/{symbol}")
    BrokerFlowView brokerFlow(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate d = resolveDate(symbol, date);
        return query.brokerFlow(symbol, d)
                .orElseThrow(() -> new NotFoundException("No broker flow for " + symbol + " on " + d));
    }

    @GetMapping("/movers")
    List<MoverView> movers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "GAINERS") MoverType type,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return query.movers(date, type, limit);
    }

    @GetMapping("/summary")
    MarketSummaryView summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return query.summary(date);
    }

    @PostMapping("/aggregate")
    ResponseEntity<Map<String, Object>> aggregate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        int symbols = aggregation.aggregate(date, false);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("tradeDate", date, "symbolsAggregated", symbols));
    }

    private LocalDate resolveDate(String symbol, LocalDate date) {
        return date != null ? date : latestDateOrThrow(symbol);
    }

    private LocalDate latestDateOrThrow(String symbol) {
        return query.latestCandle(symbol)
                .map(DailyCandleView::tradeDate)
                .orElseThrow(() -> new NotFoundException("No aggregated market data for " + symbol));
    }
}
