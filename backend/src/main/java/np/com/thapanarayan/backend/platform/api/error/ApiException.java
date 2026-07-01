package np.com.thapanarayan.backend.platform.api.error;

import org.springframework.http.HttpStatus;

/**
 * Base application exception carrying an HTTP {@link HttpStatus} and a stable {@link ErrorCode}.
 * Modules throw subclasses (or this directly) and the platform's exception handler renders an
 * {@link ApiError}. This keeps controllers free of try/catch and error-formatting boilerplate.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;

    public ApiException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }
}
