package com.sh.aicommerce.admin.PaymentCompensation.repository;

import com.sh.aicommerce.admin.PaymentCompensation.dto.response.AdminPaymentCompensationListResponseDto;
import com.sh.aicommerce.entity.PaymentCompensation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** 관리자 목록 전용 읽기 Repository. 필요한 값만 조회합니다. */
public interface AdminPaymentCompensationRepository extends Repository<PaymentCompensation, Long> {
    @Query(value = """
        select new com.sh.aicommerce.admin.PaymentCompensation.dto.response.AdminPaymentCompensationListResponseDto(
            c.id,
            p.id,
            a.id,
            pc.id,
            c.orderNumber,
            m.id,
            m.memberName,
            c.requestAmount,
            p.status,
            c.status,
            c.result,
            c.retryCount,
            c.reasonCode,
            c.lastErrorCode,
            pc.status,
            c.createdAt,
            c.updatedAt,
            c.checkedAt
        )
        from PaymentCompensation c
        join c.paymentAttempt a
        join a.payment p
        join p.order o
        join o.member m
        left join c.paymentCancellation pc
        """, countQuery = """
        select count(c)
        from PaymentCompensation c
        join c.paymentAttempt a
        join a.payment p
        join p.order o
        join o.member m
        left join c.paymentCancellation pc
        """)
    Page<AdminPaymentCompensationListResponseDto> findAllForAdmin(Pageable pageable);
}
