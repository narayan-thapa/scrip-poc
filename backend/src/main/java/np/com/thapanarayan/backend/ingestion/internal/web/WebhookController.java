package np.com.thapanarayan.backend.ingestion.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.HmacVerifier;
import np.com.thapanarayan.backend.ingestion.internal.IngestionProperties;
import np.com.thapanarayan.backend.ingestion.internal.ReprocessService;
import np.com.thapanarayan.backend.ingestion.internal.web.IngestionDtos.WebhookRequest;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scraper-completion webhook. Authenticated by an HMAC signature over {@code timestamp + "." + date}
 * with a replay window (Decision C) — not a user JWT — so it is permitted in the security config but
 * rejects any unsigned/forged/replayed call. Triggers a reprocess of the named date from the archive.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
@Tag(name = "Ingestion webhook", description = "Signed scraper-completion callback")
class WebhookController {

    private final ReprocessService reprocess;
    private final IngestionProperties props;

    WebhookController(ReprocessService reprocess, IngestionProperties props) {
        this.reprocess = reprocess;
        this.props = props;
    }

    @PostMapping("/webhook")
    ResponseEntity<UUID> webhook(@RequestHeader(value = "X-Timestamp", required = false) String timestamp,
                                 @RequestHeader(value = "X-Signature", required = false) String signature,
                                 @RequestBody WebhookRequest req) {
        String secret = props.webhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Webhook not configured");
        }
        if (req == null || req.date() == null
                || !HmacVerifier.isValid(secret, timestamp, req.date().toString(), signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid signature");
        }
        return ResponseEntity.accepted().body(reprocess.reprocess(req.date(), req.date()));
    }
}
