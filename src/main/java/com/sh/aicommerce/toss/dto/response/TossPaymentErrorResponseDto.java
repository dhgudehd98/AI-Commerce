package com.sh.aicommerce.toss.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TossPaymentErrorResponseDto {

    String code;
    String message;
}