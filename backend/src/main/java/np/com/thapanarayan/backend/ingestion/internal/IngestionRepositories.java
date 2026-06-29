package np.com.thapanarayan.backend.ingestion.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface IngestionBatchRepository extends JpaRepository<IngestionBatchEntity, Long> {

    Optional<IngestionBatchEntity> findFirstByOrderByIdDesc();
}

interface IngestionJobRepository extends JpaRepository<IngestionJobEntity, Long> {

    List<IngestionJobEntity> findByBatchIdOrderByTradeDateAsc(Long batchId);

    Page<IngestionJobEntity> findAllByOrderByIdDesc(Pageable pageable);
}

interface IngestionRejectionRepository extends JpaRepository<IngestionRejectionEntity, Long> {

    Page<IngestionRejectionEntity> findByJobId(Long jobId, Pageable pageable);
}
