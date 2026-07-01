package np.com.thapanarayan.backend.ingestion.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacVerifierTest {

    private static final String SECRET = "webhook-shared-secret";

    private static String sign(String timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void acceptsValidFreshSignature() {
        String ts = Instant.now().toString();
        assertThat(HmacVerifier.isValid(SECRET, ts, "2026-06-03", sign(ts, "2026-06-03"))).isTrue();
    }

    @Test
    void rejectsTamperedPayload() {
        String ts = Instant.now().toString();
        String sig = sign(ts, "2026-06-03");
        assertThat(HmacVerifier.isValid(SECRET, ts, "2026-06-04", sig)).isFalse();
    }

    @Test
    void rejectsWrongSecret() {
        String ts = Instant.now().toString();
        assertThat(HmacVerifier.isValid("other-secret", ts, "2026-06-03", sign(ts, "2026-06-03"))).isFalse();
    }

    @Test
    void rejectsStaleTimestampOutsideWindow() {
        String ts = Instant.now().minusSeconds(3600).toString();
        assertThat(HmacVerifier.isValid(SECRET, ts, "2026-06-03", sign(ts, "2026-06-03"))).isFalse();
    }
}
