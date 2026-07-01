package np.com.thapanarayan.backend.signal.api;

/**
 * A first-class, structured explanation for a strategy's vote (§F5). Rendered to text AND available
 * as data, so the UI can show "why" and every signal is auditable.
 */
public record Reason(
        String strategyId,
        String indicator,
        String condition,
        String observedValue,
        String threshold,
        double contribution,
        String narrative) {
}
