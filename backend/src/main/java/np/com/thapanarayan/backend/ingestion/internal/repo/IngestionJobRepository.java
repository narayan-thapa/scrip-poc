package np.com.thapanarayan.backend.ingestion.internal.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionJob;
import np.com.thapanarayan.backend.ingestion.internal.domain.Statuses.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    /** Jobs of a batch in processing order (oldest trade date first). */
    List<IngestionJob> findByBatchIdOrderByTradeDateAsc(UUID batchId);

    List<IngestionJob> findByBatchIdAndStatusInOrderByTradeDateAsc(UUID batchId, List<JobStatus> statuses);

    Page<IngestionJob> findAllByOrderByTradeDateDescStartedAtDesc(Pageable pageable);

    /** Most recent job for a date — its archive key is reused when reprocessing from raw. */
    Optional<IngestionJob> findFirstByTradeDateOrderByStartedAtDesc(LocalDate tradeDate);
}
