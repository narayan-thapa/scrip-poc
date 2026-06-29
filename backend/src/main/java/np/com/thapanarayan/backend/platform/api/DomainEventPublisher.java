package np.com.thapanarayan.backend.platform.api;

/**
 * Thin seam over Spring's {@code ApplicationEventPublisher} for decoupled
 * pipeline fan-out. Stages publish through this interface; listeners react with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} so side-effects
 * (notifications, cache eviction, backtest refresh) only fire once the
 * producing transaction has committed.
 *
 * <p>Spring Batch — not these events — owns pipeline ordering and restartability;
 * events are observation/fan-out points only.</p>
 */
public interface DomainEventPublisher {

    void publish(Object event);
}
