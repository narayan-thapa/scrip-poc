package np.com.thapanarayan.backend.iam.internal;

import np.com.thapanarayan.backend.iam.api.UserView;

/** Entity → published view (drops the password hash). */
final class UserMapper {

    private UserMapper() {
    }

    static UserView toView(AppUserEntity e) {
        return new UserView(e.getId(), e.getEmail(), e.getRole(), e.getCreatedAt());
    }
}
