package np.com.thapanarayan.backend.ingestion.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the daily pipeline scheduler (§10.11) and its tunables. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(OrchestrationProperties.class)
class OrchestrationConfig {
}
