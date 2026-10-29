package com.sh.aicommerce.paymentCancellationRecovery.service;


import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.paymentCancellationRecovery.dto.request.PaymentCancellationRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCancellationRecoveryService {

    private final PaymentCancellationRecoveryTxService recoveryTxService;
    private final TossPaymentClient client;
    public void recover(Long paymentCancellationId) {
        try {
            recoverPaymentCancellation(paymentCancellationId);
        } catch (RuntimeException e) {
            log.error("[PAYMENT_CANCEL] stage=RECOVERY_ITEM event=FAILED paymentCancellationId={}", paymentCancellationId, e);
        }
    }

    private void recoverPaymentCancellation(Long paymentCancellationId) {

        PaymentCancellationRequestDto dto = recoveryTxService.validateAndCreateRecovery(paymentCancellationId);

        if (dto == null) {
            return;
        }

        Long historyId = recoveryTxService.startPaymentCancellationRecovery(dto.getRecoveryId());

        if (historyId == null) {
            return;
        }

        try {
            TossPaymentRequestHistoryResponseDto response = client.findPaymentHistory(dto.getOrderNumber());

            // 결제는 조회됐지만 취소 내역이 없으면 승인 여부와 별개로 취소 결과 재확인 필요
            if (response != null
                    && (response.getCancels() == null || response.getCancels().isEmpty())) {
                log.warn("[PAYMENT_CANCEL] stage=RECOVERY_QUERY event=CANCEL_NOT_FOUND recoveryId={} historyId={} paymentId={} orderNumber={} pgStatus={}",
                        dto.getRecoveryId(), historyId, dto.getPaymentId(), dto.getOrderNumber(), response.getStatus());
                recoveryTxService.updateRecoveryQueryFailure(
                        historyId,
                        null,
                        "결제는 조회되었지만 PG 취소 내역이 확인되지 않았습니다.",
                        false
                );
                log.info("[PAYMENT_CANCEL] stage=RECOVERY_QUERY_FAILURE_APPLY event=TRANSACTION_RETURNED recoveryId={} historyId={} paymentId={}",
                        dto.getRecoveryId(), historyId, dto.getPaymentId());
                return;
            }

            // 취소 내역이 있으면 기존 정합성 검증 및 완료 반영
            recoveryTxService.applyPaymentCancellationResult(historyId, dto, response);
        } catch (TossPaymentException e) {
            String errorCode = e.getErrorCode();
            // 조회 오류는 취소 거절의 증거가 아님. 결제·주문·취소 상태와 예약 재고 유지
            // 결제 미조회·요청 제한·PG 내부 오류는 재시도, 인증·설정·기타 오류는 운영자 확인
            boolean retryable = "NOT_FOUND_PAYMENT".equals(errorCode)
                    || "FORBIDDEN_CONSECUTIVE_REQUEST".equals(errorCode)
                    || "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING".equals(errorCode);
            saveFailure(historyId, dto, e, errorCode, !retryable, false);
        } catch (TossRestClientException e) {
            saveFailure(historyId, dto, e, null, false, false);
        } catch (RuntimeException e) {
            saveFailure(historyId, dto, e, null, false, true);
        }
    }

    private void saveFailure(Long historyId, PaymentCancellationRequestDto dto, RuntimeException cause,
                             String errorCode, boolean manualReview, boolean applyFailure) {
        log.error("[PAYMENT_CANCEL] stage=RECOVERY_FAILURE event=FAILED recoveryId={} historyId={} paymentId={} orderNumber={} pgCode={} errorType={} applyFailure={}",
                dto.getRecoveryId(), historyId, dto.getPaymentId(), dto.getOrderNumber(), errorCode,
                cause.getClass().getSimpleName(), applyFailure, cause);
        try {
            if (applyFailure) {
                recoveryTxService.updateRecoveryApplyFailure(historyId, cause);
            } else {
                String message = cause.getMessage();
                if (message == null || message.isBlank()) {
                    message = cause.getClass().getSimpleName();
                }
                recoveryTxService.updateRecoveryQueryFailure(historyId, errorCode, message, manualReview);
            }
            log.info("[PAYMENT_CANCEL] stage=RECOVERY_FAILURE_APPLY event=TRANSACTION_RETURNED recoveryId={} historyId={} paymentId={}",
                    dto.getRecoveryId(), historyId, dto.getPaymentId());
        } catch (RuntimeException saveException) {
            saveException.addSuppressed(cause);
            log.error("[PAYMENT_CANCEL] stage=RECOVERY_FAILURE_APPLY event=FAILED recoveryId={} historyId={} paymentId={}",
                    dto.getRecoveryId(), historyId, dto.getPaymentId(), saveException);
        }
    }
}
