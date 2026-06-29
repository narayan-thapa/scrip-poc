package np.com.thapanarayan.backend.reference.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TradingDayRepository extends JpaRepository<TradingDayEntity, LocalDate> {

    @Query("select t from TradingDayEntity t where t.open = true order by t.tradeDate asc")
    List<TradingDayEntity> findAllOpenOrdered();

    @Query("select min(t.tradeDate) from TradingDayEntity t")
    LocalDate minDate();

    @Query("select max(t.tradeDate) from TradingDayEntity t")
    LocalDate maxDate();
}
