package np.com.thapanarayan.backend.iam.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import np.com.thapanarayan.backend.iam.internal.config.SecurityProperties;
import np.com.thapanarayan.backend.iam.internal.domain.AppUser;
import np.com.thapanarayan.backend.iam.internal.domain.RefreshToken;
import np.com.thapanarayan.backend.iam.internal.domain.Role;
import np.com.thapanarayan.backend.iam.internal.repo.AppUserRepository;
import np.com.thapanarayan.backend.iam.internal.repo.RefreshTokenRepository;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration, login, and rotating refresh-token lifecycle. Refresh tokens are opaque random
 * strings; only their SHA-256 hash is persisted. On refresh the presented token is rotated
 * (old revoked, new issued) so a stolen-and-replayed token is caught when the legitimate client
 * next refreshes.
 */
@Service
public class AuthService {

    /** Opaque tokens + access token returned to the caller (refresh value goes into a cookie). */
    public record IssuedTokens(String accessToken, long expiresInSeconds, String refreshToken) {}

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final SecurityProperties props;
    private final SecureRandom random = new SecureRandom();

    AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                JwtService jwt, SecurityProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.props = props;
    }

    @Transactional
    public AppUser register(String email, String rawPassword) {
        String normalized = email.toLowerCase();
        if (users.existsByEmail(normalized)) {
            throw ApiException.conflict("Email already registered");
        }
        AppUser user = new AppUser(normalized, passwordEncoder.encode(rawPassword), Role.USER);
        return users.save(user);
    }

    @Transactional
    public IssuedTokens login(String email, String rawPassword) {
        AppUser user = users.findByEmail(email.toLowerCase())
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid credentials"));
        return issue(user);
    }

    @Transactional
    public IssuedTokens refresh(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Missing refresh token");
        }
        String hash = sha256(presentedRefreshToken);
        RefreshToken stored = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid refresh token"));
        if (!stored.isActive(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Expired or revoked refresh token");
        }
        stored.revoke(); // rotate
        AppUser user = users.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Unknown user"));
        return issue(user);
    }

    @Transactional
    public void logout(String presentedRefreshToken) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            return; // nothing to revoke
        }
        refreshTokens.findByTokenHash(sha256(presentedRefreshToken)).ifPresent(RefreshToken::revoke);
    }

    private IssuedTokens issue(AppUser user) {
        String accessToken = jwt.issueAccessToken(user);
        String refreshValue = newOpaqueToken();
        RefreshToken token = new RefreshToken(
                user.getId(), sha256(refreshValue), OffsetDateTime.now().plus(props.refreshTtl()));
        refreshTokens.save(token);
        return new IssuedTokens(accessToken, jwt.accessTtlSeconds(), refreshValue);
    }

    public long refreshTtlSeconds() {
        return props.refreshTtl().toSeconds();
    }

    private String newOpaqueToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
