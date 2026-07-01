package np.com.thapanarayan.backend.platform.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the S3/MinIO object store that archives every raw scraped CSV (immutable,
 * content-hashed) so any trading day can be deterministically reprocessed. Credentials come from
 * the environment / secret manager — never hard-coded.
 */
@ConfigurationProperties(prefix = "platform.object-store")
public record ObjectStoreProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        boolean enabled) {

    public ObjectStoreProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "nepse-raw";
        }
    }
}
