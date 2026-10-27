package com.sh.aicommerce.enums.payment;

import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.payment.dto.response.PaymentCompleteDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;

public record PaymentDecision (
        PaymentBillingCardRequestDto cardRequestDto, // PG 요청 전 정리한 데이터 값
        TossPaymentSuccessResponseDto responseDto, // PG 요청 후 PG 응답 데이터
        PaymentAction action,
        PaymentCompleteDto completeDto // 이미 완료된 데이터
){
}