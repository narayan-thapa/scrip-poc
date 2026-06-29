package np.com.thapanarayan.backend.marketdata.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

interface BrokerFlowDailyRepository extends JpaRepository<BrokerFlowDailyEntity, Long> {

    List<BrokerFlowDailyEntity> findBySymbolAndTradeDateOrderByNetQtyDesc(String symbol, LocalDate tradeDate);

    @Transactional
    void deleteBySymbolAndTradeDate(String symbol, LocalDate tradeDate);
}
