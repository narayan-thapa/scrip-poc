package np.com.thapanarayan.backend.reference.internal;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.reference.api.BrokerCatalog;
import np.com.thapanarayan.backend.reference.api.BrokerView;

@Service
class BrokerService implements BrokerCatalog {

    private final BrokerRepository repository;

    BrokerService(BrokerRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BrokerView> findById(int brokerId) {
        return repository.findById(brokerId).map(BrokerService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(int brokerId) {
        return repository.existsById(brokerId);
    }

    Page<BrokerView> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(BrokerService::toView);
    }

    static BrokerView toView(BrokerEntity e) {
        return new BrokerView(e.getBrokerId(), e.getName(), e.getStatus());
    }
}
