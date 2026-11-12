package com.sh.aicommerce.transaction.service;

import com.sh.aicommerce.entity.PgTransactionCollectionRun;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.transaction.repository.PgTransactionCollectionRunRepository;
import com.sh.aicommerce.transaction.repository.PgTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PgTransactionCollectionRunService {
    private final PgTransactionCollectionRunRepository runRepository;
    private final PgTransactionRepository transactions;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(Long instanceId, Long executionId, LocalDate targetDate) {
        PgTransactionCollectionRun run = runRepository.findByJobInstanceId(instanceId).orElse(null);

        if (run == null) {
            run = runRepository.save(new PgTransactionCollectionRun(instanceId, executionId, targetDate));
        } else {
            if (!run.getTargetDate().equals(targetDate)) {
                throw new TossPaymentException("수집 실행의 대상 날짜가 일치하지 않습니다.");
            }
            run.start(executionId);
        }
        return run.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long runId, boolean successful) {
        PgTransactionCollectionRun run = runRepository.findById(runId)
                .orElseThrow(() -> new TossPaymentException("PG 거래 수집 실행이 존재하지 않습니다."));
        run.finish(successful, transactions.countByCollectionRunId(runId));
    }
}
