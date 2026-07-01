package np.com.thapanarayan.backend.iam.internal.repo;

import java.util.Optional;
import java.util.UUID;
import np.com.thapanarayan.backend.iam.internal.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);
}
