package np.com.thapanarayan.backend.notification.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndSignalId(UUID userId, UUID signalId);

    long countByUserIdAndReadFalse(UUID userId);

    /** Undispatched rows — the dispatcher's working set (outbox). */
    List<NotificationEntity> findBySentFalseOrderByCreatedAtAsc();
}
