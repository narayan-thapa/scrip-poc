package np.com.thapanarayan.backend.backtest.internal;

import java.util.UUID;
import np.com.thapanarayan.backend.backtest.api.BacktestUpdatedEvent;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.BacktestOutcome;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.BacktestRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs a backtest synchronously (single symbol, EOD data → fast) and persists run + result + trades. */
@Service
public class BacktestService {

    private final BacktestEngine engine;
    private final BacktestDao dao;
    private final ApplicationEventPublisher events;

    BacktestService(BacktestEngine engine, BacktestDao dao, ApplicationEventPublisher events) {
        this.engine = engine;
        this.dao = dao;
        this.events = events;
    }

    @Transactional
    public UUID run(BacktestRequest req, String createdBy) {
        UUID id = UUID.randomUUID();
        dao.saveRun(id, req, "RUNNING", createdBy);
        BacktestOutcome outcome = engine.run(req);
        dao.saveResult(id, outcome.metrics(), outcome.equityCurve());
        dao.saveTrades(id, outcome.trades());
        dao.updateStatus(id, "COMPLETED");
        events.publishEvent(new BacktestUpdatedEvent(id.toString(), req.symbol()));
        return id;
    }
}
