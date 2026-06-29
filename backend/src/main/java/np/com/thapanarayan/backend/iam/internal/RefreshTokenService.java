package np.com.thapanarayan.backend.iam.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, verifies, rotates, and revokes opaque refresh tokens. The raw 256-bit
 * token is returned to the caller (to set as an httpOnly cookie) but only its
 * SHA-256 hash is persisted, so a database compromise cannot reissue access tokens.
 * Rotation revokes the consumed token, limiting replay of a leaked refresh token.
 */
@Service
class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final SecureRandom random = new SecureRandom();

    RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    record Issued(String rawToken, Instant expiresAt) {
    }

    @Transactional
    Issued issue(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(properties.refreshTokenTtlDays(), ChronoUnit.DAYS);

        RefreshTokenEntity e = new RefreshTokenEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setTokenHash(hash(raw));
        e.setExpiresAt(expiresAt);
        e.setRevoked(false);
        e.setCreatedAt(Instant.now());
        repository.save(e);
        return new Issued(raw, expiresAt);
    }

    /** Verifies a presented refresh token, returning the owning user id if valid. */
    @Transactional(readOnly = true)
    Optional<UUID> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash(rawToken))
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .map(RefreshTokenEntity::getUserId);
    }

    @Transactional
    void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional
    void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId);
    }

    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
