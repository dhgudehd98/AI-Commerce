package com.sh.aicommerce.payment.repository;

import com.sh.aicommerce.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    p.status = 'CONFIRMING' and
    p.paymentMethod = 'SAVED_CARD' and
    o.orderNumber = :orderNumber and
    o.status = 'CREATED' and
    o.member.id = :memberId
    """)
    Optional<Payment> findConfirmingPaymentForUpdatePaymentStatusCardForUpdateInBillingCard(@Param("orderNumber")String orderNumber, @Param("memberId") Long memberId);


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
}
