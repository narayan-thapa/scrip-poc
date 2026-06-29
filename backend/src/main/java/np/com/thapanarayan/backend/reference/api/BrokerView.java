package np.com.thapanarayan.backend.reference.api;

/** Read model for a NEPSE broker. */
public record BrokerView(
        int brokerId,
        String name,
        BrokerStatus status) {
}
