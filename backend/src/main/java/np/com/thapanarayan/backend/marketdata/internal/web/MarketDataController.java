package np.com.thapanarayan.backend.marketdata.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import np.com.thapanarayan.backend.ingestion.api.FloorsheetReader;
import np.com.thapanarayan.backend.marketdata.internal.calc.IntradayCalculator;
import np.com.thapanarayan.backend.marketdata.internal.dao.DailyCandleDao;
import np.com.thapanarayan.backend.marketdata.internal.dao.MarketAggregateDao;
import np.com.thapanarayan.backend.marketdata.internal.domain.DailyCandle;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.AggregateDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.CandleDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.IntradayBarDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.MoverDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.MoversDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.SummaryDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.TradeDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.VwapPoint;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.page.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Price/volume reads: candles, VWAP, raw trades, movers, summary, the NEPSE aggregate, intraday. */
@RestController
@RequestMapping("/api/v1/market")
@Tag(name = "Market Data", description = "Candles, VWAP, trades, movers, summary and the NEPSE aggregate")
class MarketDataController {

    private final DailyCandleDao candles;
    private final MarketAggregateDao aggregates;
    private final FloorsheetReader trades;

    MarketDataController(DailyCandleDao candles, MarketAggregateDao aggregates, FloorsheetReader trades) {
        this.candles = candles;
        this.aggregates = aggregates;
        this.trades = trades;
    }

    @GetMapping("/candles")
    List<CandleDto> candles(@RequestParam String symbol,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return candles.find(symbol, from, to).stream().map(CandleDto::from).toList();
    }

    @GetMapping("/vwap")
    List<VwapPoint> vwap(@RequestParam String symbol,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return candles.find(symbol, from, to).stream()
                .map(c -> new VwapPoint(c.tradeDate().toString(), c.vwap())).toList();
    }

    @GetMapping("/trades")
    PageResponse<TradeDto> trades(@RequestParam String symbol,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @PageableDefault(size = 100) Pageable pageable) {
        return PageResponse.from(trades.page(symbol, date, pageable), TradeDto::from);
    }

    @GetMapping("/candles/intraday")
    List<IntradayBarDto> intraday(@RequestParam String symbol,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam(defaultValue = "5m") String interval) {
        int minutes = parseInterval(interval);
        return IntradayCalculator.bucket(trades.tradesForSymbolAndDate(symbol, date), minutes).stream()
                .map(b -> new IntradayBarDto(b.bucketStart().toString(), b.open(), b.high(), b.low(), b.close(),
                        b.volume()))
                .toList();
    }

    @GetMapping("/movers")
    MoversDto movers(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                     @RequestParam(defaultValue = "10") int limit) {
        List<DailyCandle> day = candles.listForDate(date);
        return new MoversDto(date.toString(),
                top(day, Comparator.comparing(DailyCandle::changePct, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed(), limit, true),
                top(day, Comparator.comparing(DailyCandle::changePct, Comparator.nullsLast(Comparator.naturalOrder())),
                        limit, true),
                top(day, Comparator.comparingLong(DailyCandle::volume).reversed(), limit, false),
                top(day, Comparator.comparing(DailyCandle::turnover).reversed(), limit, false));
    }

    @GetMapping("/summary")
    SummaryDto summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return aggregates.find(date)
                .map(a -> new SummaryDto(a.tradeDate().toString(), a.advances(), a.declines(), a.unchanged(),
                        a.totalVolume(), a.totalTurnover(), a.totalTrades()))
                .orElseThrow(() -> ApiException.notFound("No market summary for " + date));
    }

    @GetMapping("/aggregate")
    List<AggregateDto> aggregate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return aggregates.findRange(from, to).stream().map(AggregateDto::from).toList();
    }

    private static List<MoverDto> top(List<DailyCandle> day, Comparator<DailyCandle> order, int limit,
                                      boolean requireChange) {
        return day.stream()
                .filter(c -> !requireChange || c.changePct() != null)
                .sorted(order)
                .limit(limit)
                .map(MoverDto::from)
                .filter(Objects::nonNull)
                .toList();
    }

    private static int parseInterval(String interval) {
        return switch (interval) {
            case "1m" -> 1;
            case "15m" -> 15;
            case "5m" -> 5;
            default -> throw ApiException.notFound("Unsupported interval: " + interval);
        };
    }
}
