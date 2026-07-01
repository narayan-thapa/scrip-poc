package np.com.thapanarayan.backend.platform.internal.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the MinIO/S3 client only when an endpoint is configured ({@code platform.object-store.enabled=true}),
 * so the app boots locally without an object store. Ingestion (Phase 2) uses this to archive raw CSVs.
 */
@Configuration
class ObjectStoreConfig {

    @Bean
    @ConditionalOnProperty(prefix = "platform.object-store", name = "enabled", havingValue = "true")
    MinioClient minioClient(ObjectStoreProperties props) {
        return MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .build();
    }
}
