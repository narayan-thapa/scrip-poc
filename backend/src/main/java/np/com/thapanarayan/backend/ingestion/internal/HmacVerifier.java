package np.com.thapanarayan.backend.ingestion.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.HexFormat;

/**
 * HMAC-SHA256 verification for the scraper webhook (Decision C): the signature covers
 * {@code timestamp + "." + payload}, and a replay window bounds clock skew. A shared secret alone in
 * a query param is not enough — this prevents forged/ replayed ingestion triggers.
 */
public final class HmacVerifier {

    private static final Duration WINDOW = Duration.ofMinutes(5);

    private HmacVerifier() {
    }

    public static boolean isValid(String secret, String timestamp, String payload, String providedSignatureHex) {
        if (secret == null || secret.isBlank() || timestamp == null || providedSignatureHex == null) {
            return false;
        }
        if (!withinWindow(timestamp)) {
            return false;
        }
        String expected = hmacHex(secret, timestamp + "." + payload);
        return constantTimeEquals(expected, providedSignatureHex);
    }

    private static boolean withinWindow(String timestamp) {
        try {
            Instant ts = Instant.parse(timestamp);
            Duration skew = Duration.between(ts, Instant.now()).abs();
            return skew.compareTo(WINDOW) <= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
