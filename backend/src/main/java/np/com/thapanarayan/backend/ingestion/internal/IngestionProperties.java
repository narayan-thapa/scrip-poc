package np.com.thapanarayan.backend.ingestion.internal;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for ingestion: the Amount cross-check tolerance and the upload guardrails (Decision D). */
@ConfigurationProperties(prefix = "ingestion")
public record IngestionProperties(
        BigDecimal amountTolerance,
        Integer maxFilesPerBatch,
        Long maxFileBytes,
        Long maxBatchBytes,
        String webhookSecret) {

    public IngestionProperties {
        if (amountTolerance == null) {
            amountTolerance = new BigDecimal("0.5");
        }
        if (maxFilesPerBatch == null) {
            maxFilesPerBatch = 400;
        }
        if (maxFileBytes == null) {
            maxFileBytes = 100L * 1024 * 1024; // 100 MB
        }
        if (maxBatchBytes == null) {
            maxBatchBytes = 1024L * 1024 * 1024; // 1 GB
        }
    }
}
