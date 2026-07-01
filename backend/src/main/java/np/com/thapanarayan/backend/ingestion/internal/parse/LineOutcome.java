package np.com.thapanarayan.backend.ingestion.internal.parse;

import np.com.thapanarayan.backend.ingestion.internal.domain.FloorsheetTrade;
import np.com.thapanarayan.backend.ingestion.internal.domain.RejectionReason;

/** Outcome of parsing one raw line: a valid trade, a quarantined row, or an ignorable line. */
public sealed interface LineOutcome permits LineOutcome.Accepted, LineOutcome.Rejected, LineOutcome.Ignored {

    record Accepted(FloorsheetTrade trade) implements LineOutcome {}

    record Rejected(String rawLine, RejectionReason reason, String detail) implements LineOutcome {}

    /** Blank line or header row — counts toward neither accepted nor rejected. */
    record Ignored() implements LineOutcome {
        private static final Ignored INSTANCE = new Ignored();

        static Ignored instance() {
            return INSTANCE;
        }
    }
}
