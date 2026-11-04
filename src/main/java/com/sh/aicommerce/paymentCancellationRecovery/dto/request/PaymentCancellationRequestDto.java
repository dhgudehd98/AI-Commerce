package com.sh.aicommerce.paymentCancellationRecovery.dto.request;

import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.entity.PaymentCancellation;
import com.sh.aicommerce.entity.PaymentCancellationRecovery;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCancellationRequestDto {
    private Long recoveryId;
    private Long paymentId;
    private Long orderId;
    private String orderNumber;
    private String idempotencyKey; // PG 요청 멱등성 키
    private String failureCode; // 실패 코드에 대한 값은 따로 설정하기 -> 애초에 Exception에 저장할때 설정하기
    private String failureMessage;

    public PaymentCancellationRequestDto(
            PaymentCancellationRecovery recovery,
            PaymentCancellation cancellation,
            Payment payment,
            Orders order
    ) {
        this.recoveryId = recovery.getId();
        this.paymentId = payment.getId();
        this.orderId = order.getId();
        this.orderNumber = order.getOrderNumber();
        this.idempotencyKey = cancellation.getIdempotencyKey();
        this.failureMessage = cancellation.getFailMessage();
    }
}
