package np.com.thapanarayan.backend.iam.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import np.com.thapanarayan.backend.iam.internal.UserService;
import np.com.thapanarayan.backend.iam.internal.domain.AppUser;
import np.com.thapanarayan.backend.iam.internal.web.AuthDtos.UpdateProfileRequest;
import np.com.thapanarayan.backend.iam.internal.web.AuthDtos.UserProfile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current-user profile. Authenticated via the access-token JWT (subject = user id). */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Current-user profile")
class UserController {

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        AppUser user = users.require(UUID.fromString(jwt.getSubject()));
        return new UserProfile(user.getId().toString(), user.getEmail(), user.getRole().name());
    }

    @PatchMapping("/me")
    UserProfile updateMe(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest req) {
        UUID id = UUID.fromString(jwt.getSubject());
        users.changePassword(id, req.currentPassword(), req.newPassword());
        AppUser user = users.require(id);
        return new UserProfile(user.getId().toString(), user.getEmail(), user.getRole().name());
    }
}
