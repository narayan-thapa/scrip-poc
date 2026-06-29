package np.com.thapanarayan.backend.platform.api;

/** Thrown when a requested resource does not exist. Maps to HTTP 404. */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
