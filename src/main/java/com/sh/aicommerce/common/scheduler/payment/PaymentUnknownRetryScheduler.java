package com.sh.aicommerce.common.scheduler.payment;


import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.payment.service.PaymentRetryTxService;
import com.sh.aicommerce.payment.service.PaymentTxService;
import com.sh.aicommerce.payment.service.UnknownPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentRequestHistoryResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentUnknownRetryScheduler {

    private final PaymentRepository paymentRepository;

    private final PaymentRetryTxService retryTxService;

    private final TossPaymentClient client;



    /**
     * UNKNOWN 결제 재처리 스케줄러
     *
     * 1. UNKNOWN 결제 조회
     * 2. 복구 대상 검증 및 생성
     * 3. 복구 시작 처리
     * 4. Toss 결제 결과 조회
     * 5. 조회 결과 DB 반영
     */
//    @Scheduled
    public void unknownPaymentRetry() {
        log.info("[PAYMENT] stage=RECOVERY_SCAN event=START");
        List<Long> unknownPaymentList = retryTxService.getUnknownPaymentRetry();
        log.info("[PAYMENT] stage=RECOVERY_SCAN event=CANDIDATES count={}", unknownPaymentList.size());

        // 결제 결과 확인 필요에 대한 상황이 없는 경우에는 스케줄러 종료
        if(unknownPaymentList.isEmpty())
            return;

        for (Long paymentId : unknownPaymentList) {
            try {
                log.info("[PAYMENT] stage=RECOVERY_ITEM event=START paymentId={}", paymentId);
                unknownPaymentDetailInfo(paymentId);
            } catch (Exception e) {
                log.error(
                        "[PAYMENT] stage=RECOVERY_ITEM event=FAILED paymentId={} errorType={}",
                        paymentId, e.getClass().getSimpleName()
                );
            }
        }
    }

    public void unknownPaymentDetailInfo(Long paymentId) {
        log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=START paymentId={}", paymentId);
        UnknownPaymentRequestDto dto = retryTxService.validatePayment(paymentId);
        if(dto == null) {
            log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=SKIPPED paymentId={}", paymentId);
            return;
        }
        log.info("[PAYMENT] stage=RECOVERY_VALIDATE event=COMMITTED paymentId={} orderNumber={} attemptId={} recoveryId={}", dto.getPaymentId(), dto.getOrderNumber(), dto.getAttemptId(), dto.getRecoveryId());

        Long historyId = retryTxService.startPaymentRecovery(dto.getRecoveryId());

        if(historyId == null) {
            log.info("[PAYMENT] stage=RECOVERY_CLAIM event=SKIPPED paymentId={} recoveryId={}", paymentId, dto.getRecoveryId());
            return;
        }
        log.info("[PAYMENT] stage=RECOVERY_CLAIM event=COMMITTED paymentId={} recoveryId={} historyId={}", paymentId, dto.getRecoveryId(), historyId);

        //Unknown이 되는 상황에서는 DB 업데이트 문제가 생긴경우에는 해당 상태 값으로 변하지 않음. 외부 API 응답 문제 일수도 있기 떄문에
        // 하지만 그래도 토스 API 요청은 확인을 해봐야될거 같긴하다
        try {
            // 결제 조회에 대한 부분은 멱등성 키로 하는 것이 아닌 주문번호에 대한 값으로 확인
            TossPaymentRequestHistoryResponseDto tossResponseDto = client.findPaymentHistory(dto.getOrderNumber());
            log.info("[PAYMENT] stage=RECOVERY_QUERY event=RESPONSE_RECEIVED paymentId={} attemptId={} orderNumber={} recoveryId={} historyId={} pgStatus={} approvedAmount={}",
                    paymentId, dto.getAttemptId(), dto.getOrderNumber(), dto.getRecoveryId(), historyId,
                    tossResponseDto.getStatus(), tossResponseDto.getTotalAmount());

            // 결제 내역이 존재하는 경우
            retryTxService.applyPaymentRecoveryResult(historyId, dto, tossResponseDto);
            log.info("[PAYMENT] stage=RECOVERY_APPLY event=TRANSACTION_RETURNED paymentId={} attemptId={} orderNumber={} recoveryId={} historyId={}",
                    paymentId, dto.getAttemptId(), dto.getOrderNumber(), dto.getRecoveryId(), historyId);
            // PG 조회 결과가 있는 경우에는 DB 업데이트 결제 완료 및 실패로 변경
        } catch (TossPaymentException paymentException) {
            /*
             * 결제 조회 API 오류 코드별 복구 정책
             *
             * [결제 내역 없음 → 최대 횟수까지 재조회]
             * - 404 NOT_FOUND_PAYMENT: 주문번호에 해당하는 결제 정보 없음
             *   최대 횟수 도달 시 기존 정책에 따라 결제 최종 실패 및 예약 재고 해제
             *
             * [일시적 조회 오류 → 재시도 대기]
             * - 403 FORBIDDEN_CONSECUTIVE_REQUEST: 반복 요청 제한
             * - 500 FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING: PG 내부 처리 오류
             *   최대 횟수 미만: RETRY_WAIT / 최대 횟수 도달: MANUAL_REVIEW
             *
             * [설정 오류·분류 불가 → 즉시 운영자 확인]
             * - 401 UNAUTHORIZED_KEY: 인증되지 않은 시크릿 키 또는 클라이언트 키
             * - 403 INCORRECT_BASIC_AUTH_FORMAT: ':' 누락 등 Basic 인증 인코딩 형식 오류
             * - 404 NOT_FOUND: 일반적인 정보 없음, 결제 정보 없음으로 확정 불가
             * - 400 NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN_BELOW_AMOUNT: 할부 최소 금액 조건 오류
             * - 오류 코드 누락 또는 기타 미분류 오류
             *   재시도 횟수와 관계없이 MANUAL_REVIEW 전환
             *
             * 조회 실패만으로 실제 결제 거절 확정 불가
             * NOT_FOUND_PAYMENT 외 경로: Recovery·History만 변경, 결제 상태·예약 재고 유지
             */
            String errorCode = paymentException.getErrorCode();
            log.warn("[PAYMENT] stage=RECOVERY_QUERY event=HTTP_ERROR paymentId={} recoveryId={} historyId={} pgCode={}", paymentId, dto.getRecoveryId(), historyId, paymentException.getErrorCode());

            try {
                if (errorCode == null) {
                    retryTxService.updateRecoveryForManualReview(historyId, paymentException);
                } else {
                    switch (errorCode) {
                        case "NOT_FOUND_PAYMENT" -> retryTxService.updateRecoveryNotFoundPayment(
                                historyId, errorCode, paymentException.getMessage());
                        case "FORBIDDEN_CONSECUTIVE_REQUEST", "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING" ->
                                retryTxService.updateRecoveryQueryFailure(
                                        historyId, errorCode, paymentException.getMessage());
                        default -> retryTxService.updateRecoveryForManualReview(historyId, paymentException);
                    }
                }
                log.info("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=TRANSACTION_RETURNED paymentId={} recoveryId={} historyId={} pgCode={}",
                        paymentId, dto.getRecoveryId(), historyId, errorCode);
            } catch (RuntimeException saveException) {
                saveException.addSuppressed(paymentException);
                log.error("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=FAILED paymentId={} recoveryId={} historyId={} pgCode={}",
                        paymentId, dto.getRecoveryId(), historyId, errorCode, saveException);
            }
        } catch (TossRestClientException restClientException) {
            log.warn("[PAYMENT] stage=RECOVERY_QUERY event=RESULT_UNKNOWN paymentId={} recoveryId={} historyId={} errorType={}", paymentId, dto.getRecoveryId(), historyId, restClientException.getClass().getSimpleName());
            // 통신 오류가 있을 때도 해당 내역 값 저장해야되잖아
            try {
                retryTxService.updatePaymentRecoveryByRestClient(historyId, restClientException);
                log.info("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=TRANSACTION_RETURNED paymentId={} recoveryId={} historyId={}",
                        paymentId, dto.getRecoveryId(), historyId);
            } catch (RuntimeException saveException) {
                saveException.addSuppressed(restClientException);
                log.error("[PAYMENT] stage=RECOVERY_QUERY_FAILURE_APPLY event=FAILED paymentId={} recoveryId={} historyId={} errorType={}",
                        paymentId, dto.getRecoveryId(), historyId, saveException.getClass().getSimpleName(), saveException);
            }
        } catch (RuntimeException e) {
            log.error("[PAYMENT] stage=RECOVERY_APPLY event=FAILED paymentId={} attemptId={} orderNumber={} recoveryId={} historyId={} errorType={}",
                    paymentId, dto.getAttemptId(), dto.getOrderNumber(), dto.getRecoveryId(), historyId,
                    e.getClass().getSimpleName());
            try {
                retryTxService.updateRecoveryAndHistoryFail(historyId, e);
            } catch (RuntimeException saveException) {
                e.addSuppressed(saveException);

                log.error(
                        "[PAYMENT] stage=RECOVERY_FAILURE_APPLY event=FAILED "
                                + "paymentId={} recoveryId={} historyId={} errorType={}",
                        paymentId,
                        dto.getRecoveryId(),
                        historyId,
                        saveException.getClass().getSimpleName()
                );
            }
        }

    }
}
