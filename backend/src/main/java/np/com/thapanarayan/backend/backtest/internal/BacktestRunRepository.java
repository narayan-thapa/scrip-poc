package np.com.thapanarayan.backend.backtest.internal;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface BacktestRunRepository extends JpaRepository<BacktestRunEntity, UUID> {

    Page<BacktestRunEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
