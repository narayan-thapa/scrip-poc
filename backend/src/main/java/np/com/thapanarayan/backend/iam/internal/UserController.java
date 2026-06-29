package np.com.thapanarayan.backend.iam.internal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.iam.api.CurrentUserProvider;
import np.com.thapanarayan.backend.iam.api.UserView;

/** The authenticated user's own profile (§9). */
@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final CurrentUserProvider currentUser;
    private final AuthService auth;

    UserController(CurrentUserProvider currentUser, AuthService auth) {
        this.currentUser = currentUser;
        this.auth = auth;
    }

    @GetMapping("/me")
    UserView me() {
        return auth.currentUser(currentUser.currentUserId());
    }
}
