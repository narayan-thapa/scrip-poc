package np.com.thapanarayan.backend.signal.internal;

import java.math.BigDecimal;
import java.util.List;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfig;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfigRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Seeds a {@code strategy_config} row (default weight, enabled) for each strategy bean if missing. */
@Component
class StrategyConfigSeeder implements ApplicationRunner {

    private final List<SignalStrategy> strategies;
    private final StrategyConfigRepository repo;

    StrategyConfigSeeder(List<SignalStrategy> strategies, StrategyConfigRepository repo) {
        this.strategies = strategies;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SignalStrategy s : strategies) {
            if (!repo.existsById(s.id())) {
                repo.save(new StrategyConfig(s.id(), s.name(), "BUILTIN",
                        BigDecimal.valueOf(s.defaultWeight()), true));
            }
        }
    }
}
