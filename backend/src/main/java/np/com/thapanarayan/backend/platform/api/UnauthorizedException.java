package np.com.thapanarayan.backend.platform.api;

/** Thrown when authentication fails or is missing. Maps to HTTP 401. */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
