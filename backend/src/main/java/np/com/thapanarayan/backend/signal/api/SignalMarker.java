package np.com.thapanarayan.backend.signal.api;

/** A signal rendered as a chart marker: its id (to open the reasons panel), trade date, action, score. */
public record SignalMarker(String id, String tradeDate, SignalAction action, double score) {
}
