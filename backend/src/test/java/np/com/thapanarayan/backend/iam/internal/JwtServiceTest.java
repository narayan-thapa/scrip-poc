package np.com.thapanarayan.backend.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import np.com.thapanarayan.backend.iam.api.Role;

/** The HS256 access-token path: issuance, claims, and signature verification. */
class JwtServiceTest {

    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static SecretKey key(byte[] bytes) {
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    private static JwtService serviceWith(SecretKey signingKey) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(signingKey));
        SecurityProperties props = new SecurityProperties(null, 900, 14, null, true, 10);
        return new JwtService(encoder, props);
    }

    private static AppUserEntity admin() {
        AppUserEntity u = new AppUserEntity();
        u.setId(UUID.randomUUID());
        u.setEmail("a@b.com");
        u.setRole(Role.ADMIN);
        u.setEnabled(true);
        u.setCreatedAt(Instant.now());
        return u;
    }

    @Test
    void issuesAVerifiableTokenCarryingSubjectAndRole() {
        SecretKey signingKey = key(SECRET);
        AppUserEntity user = admin();

        String token = serviceWith(signingKey).issueAccessToken(user);

        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey).macAlgorithm(MacAlgorithm.HS256).build();
        var jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(JwtService.ISSUER);
    }

    @Test
    void rejectsATokenSignedWithADifferentKey() {
        String token = serviceWith(key(SECRET)).issueAccessToken(admin());

        byte[] otherSecret = "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8);
        JwtDecoder foreignDecoder =
                NimbusJwtDecoder.withSecretKey(key(otherSecret)).macAlgorithm(MacAlgorithm.HS256).build();

        assertThatThrownBy(() -> foreignDecoder.decode(token)).isInstanceOf(Exception.class);
    }
}
