package com.sh.aicommerce.paymentCancellation.service;

import com.sh.aicommerce.common.exception.toss.TossCancelException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.orderCancellation.dto.record.CancellationRequestRecord;
import com.sh.aicommerce.paymentCancellation.service.PaymentCancellationTxService;
import com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCancellationService {
    private final PaymentCancellationTxService txService;
    private final TossPaymentClient client;

    /** 항목별 예외 격리 및 원인 기록 */
    public void process(Long paymentCancellationId) {
        log.info("[PAYMENT_CANCEL] stage=PROCESS event=START paymentCancellationId={}", paymentCancellationId);
        try {
            cancelPayment(paymentCancellationId);
        } catch (RuntimeException e) {
            log.error("[PAYMENT_CANCEL] stage=PROCESS event=FAILED paymentCancellationId={}",
                    paymentCancellationId, e);
        }
    }

    /** 검증·선점 커밋 → PG 호출 → 별도 트랜잭션 결과 반영 */
    public void cancelPayment(Long paymentCancellationId) {
        // 호출 전 검증 실패는 UNKNOWN 처리 대상에서 제외
        txService.validateAndPaymentCancellation(paymentCancellationId);
        CancellationRequestRecord record = txService.startCancellation(paymentCancellationId);
        if (record == null) {
            log.info("[PAYMENT_CANCEL] stage=CLAIM event=SKIPPED paymentCancellationId={}", paymentCancellationId);
            return;
        }

        log.info("[PAYMENT_CANCEL] stage=CLAIM event=TRANSACTION_RETURNED paymentCancellationId={} orderCancellationId={} orderNumber={}", record.paymentCancellationId(), record.orderCancellationId(), record.orderNumber());
        try {
            log.info("[PAYMENT_CANCEL] stage=PG_REQUEST event=START paymentCancellationId={} orderNumber={}", record.paymentCancellationId(), record.orderNumber());
            TossPaymentCancelResponseDto response = client.cancel(record);
            log.info("[PAYMENT_CANCEL] stage=PG_REQUEST event=RESPONSE_RECEIVED paymentCancellationId={} orderNumber={} pgStatus={}", record.paymentCancellationId(), record.orderNumber(), response == null ? null : response.getStatus());
            txService.cancel(response, record);
            log.info("[PAYMENT_CANCEL] stage=RESULT_APPLY event=TRANSACTION_RETURNED paymentCancellationId={} orderNumber={}", record.paymentCancellationId(), record.orderNumber());
        } catch (TossCancelException e) {
            log.warn("[PAYMENT_CANCEL] stage=PG_REQUEST event=REJECTED paymentCancellationId={} orderNumber={} pgCode={}", record.paymentCancellationId(), record.orderNumber(), e.getErrorCode());
            try {
                txService.rejectCancel(record, e);
                log.info("[PAYMENT_CANCEL] stage=REJECT_APPLY event=TRANSACTION_RETURNED paymentCancellationId={}", record.paymentCancellationId());
            } catch (RuntimeException saveException) {
                saveException.addSuppressed(e);
                saveUnknown(record, saveException);
            }
        } catch (TossRestClientException e) {
            saveUnknown(record, e);
        } catch (RuntimeException e) {
            // PG 응답 정합성 검증 또는 DB 반영 실패
            saveUnknown(record, e);
        }
    }

    private void saveUnknown(CancellationRequestRecord record, RuntimeException cause) {
        log.error("[PAYMENT_CANCEL] stage=RESULT_APPLY event=RESULT_UNKNOWN orderCancellationId={} paymentCancellationId={} errorType={}",
                record.orderCancellationId(), record.paymentCancellationId(), cause.getClass().getSimpleName(), cause);
        try {
            txService.unknownCancel(record, cause);
            log.info("[PAYMENT_CANCEL] stage=UNKNOWN_APPLY event=TRANSACTION_RETURNED paymentCancellationId={}", record.paymentCancellationId());
        } catch (RuntimeException saveException) {
            saveException.addSuppressed(cause);
            log.error("[PAYMENT_CANCEL] stage=UNKNOWN_APPLY event=FAILED orderCancellationId={} paymentCancellationId={}",
                    record.orderCancellationId(), record.paymentCancellationId(), saveException);
        }
    }
}
