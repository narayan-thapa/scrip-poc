package np.com.thapanarayan.backend.indicator.internal;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface IndicatorSnapshotRepository extends JpaRepository<IndicatorSnapshotEntity, IndicatorSnapshotId> {

    Optional<IndicatorSnapshotEntity> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    Optional<IndicatorSnapshotEntity> findFirstBySymbolOrderByTradeDateDesc(String symbol);
}
