package com.sh.aicommerce.paymentCompensation.record;

/** 트랜잭션 밖 PG 조회에는 Entity 대신 불변의 식별 정보만 전달합니다. */
public record PaymentCompensationRequestRecord(
        Long compensationId, Long paymentId, Long attemptId, Long orderId,
        String orderNumber, Integer requestAmount
) {
}
