package np.com.thapanarayan.backend.reference.internal.repo;

import java.util.List;
import np.com.thapanarayan.backend.reference.internal.domain.Instrument;
import np.com.thapanarayan.backend.reference.internal.domain.InstrumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstrumentRepository extends JpaRepository<Instrument, String> {

    @Query("""
            select i from Instrument i
            where (:sector is null or i.sector = :sector)
              and (:status is null or i.status = :status)
              and (:q is null or lower(i.symbol) like lower(concat('%', :q, '%'))
                              or lower(i.name)   like lower(concat('%', :q, '%')))
            """)
    Page<Instrument> search(@Param("sector") String sector,
                            @Param("status") InstrumentStatus status,
                            @Param("q") String q,
                            Pageable pageable);

    @Query("select distinct i.sector from Instrument i where i.sector is not null order by i.sector")
    List<String> findDistinctSectors();
}
