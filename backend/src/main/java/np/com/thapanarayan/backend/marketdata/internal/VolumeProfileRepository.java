package np.com.thapanarayan.backend.marketdata.internal;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface VolumeProfileRepository extends JpaRepository<VolumeProfileEntity, VolumeProfileId> {

    Optional<VolumeProfileEntity> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);
}
