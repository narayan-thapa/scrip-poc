package np.com.thapanarayan.backend.ingestion.internal.repo;

import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, UUID> {

    Page<IngestionBatch> findAllByOrderBySubmittedAtDesc(Pageable pageable);
}
