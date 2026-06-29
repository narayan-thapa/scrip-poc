package np.com.thapanarayan.backend.iam.internal;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import np.com.thapanarayan.backend.iam.api.CurrentUserProvider;
import np.com.thapanarayan.backend.iam.api.Role;
import np.com.thapanarayan.backend.platform.api.UnauthorizedException;

/** Resolves the authenticated principal from the validated JWT in the security context. */
@Component
class CurrentUserProviderImpl implements CurrentUserProvider {

    @Override
    public UUID currentUserId() {
        return UUID.fromString(jwt().getSubject());
    }

    @Override
    public Role currentRole() {
        String role = jwt().getClaimAsString("role");
        return role == null ? Role.USER : Role.valueOf(role);
    }

    @Override
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt;
    }

    private Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt token) {
            return token;
        }
        throw new UnauthorizedException("UNAUTHENTICATED", "Authentication required");
    }
}
