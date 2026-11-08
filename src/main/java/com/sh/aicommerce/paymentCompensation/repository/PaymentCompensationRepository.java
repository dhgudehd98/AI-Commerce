package com.sh.aicommerce.paymentCompensation.repository;

import com.sh.aicommerce.entity.PaymentCompensation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCompensationRepository extends JpaRepository<PaymentCompensation, Long> {
    Optional<PaymentCompensation> findByPaymentAttemptId(Long attemptId);


    @Query("""
    select pc
    from PaymentCompensation pc
    join fetch pc.paymentAttempt pa
    where pc.id = :compensationId
    """)
    Optional<PaymentCompensation> findPaymentAndAttemptById(@Param("compensationId")Long compensationId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PaymentCompensation c where c.id = :id")
    Optional<PaymentCompensation> findByIdForUpdate(@Param("id") Long id);

    java.util.List<PaymentCompensation> findAllByPaymentCancellationId(Long cancellationId);
    @Query("""
    select c.id from PaymentCompensation c
    where c.status = 'PENDING' and c.updatedAt <= :cutoff
    order by c.updatedAt, c.id
    """)
    java.util.List<Long> findPendingRetryIds(@Param("cutoff") java.time.LocalDateTime cutoff,
                                           org.springframework.data.domain.Pageable pageable);
    /** PG 취소 성공 반영과 같은 트랜잭션에서 호출합니다. 완료된 보상은 변경하지 않습니다. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("""
    update PaymentCompensation c
       set c.status = com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationStatus.FOLLOW_UP_PENDING,
           c.lastErrorCode = null,
           c.updatedAt = :updatedAt
     where c.paymentCancellation.id = :cancellationId
       and c.status = com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationStatus.CANCELLATION_PENDING
    """)
    int markFollowUpPending(@Param("cancellationId") Long cancellationId,
                            @Param("updatedAt") java.time.LocalDateTime updatedAt);
    @Query("""
    select c
    from PaymentCompensation c
    join fetch c.paymentAttempt a
    left join fetch c.paymentCancellation pc
    where a.payment.order.id = :orderId
    """)
    Optional<PaymentCompensation> findByOrderId(@Param("orderId") Long orderId);
}
