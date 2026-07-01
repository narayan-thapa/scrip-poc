package np.com.thapanarayan.backend.marketdata.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.api.FloorsheetReader;
import np.com.thapanarayan.backend.ingestion.api.TradeView;
import np.com.thapanarayan.backend.marketdata.internal.MarketDataProperties;
import np.com.thapanarayan.backend.marketdata.internal.calc.VolumeProfileCalculator;
import np.com.thapanarayan.backend.marketdata.internal.calc.VolumeProfileResult;
import np.com.thapanarayan.backend.marketdata.internal.dao.BrokerFlowDao;
import np.com.thapanarayan.backend.marketdata.internal.dao.VolumeProfileDao;
import np.com.thapanarayan.backend.marketdata.internal.domain.BrokerFlow;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.BinDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.BrokerFlowDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.BrokerFlowRowDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.BrokerNetDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.TopBrokersDto;
import np.com.thapanarayan.backend.marketdata.internal.web.MarketDtos.VolumeProfileDto;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Volume profile + broker flow reads (the two floorsheet-native analytics). */
@RestController
@RequestMapping("/api/v1/market")
@Tag(name = "Market Data — flow & profile", description = "Volume profile and broker flow")
class FlowProfileController {

    private final VolumeProfileDao profiles;
    private final BrokerFlowDao brokerFlow;
    private final FloorsheetReader trades;
    private final MarketDataProperties props;

    FlowProfileController(VolumeProfileDao profiles, BrokerFlowDao brokerFlow, FloorsheetReader trades,
                          MarketDataProperties props) {
        this.profiles = profiles;
        this.brokerFlow = brokerFlow;
        this.trades = trades;
        this.props = props;
    }

    @GetMapping("/volume-profile")
    VolumeProfileDto volumeProfile(@RequestParam String symbol,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) Integer bins) {
        // Stored daily profile when available and no custom binning requested; else compute composite.
        if (bins == null) {
            var stored = profiles.find(symbol, from, to);
            if (stored.isPresent()) {
                return VolumeProfileDto.from(stored.get());
            }
        }
        List<TradeView> windowTrades = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            windowTrades.addAll(trades.tradesForSymbolAndDate(symbol, d));
        }
        if (windowTrades.isEmpty()) {
            throw ApiException.notFound("No trades for " + symbol + " in " + from + ".." + to);
        }
        VolumeProfileResult r = VolumeProfileCalculator.compute(windowTrades, bins != null ? bins : props.volumeProfileBins());
        return new VolumeProfileDto(symbol, from.toString(), to.toString(), r.poc(), r.vah(), r.val(),
                r.bins().stream().map(b -> new BinDto(b.price(), b.volume())).toList());
    }

    @GetMapping("/broker-flow")
    BrokerFlowDto brokerFlow(@RequestParam String symbol,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<BrokerFlow> rows = brokerFlow.find(symbol, date);
        long totalBuy = rows.stream().mapToLong(BrokerFlow::buyQty).sum();
        long totalSell = rows.stream().mapToLong(BrokerFlow::sellQty).sum();
        long maxBuy = rows.stream().mapToLong(BrokerFlow::buyQty).max().orElse(0);
        long maxSell = rows.stream().mapToLong(BrokerFlow::sellQty).max().orElse(0);
        List<BrokerFlowRowDto> dto = rows.stream()
                .map(r -> new BrokerFlowRowDto(r.brokerId(), r.buyQty(), r.sellQty(), r.netQty(),
                        r.buyAmount(), r.sellAmount()))
                .toList();
        return new BrokerFlowDto(symbol, date.toString(), dto,
                share(maxBuy, totalBuy), share(maxSell, totalSell),
                hhi(rows, totalBuy, true), hhi(rows, totalSell, false));
    }

    @GetMapping("/broker-flow/top")
    TopBrokersDto topBrokers(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam(defaultValue = "10") int limit) {
        List<BrokerFlowDao.BrokerNet> net = brokerFlow.netByBrokerForDate(date); // net desc
        List<BrokerNetDto> accumulators = net.stream().filter(b -> b.netQty() > 0).limit(limit)
                .map(b -> new BrokerNetDto(b.brokerId(), b.netQty(), b.buyQty(), b.sellQty())).toList();
        List<BrokerNetDto> distributors = net.stream().filter(b -> b.netQty() < 0)
                .sorted((a, b) -> Long.compare(a.netQty(), b.netQty())).limit(limit)
                .map(b -> new BrokerNetDto(b.brokerId(), b.netQty(), b.buyQty(), b.sellQty())).toList();
        return new TopBrokersDto(date.toString(), accumulators, distributors);
    }

    private static double share(long max, long total) {
        return total == 0 ? 0.0 : (double) max / total;
    }

    private static double hhi(List<BrokerFlow> rows, long total, boolean buy) {
        if (total == 0) {
            return 0.0;
        }
        double sum = 0;
        for (BrokerFlow r : rows) {
            double s = (double) (buy ? r.buyQty() : r.sellQty()) / total;
            sum += s * s;
        }
        return sum;
    }
}
