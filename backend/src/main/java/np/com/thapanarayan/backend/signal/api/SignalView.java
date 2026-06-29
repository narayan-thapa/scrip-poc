package np.com.thapanarayan.backend.signal.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The published daily signal for a (symbol, trade_date): an action with the score
 * that produced it, the full weighted vote breakdown, the top contributing reasons,
 * and a human-readable narrative.
 *
 * @param score      confluence score ∈ [-100, +100]
 * @param barCount   number of history bars the strategies evaluated over
 * @param votes      the full per-strategy breakdown (including dissenters)
 * @param topReasons the highest-magnitude reasons across all strategies, for a quick "why"
 */
public record SignalView(
        UUID id,
        String symbol,
        LocalDate tradeDate,
        SignalAction action,
        BigDecimal score,
        int barCount,
        List<StrategyVoteView> votes,
        List<ReasonView> topReasons,
        String narrative,
        Instant computedAt) {
}
