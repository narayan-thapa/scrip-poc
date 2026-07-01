package np.com.thapanarayan.backend.iam.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import np.com.thapanarayan.backend.iam.internal.AuthService;
import np.com.thapanarayan.backend.iam.internal.config.SecurityProperties;
import np.com.thapanarayan.backend.iam.internal.domain.AppUser;
import np.com.thapanarayan.backend.iam.internal.web.AuthDtos.LoginRequest;
import np.com.thapanarayan.backend.iam.internal.web.AuthDtos.RegisterRequest;
import np.com.thapanarayan.backend.iam.internal.web.AuthDtos.TokenResponse;
import np.com.thapanarayan.backend.iam.internal.web.AuthDtos.UserProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. The access token is returned in the JSON body (held in memory by the
 * SPA); the refresh token is delivered only as an httpOnly, Secure, SameSite cookie scoped to
 * {@code /api/v1/auth}, so JavaScript can never read it (security Decision B).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Registration, login, token refresh and logout")
class AuthController {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService auth;
    private final SecurityProperties props;

    AuthController(AuthService auth, SecurityProperties props) {
        this.auth = auth;
        this.props = props;
    }

    @PostMapping("/register")
    ResponseEntity<UserProfile> register(@Valid @RequestBody RegisterRequest req) {
        AppUser user = auth.register(req.email(), req.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserProfile(user.getId().toString(), user.getEmail(), user.getRole().name()));
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthService.IssuedTokens tokens = auth.login(req.email(), req.password());
        return tokenResponse(tokens);
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return tokenResponse(auth.refresh(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<TokenResponse> tokenResponse(AuthService.IssuedTokens tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(tokens.refreshToken()).toString())
                .body(TokenResponse.bearer(tokens.accessToken(), tokens.expiresInSeconds()));
    }

    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite(props.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(auth.refreshTtlSeconds())
                .build();
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(props.cookieSecure())
                .sameSite(props.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
