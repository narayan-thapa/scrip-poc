package np.com.thapanarayan.backend.reference.internal;

import np.com.thapanarayan.backend.reference.api.InstrumentDirectory;
import np.com.thapanarayan.backend.reference.internal.domain.Instrument;
import np.com.thapanarayan.backend.reference.internal.domain.InstrumentType;
import np.com.thapanarayan.backend.reference.internal.repo.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InstrumentDirectoryImpl implements InstrumentDirectory {

    private final InstrumentRepository repository;

    InstrumentDirectoryImpl(InstrumentRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String symbol) {
        return repository.existsById(symbol);
    }

    @Override
    public java.util.Optional<Long> listedShares(String symbol) {
        return repository.findById(symbol).map(Instrument::getListedShares);
    }

    @Override
    @Transactional
    public boolean ensureProvisional(String symbol) {
        if (repository.existsById(symbol)) {
            return false;
        }
        Instrument provisional = new Instrument(symbol, symbol, null, InstrumentType.EQUITY);
        provisional.setProvisional(true);
        repository.save(provisional);
        return true;
    }
}
