package np.com.thapanarayan.backend.platform.api.error;

/**
 * Stable, machine-readable error codes surfaced in {@link ApiError#code()}.
 * Clients branch on these, not on the human-readable message. New modules add codes here.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    NOT_FOUND,
    CONFLICT,
    UNAUTHORIZED,
    FORBIDDEN,
    RATE_LIMITED,
    INTERNAL_ERROR
}
