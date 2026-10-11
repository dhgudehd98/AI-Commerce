package com.sh.aicommerce.wms.outboundHistory.repository;

import com.sh.aicommerce.entity.OutboundHistory;
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
public interface OutboundHistoryRepository extends JpaRepository<OutboundHistory, Long> {
    boolean existsByOutboundIdAndEventType(Long outboundId, com.sh.aicommerce.wms.outbound.enums.OutboundHistoryEventType eventType);

    @Query(
    """
    select oh
    from OutboundHistory oh
    join fetch oh.outbound o
    where oh.outbound.id = :outboundId
    """
    )
    Page<OutboundHistory> findByOutboundId(@Param("outboundId")Long outboundId, Pageable pageable);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select oh
    from OutboundHistory oh
    where oh.outbound.id = :outboundId
    """)
    Optional<Object> findHistoryByOutboundIdWithLock(@Param("outboundId")Long outboundId);
}