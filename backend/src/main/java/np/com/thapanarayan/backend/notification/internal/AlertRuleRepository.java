package np.com.thapanarayan.backend.notification.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, UUID> {

    List<AlertRuleEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<AlertRuleEntity> findByIdAndUserId(UUID id, UUID userId);

    /** All enabled rules — the Stage 9 evaluator's working set on each signals run. */
    List<AlertRuleEntity> findByEnabledTrue();
}
