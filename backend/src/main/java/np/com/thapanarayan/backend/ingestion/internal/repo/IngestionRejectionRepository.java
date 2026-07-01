package np.com.thapanarayan.backend.ingestion.internal.repo;

import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionRejection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRejectionRepository extends JpaRepository<IngestionRejection, Long> {

    Page<IngestionRejection> findByJobId(UUID jobId, Pageable pageable);

    List<IngestionRejection> findByJobId(UUID jobId);
}
