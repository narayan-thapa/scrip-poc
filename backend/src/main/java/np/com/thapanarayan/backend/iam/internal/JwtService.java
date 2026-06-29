package np.com.thapanarayan.backend.iam.internal;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived HS256 access tokens. The subject is the user id and a {@code
 * role} claim drives authorization; nothing sensitive (no password, no email) is put
 * in the token. Verification is handled by the resource-server {@code JwtDecoder}.
 */
@Service
class JwtService {

    static final String ISSUER = "nepse-signal-platform";

    private final JwtEncoder encoder;
    private final SecurityProperties properties;

    JwtService(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    String issueAccessToken(AppUserEntity user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.accessTokenTtlSeconds()))
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    int accessTokenTtlSeconds() {
        return properties.accessTokenTtlSeconds();
    }
}
