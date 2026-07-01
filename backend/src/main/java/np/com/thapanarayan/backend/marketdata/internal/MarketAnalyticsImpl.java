package np.com.thapanarayan.backend.marketdata.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.marketdata.api.MarketAnalytics;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;
import np.com.thapanarayan.backend.marketdata.internal.dao.BrokerFlowDao;
import np.com.thapanarayan.backend.marketdata.internal.dao.VolumeProfileDao;
import np.com.thapanarayan.backend.marketdata.internal.domain.BrokerFlow;
import org.springframework.stereotype.Service;

@Service
class MarketAnalyticsImpl implements MarketAnalytics {

    private final VolumeProfileDao profiles;
    private final BrokerFlowDao brokerFlow;

    MarketAnalyticsImpl(VolumeProfileDao profiles, BrokerFlowDao brokerFlow) {
        this.profiles = profiles;
        this.brokerFlow = brokerFlow;
    }

    @Override
    public Optional<VolumeProfileView> volumeProfile(String symbol, LocalDate date) {
        return profiles.find(symbol, date, date)
                .map(p -> new VolumeProfileView(p.symbol(), p.poc(), p.vah(), p.val()));
    }

    @Override
    public Optional<BrokerFlowView> brokerFlow(String symbol, LocalDate date) {
        List<BrokerFlow> rows = brokerFlow.find(symbol, date);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        long totalBuy = rows.stream().mapToLong(BrokerFlow::buyQty).sum();
        long totalSell = rows.stream().mapToLong(BrokerFlow::sellQty).sum();
        long maxBuy = rows.stream().mapToLong(BrokerFlow::buyQty).max().orElse(0);
        long maxSell = rows.stream().mapToLong(BrokerFlow::sellQty).max().orElse(0);
        return Optional.of(new BrokerFlowView(symbol,
                share(maxBuy, totalBuy), share(maxSell, totalSell),
                hhi(rows, totalBuy, true), hhi(rows, totalSell, false)));
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
