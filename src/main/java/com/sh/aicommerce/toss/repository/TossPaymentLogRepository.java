package com.sh.aicommerce.toss.repository;

import com.sh.aicommerce.entity.TossPaymentLog;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TossPaymentLogRepository extends JpaRepository<TossPaymentLog, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select tl
    from TossPaymentLog tl
    where tl.cancellationId = :cancellationId
    """)
    Optional<TossPaymentLog> findLogByCancellationIdWithLock(@Param("cancellationId") Long cancellationId);
}