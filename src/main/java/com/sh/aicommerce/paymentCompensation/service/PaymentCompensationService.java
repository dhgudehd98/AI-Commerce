package com.sh.aicommerce.paymentCompensation.service;

import com.sh.aicommerce.common.exception.paymentCompensation.PaymentCompensationException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.paymentCancellation.service.PaymentCancellationService;
import com.sh.aicommerce.paymentCompensation.record.PaymentCompensationRequestRecord;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompensationService {
    private final PaymentCompensationTxService txService;
    private final TossPaymentClient client;
    private final PaymentCancellationService cancellationService;

    public void compensationProcess(Long compensationId) {
        try {
            PaymentCompensationRequestRecord record = txService.findPaymentAndAttempt(compensationId);
            if (record == null) return;
            // 외부 조회·취소 호출은 보상 준비 DB 트랜잭션이 종료된 후 수행합니다.
            TossPaymentRequestHistoryResponseDto response = client.findPaymentHistory(record.orderNumber());
            if (response == null || !("DONE".equals(response.getStatus()) || "CANCELED".equals(response.getStatus()))) {
                txService.defer(compensationId, "PG_RESULT_NOT_FINAL", false);
                return;
            }
            Long cancellationId = txService.prepareCancellation(compensationId, response);
            if (cancellationId != null) {
                // 기존 취소 서비스의 READY → PROCESSING 선점을 재사용합니다.
                cancellationService.process(cancellationId);
            }
        } catch (TossPaymentException e) {
            // 미조회는 승인 실패의 증거가 아닙니다. 예약 해제나 실패 확정을 하지 않습니다.
            saveFailure(compensationId, e.getErrorCode(), false, e);
        } catch (PaymentCompensationException e) {
            saveFailure(compensationId, "COMPENSATION_VALIDATION_FAILED", true, e);
        } catch (RuntimeException e) {
            saveFailure(compensationId, "COMPENSATION_EXECUTION_FAILED", false, e);
        }
    }

    private void saveFailure(Long compensationId, String code, boolean manual, RuntimeException cause) {
        log.error("[PAYMENT] stage=COMPENSATION event=FAILED compensationId={} errorCode={} errorType={}",
                compensationId, code, cause.getClass().getSimpleName());
        try {
            txService.defer(compensationId, code, manual);
        } catch (RuntimeException saveException) {
            log.error("[PAYMENT] stage=COMPENSATION_FAILURE_APPLY event=FAILED compensationId={} errorType={}",
                    compensationId, saveException.getClass().getSimpleName());
        }
    }
}
