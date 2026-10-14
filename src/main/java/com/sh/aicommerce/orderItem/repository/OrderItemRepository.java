package com.sh.aicommerce.orderItem.repository;

import com.sh.aicommerce.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;


@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query(
    """
    select oi
    from OrderItem  oi
    join fetch oi.order o
    join fetch oi.productOption po
    where oi.id = :orderItemId
    and o.status = 'DELIVERED'
    and o.member.id = :memberId
    and o.delivery.deliveryStatus = 'DELIVERED'
    """
    )
    Optional<OrderItem> validateOrderItemPaidOrderByMemberIdAndOrderItemId(@Param("memberId") Long memberId, @Param("orderItemId")Long orderItemId);

    @Query(
    """
    select oi
    from OrderItem oi
    join fetch oi.productOption
    where oi.order.id=:orderId
    """
    )
    List<OrderItem> findByOrderId(@Param("orderId")Long orderId);

    @Query("""
    select oi.productNameSnapshot
    from OrderItem oi
    where oi.order.orderNumber = :orderNumber
    """)
    List<String> findByOrderNumber(@Param("orderNumber") String orderNumber);
}
