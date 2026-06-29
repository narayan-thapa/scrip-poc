package np.com.thapanarayan.backend.reference.internal;

import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.reference.api.InstrumentCatalog;
import np.com.thapanarayan.backend.reference.api.InstrumentStatus;
import np.com.thapanarayan.backend.reference.api.InstrumentView;

@Service
class InstrumentService implements InstrumentCatalog {

    private final InstrumentRepository repository;
    private final NepseClock clock;

    InstrumentService(InstrumentRepository repository, NepseClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InstrumentView> findBySymbol(String symbol) {
        return repository.findById(symbol).map(InstrumentService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(String symbol) {
        return repository.existsById(symbol);
    }

    @Override
    @Transactional
    public InstrumentView getOrCreateProvisional(String symbol) {
        Optional<InstrumentEntity> existing = repository.findById(symbol);
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        InstrumentEntity entity = new InstrumentEntity();
        entity.setSymbol(symbol);
        entity.setName(symbol);
        entity.setStatus(InstrumentStatus.PROVISIONAL);
        entity.setCreatedAt(Instant.now(clock.clock()));
        try {
            return toView(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException raceLost) {
            // Another thread created the same provisional symbol first; read theirs.
            return repository.findById(symbol)
                    .map(InstrumentService::toView)
                    .orElseThrow(() -> raceLost);
        }
    }

    Page<InstrumentView> search(String sector, InstrumentStatus status, String q, Pageable pageable) {
        return repository.search(sector, status, q, pageable).map(InstrumentService::toView);
    }

    java.util.List<String> sectors() {
        return repository.findDistinctSectors();
    }

    static InstrumentView toView(InstrumentEntity e) {
        return new InstrumentView(
                e.getSymbol(), e.getName(), e.getSector(), e.getListedShares(),
                e.getStatus(), e.getPriceBand(), e.getCreatedAt());
    }
}
