package np.com.thapanarayan.backend.iam.api;

import java.time.Instant;
import java.util.UUID;

/** Public profile of an application user (never carries the password hash). */
public record UserView(UUID id, String email, Role role, Instant createdAt) {
}
