package np.com.thapanarayan.backend.marketdata.internal;

import java.util.List;

import np.com.thapanarayan.backend.marketdata.api.BrokerNetView;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.IntradayCandleView;
import np.com.thapanarayan.backend.marketdata.api.VolumeBinView;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;

/** Entity → published-view conversions for the marketdata read side. */
final class MarketDataMapper {

    private MarketDataMapper() {
    }

    static DailyCandleView toView(DailyCandleEntity e) {
        return new DailyCandleView(
                e.getSymbol(), e.getTradeDate(), e.getOpen(), e.getHigh(), e.getLow(), e.getClose(),
                e.getVolume(), e.getTurnover(), e.getVwap(), e.getPreviousClose(), e.getChangePercent(),
                e.getTradeCount());
    }

    static IntradayCandleView toView(IntradayCandleEntity e) {
        return new IntradayCandleView(
                e.getSymbol(), e.getBucketStart(), e.getIntervalMinutes(), e.getOpen(), e.getHigh(),
                e.getLow(), e.getClose(), e.getVolume(), e.getTurnover(), e.getTradeCount());
    }

    static VolumeProfileView toView(VolumeProfileEntity e) {
        List<VolumeBinView> bins = e.getBins().stream()
                .map(b -> new VolumeBinView(b.priceLow(), b.priceHigh(), b.volume(), b.inValueArea(), b.node()))
                .toList();
        return new VolumeProfileView(
                e.getSymbol(), e.getTradeDate(), e.getBinCount(), e.getBinWidth(), e.getPriceMin(),
                e.getPriceMax(), e.getPocPrice(), e.getValueAreaHigh(), e.getValueAreaLow(),
                e.getTotalVolume(), e.getValueAreaVolume(), bins);
    }

    static BrokerNetView toView(BrokerFlowDailyEntity e) {
        return new BrokerNetView(
                e.getBrokerId(), e.getBuyQty(), e.getSellQty(), e.getNetQty(),
                e.getBuyAmount(), e.getSellAmount(), e.getNetAmount());
    }
}
