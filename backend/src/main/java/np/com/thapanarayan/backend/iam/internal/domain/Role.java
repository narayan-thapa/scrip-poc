package np.com.thapanarayan.backend.iam.internal.domain;

/** Application roles. ADMIN ⊃ ANALYST ⊃ USER in capability, but stored as a single role per user. */
public enum Role {
    USER,
    ANALYST,
    ADMIN
}
