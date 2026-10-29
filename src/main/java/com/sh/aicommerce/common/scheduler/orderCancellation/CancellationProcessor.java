package com.sh.aicommerce.common.scheduler.orderCancellation;

import com.sh.aicommerce.common.exception.orderCancellation.OrderCancellationException;
import com.sh.aicommerce.common.exception.paymentAttempt.PaymentAttemptException;
import com.sh.aicommerce.common.exception.toss.TossCancelException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.PaymentAttemptStatus;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.orderCancellation.dto.record.CancellationRequestRecord;
import com.sh.aicommerce.orderCancellation.repository.OrderCancellationRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
import com.sh.aicommerce.paymentCancellation.repository.PaymentCancellationRepository;
import com.sh.aicommerce.paymentCancellation.service.PaymentCancellationTxService;
import com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class CancellationProcessor {

    private final PaymentCancellationTxService txService;
    private final TossPaymentClient client;

    /**
     * 주문 취소 처리 요청 Flow
     * 1. OrderCancellation Status : CANCEL_REQUEST 데이터 수집
     * 2. 데이터 검증
     *   - Payment, Order, PaymentAttempt : PAID 상태인지 확인
     *   - OrderCancellation , Order 동일한 값을 가지고 있는지 확인
     *   - PaymentCancellation이 이미 있는지 확인
     * 3. PaymentCancellation(주문 취소 요청 Entity) 생성
     *
     */
    public void process(Long orderCancellationId) {
        try {
            cancelPayment(orderCancellationId);
        } catch (RuntimeException e) {
            log.error("에러 발생 에러 메세지 : {}", e.getMessage());
        }

    }

    public void cancelPayment(Long orderCancellationId) {

        txService.validateAndPaymentCancellation(orderCancellationId);
        CancellationRequestRecord record = txService.startCancellation(orderCancellationId);

        if (record == null) {
            return;
        }

        try {
            // TossAPI 결제 취소 요청
            TossPaymentCancelResponseDto response = client.cancel(record);
            // 결제 취소 요청 완료 시, 상태 값 변경
            txService.cancel(response, record);
        } catch (TossCancelException cancelException) {

        } catch (TossRestClientException restClientException) {

        } catch (RuntimeException runtimeException) {

        }
    }


}