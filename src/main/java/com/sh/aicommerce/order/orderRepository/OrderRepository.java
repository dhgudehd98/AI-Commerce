package com.sh.aicommerce.order.orderRepository;

import com.sh.aicommerce.entity.Orders;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface OrderRepository extends JpaRepository<Orders, Long> {

    @Query(
    """
    select o
    from Orders o
    join fetch o.payment p
    where
        o.orderNumber = :orderNumber and
        o.status = 'CREATED'
    """)
    Optional<Orders> findByAndOrderNumberAndStatusWithPayment(@Param("orderNumber") String orderNumber);
}
