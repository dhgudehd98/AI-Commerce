package com.sh.aicommerce.admin.Main.repository;

import com.sh.aicommerce.entity.PaymentLedger;
import com.sh.aicommerce.enums.paymentLedger.PaymentLedgerType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/** 대시보드 전용 집계. 금액은 원장, 현재 작업 수는 업무 상태를 기준으로 조회합니다. */
public interface AdminDashboardRepository extends Repository<PaymentLedger, Long> {
    interface LedgerTotals {
        PaymentLedgerType getType();
        Long getTransactionCount();
        Long getTotalAmount();
    }

    @Query("""
        select l.type as type, count(l) as transactionCount, sum(l.amount) as totalAmount
        from PaymentLedger l
        where l.occurredAt >= :from and l.occurredAt < :until
        group by l.type
        """)
    List<LedgerTotals> aggregateLedger(@Param("from") OffsetDateTime from,
                                      @Param("until") OffsetDateTime until);

    @Query("select count(c) from PaymentCompensation c where c.status = 'PENDING'")
    long countPendingCompensations();

    @Query("select count(c) from PaymentCompensation c where c.status = 'FOLLOW_UP_PENDING'")
    long countFollowUpPendingCompensations();

    @Query("select count(c) from PaymentCancellation c where c.status in ('PROCESSING', 'UNKNOWN')")
    long countUnconfirmedCancellations();

    /** 같은 주문의 여러 작업이 동시에 확인 대상이어도 주문 한 건으로 집계합니다. */
    @Query("""
        select count(o) from Orders o
        where exists (
            select oc.id from OrderCancellation oc
            where oc.order = o and oc.cancelStatus = 'MANUAL_REVIEW_REQUIRED'
        ) or exists (
            select pc.id from PaymentCancellation pc
            where pc.payment.order = o and pc.status = 'MANUAL_REVIEW_REQUIRED'
        ) or exists (
            select c.id from PaymentCompensation c
            where c.paymentAttempt.payment.order = o
              and (c.status = 'MANUAL_REVIEW_REQUIRED'
                   or (c.status = 'PENDING' and c.updatedAt <= :pendingCutoff))
        ) or exists (
            select p.id from Payment p
            where p.order = o and p.status = 'PROCESSING' and p.updatedAt <= :processingCutoff
        )
        """)
    long countAttentionOrders(@Param("pendingCutoff") LocalDateTime pendingCutoff,
                               @Param("processingCutoff") LocalDateTime processingCutoff);

    /** 재시도 소진 상태인 주문 수. 단순 검증 실패의 즉시 관리자 확인과 구분합니다. */
    @Query("""
        select count(o) from Orders o
        where exists (
            select c.id from PaymentCompensation c
            where c.paymentAttempt.payment.order = o
              and c.status = 'MANUAL_REVIEW_REQUIRED' and c.retryCount >= :maxCompensationRetries
        ) or exists (
            select r.id from PaymentCancellationRecovery r
            where r.paymentCancellation.payment.order = o
              and r.status = 'MANUAL_REVIEW' and r.retryCount >= r.maxRetryCount
        )
        """)
    long countRetryExhaustedOrders(@Param("maxCompensationRetries") int maxCompensationRetries);
}
