package np.com.thapanarayan.backend.platform.api.error;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Canonical error envelope returned by every endpoint on failure.
 *
 * <p>Kept deliberately small and stable: a machine-readable {@code code}, a human {@code message},
 * the HTTP {@code status}, the request {@code path}, a server {@code timestamp}, and an optional
 * list of field-level {@link FieldError}s for validation failures. This is part of the
 * {@code platform} module's published API so every other module reports errors the same way.
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        List<FieldError> fieldErrors) {

    public record FieldError(String field, String message) {}

    public static ApiError of(OffsetDateTime timestamp, int status, String code, String message, String path) {
        return new ApiError(timestamp, status, code, message, path, List.of());
    }
}
