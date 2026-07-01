package np.com.thapanarayan.backend.reference.internal.repo;

import java.time.LocalDate;
import java.util.List;
import np.com.thapanarayan.backend.reference.internal.domain.TradingDay;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingDayRepository extends JpaRepository<TradingDay, LocalDate> {

    boolean existsByTradeDateAndOpenTrue(LocalDate tradeDate);

    /** Open trading days strictly before {@code date}, most-recent first. Use a limit Pageable. */
    List<TradingDay> findByTradeDateLessThanAndOpenTrueOrderByTradeDateDesc(LocalDate date, Pageable pageable);

    /** Open trading day on/after {@code date}, earliest first. */
    List<TradingDay> findByTradeDateGreaterThanEqualAndOpenTrueOrderByTradeDateAsc(LocalDate date, Pageable pageable);

    List<TradingDay> findByTradeDateBetweenAndOpenTrueOrderByTradeDateAsc(LocalDate from, LocalDate to);

    List<TradingDay> findByTradeDateBetweenOrderByTradeDateAsc(LocalDate from, LocalDate to);
}
