package np.com.thapanarayan.backend.platform.api;

import java.time.Instant;
import java.util.List;

/**
 * Canonical error body produced by {@code GlobalExceptionHandler}. Never leaks
 * stack traces or internal types to the client.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<String> details) {
}
