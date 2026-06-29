package np.com.thapanarayan.backend.iam.internal;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 6 configuration (§10.10, §11): a stateless JWT resource server.
 *
 * <ul>
 *   <li><b>Passwords</b>: Argon2id.</li>
 *   <li><b>Access token</b>: HS256 JWT, sent as a Bearer header (never a cookie), so
 *       the main API is not exposed to CSRF.</li>
 *   <li><b>CSRF</b>: Spring's CSRF filter is disabled because no ambient cookie
 *       authenticates the API; the only cookie (the refresh token) is scoped to
 *       {@code /api/v1/auth} and marked {@code SameSite=Strict}, which is what
 *       defends the refresh/logout endpoints. (Not removed for convenience — the
 *       control is provided by SameSite + Bearer-header auth.)</li>
 *   <li><b>CORS</b>: locked to the single Angular origin, credentials allowed.</li>
 *   <li><b>Secret</b>: from {@code NEPSE_SECURITY_JWT_SECRET}; an ephemeral random
 *       key is used only when unset (dev), and a too-short configured secret fails
 *       closed at startup.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final int MIN_SECRET_BYTES = 32; // HS256 requires a >= 256-bit key

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        String configured = properties.jwtSecret();
        if (configured != null && !configured.isBlank()) {
            byte[] bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "nepse.security.jwt-secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        }
        byte[] ephemeral = new byte[MIN_SECRET_BYTES];
        new SecureRandom().nextBytes(ephemeral);
        log.warn("nepse.security.jwt-secret is not set — using an EPHEMERAL key. "
                + "Tokens will not survive a restart. Set NEPSE_SECURITY_JWT_SECRET in production.");
        return new SecretKeySpec(ephemeral, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.corsAllowedOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-None-Match"));
        config.setExposedHeaders(List.of("ETag"));
        config.setAllowCredentials(true); // required so the browser sends the refresh cookie
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter,
            AuthRateLimitFilter rateLimitFilter) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth + health are open. The /ws handshake is open; the STOMP
                        // CONNECT frame itself is JWT-authenticated by a channel interceptor.
                        .requestMatchers("/api/v1/auth/**", "/ws/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        // Public read-only market/analytics data.
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/instruments/**", "/api/v1/sectors", "/api/v1/brokers/**",
                                "/api/v1/calendar/**", "/api/v1/market/**", "/api/v1/indicators/**",
                                "/api/v1/signals/**", "/api/v1/charts/**", "/api/v1/strategies/**")
                        .permitAll()
                        // Admin: ingestion, pipeline orchestration, signal (re)generation.
                        .requestMatchers("/api/v1/ingestion/**", "/api/v1/system/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/signals/generate",
                                "/api/v1/signals/*/generate")
                        .hasRole("ADMIN")
                        // Analyst/Admin: strategy tuning + backtest runs.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/strategies/**").hasAnyRole("ANALYST", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/backtests").hasAnyRole("ANALYST", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/backtests/**").hasAnyRole("ANALYST", "ADMIN")
                        // Everything else (watchlists, alerts, users/me, backtest reads) needs a login.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .addFilterBefore(rateLimitFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
