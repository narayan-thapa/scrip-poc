package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import np.com.thapanarayan.backend.signal.api.SignalAction;

interface SignalRepository extends JpaRepository<SignalEntity, UUID> {

    Optional<SignalEntity> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    Optional<SignalEntity> findFirstBySymbolOrderByTradeDateDesc(String symbol);

    List<SignalEntity> findBySymbolAndTradeDateBetweenOrderByTradeDateDesc(
            String symbol, LocalDate fromInclusive, LocalDate toInclusive);

    /** The most recent trade date that has any signal — anchors the "latest" feed. */
    @Query("select max(s.tradeDate) from SignalEntity s")
    Optional<LocalDate> findLatestTradeDate();

    List<SignalEntity> findByTradeDateOrderBySymbolAsc(LocalDate tradeDate);

    List<SignalEntity> findByTradeDateAndActionOrderBySymbolAsc(LocalDate tradeDate, SignalAction action);
}
