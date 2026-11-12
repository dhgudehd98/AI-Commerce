package com.sh.aicommerce.paymentCancellationRecovery.service;


import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossCancelException;
import com.sh.aicommerce.orderCancellation.dto.record.CancellationRequestRecord;
import com.sh.aicommerce.paymentCancellation.service.PaymentCancellationService;
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
    private final PaymentCancellationService cancellationService;
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

            if (response != null && "DONE".equals(response.getStatus())
                    && (response.getCancels() == null || response.getCancels().isEmpty())) {
                CancellationRequestRecord record = recoveryTxService
                        .prepareRecoveryCancellation(historyId, dto, response);
                // DB 트랜잭션 밖에서 기존 작업·멱등성 키로 재요청. 실패는 아래 catch로 전달
                cancellationService.processRecovery(record);
                // 기존 조회 응답 검증·원장 저장·Recovery 완료 반영을 재사용합니다.
                response = client.findPaymentHistory(dto.getOrderNumber());
            }

            if (response == null || !"CANCELED".equals(response.getStatus())
                    || response.getCancels() == null || response.getCancels().isEmpty()) {
                recoveryTxService.updateRecoveryQueryFailure(historyId, "PG_CANCEL_NOT_CONFIRMED",
                        "PG에서 전체 취소 완료를 확인하지 못했습니다.", false);
                return;
            }

            // 취소 내역이 있으면 기존 정합성 검증 및 완료 반영
            recoveryTxService.applyPaymentCancellationResult(historyId, dto, response);
        } catch (TossCancelException e) {
            // 명확한 재요청 거절은 반복 호출하지 않고 관리자 확인으로 넘깁니다.
            saveFailure(historyId, dto, e, e.getErrorCode(), true, false);
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
