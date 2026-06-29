package np.com.thapanarayan.backend.signal.internal;

import java.math.BigDecimal;
import java.util.List;

import np.com.thapanarayan.backend.signal.api.SignalAction;

/**
 * The outcome of the confluence scorer (§6.4): the blended score, the action it
 * maps to, and the full per-strategy breakdown (including dissenters and abstainers,
 * so the UI can explain the verdict).
 */
record ConfluenceResult(BigDecimal score, SignalAction action, List<ScoredVote> votes) {
}
