package np.com.thapanarayan.backend.iam.internal.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response payloads for the auth + user endpoints. */
final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 10, max = 100) String password) {}

    record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    /** Access token returned in the body; the refresh token is set as an httpOnly cookie. */
    record TokenResponse(String accessToken, String tokenType, long expiresIn) {
        static TokenResponse bearer(String accessToken, long expiresIn) {
            return new TokenResponse(accessToken, "Bearer", expiresIn);
        }
    }

    record UserProfile(String id, String email, String role) {}

    /** Phase 1 profile update: password change (current + new). */
    record UpdateProfileRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 100) String newPassword) {}
}
