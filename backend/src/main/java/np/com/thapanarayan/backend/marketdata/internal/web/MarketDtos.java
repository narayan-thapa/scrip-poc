package np.com.thapanarayan.backend.marketdata.internal.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.api.TradeView;
import np.com.thapanarayan.backend.marketdata.internal.domain.DailyCandle;
import np.com.thapanarayan.backend.marketdata.internal.domain.MarketAggregateDaily;
import np.com.thapanarayan.backend.marketdata.internal.domain.VolumeProfile;

/** Response payloads for the market-data API. Shapes are chart/UI-friendly. */
final class MarketDtos {

    private MarketDtos() {
    }

    record CandleDto(String time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close,
                     long volume, BigDecimal turnover, int trades, BigDecimal vwap, BigDecimal changePct) {
        static CandleDto from(DailyCandle c) {
            return new CandleDto(c.tradeDate().toString(), c.open(), c.high(), c.low(), c.close(),
                    c.volume(), c.turnover(), c.tradesCount(), c.vwap(), c.changePct());
        }
    }

    record VwapPoint(String time, BigDecimal vwap) {}

    record TradeDto(String time, int buyerBroker, int sellerBroker, long quantity,
                    BigDecimal price, BigDecimal amount) {
        static TradeDto from(TradeView t) {
            return new TradeDto(t.tradeTime().toString(), t.buyerBroker(), t.sellerBroker(),
                    t.quantity(), t.price(), t.amount());
        }
    }

    record VolumeProfileDto(String symbol, String windowFrom, String windowTo,
                            BigDecimal poc, BigDecimal vah, BigDecimal val, List<BinDto> bins) {
        static VolumeProfileDto from(VolumeProfile p) {
            return new VolumeProfileDto(p.symbol(), p.windowFrom().toString(), p.windowTo().toString(),
                    p.poc(), p.vah(), p.val(),
                    p.bins().stream().map(b -> new BinDto(b.price(), b.volume())).toList());
        }
    }

    record BinDto(BigDecimal price, long volume) {}

    record BrokerFlowRowDto(int brokerId, long buyQty, long sellQty, long netQty,
                            BigDecimal buyAmount, BigDecimal sellAmount) {}

    record BrokerFlowDto(String symbol, String date, List<BrokerFlowRowDto> brokers,
                         double topBuyerShare, double topSellerShare, double hhiBuy, double hhiSell) {}

    record BrokerNetDto(int brokerId, long netQty, long buyQty, long sellQty) {}

    record TopBrokersDto(String date, List<BrokerNetDto> accumulators, List<BrokerNetDto> distributors) {}

    record MoverDto(String symbol, BigDecimal close, BigDecimal changePct, long volume, BigDecimal turnover) {
        static MoverDto from(DailyCandle c) {
            return new MoverDto(c.symbol(), c.close(), c.changePct(), c.volume(), c.turnover());
        }
    }

    record MoversDto(String date, List<MoverDto> gainers, List<MoverDto> losers,
                     List<MoverDto> mostActiveByVolume, List<MoverDto> mostActiveByTurnover) {}

    record SummaryDto(String date, int advances, int declines, int unchanged,
                      long totalVolume, BigDecimal totalTurnover, long totalTrades) {}

    record AggregateDto(String time, long totalVolume, BigDecimal totalTurnover, long totalTrades,
                        int advances, int declines, int unchanged, BigDecimal indexProxyClose,
                        BigDecimal officialIndexClose) {
        static AggregateDto from(MarketAggregateDaily a) {
            return new AggregateDto(a.tradeDate().toString(), a.totalVolume(), a.totalTurnover(), a.totalTrades(),
                    a.advances(), a.declines(), a.unchanged(), a.indexProxyClose(), a.officialIndexClose());
        }
    }

    record IntradayBarDto(String time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {}
}
