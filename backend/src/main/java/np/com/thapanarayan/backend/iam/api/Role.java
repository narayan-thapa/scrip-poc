package np.com.thapanarayan.backend.iam.api;

/**
 * Application roles (§10.10). Hierarchy is enforced by explicit endpoint rules, not
 * implication: ADMIN endpoints require ADMIN, ANALYST endpoints accept ANALYST or
 * ADMIN, the rest require any authenticated USER.
 */
public enum Role {
    USER,
    ANALYST,
    ADMIN
}
