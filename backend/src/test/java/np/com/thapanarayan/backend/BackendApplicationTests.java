package np.com.thapanarayan.backend;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Integration smoke test: boots the full Spring context against a real Postgres (Testcontainers)
 * and runs Flyway. Verifies the application wires up end-to-end. Requires Docker — tagged
 * {@code integration} and excluded from the default {@code mvn test} run (see surefire config);
 * CI runs it on a Docker-enabled job.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
