package np.com.thapanarayan.backend.ingestion.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ingestion module configuration: enables {@link IngestionProperties} and a
 * virtual-thread executor used to run batch jobs off the request thread so
 * uploads return {@code 202} immediately.
 */
@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
class IngestionConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService ingestionExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
