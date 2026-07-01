package np.com.thapanarayan.backend.signal.api;

/** Published lightweight signal (id + action + score) for cross-module annotation + notifications. */
public record SignalView(String id, String symbol, SignalAction action, double score) {
}
