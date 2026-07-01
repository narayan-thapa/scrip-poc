package np.com.thapanarayan.backend.iam.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import np.com.thapanarayan.backend.iam.internal.AuthService;
import np.com.thapanarayan.backend.iam.internal.JwtService;
import np.com.thapanarayan.backend.iam.internal.config.SecurityProperties;
import np.com.thapanarayan.backend.iam.internal.domain.AppUser;
import np.com.thapanarayan.backend.iam.internal.domain.RefreshToken;
import np.com.thapanarayan.backend.iam.internal.domain.Role;
import np.com.thapanarayan.backend.iam.internal.repo.AppUserRepository;
import np.com.thapanarayan.backend.iam.internal.repo.RefreshTokenRepository;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AppUserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock JwtService jwt;

    @SuppressWarnings("deprecation")
    PasswordEncoder encoder = NoOpPasswordEncoder.getInstance(); // plaintext compare keeps the test simple

    SecurityProperties props = new SecurityProperties(
            "test-secret", Duration.ofMinutes(15), Duration.ofDays(7), "test", false, "Lax", List.of());

    AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, refreshTokens, encoder, jwt, props);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("a@b.com")).thenReturn(true);
        assertThatThrownBy(() -> auth.register("A@B.com", "password12"))
                .isInstanceOf(ApiException.class);
        verify(users, never()).save(any());
    }

    @Test
    void loginWithWrongPasswordIsUnauthorized() {
        AppUser user = new AppUser("a@b.com", "rightpass", Role.USER);
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> auth.login("a@b.com", "wrongpass"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void loginIssuesAccessAndRefreshTokens() {
        AppUser user = new AppUser("a@b.com", "secretpass", Role.USER);
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(jwt.issueAccessToken(user)).thenReturn("access-jwt");
        when(jwt.accessTtlSeconds()).thenReturn(900L);

        AuthService.IssuedTokens tokens = auth.login("a@b.com", "secretpass");

        assertThat(tokens.accessToken()).isEqualTo("access-jwt");
        assertThat(tokens.refreshToken()).isNotBlank();
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void refreshRotatesTokenRevokingTheOldOne() {
        AppUser user = new AppUser("a@b.com", "secretpass", Role.USER);
        // A stored, active refresh token whose hash we don't need to know (service hashes the input).
        RefreshToken existing = new RefreshToken(user.getId(), "anyhash", OffsetDateTime.now().plusDays(1));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(existing));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwt.issueAccessToken(user)).thenReturn("new-access");
        when(jwt.accessTtlSeconds()).thenReturn(900L);

        AuthService.IssuedTokens rotated = auth.refresh("presented-refresh-value");

        assertThat(existing.isRevoked()).as("old token rotated out").isTrue();
        assertThat(rotated.accessToken()).isEqualTo("new-access");
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(user.getId());
    }

    @Test
    void refreshRejectsExpiredToken() {
        AppUser user = new AppUser("a@b.com", "secretpass", Role.USER);
        RefreshToken expired = new RefreshToken(user.getId(), "h", OffsetDateTime.now().minusSeconds(1));
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> auth.refresh("x")).isInstanceOf(ApiException.class);
        verify(refreshTokens, never()).save(any());
    }
}
