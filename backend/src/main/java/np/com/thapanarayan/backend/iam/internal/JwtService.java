package np.com.thapanarayan.backend.iam.internal;

import java.time.Instant;
import java.util.List;
import np.com.thapanarayan.backend.iam.internal.config.SecurityProperties;
import np.com.thapanarayan.backend.iam.internal.domain.AppUser;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived HMAC-signed access tokens carrying the user id, email and role. */
@Service
class JwtService {

    private final JwtEncoder encoder;
    private final SecurityProperties props;

    JwtService(JwtEncoder encoder, SecurityProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    String issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", List.of(user.getRole().name()))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }
}
