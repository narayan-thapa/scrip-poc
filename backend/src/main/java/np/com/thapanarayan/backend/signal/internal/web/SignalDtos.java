package np.com.thapanarayan.backend.signal.internal.web;

import java.util.List;
import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.internal.ConfluenceResult.VoteEntry;
import np.com.thapanarayan.backend.signal.internal.SignalRecord;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfig;

/** Request/response payloads for the signal + strategy API. */
final class SignalDtos {

    private SignalDtos() {
    }

    record SignalSummary(String id, String symbol, String tradeDate, String action, double score, double confidence) {
        static SignalSummary from(SignalRecord s) {
            return new SignalSummary(s.id().toString(), s.symbol(), s.tradeDate().toString(),
                    s.action().name(), s.score(), s.confidence());
        }
    }

    record SignalDetail(String id, String symbol, String tradeDate, String action, double score, double confidence,
                        List<Reason> reasons, List<VoteEntry> votes) {
        static SignalDetail from(SignalRecord s) {
            return new SignalDetail(s.id().toString(), s.symbol(), s.tradeDate().toString(), s.action().name(),
                    s.score(), s.confidence(), s.reasons(), s.votes());
        }
    }

    record GenerateRequest(String date) {}

    record StrategyConfigDto(String id, String name, String type, double weight, boolean enabled) {
        static StrategyConfigDto from(StrategyConfig c) {
            return new StrategyConfigDto(c.getId(), c.getName(), c.getType(), c.getWeight().doubleValue(), c.isEnabled());
        }
    }

    record PatchStrategyRequest(Double weight, Boolean enabled) {}
}
