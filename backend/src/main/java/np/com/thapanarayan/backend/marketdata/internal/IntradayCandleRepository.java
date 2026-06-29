package np.com.thapanarayan.backend.marketdata.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface IntradayCandleRepository extends JpaRepository<IntradayCandleEntity, IntradayCandleId> {

    List<IntradayCandleEntity> findBySymbolAndTradeDateOrderByBucketStartAsc(String symbol, LocalDate tradeDate);

    @Transactional
    void deleteBySymbolAndTradeDate(String symbol, LocalDate tradeDate);
}
