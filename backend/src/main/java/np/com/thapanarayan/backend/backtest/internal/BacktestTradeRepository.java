package np.com.thapanarayan.backend.backtest.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BacktestTradeRepository extends JpaRepository<BacktestTradeEntity, UUID> {

    List<BacktestTradeEntity> findByRunIdOrderByEntryDateAsc(UUID runId);
}
