package np.com.thapanarayan.backend.ingestion.internal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.security.MessageDigest;

import java.util.HexFormat;

import np.com.thapanarayan.backend.platform.api.DomainException;

/**
 * Verifies scraper webhook calls with an HMAC-SHA256 signature over
 * {@code timestamp + "." + body}, plus a replay window on the timestamp. A shared
 * secret in a query param would be forgeable/replayable; this is not. The secret
 * lives in config (env/secret manager), never in code.
 */
final class WebhookVerifier {

    private WebhookVerifier() {
    }

    static void verify(String secret, long replaySeconds, String signatureHeader,
            String timestampHeader, byte[] body) {
        if (secret == null || secret.isBlank()) {
            throw new DomainException("WEBHOOK_DISABLED", "webhook secret is not configured");
        }
        if (signatureHeader == null || timestampHeader == null) {
            throw new DomainException("WEBHOOK_UNSIGNED", "missing signature or timestamp header");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            throw new DomainException("WEBHOOK_BAD_TIMESTAMP", "timestamp must be epoch seconds");
        }
        long skew = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (skew > replaySeconds) {
            throw new DomainException("WEBHOOK_REPLAY", "timestamp outside the replay window");
        }
        String expected = hmacHex(secret, timestamp + "." + new String(body, StandardCharsets.UTF_8));
        if (!constantTimeEquals(expected, signatureHeader.trim())) {
            throw new DomainException("WEBHOOK_BAD_SIGNATURE", "signature mismatch");
        }
    }

    private static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
