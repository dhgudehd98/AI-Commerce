package com.sh.aicommerce.transactionReconciliation.service;

import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.entity.PgTransactionCollectionRun;
import com.sh.aicommerce.entity.PgTransactionReconciliationRun;
import com.sh.aicommerce.transaction.repository.PgTransactionCollectionRunRepository;
import com.sh.aicommerce.transaction.repository.PgTransactionRepository;
import com.sh.aicommerce.transactionReconciliation.dto.response.ReconciliationStartResult;
import com.sh.aicommerce.transactionReconciliation.repository.PgTransactionReconciliationRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PgTransactionReconciliationService {

    private final PgTransactionCollectionRunRepository collectionRunRepository;
    private final PgTransactionReconciliationRunRepository reconciliationRunRepository;
    private final PgTransactionRepository transactionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconciliationStartResult start(Long instanceId, Long executionId, LocalDate targetDate) {
        PgTransactionCollectionRun collectionRun = collectionRunRepository
                .findFirstByTargetDateOrderByJobExecutionIdDesc(targetDate)
                .orElseThrow(() -> new PaymentException("해당 날짜의 PG 거래 수집 실행이 없습니다."));

        if (collectionRun.getStatus() != PgTransactionCollectionRun.Status.COMPLETED) {
            throw new PaymentException("PG 거래 수집이 완료되지 않아 대사를 실행할 수 없습니다.");
        }

        long pgCount = transactionRepository.countByCollectionRunId(collectionRun.getId());
        PgTransactionReconciliationRun run = reconciliationRunRepository.save(
                PgTransactionReconciliationRun.builder()
                        .jobInstanceId(instanceId)
                        .jobExecutionId(executionId)
                        .targetDate(targetDate)
                        .collectionRun(collectionRun)
                        .pgCount(pgCount)
                        .build()
        );

        log.info("[거래 대사 시작] runId={} collectionRunId={} targetDate={} pgCount={}",
                run.getId(), collectionRun.getId(), targetDate, pgCount);
        return new ReconciliationStartResult(run.getId(), collectionRun.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long runId, boolean completed, String errorCode, String errorMessage) {
        PgTransactionReconciliationRun run = reconciliationRunRepository.findById(runId)
                .orElseThrow(() -> new PaymentException("대사 실행이 없습니다."));
        run.finish(completed, errorCode, errorMessage);
        log.info("[거래 대사 종료] runId={} status={} processedCount={} pgCount={}",
                runId, run.getStatus(), run.getProcessedCount(), run.getPgCount());
    }
}
