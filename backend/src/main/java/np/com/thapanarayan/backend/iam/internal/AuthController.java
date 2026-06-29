package np.com.thapanarayan.backend.iam.internal;

import java.time.Duration;
import java.time.Instant;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.iam.api.UserView;
import np.com.thapanarayan.backend.iam.internal.AuthDtos.LoginRequest;
import np.com.thapanarayan.backend.iam.internal.AuthDtos.RegisterRequest;
import np.com.thapanarayan.backend.iam.internal.AuthDtos.TokenResponse;
import np.com.thapanarayan.backend.platform.api.UnauthorizedException;

/**
 * Auth endpoints (§9). The access token is returned in the body (held in memory by
 * the SPA); the refresh token is set as an httpOnly + Secure + SameSite=Strict cookie
 * scoped to {@code /api/v1/auth}, so it is never readable by JS and only sent to the
 * refresh/logout endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    static final String REFRESH_COOKIE = "refresh_token";

    private final AuthService auth;
    private final SecurityProperties properties;

    AuthController(AuthService auth, SecurityProperties properties) {
        this.auth = auth;
        this.properties = properties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserView register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.email(), request.password());
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(auth.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new UnauthorizedException("NO_SESSION", "No active session");
        }
        return withRefreshCookie(auth.refresh(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        auth.logout(refreshToken);
        ResponseCookie cleared = baseCookie("").maxAge(0).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(AuthService.AuthResult result) {
        ResponseCookie cookie = baseCookie(result.refresh().rawToken())
                .maxAge(Duration.between(Instant.now(), result.refresh().expiresAt()))
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(TokenResponse.bearer(result.accessToken(), result.expiresInSeconds(), result.user()));
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/api/v1/auth");
    }
}
