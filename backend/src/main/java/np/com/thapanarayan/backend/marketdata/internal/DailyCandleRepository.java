package np.com.thapanarayan.backend.marketdata.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DailyCandleRepository extends JpaRepository<DailyCandleEntity, DailyCandleId> {

    Optional<DailyCandleEntity> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    List<DailyCandleEntity> findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
            String symbol, LocalDate fromInclusive, LocalDate toInclusive);

    Optional<DailyCandleEntity> findFirstBySymbolOrderByTradeDateDesc(String symbol);

    /** Most recent candles up to {@code asOf}, newest first; caller reverses for ascending. */
    List<DailyCandleEntity> findBySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(
            String symbol, LocalDate asOf, Limit limit);

    List<DailyCandleEntity> findByTradeDate(LocalDate tradeDate);

    @Query("select c.symbol from DailyCandleEntity c where c.tradeDate = :tradeDate order by c.symbol")
    List<String> findSymbolsByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
