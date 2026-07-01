package np.com.thapanarayan.backend.indicator.api;

import java.util.List;

/**
 * Self-describing catalog entry for a study (built-in or plugin). Drives {@code /indicators/catalog}
 * and the generic settings dialog. {@code feedsSignalEngine} marks studies that contribute a vote to
 * confluence (Phase 5).
 */
public record IndicatorDescriptor(
        String id,
        String name,
        String category,
        OutputKind outputKind,
        List<ParamSpec> params,
        boolean feedsSignalEngine) {
}
