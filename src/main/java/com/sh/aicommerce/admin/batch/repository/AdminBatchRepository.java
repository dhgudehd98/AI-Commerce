package com.sh.aicommerce.admin.batch.repository;

import com.sh.aicommerce.admin.batch.dto.response.AdminCollectionRunResponseDto;
import com.sh.aicommerce.admin.batch.dto.response.AdminReconciliationRunResponseDto;
import com.sh.aicommerce.entity.PgTransactionCollectionRun;
import com.sh.aicommerce.entity.PgTransactionReconciliationRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import com.sh.aicommerce.admin.batch.dto.response.AdminMismatchResponseDto;
import com.sh.aicommerce.admin.batch.dto.response.AdminMismatchIssueResponseDto;

/** 실행 목록에 필요한 값만 조회하여 Entity 노출·연관관계 추가 조회를 방지합니다. */
public interface AdminBatchRepository extends Repository<PgTransactionReconciliationRun, Long> {
    @Query(value = """
        select new com.sh.aicommerce.admin.batch.dto.response.AdminCollectionRunResponseDto(
            r.id, r.targetDate, r.status, r.jobInstanceId, r.jobExecutionId,
            r.startedAt, r.completedAt, r.collectedCount, r.errorMessage)
        from PgTransactionCollectionRun r
        where (:from is null or r.targetDate >= :from)
          and (:to is null or r.targetDate <= :to)
          and (:status is null or r.status = :status)
        """, countQuery = """
        select count(r) from PgTransactionCollectionRun r
        where (:from is null or r.targetDate >= :from)
          and (:to is null or r.targetDate <= :to)
          and (:status is null or r.status = :status)
        """)
    Page<AdminCollectionRunResponseDto> findCollections(
            @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("status") PgTransactionCollectionRun.Status status, Pageable pageable);

    @Query(value = """
        select new com.sh.aicommerce.admin.batch.dto.response.AdminReconciliationRunResponseDto(
            r.id, c.id, r.targetDate, r.status, r.jobInstanceId, r.jobExecutionId,
            r.startedAt, r.completedAt, r.pgCount, r.processedCount, r.matchedCount,
            r.mismatchCount, r.excludedCount, r.matchedLedgerCount, r.errorCode, r.errorMessage)
        from PgTransactionReconciliationRun r
        join r.collectionRun c
        where (:from is null or r.targetDate >= :from)
          and (:to is null or r.targetDate <= :to)
          and (:status is null or r.status = :status)
        """, countQuery = """
        select count(r) from PgTransactionReconciliationRun r
        join r.collectionRun c
        where (:from is null or r.targetDate >= :from)
          and (:to is null or r.targetDate <= :to)
          and (:status is null or r.status = :status)
        """)
    Page<AdminReconciliationRunResponseDto> findReconciliations(
            @Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("status") PgTransactionReconciliationRun.Status status, Pageable pageable);

    @Query("select count(r) from PgTransactionReconciliationRun r where r.id = :runId")
    long countRunById(@Param("runId") Long runId);

    // Issue를 JOIN해서 페이징하면 거래 한 건이 여러 행으로 나뉘므로 Result만 페이징합니다.
    @Query(value = """
        select new com.sh.aicommerce.admin.batch.dto.response.AdminMismatchResponseDto(
            r.id, t.id, p.id, l.id, t.orderId, t.paymentKey, t.transactionKey,
            r.transactionType, t.amount, t.currency, r.amountDiff, r.checkedAt)
        from PgTransactionReconciliationResult r
        join r.pgTransaction t
        left join r.payment p
        left join r.paymentLedger l
        where r.reconciliationRun.id = :runId and r.status = 'MISMATCHED'
        """, countQuery = """
        select count(r) from PgTransactionReconciliationResult r
        where r.reconciliationRun.id = :runId and r.status = 'MISMATCHED'
        """)
    Page<AdminMismatchResponseDto> findMismatches(@Param("runId") Long runId, Pageable pageable);

    @Query("""
        select new com.sh.aicommerce.admin.batch.dto.response.AdminMismatchIssueResponseDto(
            i.id, r.id, i.code, i.fieldName, i.pgValue, i.internalValue, i.description)
        from PgTransactionReconciliationIssue i
        join i.result r
        where r.id in :resultIds
        order by r.id, i.id
        """)
    List<AdminMismatchIssueResponseDto> findIssuesByResultIds(@Param("resultIds") List<Long> resultIds);
}
