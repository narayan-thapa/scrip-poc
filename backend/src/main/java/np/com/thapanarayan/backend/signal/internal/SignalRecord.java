package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalAction;

/** A persisted signal: action, score, confidence, top reasons and the full vote vector. */
public record SignalRecord(
        UUID id,
        String symbol,
        LocalDate tradeDate,
        SignalAction action,
        double score,
        double confidence,
        List<Reason> reasons,
        List<ConfluenceResult.VoteEntry> votes,
        OffsetDateTime generatedAt) {
}
