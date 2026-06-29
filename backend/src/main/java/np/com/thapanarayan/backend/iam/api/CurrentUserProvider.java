package np.com.thapanarayan.backend.iam.api;

import java.util.UUID;

/**
 * Published access to the authenticated principal, resolved from the validated JWT.
 * User-feature modules (watchlist, notification) depend on this rather than reading
 * the Spring {@code SecurityContext} or the iam {@code internal} package directly.
 */
public interface CurrentUserProvider {

    /** The authenticated user's id (JWT subject). */
    UUID currentUserId();

    /** The authenticated user's role. */
    Role currentRole();

    boolean isAuthenticated();
}
