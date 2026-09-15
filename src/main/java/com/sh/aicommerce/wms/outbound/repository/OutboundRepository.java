package com.sh.aicommerce.wms.outbound.repository;

import com.sh.aicommerce.entity.Outbound;


import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface OutboundRepository extends JpaRepository<Outbound, Long> {

    @Query("""
            select ob
            from Outbound ob
            join fetch ob.order o
            join fetch o.delivery d
            where ob.id = :outboundId
            """)
    Optional<Outbound> findById(@Param("outboundId") Long outboundId);

    @Query("""
            select ob
            from Outbound ob
            join fetch ob.order o
            """)
    Page<Outbound> findOutboundAll(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ob
            from Outbound ob
            join fetch ob.order o
            join fetch o.delivery d
            where ob.id = :outboundId
            and ob.cancellationRequest = false
            """)
    Optional<Outbound> findByIdForUpdate(Long outboundId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select ob
    from Outbound ob
    where ob.order.id = :orderId
    and (
        ob.outBoundStatus = 'PENDING' or 
        ob.outBoundStatus = 'PICKING' or
        ob.outBoundStatus = 'PICKED' or
        ob.outBoundStatus = 'PACKING' or
        ob.outBoundStatus = 'PACKED' or
        ob.outBoundStatus = 'ON_HOLD'  
        )
    """)
    Optional<Outbound> findOutboundStatusBeforePackedByOrderIdForUpdate(@Param("orderId") Long orderId);
}
