package com.sh.aicommerce.payment.repository;

import com.sh.aicommerce.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
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
    Optional<Payment> findWithOrderByOrderNumber(@Param("amount") Integer amount, @Param("orderNumber")String orderNumber);
}
