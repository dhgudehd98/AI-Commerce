package com.sh.aicommerce.orderCancellation.repository;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.orderCancellation.dto.OrderCancellationWithOutbound;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query(
    value = """
    select new com.sh.aicommerce.orderCancellation.dto.OrderCancellationWithOutbound(
       oc,
       ob,
       oc.order.id,
       oc.order.orderNumber
    )
    from OrderCancellation oc 
    join Outbound ob on ob.order=  oc.order
    """,
    countQuery = """
    select count(oc)
    from OrderCancellation  oc
    join Outbound ob on ob.order= oc.order
    """
    )
    Page<OrderCancellationWithOutbound> findOrderCancellationAll(Pageable pageable);
}
