package np.com.thapanarayan.backend.ingestion.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Async} (so {@link IngestionPipeline#runBatchAsync} returns immediately) and
 * {@code @Scheduled} (for the 15:01 NPT daily run). With {@code spring.threads.virtual.enabled=true},
 * async work runs on Boot's virtual-thread executor.
 */
@Configuration
@EnableAsync
@EnableScheduling
class IngestionAsyncConfig {
}
