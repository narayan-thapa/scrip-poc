package np.com.thapanarayan.backend.iam.internal;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.iam.api.Role;
import np.com.thapanarayan.backend.iam.api.UserView;
import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.UnauthorizedException;

/**
 * Registration and the token lifecycle (§10.10). Passwords are Argon2id-hashed;
 * credential checks return a single generic error (no user enumeration) and run the
 * hash even for unknown emails to equalize timing. Login/refresh mint a short-lived
 * access JWT plus a rotating opaque refresh token.
 */
@Service
class AuthService {

    // A valid Argon2id hash of a random value, used to equalize timing when the email is unknown.
    private static final String DUMMY_HASH =
            "$argon2id$v=19$m=16384,t=2,p=1$c29tZXNhbHR2YWx1ZQ$3g2Z3JhdmVsbGluZ3RpbWluZ2d1YXJk";

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final NepseClock clock;

    AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
            RefreshTokenService refreshTokens, NepseClock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    record AuthResult(String accessToken, int expiresInSeconds, RefreshTokenService.Issued refresh, UserView user) {
    }

    @Transactional
    public UserView register(String email, String rawPassword) {
        String normalized = normalize(email);
        if (users.existsByEmail(normalized)) {
            throw new DomainException("EMAIL_TAKEN", "Email is already registered");
        }
        AppUserEntity user = new AppUserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(normalized);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now(clock.clock()));
        return UserMapper.toView(users.save(user));
    }

    @Transactional
    public AuthResult login(String email, String rawPassword) {
        AppUserEntity user = authenticate(email, rawPassword);
        return issue(user);
    }

    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        UUID userId = refreshTokens.verify(rawRefreshToken)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH", "Invalid or expired session"));
        refreshTokens.revoke(rawRefreshToken); // rotate: the presented token is single-use
        AppUserEntity user = users.findById(userId)
                .filter(AppUserEntity::isEnabled)
                .orElseThrow(() -> new UnauthorizedException("INVALID_REFRESH", "Invalid or expired session"));
        return issue(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public UserView currentUser(UUID userId) {
        return users.findById(userId).map(UserMapper::toView)
                .orElseThrow(() -> new UnauthorizedException("UNKNOWN_USER", "User no longer exists"));
    }

    private AuthResult issue(AppUserEntity user) {
        String accessToken = jwtService.issueAccessToken(user);
        RefreshTokenService.Issued refresh = refreshTokens.issue(user.getId());
        return new AuthResult(accessToken, jwtService.accessTokenTtlSeconds(), refresh, UserMapper.toView(user));
    }

    private AppUserEntity authenticate(String email, String rawPassword) {
        AppUserEntity user = users.findByEmail(normalize(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, DUMMY_HASH); // equalize timing; result ignored
            throw new UnauthorizedException("BAD_CREDENTIALS", "Invalid email or password");
        }
        if (!user.isEnabled() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new UnauthorizedException("BAD_CREDENTIALS", "Invalid email or password");
        }
        return user;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
