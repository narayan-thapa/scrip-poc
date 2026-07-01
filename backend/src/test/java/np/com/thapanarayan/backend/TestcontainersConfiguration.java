package np.com.thapanarayan.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spins up a real PostgreSQL for {@code @SpringBootTest} via Testcontainers. {@link ServiceConnection}
 * auto-wires the datasource, so Flyway runs the real migrations against a real Postgres on each run.
 *
 * <p>Requires Docker. ArchUnit and the Ta4j smoke test are plain unit tests and do NOT need Docker,
 * so the build's boundary/indicator gates run anywhere; only the integration slice needs a daemon.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }
}
