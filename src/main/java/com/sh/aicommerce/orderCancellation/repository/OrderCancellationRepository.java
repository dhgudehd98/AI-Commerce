package com.sh.aicommerce.orderCancellation.repository;

import com.sh.aicommerce.entity.OrderCancellation;
import com.sh.aicommerce.enums.order.OrderCancelStatus;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationDetailRecord;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationFailedDetailResponseRecord;
import com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationWithOutbound;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    select new com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationWithOutbound(
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

    // 주문 취소 실패 리스트 조회
    @Query(
            value = """
        select new com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationWithOutbound(
            oc,
            ob,
            oc.order.id,
            oc.order.orderNumber
        )
        from OrderCancellation oc
        join Outbound ob on ob.order = oc.order
        where oc.cancelStatus in :statuses
        """,
            countQuery = """
        select count(oc)
        from OrderCancellation oc
        join Outbound ob on ob.order = oc.order
        where oc.cancelStatus in :statuses
        """
    )
    Page<OrderCancellationWithOutbound> findFailedOrderCancellations(
            @Param("statuses") List<OrderCancelStatus> statuses,
            Pageable pageable
    );

    // 특정 주문 취소 요청 실패 상세 요청
    @Query("""
    select new com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationFailedDetailResponseRecord(
        oc,
        o,
        p,
        ob,
        m,
        d,
        r
    )
    from OrderCancellation oc
    left join oc.order o
    left join o.member m
    left join Payment p on p.order = o
    left join Outbound ob on ob.order = o
    left join Delivery d on d.order = o
    left join Refund r on r.orderCancellation = oc
    where oc.id = :cancellationId
    """)
    Optional<OrderCancellationFailedDetailResponseRecord> findFailureOrderCancellationDetailById(
            @Param("cancellationId") Long cancellationId
    );


    @Query(
    value = """
    select new com.sh.aicommerce.orderCancellation.dto.record.OrderCancellationDetailRecord(
        oc,
        oc.order,
        ob,
        m,
        d,
        p 
    )
    from OrderCancellation oc
    join Outbound ob on ob.order = oc.order
    join Delivery d on d.order = oc.order
    join Payment p on p.order = oc.order
    join Member m on oc.order.member = m 
    where oc.id = :cancelId
    """
    )
    Optional<OrderCancellationDetailRecord> findByIdWithOutbound(@Param("cancelId")Long cancelId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
    """
    select oc
    from OrderCancellation oc
    where oc.id = :cancelId
    """
    )
    Optional<OrderCancellation> findByCancelIdForUpdate(@Param("cancelId")Long cancelId);

    @Query("""
      select oc.id
      from OrderCancellation oc
      where oc.cancelStatus in (
          'PROCESSING_START',
          'ITEM_RETURNED',
          'PRODUCT_RESERVATION_RELEASED',
          'PAYMENT_CANCEL_COMPLETED'
      )
      """)
    List<Long> findTargetOrderCancellation();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
    """
    select oc
    from OrderCancellation oc
    join fetch oc.order
    where oc.id = :cancellationId
    """
    )
    Optional<OrderCancellation> findByIdForUpdate(@Param("cancellationId") Long cancellationId);

    @Query(
    """
    select oc
    from OrderCancellation oc
    join fetch oc.order
    where oc.id = :cancellationId
    """
    )
    Optional<OrderCancellation> findByIdWithOrder(@Param("cancellationId") Long cancellationId);

    @Query(
            """
            select oc.id
            from OrderCancellation oc
            where oc.cancelStatus in(
                'PRODUCT_RESERVATION_RELEASE_FAILED',
                'PAYMENT_CANCEL_FAILED',
                'PAYMENT_CANCEL_FAILED_CHECK',
                'ITEM_RETURN_FAILED' )
            """
    )
    List<Long> findTargetOrderCancellationFailed();

    Optional<OrderCancellation> findOrderCancellationById(Long cancellationId);

}
