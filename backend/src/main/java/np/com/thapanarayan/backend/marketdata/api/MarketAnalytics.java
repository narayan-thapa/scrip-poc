package np.com.thapanarayan.backend.marketdata.api;

import java.time.LocalDate;
import java.util.Optional;

/** Published read access to the floorsheet-native analytics (volume profile + broker flow). */
public interface MarketAnalytics {

    Optional<VolumeProfileView> volumeProfile(String symbol, LocalDate date);

    /** Stored profile for the window if present, else the daily profile for {@code to} (charting). */
    Optional<VolumeProfileView> volumeProfile(String symbol, LocalDate from, LocalDate to);

    Optional<BrokerFlowView> brokerFlow(String symbol, LocalDate date);
}
