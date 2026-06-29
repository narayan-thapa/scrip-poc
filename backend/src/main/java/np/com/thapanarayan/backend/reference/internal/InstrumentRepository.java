package np.com.thapanarayan.backend.reference.internal;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import np.com.thapanarayan.backend.reference.api.InstrumentStatus;

interface InstrumentRepository extends JpaRepository<InstrumentEntity, String> {

    @Query("""
            select i from InstrumentEntity i
            where (:sector is null or i.sector = :sector)
              and (:status is null or i.status = :status)
              and (:q is null
                   or upper(i.symbol) like upper(concat('%', :q, '%'))
                   or upper(i.name)   like upper(concat('%', :q, '%')))
            """)
    Page<InstrumentEntity> search(@Param("sector") String sector,
            @Param("status") InstrumentStatus status,
            @Param("q") String q,
            Pageable pageable);

    @Query("select distinct i.sector from InstrumentEntity i where i.sector is not null order by i.sector")
    List<String> findDistinctSectors();
}
