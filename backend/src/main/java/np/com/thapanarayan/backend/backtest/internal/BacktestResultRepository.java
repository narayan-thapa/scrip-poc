package np.com.thapanarayan.backend.backtest.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BacktestResultRepository extends JpaRepository<BacktestResultEntity, UUID> {
}
