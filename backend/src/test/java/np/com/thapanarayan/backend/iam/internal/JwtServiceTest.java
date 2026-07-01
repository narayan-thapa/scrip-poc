package np.com.thapanarayan.backend.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import np.com.thapanarayan.backend.iam.internal.config.SecurityProperties;
import np.com.thapanarayan.backend.iam.internal.domain.AppUser;
import np.com.thapanarayan.backend.iam.internal.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Reproduces the SecurityConfig JWT wiring (symmetric HMAC key) and round-trips a real token through
 * {@link JwtService}. Before the fix, encoding threw "Failed to select a JWK signing key" because the
 * encoder defaulted the header to RS256 with no matching JWK; the fix pins HS256 in the JWS header.
 */
class JwtServiceTest {

    // HS256 needs a ≥256-bit (32-byte) key — mirrors SecurityConfig's SecretKeySpec("HmacSHA256").
    private static final SecretKey KEY =
            new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
    private final SecurityProperties props = new SecurityProperties(
            "test", Duration.ofMinutes(15), Duration.ofDays(7), "nepse-platform", false, "Lax", List.of());
    private final JwtService jwtService = new JwtService(encoder, props);

    @Test
    void issuesAnHs256TokenThatTheResourceServerDecoderAccepts() {
        AppUser user = new AppUser("trader@example.com", "hash", Role.ADMIN);

        String token = jwtService.issueAccessToken(user); // pre-fix: throws "Failed to select a JWK signing key"

        assertThat(token).isNotBlank();
        Jwt decoded = decoder.decode(token); // proves the HS256 decoder accepts what we signed
        assertThat(decoded.getHeaders()).containsEntry("alg", "HS256");
        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("trader@example.com");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("nepse-platform");
    }
}
