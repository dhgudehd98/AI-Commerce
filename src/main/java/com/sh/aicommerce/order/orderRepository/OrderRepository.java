package com.sh.aicommerce.order.orderRepository;

import com.sh.aicommerce.admin.Order.dto.response.AdminOrderTimelineRecord;
import com.sh.aicommerce.entity.Orders;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select o
    from Orders o
    where o.id = :orderId
    and o.member.id = :memberId
    """)
    Optional<Orders> findByMemberIdForUpdate(@Param("memberId") Long memberId, @Param("orderId")Long orderId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select o
    from Orders o
    where o.id =:orderId
    """)
    Optional<Orders> findByIdWithLock(@Param("orderId") Long orderId);

    @Query("""
     select o
     from Orders o
     where o.orderNumber = :orderNumber
     """)
    Optional<Orders> findOrderByOrderNumber(@Param("orderNumber")String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select o
    from Orders o
    where o.orderNumber = :orderNumber
    """)
    Optional<Orders> findOrderByOrderNumberForUpdate(@Param("orderNumber")String orderNumber);


    /** 단일 관계만 함께 조회하고, 보상·원장 등 다건 이력은 별도 조회합니다. */
    @Query("""
    select new com.sh.aicommerce.admin.Order.dto.response.AdminOrderTimelineRecord(
        o, p, pc, oc
    )
    from Orders o
    left join o.payment p
    left join OrderCancellation oc on oc.order = o
    left join PaymentCancellation pc on pc.orderCancellation = oc and pc.payment = p
    where o.id = :orderId
    """)
    Optional<AdminOrderTimelineRecord> findOrderTimeRecordByOrderId(@Param("orderId") Long orderId);
    @Query(value = """
    select new com.sh.aicommerce.admin.Order.dto.response.OrdersListResponseDto(
        o.id, o.orderNumber, m.id, m.memberName, o.totalPrice, o.status,
        p.id, p.status, p.compensationRequired, o.orderedAt
    )
    from Orders o
    join o.member m
    left join o.payment p
    """, countQuery = """
    select count(o)
    from Orders o
    join o.member m
    """)
    org.springframework.data.domain.Page<com.sh.aicommerce.admin.Order.dto.response.OrdersListResponseDto>
    findAllForAdmin(org.springframework.data.domain.Pageable pageable);
}
