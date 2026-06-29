package np.com.thapanarayan.backend.reference.api;

import java.util.Optional;

/** Published lookup surface for the broker registry. */
public interface BrokerCatalog {

    Optional<BrokerView> findById(int brokerId);

    boolean exists(int brokerId);
}
