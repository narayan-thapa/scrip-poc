package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalQuery;
import np.com.thapanarayan.backend.signal.api.SignalView;

/** Serves the published {@link SignalQuery} read surface over persisted signals. */
@Service
class SignalQueryService implements SignalQuery {

    private final SignalRepository signals;
    private final SignalProperties properties;

    SignalQueryService(SignalRepository signals, SignalProperties properties) {
        this.signals = signals;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SignalView> findById(UUID id) {
        return signals.findById(id).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SignalView> latestForSymbol(String symbol) {
        return signals.findFirstBySymbolOrderByTradeDateDesc(symbol).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignalView> forSymbol(String symbol, LocalDate fromInclusive, LocalDate toInclusive) {
        return signals.findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(symbol, fromInclusive, toInclusive)
                .stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignalView> forDate(LocalDate tradeDate) {
        return signals.findByTradeDateOrderBySymbolAsc(tradeDate).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SignalView> latest(Optional<SignalAction> action) {
        return signals.findLatestTradeDate()
                .map(date -> action
                        .map(a -> signals.findByTradeDateAndActionOrderBySymbolAsc(date, a))
                        .orElseGet(() -> signals.findByTradeDateOrderBySymbolAsc(date)))
                .orElseGet(List::of)
                .stream().map(this::toView).toList();
    }

    private SignalView toView(SignalEntity e) {
        return SignalMapper.toView(e, properties.maxTopReasons());
    }
}
