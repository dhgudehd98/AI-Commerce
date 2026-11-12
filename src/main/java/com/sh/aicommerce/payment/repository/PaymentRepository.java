package com.sh.aicommerce.payment.repository;

import com.sh.aicommerce.admin.Payment.dto.response.AdminPaymentListResponseDto;
import com.sh.aicommerce.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Payment p
    join fetch p.order o
    where
    p.status = 'READY' and
    p.paymentMethod = 'TOSS' and
    p.amount = :amount and
    o.orderNumber = :orderNumber and
    o.status = 'CREATED'
    """)
    public Optional<Payment> findPaymentForUpdatePaymentStatusForUpdateInWidget(@Param("amount")Integer amount, @Param("orderNumber") String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Payment p 
    join fetch p.order o
    where
    p.status = 'CONFIRMING' and
    p.paymentMethod = 'TOSS' and   
    p.amount = :amount and
    o.orderNumber = :orderNumber and
    o.status = 'CREATED' 
    """)
    Optional<Payment> findConfirmingPaymentWithOrderByOrderNumberForUpdateInWidget(@Param("amount") Integer amount, @Param("orderNumber")String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Payment p
    join fetch p.order o
    where
    p.status = 'READY' and
    p.paymentMethod = 'SAVED_CARD' and
    o.orderNumber = :orderNumber and
    o.status = 'CREATED' and
    o.member.id = :memberId
    """)
    Optional<Payment> findPaymentForUpdatePaymentStatusCardForUpdateInBillingCard(@Param("orderNumber")String orderNumber, @Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Payment p
    join fetch p.order o
    where
    p.status = 'PROCESSING' and
    p.paymentMethod = 'SAVED_CARD' and
    o.orderNumber = :orderNumber and
    o.status = 'PAYMENT_PROCESSING' and
    o.member.id = :memberId
    """)
    Optional<Payment> findPaymentByOrderNumberForUpdate(@Param("orderNumber")String orderNumber, @Param("memberId") Long memberId);

    @Query("""
  select count(p) > 0
  from Payment p
  join p.order o
  where
      o.orderNumber = :orderNumber
      and p.paymentMethod = 'SAVED_CARD'
      and p.status = 'PAID'
      and o.status = 'PAID'
  """)
    boolean existsPaidBillingCardPayment(@Param("orderNumber") String orderNumber);

    @Query(
    """
    select p
    from Payment p
    join fetch p.order o
    where 
        o.orderNumber = :orderNumber 
        and p.paymentMethod = 'SAVED_CARD'
        and p.status = 'PAID'
        and o.status = 'PAID'
    """
    )
    Optional<Payment> findPaidBillingCardPayment(@Param("orderNumber")String orderNumber);

    @Query("""
  select count(p) > 0
  from Payment p
  join p.order o
  where
      p.paymentMethod = 'TOSS'
      and p.paymentKey = :paymentKey
      and p.amount = :amount
      and p.status = 'PAID'
      and o.orderNumber = :orderNumber
      and o.status = 'PAID'
  """)
    boolean existsPaidTossWidget(@Param("orderNumber") String orderNumber,@Param("paymentKey") String paymentKey, @Param("amount")Integer amount);

    @Query("""
     select p
     from Payment p
     join fetch p.order o
     where
         p.paymentMethod = 'TOSS'
      and p.paymentKey = :paymentKey
      and p.amount = :amount
      and p.status = 'PAID'
      and o.orderNumber = :orderNumber
      and o.status = 'PAID'
            """
    )
    Optional<Payment> findPaidTossWidgetPayment(@Param("orderNumber")String orderNumber,@Param("paymentKey")String paymentKey,@Param("amount") Integer amount);

    @Query(
    """
    select p
    from Payment p
    join fetch p.order o
    where p.order.orderNumber = :orderNumber
    """
    )
    Optional<Payment> findPaymentWithOrderByOrderNumber(@Param("orderNumber")String orderNumber);



    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select p
            from Payment p
            join fetch p.order o
            where p.id =:paymentId
            and p.status = 'CONFIRMING'
            and p.updatedAt <= :expiredAt
            and o.status = 'CREATED'
            """
    )
    Optional<Payment> findExpiredPaymentForUpdate(@Param("paymentId")Long paymentId,@Param("expiredAt") LocalDateTime expiredAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Payment p
    where p.order.id = :orderId
    """
    )
    Optional<Payment> findPaymentByOrderIdWithLock(@Param("orderId") Long orderId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Payment p
    where p.paymentKey=:paymentKey
    """)
    Optional<Payment> findByPaymentKeyForUpdate(@Param("paymentKey") String paymentKey);

    Optional<Payment> findByOrderId(Long id);

    @Query("""
    select p
    from Payment p
    where p.paymentKey = :paymentKey
    """)
    Optional<Payment> findPaymentByPaymentKey(@Param("paymentKey")String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select p
    from Payment p
    join p.order o on p.order.id = o.id
    where p.id = :paymentId
    and o.orderNumber = :orderNumber
   
    """)
    Optional<Payment> findPaymentByIdWithOrderForUpdate(@Param("paymentId")Long paymentId, @Param("orderNumber")String orderNumber);

    @Query("""
    select p
    from Payment p
    join fetch p.order o
    where p.id = :paymentId
    """)
    Optional<Payment> findPaymentByIdWithOrder(@Param("paymentId")Long paymentId);

    @Query("""
    select p
    from Payment p
    where p.order.id = :orderId
    """)
    Optional<Payment> findPaymentByOrderId(@Param("orderId") Long orderId);

    @Query("""
    select p.id
    from Payment p
    where p.status = 'PROCESSING'
    and p.updatedAt <= :updatedAt
    """)
    List<Long> findPaymentStatusProcessing(@Param("updatedAt") LocalDateTime updatedAt);

    // 기존 승인 시도는 있지만 보상 작업이 없는 장시간 진행상태에 건만 수집
    @Query("""
    select new com.sh.aicommerce.paymentCompensation.record.PaymentCompensationScanRecord(p.id, o.orderNumber)
    from Payment p
    join p.order o
    where p.status = 'PROCESSING'
      and p.updatedAt <= :cutoff
      and exists (select a.id from PaymentAttempt a where a.payment = p)
      and not exists (
          select c.id from PaymentCompensation c
          where c.paymentAttempt.payment = p
      )
    order by p.updatedAt, p.id
    """)
    List<com.sh.aicommerce.paymentCompensation.record.PaymentCompensationScanRecord> findMissingProcessingCompensations(
            @Param("cutoff") LocalDateTime cutoff, org.springframework.data.domain.Pageable pageable);

    @Query(
            value = """
        select new com.sh.aicommerce.admin.Payment.dto.response.AdminPaymentListResponseDto(
            p.id,
            o.id,
            o.orderNumber,
            m.id,
            m.memberName,
            p.amount,
            p.status,
            p.paymentMethod,
            p.paymentMethodLabel,
            p.compensationRequired,
            p.createdAt,
            p.paidAt
        )
        from Payment p
        join p.order o
        join o.member m
        """,
            countQuery = """
        select count(p)
        from Payment p
        join p.order o
        join o.member m
        """
    )
    Page<AdminPaymentListResponseDto> findPaymentAll(Pageable pageable);

}
