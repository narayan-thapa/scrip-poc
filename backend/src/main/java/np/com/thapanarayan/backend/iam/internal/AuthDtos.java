package np.com.thapanarayan.backend.iam.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import np.com.thapanarayan.backend.iam.api.UserView;

/** Request/response payloads for the auth endpoints. */
final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password) {
    }

    record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    /**
     * Login/refresh response. The access token is returned in the body for the client
     * to hold in memory; the refresh token is NOT here — it is set as an httpOnly cookie.
     */
    record TokenResponse(String accessToken, String tokenType, int expiresInSeconds, UserView user) {

        static TokenResponse bearer(String accessToken, int expiresInSeconds, UserView user) {
            return new TokenResponse(accessToken, "Bearer", expiresInSeconds, user);
        }
    }
}
