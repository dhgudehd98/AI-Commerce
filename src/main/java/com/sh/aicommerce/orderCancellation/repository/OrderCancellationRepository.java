package com.sh.aicommerce.orderCancellation.repository;

import com.sh.aicommerce.entity.OrderCancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderCancellationRepository extends JpaRepository<OrderCancellation, Long> {

    @Query(
     """
     select oc
     from OrderCancellation oc
     where oc.order.id = :orderId
          
     """)
    Optional<OrderCancellation> findByOrderId(@Param("orderId") Long orderId);
}
