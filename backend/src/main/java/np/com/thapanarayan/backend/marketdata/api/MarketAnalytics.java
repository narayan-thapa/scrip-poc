package np.com.thapanarayan.backend.marketdata.api;

import java.time.LocalDate;
import java.util.Optional;

/** Published read access to the floorsheet-native analytics (volume profile + broker flow). */
public interface MarketAnalytics {

    Optional<VolumeProfileView> volumeProfile(String symbol, LocalDate date);

    Optional<BrokerFlowView> brokerFlow(String symbol, LocalDate date);
}
