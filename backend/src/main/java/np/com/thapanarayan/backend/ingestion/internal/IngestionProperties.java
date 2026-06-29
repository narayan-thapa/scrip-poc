package np.com.thapanarayan.backend.ingestion.internal;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Ingestion guardrails and tunables. Caps treat uploads as hostile: bounded file
 * size, bounded batch file-count and total size.
 */
@ConfigurationProperties(prefix = "nepse.ingestion")
record IngestionProperties(
        @DefaultValue("52428800") long maxFileBytes,        // 50 MiB per file
        @DefaultValue("400") int maxBatchFiles,             // ~ one+ trading year per backfill
        @DefaultValue("1073741824") long maxBatchBytes,     // 1 GiB per batch request
        @DefaultValue("0.5") BigDecimal amountTolerance,    // |amount - qty*price|
        @DefaultValue("500") int chunkSize,                 // Spring Batch chunk size
        @DefaultValue("./data/raw-archive") String archiveRoot,
        @DefaultValue("false") boolean rejectNonTradingDays,
        @DefaultValue("") String webhookSecret,             // HMAC key; empty disables webhook
        @DefaultValue("300") long webhookReplaySeconds) {
}
