package com.sh.aicommerce.wms.outbound.repository;

import com.sh.aicommerce.entity.Outbound;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;


@Repository
public interface OutboundRepository extends JpaRepository<Outbound, Long> {

    @Query("""
            select ob
            from Outbound ob
            join fetch ob.order o
            """)
    Page<Outbound> findOutboundAll(Pageable pageable);
}
