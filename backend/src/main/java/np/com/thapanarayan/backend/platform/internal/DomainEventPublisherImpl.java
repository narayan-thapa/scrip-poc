package np.com.thapanarayan.backend.platform.internal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.platform.api.DomainEventPublisher;

/**
 * Delegates to Spring's {@link ApplicationEventPublisher}, wrapped in a
 * transaction so there is always a commit boundary for
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} listeners to bind to:
 * if the caller already has a transaction this joins it; otherwise a short one is
 * created and committed here. Listeners then run after that commit.
 */
@Component
class DomainEventPublisherImpl implements DomainEventPublisher {

    private final ApplicationEventPublisher delegate;

    DomainEventPublisherImpl(ApplicationEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public void publish(Object event) {
        delegate.publishEvent(event);
    }
}
