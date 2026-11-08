package com.sh.aicommerce.admin.PaymentCancellation.repository;

import com.sh.aicommerce.admin.PaymentCancellation.dto.response.AdminPaymentCancellationListResponseDto;
import com.sh.aicommerce.entity.PaymentCancellation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 관리자 목록 전용 읽기 Repository. 필요한 값만 조회합니다. */
public interface AdminPaymentCancellationRepository extends Repository<PaymentCancellation, Long> {
    @Query(value = """
        select new com.sh.aicommerce.admin.PaymentCancellation.dto.response.AdminPaymentCancellationListResponseDto(
            c.id,
            p.id,
            oc.id,
            o.orderNumber,
            m.id,
            m.memberName,
            p.amount,
            c.cancelAmount,
            c.status,
            p.status,
            oc.cancelStatus,
            oc.inventoryStatus,
            c.cancelReason,
            c.failCode,
            c.failMessage,
            oc.requestedAt,
            c.canceledAt,
            c.updatedAt
        )
        from PaymentCancellation c
        join c.payment p
        join c.orderCancellation oc
        join p.order o
        join o.member m
        """, countQuery = """
        select count(c)
        from PaymentCancellation c
        join c.payment p
        join c.orderCancellation oc
        join p.order o
        join o.member m
        """)
    Page<AdminPaymentCancellationListResponseDto> findAllForAdmin(Pageable pageable);
}
