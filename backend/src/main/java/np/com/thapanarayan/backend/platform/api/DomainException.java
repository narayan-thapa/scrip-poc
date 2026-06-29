package np.com.thapanarayan.backend.platform.api;

/**
 * Base type for expected, client-facing domain errors. Carries a stable
 * machine-readable {@code code} alongside the human message.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
