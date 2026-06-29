package np.com.thapanarayan.backend.signal.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.signal.api.StrategyConfigView;
import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * Reads and updates the per-strategy confluence configuration. Provides the
 * enabled-weight map the scorer blends with, and the {@code /strategies} CRUD
 * surface. The strategy catalog is fixed (the {@link StrategyId} enum), so only
 * existing rows are read and updated — there is no create/delete of arbitrary
 * strategies.
 */
@Service
class StrategyConfigService {

    private final StrategyConfigRepository repository;
    private final NepseClock clock;

    StrategyConfigService(StrategyConfigRepository repository, NepseClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Enabled strategy → weight, for the confluence scorer and score indicator. */
    @Transactional(readOnly = true)
    public Map<StrategyId, Double> enabledWeights() {
        Map<StrategyId, Double> weights = new EnumMap<>(StrategyId.class);
        for (StrategyConfigEntity e : repository.findAll()) {
            if (e.isEnabled() && e.getWeight() != null && e.getWeight().signum() > 0) {
                parse(e.getStrategyId()).ifPresent(id -> weights.put(id, e.getWeight().doubleValue()));
            }
        }
        return weights;
    }

    @Transactional(readOnly = true)
    public List<StrategyConfigView> list() {
        return repository.findAll().stream()
                .map(StrategyConfigService::toView)
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(v -> v.strategyId().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StrategyConfigView get(StrategyId id) {
        return repository.findById(id.name())
                .map(StrategyConfigService::toView)
                .orElseThrow(() -> new NotFoundException("No configuration for strategy " + id));
    }

    /** Updates the weight and enabled flag of an existing strategy. */
    @Transactional
    public StrategyConfigView update(StrategyId id, boolean enabled, BigDecimal weight) {
        if (weight == null || weight.signum() < 0) {
            throw new DomainException("INVALID_WEIGHT", "Strategy weight must be >= 0");
        }
        StrategyConfigEntity e = repository.findById(id.name())
                .orElseThrow(() -> new NotFoundException("No configuration for strategy " + id));
        e.setEnabled(enabled);
        e.setWeight(weight);
        e.setUpdatedAt(Instant.now(clock.clock()));
        return toView(repository.save(e));
    }

    private static StrategyConfigView toView(StrategyConfigEntity e) {
        return parse(e.getStrategyId())
                .map(id -> new StrategyConfigView(id, e.getLabel(), e.isEnabled(), e.getWeight(), e.getUpdatedAt()))
                .orElse(null);
    }

    private static java.util.Optional<StrategyId> parse(String id) {
        try {
            return java.util.Optional.of(StrategyId.valueOf(id));
        } catch (IllegalArgumentException unknown) {
            return java.util.Optional.empty();
        }
    }
}
