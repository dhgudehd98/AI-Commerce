package com.sh.aicommerce.refund.repository;

import com.sh.aicommerce.entity.Refund;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    boolean existsByOrderCancellation_Id(@Param("cancellationId") Long cancellationId);

    @Query("""
    select r
    from Refund r
    where r.orderCancellation.id = :orderCancellationId
    """)
    Optional<Refund> findByOrderCancellationId(@Param("orderCancellationId")Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select r
    from Refund r
    where r.orderCancellation.id = :orderCancellationId
    """)
    Optional<Refund> findByOrderCancellationIdWithLock(@Param("orderCancellationId")Long cancellationId);
}